# Mozilla Android gradle-plugin
The [`org.mozilla.android` gradle plugin, hosted on plugins.gradle.org][plugins], is used to share build code across Mozilla's Android projects like Firefox for Fire TV.

## Using the plugin
To use the plugin, you must first apply it and then configure specific tasks
you want to run. To apply the plugin, use the legacy plugin application method.

In `<project-root>/build.gradle`:
```groovy
buildscript {
  repositories {
    maven {
      url "https://plugins.gradle.org/m2/"
    }
  }
  dependencies {
    classpath "org.mozilla.android:gradle-plugin:0.2.1"
  }
}
```

In the module to use the plugin, e.g. `<project-root>/app/build.gradle`:
```groovy
apply plugin: "org.mozilla.android"
```

Applying the plugin via the modern plugins DSL syntax is currently broken
([#37](https://github.com/mozilla-mobile/android-automation-tools/issues/37)).

### Consume a local GeckoView

The plugin helps you use a locally-built GeckoView in a downstream GeckoView-consuming App.  Apply the plugin and, in `local.properties` in the root directory of the consuming App -- e.g., in `/path/to/firefox-tv/local.properties` or `/path/to/fenix/local.properties` -- add a line like:

```
local-geckoview.topsrcdir=/absolute/path/to/mozilla-central
```

This will use the local GeckoView produced by the default object directory corresponding to that source directory (i.e., corresponding to your `mozconfig` or `.mozconfig`).  If you have multiple object directories, set both the source directory _and_ the object directory, like:

```
local-geckoview.topsrcdir=/absolute/path/to/mozilla-central
local-geckoview.topsrcdir=/absolute/path/to/mozilla-central/objdir
```

This functionality will remain in place until such time as Gradle composite builds work well with `mozilla-central`.

#### Updating the local GeckoView

The plugin only consumers a locally-built GeckoView: it does not build or in any other way change your object directory.

To update your local GeckoView, do the following.  **If you're doing full builds of Gecko**, invoke (in your source directory):

```shell
./mach build binaries && ./mach gradle geckoview:publishWithGeckoBinariesDebugPublicationToMavenRepository
```

**If you're using artifact builds of Gecko**, invoke:

```shell
./mach gradle geckoview:publishWithGeckoBinariesDebugPublicationToMavenRepository
```

Then invoke the downstream GeckoView-consuming App's Gradle command, which is often `./gradlew app:assemble...` or `./gradlew app:install...`.  Changes to GeckoView (both to libraries like `libxul.so` and to JS resources in the `omni.ja`) should be incorporated into the downstream App's built artifacts immediately.

### Tasks
The following tasks can be imported from `org.mozilla.android.tasks` (click the links for more details):
- [`ValidateAndroidAppReleaseConfiguration`][validate]: runs validation on `assembleRelease` such as ensuring there are no uncommitted changes and there is a checked out git tag

### Repository injection
Application of this plugin to a project will inject shared Mozilla repositories. Currently injected repositories are:
- `appservices`: `https://dl.bintray.com/ncalexander/application-services`

#### Adding a task to your project
After applying the plugin, you can apply a task, like `ValidateAndroidAppReleaseConfiguration`, by:
```groovy
import org.mozilla.android.tasks.*

task taskName(type: ValidateAndroidAppReleaseConfiguration) {
    // Configure the task...
}
```

## Developing the gradle-plugin
To work on the gradle-plugin, start by importing the `android-automation-tools`
repository into Android Studio.

### Developing the plugin against local repositories
Like [the android components suggest][components local], it's preferable to avoid depending on apps outside of the repository. Instead:
- Write unit tests and/or Gradle TestKit tests.
- Work against the sample app in `samples/app`.  For example, to test consuming a local GeckoView, modify `samples/local.properties` and run `./gradlew -p samples app:assembleDebug`, etc.

If you wish to develop against other local repositories, it's best to use Gradle composite builds, following the model of the samples.

### Publishing to plugins.gradle.org
To publish the plugin to the gradle plugin portal, first [create credentials][]. After making your changes and bumping the version, run the following from the root directory:
```gradlew
./gradlew gradle-plugin:publishPlugins
```

[plugins]: https://plugins.gradle.org/plugin/org.mozilla.android
[components local]: https://mozilla-mobile.github.io/android-components/contributing/testing-components-inside-app
[create credentials]: https://guides.gradle.org/publishing-plugins-to-gradle-plugin-portal/#create_an_account_on_the_gradle_plugin_portal
[validate]: https://github.com/mozilla-mobile/android-automation-tools/blob/master/gradle-plugin/src/main/kotlin/org/mozilla/android/tasks/ValidateAndroidAppReleaseConfiguration.kt
