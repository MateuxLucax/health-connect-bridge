# 12. Release automatically on every green push to main

Status: Accepted

## Context

Releases were cut by pushing `vX.Y.Z` tags by hand, so fixes on `main`
reached the phone only when someone remembered to tag. A tag or release
created by CI with `GITHUB_TOKEN` does not start another workflow run, so CI
cannot tag and then rely on the tag build to publish the APK. The unit tests
ran in the Build workflow and the emulator test in a separate Integration
workflow, so no single run knew whether both had passed.

## Decision

- Every push to `main` that passes all jobs is a release. The version is
  `<VERSION>.<run number>`, where the `VERSION` file holds `major.minor`; the
  Android `versionName` is that version and `versionCode` is the run number of
  the Build workflow, which keeps increasing across releases.
- The emulator test moved into the Build workflow. The `release` job needs
  both the `build` job (unit tests, signed APK) and the `integration` job, and
  creates the GitHub Release `vX.Y.Z` at the built commit with generated notes,
  the APK and its signer certificate SHA-256.
- Builds of `main` and `v*` tags fail without the signing secrets: a debug
  key changes on every build, so updates would not install.
- Permissions stay split: top-level `contents: read`; the signing secrets
  only reach the `build` job; only `release` and `screenshots` get
  `contents: write`; checkouts don't persist credentials, except in
  `screenshots`, which pushes and runs no build or test code.
- Pushes that only change `docs/**` or Markdown files do not run the
  workflow, so they do not release. Screenshot commits carry `[skip ci]`.
  Manually pushed `v*` tags still build and release; the upload is
  idempotent when the release already exists.

## Consequences

- No manual release step; the latest release always matches the newest green
  `main`.
- Patch numbers are not contiguous (manual and skipped runs also count) and
  say nothing about the size of the change.
- Changing major or minor means editing `VERSION`.
- A release now waits for the emulator test (tens of minutes).
