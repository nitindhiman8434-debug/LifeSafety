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
  MainActivity.kt        single activity; AppRoot decides which screen shows
  ui/                    AppNavigation (session gate + navigation), UiText, shared components, theme
  auth/                  Google sign-in, user profile and role, sign-in and role screens
  pairing/               links, codes, consent screen, "Who can see my data", code screens
  admin/                 admin dashboard and driver detail (Phase 3 adds map, settings, trips)
  trip/                  driver home (Phase 2 adds the trip service and overspeed logic)
  alerts/                (Phase 3) FCM, alert inbox
  settings/              (Phase 3) speed limit and other driver settings
functions/src/index.ts   Cloud Functions: roles, pairing codes, consent, remove and leave
firestore.rules          Firestore security rules (deny by default)
firebase.json, .firebaserc   Firebase CLI configuration
docs/                    step-by-step guides for each phase
gradle/libs.versions.toml  every library and plugin version
```

Folders marked with a phase do not exist yet. They are created in that phase.

## How pairing works

An admin asks the `createPairingCode` function for a 6-digit code (valid 10 minutes, single use, stored as a SHA-256 hash). The driver enters it; `redeemPairingCode` creates a link document in `pending_consent` state. The driver reads the consent screen and taps Agree; `respondToConsent` activates the link and adds the admin to the driver's `adminIds`, which is what the Firestore rules check before an admin may read driver data. A second admin follows the same path with `createCoAdminCode` and `redeemCoAdminCode`. Wrong codes are counted per user; after five in 15 minutes the functions refuse further attempts. The app never writes links, codes or roles itself.

## Setup

Follow [docs/phase-0-setup.md](docs/phase-0-setup.md) first (Android Studio on Windows, Firebase project, ₹500 budget alert, first run on the phone), then [docs/phase-1-setup.md](docs/phase-1-setup.md) (Google sign-in, SHA-1, Firestore, Firebase CLI, deploying rules and functions).

Short version for someone who already has Android Studio and the Firebase CLI:

1. Clone this repository and open it in Android Studio.
2. In the Firebase Console, create a project on the Blaze plan, register an Android app with package name `com.lifesafety.driversafety`, enable Google sign-in, add your debug SHA-1, and create a Firestore database in asia-south1.
3. Download `google-services.json` and place it at `app/google-services.json`. The file is git-ignored on purpose.
4. Put your project ID in `.firebaserc`, then `cd functions && npm install && cd ..` and `firebase deploy --only firestore:rules,functions`.
5. Build and install the debug APK, or connect a phone with USB debugging and press Run.

## Run

Press the green Run button in Android Studio with your phone selected, or from the Android Studio Terminal:

```
.\gradlew installDebug
```

## Test

Automated tests arrive in Phase 2 with the overspeed logic. Until then each phase has a manual test checklist at the end of its guide in `docs/`. Phase 1 needs two Google accounts (admin and driver) and a third for the second-admin test; the 12-step checklist is in `docs/phase-1-setup.md`.

The Cloud Functions type-check with:

```
cd functions && npm install && npx tsc --noEmit
```

## Release

Written in Phase 5. It will cover the release keystore and its backup, Play App Signing, App Check, the store listing, Data safety answers, the privacy policy and account deletion pages, and the internal, closed and production tracks.

## Phases

| Phase | What it delivers | Status |
|---|---|---|
| 0 | Project, Firebase project, budget alert, Hello screen on the phone | Done |
| 1 | Roles, Google login, consent, pairing codes, admin roles, remove and leave | Ready to test |
| 2 | Driver trip: foreground service, live speed, overspeed alarm, auto-end, offline queue, Simulate drive | |
| 3 | Admin side: Cloud Functions, FCM alerts, dashboard, driver detail, settings, trip list | |
| 4 | Tracking status, battery alerts, SOS, setup screen | |
| 5 | Play Store release | |

## Conventions

Standard Android stack, as few libraries as possible, folders by feature, plain-English comments on non-obvious logic. No extra modules or custom frameworks. Every user-visible string goes into `res/values/strings.xml` and `res/values-hi/strings.xml`.
