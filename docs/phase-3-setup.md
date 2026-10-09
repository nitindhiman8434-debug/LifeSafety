# Phase 3: the admin side

Phase 3 gives the admins their three screens. The **Dashboard** shows counters (on trip, over the limit, unread alerts), a map with every driver's last known position and a card per driver with status, live speed, battery and last update. **Driver detail** shows the live map and speed, a Call button, **Request trip start**, **End trip now** (primary admin only), the settings (speed limit, tolerance, admin alert delay, auto-end minutes, driver can end trip, driver's phone number; editable by the primary admin, read-only for the second admin), the recent events, the trip list and the Admins section from Phase 1. **Alerts** is the inbox: overspeed started, back to normal, second admin added or removed, link ended. Every alert also arrives as a push notification on both admins' phones.

On the driver side, a tapped "Please start a trip" notification starts the trip, and "End trip now" from the primary admin ends it with a message saying who ended it.

Behind the scenes: new Cloud Functions, a Firestore trigger that turns the driver's overspeed events into alerts, Firebase Cloud Messaging for the push, and Google Maps for the two maps. Plan for about an hour, most of it the one-time Google Maps key.

You need two phones for the full test: the admin phone (yours) and the driver phone from Phase 2. With one phone you can still test everything except seeing the push arrive while the driver drives; sign out and in as the other role instead.

## Step 1: Get the code

Android Studio **☰** > **Git** > **Pull…** > **Pull**. Let Gradle sync; it downloads the maps and messaging libraries (2 to 5 minutes).

## Step 2: A Google Maps key (one time)

The maps need an API key from Google Cloud. Your Firebase project is also a Google Cloud project, so the key goes into the same project. Showing a map inside an Android app is free under Google's current pricing; the ₹500 budget alert from Phase 0 watches the project in any case.

1. Open https://console.cloud.google.com in the browser where you are signed in with ishudh@gmail.com. At the top, next to "Google Cloud", click the project picker and choose **Driver Safety Monitor** (the same project as Firebase; if a list of projects is empty, open the **All** tab).
2. **☰** (top-left) > **APIs & Services** > **Library**. In the search box type **Maps SDK for Android**, open it, click **Enable**. If it already says **Manage**, it is enabled.
3. **☰** > **APIs & Services** > **Credentials**. Click **+ Create credentials** > **API key**. A box shows the new key (starts with `AIza`). Click **Copy**, then close the box.
4. Now lock the key to your app so nobody else can use it. In the list under **API Keys** click the key's name (something like "API key 1"). On its page:
   - Under **Application restrictions** choose **Android apps**. Click **+ Add**. Package name: `com.lifesafety.driversafety`. SHA-1 certificate fingerprint: the same SHA-1 you added to Firebase in Phase 1 step 3 (from `signingReport`, the 20 pairs with colons). Click **Done**.
   - Under **API restrictions** choose **Restrict key**, open the dropdown, tick **Maps SDK for Android**, click **OK**.
   - Click **Save**. The key takes up to 5 minutes to start working.
5. Put the key on your laptop, not in the code. In Android Studio's Project panel (left), under **Gradle Scripts**, open **local.properties (SDK Location)**. Add one line at the end (your key instead of the dots):

   ```
   MAPS_API_KEY=AIza..........................
   ```

   Save. This file is listed in `.gitignore`, so the key never goes to GitHub. Click the elephant icon **Sync Project with Gradle Files**.

If you ever see "local.properties" being rewritten by Android Studio, check that the line is still there; Android Studio only changes the `sdk.dir` line.

The debug SHA-1 belongs to this laptop. Phase 5 adds the release signing key's SHA-1 to the same API key before the Play Store build.

## Step 3: Deploy the server side

Both the rules and the functions changed. Do not use the Android Studio Terminal for this (it crashed on your laptop in Phase 2); use a separate PowerShell window: Windows key, type **PowerShell**, Enter, then:

```
cd "C:\Users\NiTiN Dhiman\StudioProjects\LifeSafety"
firebase deploy
```

It uploads the rules and all 15 functions and ends with **Deploy complete!** after 3 to 6 minutes. Two things that can happen the first time:

- It enables a few more Google Cloud services for the Firestore trigger (Eventarc, Cloud Run, Pub/Sub). If it stops with an error that mentions **Eventarc**, **service agent** or **permissions propagate**, wait 3 minutes and run `firebase deploy` again. This is normal on the first deploy of a trigger.
- It may ask about a **cleanup policy** or **container images** and a number of days. Press Enter to accept the default.

The trigger is created in the same location as your Firestore database, automatically. Nothing to configure.

## Step 4: Build and install on both phones

**☰** > **Build** > **Generate App Bundles or APKs** > **Generate APKs**, then **locate** and send `app-debug.apk` to **both** phones. Install over the old version on each. The admin phone needs the new version too: it is what registers the phone for push notifications.

Open the app on each phone once and, when asked, tap **Allow** for notifications (Android 13 or newer). The admin phone asks only for notifications; the driver phone already has location from Phase 2.

## Step 5: See a trip and the alerts

