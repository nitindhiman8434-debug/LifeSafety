# Driver Safety Monitor

One Android app, two roles. An **Admin** (vehicle owner, company or manager) sets a speed limit for an employed **Driver**. During a trip, if the driver goes over the limit, the driver's phone sounds an alarm and both admins get an alert. When the speed drops back under the limit, the alarm stops and the admins get a "Back to normal" alert with top speed and duration.

The app monitors employed drivers for their employer only. The driver always sees and agrees to the monitoring before any admin sees data. It declares itself as an enterprise management tool to Google Play (`isMonitoringTool` in the manifest).

Languages: English and Hindi. All text lives in string resources.

## Stack

Kotlin, Jetpack Compose, Material 3, MVVM with repositories, Room, WorkManager, Fused Location Provider, a `location` foreground service for trips, Google Maps Compose. Backend is Firebase only: Authentication (Google Sign-In), Cloud Firestore, Cloud Functions (TypeScript), Cloud Messaging, scheduled functions, Crashlytics.

minSdk 26 (Android 8.0). compileSdk and targetSdk 36 (Android 16).

## Project layout

```
app/src/main/java/com/lifesafety/driversafety/
  MainActivity.kt        app entry point (Phase 0: the Hello screen)
  ui/theme/              Material 3 colours, typography, theme
  auth/                  (Phase 1) login, role selection, consent
  pairing/               (Phase 1) codes, linking, admin roles
  trip/                  (Phase 2) foreground service, speed, overspeed state machine
  alerts/                (Phase 3) FCM, alert inbox
  admin/                 (Phase 3) dashboard, driver detail
  settings/              (Phase 3) speed limit and other driver settings
functions/               (Phase 3) Cloud Functions in TypeScript
docs/                    step-by-step guides for each phase
gradle/libs.versions.toml  every library and plugin version
```

Folders marked with a phase do not exist yet. They are created in that phase.

## Setup

Follow [docs/phase-0-setup.md](docs/phase-0-setup.md). It walks through installing Android Studio on Windows, enabling USB debugging on the phone, creating the Firebase project, setting the ₹500 budget alert and running the app on the phone.

Short version for someone who already has Android Studio:

1. Clone this repository and open it in Android Studio.
2. In the Firebase Console, create a project and register an Android app with package name `com.lifesafety.driversafety`.
3. Download `google-services.json` and place it at `app/google-services.json`. The file is git-ignored on purpose.
4. Connect a phone with USB debugging on and press Run.

## Run

Press the green Run button in Android Studio with your phone selected, or from the Android Studio Terminal:

```
.\gradlew installDebug
```

## Test

Automated tests arrive in Phase 2 with the overspeed logic. Until then each phase has a manual test checklist at the end of its guide in `docs/`. For Phase 0 the check is simple: the Hello screen opens on the phone and shows the phone's model name and Android version, in Hindi when the phone language is Hindi, and in dark colours when the phone is in dark mode.

## Release

Written in Phase 5. It will cover the release keystore and its backup, Play App Signing, App Check, the store listing, Data safety answers, the privacy policy and account deletion pages, and the internal, closed and production tracks.

## Phases

| Phase | What it delivers | Status |
|---|---|---|
| 0 | Project, Firebase project, budget alert, Hello screen on the phone | Ready to test |
| 1 | Roles, Google login, consent, pairing codes, admin roles, remove and leave | Next |
| 2 | Driver trip: foreground service, live speed, overspeed alarm, auto-end, offline queue, Simulate drive | |
| 3 | Admin side: Cloud Functions, FCM alerts, dashboard, driver detail, settings, trip list | |
| 4 | Tracking status, battery alerts, SOS, setup screen | |
| 5 | Play Store release | |

## Conventions

Standard Android stack, as few libraries as possible, folders by feature, plain-English comments on non-obvious logic. No extra modules or custom frameworks. Every user-visible string goes into `res/values/strings.xml` and `res/values-hi/strings.xml`.
