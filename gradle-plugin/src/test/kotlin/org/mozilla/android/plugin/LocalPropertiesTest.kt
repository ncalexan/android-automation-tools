/* Any copyright is dedicated to the Public Domain.
 * http://creativecommons.org/publicdomain/zero/1.0/ */

package org.mozilla.android.plugin

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class LocalPropertiesTest: AbstractPluginTest() {

    @Test
    fun `local properties is empty when absent`() {
        givenBuildScript("""
            plugins {
                id("org.mozilla.android")
            }

            tasks.register("doTest") {
                doLast {
                    var localProperties: java.util.Properties = project.ext.get("localProperties") as java.util.Properties
                    println(localProperties.stringPropertyNames().size)
                }
            }
        """)

        assertEquals("0",
                build("doTest", "--quiet").output.trimEnd())
    }

    @Test
    fun `local properties is populated when present`() {
        givenBuildScript("""
            plugins {
                id("org.mozilla.android")
            }

            tasks.register("doTest") {
                doLast {
                    var localProperties: java.util.Properties = project.ext.get("localProperties") as java.util.Properties
                    println(localProperties.stringPropertyNames().size)
                }
            }
        """)

        File(this.temporaryFolder, "local.properties").writeText("foo=bar")

        assertEquals("1",
                build("doTest", "--quiet").output.trimEnd())
    }
}