1. Admin phone: the dashboard now shows three counters and the driver's card with **Idle** (or **No data yet** before the first trip). The bell at the top opens Alerts (empty for now).
2. Driver phone: turn **Simulate drive (test mode)** on, tap **Start Trip**.
3. Admin phone, within 20 seconds: the card says **On trip** with the speed and the limit, "Battery …", "just now". A map appears above the cards with a blue pin. The **On trip** counter shows 1. Tap the card: Driver detail shows the same live numbers, the map following the pin, and under it the buttons.
4. About 47 seconds into the simulation (10 seconds after the driver's alarm started) the admin phone gets a notification **Overspeed: <driver name>** with "75 km/h, limit 60 km/h" and the street if the driver phone could look it up. The card and pin turn red, **Over the limit** shows 1, the bell shows a badge.
5. About 20 seconds later: **Back to normal: <driver name>** with the top speed and how long it lasted.
6. Tap the bell: both alerts are in the inbox, unread ones highlighted. Tap one: it is marked read and the driver opens. **Mark all read** is at the top.
7. The driver phone's trip ends by itself after 15 minutes without movement, or tap **End trip (test mode)**. The admin card goes back to **Idle**; Driver detail > Trips lists the trip with duration, distance, top speed and the number of overspeed alerts.

Every alert also went to the second admin, if there is one.

## Step 6: Request a trip start

Driver phone idle (no trip), app closed or open. Admin phone > Driver detail > **Request trip start** > **Send**. The driver phone shows the notification **Please start a trip** with your name. Tap it: the app opens and the trip starts (with Simulate drive on, it uses the fake speeds again). The admin sees "Request sent to the driver's phone".

If instead the admin sees "Request saved, but the driver's phone is not registered for notifications yet", the driver has not opened this version of the app since installing it. Open it once on the driver phone and try again.

## Step 7: End a trip from the admin phone

While the driver is on a trip, primary admin > Driver detail > **End trip now** > **End trip now**. Within a few seconds the driver phone's trip ends and the driver screen says "Your trip was ended by <your name>". Trips on the admin phone shows "Ended by the admin". The second admin does not have this button.

## Step 8: Change the settings

Primary admin > Driver detail > Settings card. Change the speed limit to 50 and tap **Save settings**. "Settings saved" appears and the driver phone's limit badge changes to 50 within seconds, even during a trip. Try the others:

- **Driver's phone number**: after saving, a **Call** button appears above the settings. Tapping it opens the phone's dialer with the number.
- **Driver can end a trip** on: the driver phone shows **End Trip** during a trip.
- **Auto-end after parking**: 1 minute makes the home test faster; a parked simulation (speeds at 0 from 90 s to 120 s) is too short to trigger it, so for a real test park the car.

The second admin sees the same card read-only.

## Step 9: Alerts about the link itself

Add a second admin (Phase 1 guide, step 8). When the driver agrees, the primary admin gets **Second admin added**. When the second admin leaves, or the primary removes them, or the driver removes an admin, the others get an alert. When the driver removes the primary admin, both admins get **Link ended**.

## Test checklist for Phase 3

1. Admin phone shows the driver card with **On trip**, the speed and the limit within 20 seconds of the driver starting.
2. The map on the dashboard and in Driver detail shows the pin and follows it. (If the map stays grey or blank, see Troubleshooting.)
3. Overspeed push notification arrives on the admin phone with speed and limit; the card and the pin turn red; **Over the limit** counts 1.
4. Back to normal push notification arrives with top speed and duration.
5. Both alerts are in the inbox; tapping marks read; the badge count drops.
6. Request trip start: the driver phone's notification starts the trip when tapped.
7. End trip now ends the driver's trip within a few seconds and the driver sees who ended it.
8. Speed limit change reaches the driver phone within seconds.
9. Phone number: Call button opens the dialer.
10. Second admin: gets the same push alerts, sees settings read-only, can request a trip start, has no End trip now.
11. Link alerts: adding and removing the second admin produces alerts.
12. Hindi: switch the admin phone to Hindi; the notification text and the screens are in Hindi (speeds and times stay in digits).

## Troubleshooting

**The map is grey or blank with only the Google logo.** The key is missing or wrong. Check `local.properties` has the `MAPS_API_KEY=` line, that you synced and rebuilt the APK after adding it, that **Maps SDK for Android** is enabled, and that the key's Android restriction has exactly the package name `com.lifesafety.driversafety` and this laptop's SHA-1. A new key can take 5 minutes to activate. Android Studio's **Logcat** panel (bottom bar) shows the exact reason when you filter for `Google Maps`.

**No push notifications on the admin phone.** Notifications must be allowed (Settings > Apps > Driver Safety Monitor > Notifications). The app must have been opened once after installing this version. In the Firebase console > Firestore Database > **users** > the admin's document, the field **fcmTokens** must contain a long value; if it is missing, sign out and in again in the app. Xiaomi, Vivo, Oppo, Realme and OnePlus phones also need the app excluded from battery saving (Phase 2 troubleshooting), or they delay pushes.

**The admin sees "End trip saved. The driver's phone seems offline".** The command is stored; the driver phone ends the trip the moment it is back online. If the driver's phone died mid-trip, the card stays **On trip** until the data is 3 minutes old, then shows **Offline**. Phase 4 adds the tracking-lost alerts for this.

**`firebase deploy` fails mentioning Eventarc or permissions.** Wait 3 minutes and run it again; the first trigger deploy sometimes finishes before Google's permissions have propagated.

**Alerts arrive late with "(sent late, phone was offline)".** The driver phone had no connection when the overspeed happened; the event was uploaded when the connection came back, and the alert followed it. That is the offline queue working as designed.

**The driver tapped "Please start a trip" but nothing started.** The driver app shows the Location card first if the permission is missing; after **Allow** the trip starts. If the driver was already on a trip, the request is ignored.

## What to send me when done

"Phase 3 works" plus anything from the checklist that did not match. Then I start Phase 4: tracking status (Good / At risk / Lost with the reason, the heartbeat, the every-minute server check), battery alerts at 20%, 10% and 5%, SOS with the loud channel and the 112 button, and the driver's setup screen with the brand-specific battery steps.
