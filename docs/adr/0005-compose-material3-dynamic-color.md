# 5. Jetpack Compose and Material 3 with dynamic color

Status: Accepted

## Context

The UI is small: a five-step onboarding, a Home tab and a Settings tab. It
should look native on current Android, including dark theme and themed
icons.

## Decision

Build the UI with Jetpack Compose and Material 3. `AppTheme` uses dynamic
color (Material You) on Android 12+ and a fixed light/dark scheme derived
from the app icon on older versions. The launcher icon has a monochrome
layer for themed icons.

## Consequences

- One declarative UI toolkit; no XML layouts except the plain
  `PrivacyActivity`.
- The app follows the user's wallpaper colors and dark mode.
- Compose adds the Compose BOM and `activity-compose` to the dependencies.
