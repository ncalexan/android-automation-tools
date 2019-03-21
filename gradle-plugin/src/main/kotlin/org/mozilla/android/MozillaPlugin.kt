/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.android

import com.beust.klaxon.JsonObject
import com.beust.klaxon.Parser
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.component.ModuleComponentSelector
import org.gradle.api.artifacts.repositories.IvyPatternRepositoryLayout
import org.gradle.api.artifacts.repositories.MavenArtifactRepository
import org.gradle.api.artifacts.repositories.RepositoryLayout
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging
import org.mozilla.android.ext.execWaitForStdOut
import java.io.File
import java.net.URI
import java.util.*

class MozillaPlugin : Plugin<Project> {
    lateinit var pluginLogger: Logger

    override fun apply(project: Project) {
        pluginLogger = Logging.getLogger("org.mozilla.android")

        injectLocalPropertiesExtension(project)
        injectAppServicesMavenRepository(project)
        substituteLocalGeckoView(project)
    }

    /**
     * Read `$rootDir/local.properties` and expose it as a `Properties` instance in `project` extension map.
     *
     * If `local.properties` cannot be read, this will be an empty `Properties()` instance.
     */
    private fun injectLocalPropertiesExtension(project: Project) {
        val localProperties: Properties = Properties()
        val file = project.rootProject.file("local.properties")
        if (file.canRead()) {
            localProperties.load(file.inputStream())
            pluginLogger.lifecycle("Loaded local properties: ${file}", file.absolutePath)
        } else {
            pluginLogger.lifecycle("No local properties loaded; did not find: ${file}", file.absolutePath)
        }
        project.extensions.extraProperties.set("localProperties", localProperties)
    }

