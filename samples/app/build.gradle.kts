/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

plugins {
    id("com.android.application")
    id("kotlin-android")
    id("kotlin-android-extensions")
}

apply(plugin="org.mozilla.android")

android {
    compileSdkVersion(28)
    defaultConfig {
        applicationId = "org.mozilla.android.test"
        minSdkVersion(21)
        targetSdkVersion(28)
        versionCode = 1
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
}

dependencies {
    implementation("org.mozilla.geckoview:geckoview-nightly-armeabi-v7a:+")
}
