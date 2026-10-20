This folder is not used by the app build. `com.romanianguy.klover.yml` is the build recipe for the F-Droid repository: it goes to
[fdroiddata](https://gitlab.com/fdroid/fdroiddata) as `metadata/com.romanianguy.klover.yml`, in a merge request. It was formatted
and checked with F-Droid's own `fdroid rewritemeta`, `fdroid lint` and `fdroid checkupdates`.

The store listing (title, descriptions, changelogs, icon, screenshots) is in `fastlane/metadata/android/en-US/` in the root of
the repository, where F-Droid reads it.

The build points at a commit rather than the `v1.1.1` tag because F-Droid only takes the icon and screenshots from the source it
builds, and they were added after that tag. The app code in both is identical.
