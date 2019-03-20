/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.android

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.repositories.MavenArtifactRepository
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging
import java.net.URI
import java.util.*

class MozillaPlugin : Plugin<Project> {
    lateinit var pluginLogger: Logger

    override fun apply(project: Project) {
        pluginLogger = Logging.getLogger("org.mozilla.android")

        injectLocalPropertiesExtension(project)
        injectAppServicesMavenRepository(project)
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
