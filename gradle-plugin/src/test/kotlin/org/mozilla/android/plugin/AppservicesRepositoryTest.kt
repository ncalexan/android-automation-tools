/* Any copyright is dedicated to the Public Domain.
 * http://creativecommons.org/publicdomain/zero/1.0/ */

package org.mozilla.android.plugin

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class AppservicesRepositoryTest: AbstractPluginTest() {

    @Test
    fun `appservices repository is inserted at the head of the list of repositories`() {
        givenBuildScript("""
            repositories {
                jcenter()
            }

            plugins {
                id("org.mozilla.android")
            }

            repositories {
                mavenCentral()
            }

            tasks.register("doTest") {
                doLast {
                    println(project.repositories.toList().map { it.getName() }.joinToString(":"))
                }
            }
        """)

        assertEquals(
                build("doTest", "--quiet").output.trimEnd(),
                "appservices:BintrayJCenter:MavenRepo")
    }
}
