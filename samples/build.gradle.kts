/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

buildscript {
    repositories {
        google()
        jcenter()
    }

    dependencies {
        classpath("com.android.tools.build:gradle:3.2.1")
        classpath(kotlin("gradle-plugin", version = "1.3.20"))
        classpath("org.mozilla.android:gradle-plugin:+") // Substituted in.
    }
}

allprojects {
    repositories {
        google()
        jcenter()
        maven {
            name = "mozilla"
            url = uri("https://maven.mozilla.org/maven2")
        }
    }
}
