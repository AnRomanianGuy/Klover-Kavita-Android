This folder is not used by the app build. `com.romanianguy.klover.yml` is the build recipe for the F-Droid repository: it goes to
[fdroiddata](https://gitlab.com/fdroid/fdroiddata) as `metadata/com.romanianguy.klover.yml`, in a merge request. It was formatted
and checked with F-Droid's own `fdroid rewritemeta`, `fdroid lint` and `fdroid checkupdates`.

The store listing (title, descriptions, changelogs, icon, screenshots) is in `fastlane/metadata/android/en-US/` in the root of
the repository, where F-Droid reads it.
