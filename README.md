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
  admin/                 admin dashboard (counters, map, driver cards), driver detail (live map, actions,
                         settings form, events, trips), AdminViewModel, AdminRepository, DriverMap
  trip/                  driver home and trip screen, TripService (foreground, GPS), SpeedSmoother,
                         OverspeedStateMachine, AutoEndDetector, AlarmPlayer, Notifications, TripRepository,
                         db/ (Room), sync/ (WorkManager)
  settings/              DriverSettings (speed limit, tolerance, alert delay, auto-end, driver can end trip)
  alerts/                push messages (AlertsMessagingService, FcmTokens, AlertNotifications), the alert
                         inbox (AlertsRepository, AlertsScreen), AppIntents for tapped notifications
app/src/debug/.../trip/DriveSimulator.kt   fake GPS for testing; src/release has a stub that does nothing
functions/src/index.ts   Cloud Functions: roles, pairing codes, consent, remove and leave, push tokens,
                         settings, request trip start, end trip now, the event-to-alert trigger
firestore.rules          Firestore security rules (deny by default)
firebase.json, .firebaserc   Firebase CLI configuration
docs/                    step-by-step guides for each phase
gradle/libs.versions.toml  every library and plugin version
```

## How a trip works

Start Trip starts `TripService`, a foreground service of type location. Every GPS fix goes through `SpeedSmoother` (accuracy better than 25 m, average of the last 3 readings), then `OverspeedStateMachine` (alarm after 3 s over limit + tolerance, "Overspeed started" after the admin alert delay, "Back to normal" after 5 s at or under the limit), then `AutoEndDetector` (parked or no GPS for the auto-end minutes). Points go to Room and are uploaded in batches about every 10 seconds; events (with battery, network, address, mock-location flag) are uploaded at once when possible. Anything left over is uploaded later by `SyncWorker` through WorkManager and marked "delayed". The driver record's `live` field carries the current status for the admin dashboard. The three pure-logic classes have unit tests.

## How alerts work

The driver's phone uploads each event to `drivers/{driverId}/events`. The `onDriverEvent` Cloud Function (a Firestore trigger) copies overspeed events into `users/{adminId}/alerts` for every approved admin and sends a data-only push message through Firebase Cloud Messaging to each admin's registered phones (`registerFcmToken` keeps at most five tokens per user). The phone builds the notification text itself, in its own language. Link changes (second admin added or removed, link ended) create alerts the same way from the pairing functions. The admin's "Request trip start" is a push to the driver's phone whose notification starts the trip when tapped; "End trip now" writes a command on the driver record that the trip service obeys and then clears, with a push to make it immediate. Only the primary admin can change settings, through `updateDriverSettings`; the phone picks them up live.

## How pairing works

An admin asks the `createPairingCode` function for a 6-digit code (valid 10 minutes, single use, stored as a SHA-256 hash). The driver enters it; `redeemPairingCode` creates a link document in `pending_consent` state. The driver reads the consent screen and taps Agree; `respondToConsent` activates the link and adds the admin to the driver's `adminIds`, which is what the Firestore rules check before an admin may read driver data. A second admin follows the same path with `createCoAdminCode` and `redeemCoAdminCode`. Wrong codes are counted per user; after five in 15 minutes the functions refuse further attempts. The app never writes links, codes or roles itself.

## Setup

Follow [docs/phase-0-setup.md](docs/phase-0-setup.md) first (Android Studio on Windows, Firebase project, ₹500 budget alert, first run on the phone), then [docs/phase-1-setup.md](docs/phase-1-setup.md) (Google sign-in, SHA-1, Firestore, Firebase CLI, deploying rules and functions), [docs/phase-2-setup.md](docs/phase-2-setup.md) (permissions, the trip, Simulate drive) and [docs/phase-3-setup.md](docs/phase-3-setup.md) (Google Maps key, push notifications, the admin screens).

Short version for someone who already has Android Studio and the Firebase CLI:

1. Clone this repository and open it in Android Studio.
2. In the Firebase Console, create a project on the Blaze plan, register an Android app with package name `com.lifesafety.driversafety`, enable Google sign-in, add your debug SHA-1, and create a Firestore database in asia-south1.
3. Download `google-services.json` and place it at `app/google-services.json`. The file is git-ignored on purpose.
4. Enable **Maps SDK for Android** in Google Cloud, create an API key restricted to the package name and your SHA-1, and add `MAPS_API_KEY=...` to `local.properties` (also git-ignored). Without it the app builds and runs; the maps stay blank.
5. Put your project ID in `.firebaserc`, then `cd functions; npm install; cd ..` and `firebase deploy`. The Firestore trigger is placed in the database's location automatically.
6. Build and install the debug APK, or connect a phone with USB debugging and press Run.

## Run

Press the green Run button in Android Studio with your phone selected, or build an APK with **Build** > **Generate App Bundles or APKs** > **Generate APKs** and send `app/build/outputs/apk/debug/app-debug.apk` to the phone. From a terminal that has Java on its PATH, `.\gradlew installDebug` also works.

## Test

Unit tests cover the overspeed state machine, the speed smoother and the auto-end detector (`app/src/test`). In Android Studio, right-click the `app/src/test` folder and choose **Run 'Tests in…'**; from a terminal with Java, `.\gradlew testDebugUnitTest`. Each phase also has a manual test checklist at the end of its guide in `docs/`.

The backend has an end-to-end test that runs against the Firebase emulators. It creates test users, calls every Cloud Function the way the app does, and reads Firestore with those users' tokens so the security rules are enforced. From the project folder, with the Firebase CLI and Java installed (the Firestore emulator needs Java) and `cd functions; npm install; npm run build; cd ..` done once:

```
firebase emulators:exec --only "auth,functions,firestore" "node functions/e2e/e2e.mjs"
```

It prints one line per check and ends with ALL PASSED. The functions also type-check with `cd functions; npx tsc --noEmit; cd ..`. Push messages are not sent from the emulator (there are no real phones); the test checks the alert documents and the token bookkeeping instead.

## Release

Written in Phase 5. It will cover the release keystore and its backup, Play App Signing, App Check, the store listing, Data safety answers, the privacy policy and account deletion pages, and the internal, closed and production tracks.

## Phases

| Phase | What it delivers | Status |
|---|---|---|
| 0 | Project, Firebase project, budget alert, Hello screen on the phone | Done |
| 1 | Roles, Google login, consent, pairing codes, admin roles, remove and leave | Done |
| 2 | Driver trip: foreground service, live speed, overspeed alarm, auto-end, offline queue, Simulate drive | Done |
| 3 | Admin side: Cloud Functions, FCM alerts, dashboard, driver detail, settings, trip list | Ready to test |
| 4 | Tracking status, battery alerts, SOS, setup screen | |
| 5 | Play Store release | |

## Conventions

Standard Android stack, as few libraries as possible, folders by feature, plain-English comments on non-obvious logic. No extra modules or custom frameworks. Every user-visible string goes into `res/values/strings.xml` and `res/values-hi/strings.xml`.