    private fun substituteLocalGeckoView(project: Project) {
        val localProperties: Properties = project.extensions.extraProperties.get("localProperties") as Properties
        val maybeTopsrcdir: String? = localProperties.getProperty("local-geckoview.topsrcdir")
        val maybeTopobjdir: String? = localProperties.getProperty("local-geckoview.topobjdir")

        if (maybeTopsrcdir == null) {
            pluginLogger.lifecycle("Not substituting local GeckoView: see https://github.com/mozilla-mobile/android-automation-tools/tree/master/gradle-plugin#consume-a-local-geckoview for instructions.")
            return
        }

        require(File(maybeTopsrcdir).isAbsolute) { "local-geckoview.topsrcdir must be absolute" }
        val topsrcdir = maybeTopsrcdir

        // Cribbed from https://hg.mozilla.org/mozilla-central/file/tip/settings.gradle.  When run in
        // topobjdir, `mach environment` correctly finds the mozconfig corresponding to that object
        // directory.
        val commandLine = arrayOf("${topsrcdir}/mach", "environment", "--format", "json", "--verbose")
        val standardOutput = Runtime.getRuntime().execWaitForStdOut(commandLine, null, File(maybeTopobjdir ?: topsrcdir))

        val mozconfig = Parser.default().parse(standardOutput.reader()) as JsonObject

        if (topsrcdir != mozconfig.string("topsrcdir")) {
            throw IllegalArgumentException("Specified topsrcdir ('${topsrcdir}') is not mozconfig topsrcdir ('${mozconfig.string("topsrcdir")}')")
        }

        if (maybeTopobjdir != null) {
            require(File(maybeTopobjdir).isAbsolute) { "local-geckoview.topobjdir must be absolute" }
        }
        if (maybeTopobjdir == null) {
            pluginLogger.lifecycle("Found topobjdir ${mozconfig.string("topobjdir")} from topsrcdir ${topsrcdir}")
        }
        val topobjdir = maybeTopobjdir ?: mozconfig.string("topobjdir")

        if (mozconfig.obj("substs")?.string("MOZ_BUILD_APP") != "mobile/android") {
            throw IllegalStateException("Building with Gradle is only supported for Fennec, i.e., MOZ_BUILD_APP == 'mobile/android'.")
        }

        pluginLogger.lifecycle("Will substitute GeckoView with local GeckoView from ${topobjdir}/gradle/build/mobile/android/geckoview/maven")

        if (mozconfig.obj("substs")?.string("COMPILE_ENVIRONMENT") == null) {
            pluginLogger.lifecycle("To update the local GeckoView, run `./mach gradle geckoview:publishWithGeckoBinariesDebugPublicationToMavenRepository` in ${topsrcdir}")
        } else {
            pluginLogger.lifecycle("To update the local GeckoView, run `./mach build binaries && ./mach gradle geckoview:publishWithGeckoBinariesDebugPublicationToMavenRepository` in ${topsrcdir}")
        }

        project.repositories.ivy { ivy ->
            ivy.url = project.uri("${topobjdir}/gradle/build/mobile/android/geckoview/maven")
            ivy.layout("pattern") { layout: RepositoryLayout ->
                with(layout as IvyPatternRepositoryLayout) {
                    // A regular Maven repository has
                    //
                    // artifact("[organisation]/[module]/[revision]/[artifact]-[revision](-[classifier])(.[ext])")
                    //
                    // But that doesn't play well with Jetifier, which appears to capture the artifact
                    // name pre-substitution: perhaps our substitutions and Jetifier's substitutions are
                    // fighting?  Using an Ivy repository and using the [module] rather than the
                    // [artifact] seems to address the issue and let's Jetifier find the correct AAR.
                    //
                    // In addition, we need "org/mozilla/geckoview" and [organisation] gives
                    // "org.mozilla.geckoview", so we hard-code the group in the path.  See
                    // https://docs.gradle.org/current/dsl/org.gradle.api.artifacts.repositories.IvyArtifactRepository.html#org.gradle.api.artifacts.repositories.IvyArtifactRepository:layout(java.lang.String,%20groovy.lang.Closure).
                    artifact("org/mozilla/geckoview/[module]/[revision]/[module]-[revision](-[classifier])(.[ext])")

                    // This is unused; it just needs to be a file that isn't found.  If we don't have
                    // this, the artifact (.aar) is interpreted as an Ivy XML file, which doesn't work.
                    ivy("ivy-files/[organisation]/[module]/[revision]/ivy.xml")
                }
            }
        }

        /**
         * Android dependencies default to the "@aar" classifier.  Substituting dependencies "loses"
         * the default after substitution.  We can either force consumers to specify (with "@aar")
         * themselves or we can patch things up ourselves.  There are many consumers so we patch
         * things up ourselves.  However, classifier specifications are second-class citizens in
         * Gradle, so patching things is equivalent to removing the existing dependency and adding a
         * new dependency with a non-default classifier.  Janky, but it seems to work.
         *
         * Why not just replace the dependency here?  We could, but it looks more mysterious.  In
         * the output of `dependencies` tasks, for instance, doing this patching up and then
         * substituion gives helpful output showing `before -> after` mappings.
         */
        fun forceGeckoViewDependenciesToHaveAarClassifier(project: Project): Boolean {
            var anyRemoved = false
            var anyAdded = false

            project.configurations.all { config ->
                if (config.isCanBeResolved()) {
                    val seen: MutableSet<Configuration> = mutableSetOf()
                    val next: MutableList<Configuration> = mutableListOf(config)
                    while (next.isNotEmpty()) {
                        val c = next.removeAt(0)
                        if (!seen.add(c)) {
                            continue
                        }
                        next.addAll(c.extendsFrom.toList())

                        val toRemove = c.dependencies.filter { dep -> dep.group == "org.mozilla.geckoview" && dep.name.startsWith("geckoview-nightly") }
                        for (d in toRemove) {
                            val removed = c.dependencies.remove(d)
                            val added = c.dependencies.add(project.dependencies.create("${d.group}:${d.name}:${d.version}@aar"))
                            pluginLogger.debug("Forced geckoview dependency to have @aar classifier in ${c} (inherited from ${config})")

                            anyRemoved = anyRemoved || removed
                            anyAdded = anyAdded || added
                        }
                    }
                }
            }

            return (anyRemoved || anyAdded)
        }

        /**
         * If the given configuration consumes GeckoView dependencies, substitute for the local
         * GeckoView dependency instead.
         */
        fun substituteGeckoViewDependenciesForLocalGeckoView(config: Configuration, arch: String) {
            if (!config.isCanBeResolved()) {
                return
            }

            config.resolutionStrategy { strategy ->
                strategy.dependencySubstitution.all inner@{dependency ->
                    val requested = dependency.requested as? ModuleComponentSelector
                    if (requested == null) {
                        return@inner
                    }

                    // Right now we replace all architectures with the local GeckoView
                    // architecture.  Some of the APKs produced won't run on the install ABI,
                    // because the libs are wrong.  This should be more obvious than not
                    // replacing, which will work but not pick up local changes.
                    val name = "geckoview-default-${arch}"

                    val group = requested.group
                    if (group == "org.mozilla.geckoview" && requested.module.startsWith("geckoview-nightly")) {
                        pluginLogger.lifecycle("Substituting ${group}:${requested.module} with local GeckoView ${group}:${name} in ${config}")

                        dependency.useTarget(mapOf("group" to group, "name" to name, "version" to "+"))

                        // We substitute with a dynamic version ("+").  It seems that Gradle
                        // discovers the underlying AAR is out of date correctly based on file
                        // timestamp already, but let's try to avoid some class of cache
                        // invalidation error while we're here.
                        strategy.cacheDynamicVersionsFor(0, "seconds")
                    }
                }
            }
        }

        val arch = mozconfig.obj("substs")!!.string("ANDROID_CPU_ARCH")!!

        project.afterEvaluate { p ->
            val anyChanged = forceGeckoViewDependenciesToHaveAarClassifier(p)
            check(anyChanged) { "Local GeckoView substitution requested but no GeckoView dependencies detected." +
                    "Something has gone wrong, or you need to remove local-geckoview.* from local.properties." }
        }

        project.configurations.all { config ->
            substituteGeckoViewDependenciesForLocalGeckoView(config, arch)
        }
    }

    private fun injectAppServicesMavenRepository(project: Project) {
        with(project) {
            // Add a custom repository for application-services dependency resolution.
            // This should be temporary while https://github.com/mozilla/application-services
            // isn't publishing to maven.mozilla.org yet.
            // See https://github.com/mozilla/application-services/issues/252.
            val customName = "appservices"
            val customURI = URI.create("https://dl.bintray.com/ncalexander/application-services")

            // NB: if you change this repository, or add more repositories, please update the repository
            // injection section of the README.me.

            // If there's already a Maven repo with the right URL, or even the right name, roll with it.
            // The name gives the opportunity to customize, if it helps in the wild.
            val existing = project.repositories.find {
                (it is MavenArtifactRepository) && (it.url == customURI || it.name == customName)
            }

            // Otherwise, inject the dependency.
            if (existing == null) {
                pluginLogger.info("Injecting repository for project '${project}': '${customName}' Maven repository with url '${customURI.toASCIIString()}'")
                val customMavenRepo = project.repositories.maven {
                    it.name = customName
                    it.url = customURI
                }

                project.repositories.removeAt(project.repositories.size - 1)
                project.repositories.addFirst(customMavenRepo)

                pluginLogger.debug("Repository list for ${project} after injection:")
                project.repositories.toList().forEach {
                    logger.info("- ${it.getName()}")
                }
            }
        }
    }
}
