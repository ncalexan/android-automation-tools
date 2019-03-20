/* Any copyright is dedicated to the Public Domain.
 * http://creativecommons.org/publicdomain/zero/1.0/ */

package org.mozilla.android.plugin

import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import org.junit.jupiter.api.io.TempDir
import java.io.File

open class AbstractPluginTest {
    fun build(vararg arguments: String): BuildResult =
            GradleRunner
                    .create()
                    .withProjectDir(temporaryFolder)
                    .withPluginClasspath()
                    .withArguments(*arguments)
//                    .withDebug(true)
                    .build()

    fun givenBuildScript(script: String) =
            File(temporaryFolder, "build.gradle.kts").apply {
                writeText(script)
            }

    @TempDir
    @JvmField
    var temporaryFolder: File? = null
}
