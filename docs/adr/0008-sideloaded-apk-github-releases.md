# 8. Sideloaded APK distributed through GitHub Releases

Status: Accepted (release trigger superseded by [0012](0012-automatic-releases.md))

## Context

This is a personal app. A Play Store listing would require a Health Connect
permissions declaration and review, and Play policies (for example on battery
optimization exemption requests) that do not fit a self-hosted tool.

## Decision

The Build workflow runs unit tests and builds a release APK on every push to
`main` (workflow artifact). A `v*` tag publishes
`health-connect-bridge-<version>.apk` to a GitHub Release; the version name
comes from the tag and the version code from the run number. The APK is
signed with a key from repository secrets, or with a throwaway debug key when
none is configured. Release builds are not minified.

## Consequences

- Users install manually and allow unknown sources.
- The app asks for the battery exemption directly
  (`ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`), which is fine outside Play.
- Without signing secrets each build has a different key, so updates need an
  uninstall.
- No minification keeps the reflection-based exercise type names working.
