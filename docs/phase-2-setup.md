# Phase 2: the trip, live speed and the overspeed alarm

Phase 2 is the heart of the product. The driver taps **Start Trip**, the phone measures speed from GPS, and if the driver stays over the admin's speed limit for 3 seconds the phone beeps, says "Please slow down" (or "Kripya speed kam karein" in Hindi) and the screen turns red. If the speed stays over the limit for the admin alert delay (10 seconds by default), an "Overspeed started" event is recorded. When the speed is back at or under the limit for 5 seconds, the alarm stops and a "Back to normal" event with top speed and duration is recorded. The trip ends by itself when the vehicle has been parked for 15 minutes. Everything is saved on the phone first and uploaded as the network allows, so a dead spot loses nothing.

What is not in Phase 2, on purpose: the push alerts to the admins and the admin's live map arrive in Phase 3, so for now the events are visible in the Firebase console. "Please start trip" requests and "End trip now" from the admin are Phase 3 too. SOS, battery alerts, tracking status and the battery-settings setup screen are Phase 4.

Plan for about an hour, plus a real drive if you can.

## Step 1: Get the code and update the rules

1. Android Studio **☰** > **Git** > **Pull…** > **Pull**. Let Gradle sync; it downloads a few new libraries (2 to 5 minutes).
2. The database rules changed (the phone now writes trips and events). In the Android Studio Terminal (left bar **>_** icon; the prompt shows `LifeSafety`, the project folder) run:

   ```
   firebase deploy --only firestore:rules
   ```

   If you use a separate PowerShell window instead, first go to the project folder: `cd "C:\Users\NiTiN Dhiman\StudioProjects\LifeSafety"`. It ends with **Deploy complete!** in under a minute. The functions did not change, so nothing else to deploy.

## Step 2: Build and install

**☰** > **Build** > **Generate App Bundles or APKs** > **Generate APKs**, then **locate**, send `app-debug.apk` to the driver phone and install it over the old version. The driver account must be linked to an admin (Phase 1). If it is not, pair first: admin taps **Add driver**, driver types the code, taps **Join**, then taps **Agree** on the consent screen that opens (Phase 1 guide, steps 3 and 4).

## Step 3: Permissions

Open the app as the driver. The trip screen now shows a card asking for **Location**. Tap **Allow**, then in the Android dialog choose **While using the app** (Android 9 or older just says **Allow**) and, on Android 12 or newer, make sure **Precise** is selected at the top. On Android 13 or newer a second card asks for **Notifications**; tap **Allow** there too (older phones do not ask). A notification "Monitoring active: <admin name> can see your trips" appears and stays while you are linked. That notification is required by Google Play for a monitoring app.

## Step 4: Test the alarm at home with Simulate drive

Because this is the debug build, the bottom of the trip screen has a switch **Simulate drive (test mode)**. It feeds fake speeds so you can test without a car. It does not exist in the release build.

1. Turn the switch on. Turn the phone's **alarm** volume up (not media volume).
2. Tap **Start Trip**. The notification changes to "Trip in progress". A few seconds later the first fake reading appears and the big number starts climbing: 0 to 50 in the first 10 seconds. The times below count from that first reading.
3. Just after 30 seconds the number climbs from 50 toward 75, above the limit of 60 (the number is an average of the last three readings, so it lags the fake speed by a second or two). About 3 seconds after it passes 60, around 37 seconds, the screen turns red with **SLOW DOWN**, the phone beeps every second and says "Please slow down" every 4 seconds.
4. Ten seconds after the alarm began, around 47 seconds, the phone records "Overspeed started". Nothing visible changes on the phone; you check it in step 5.
5. Just after 60 seconds the number drops from 75 toward 40. Five seconds after it is back at or under 60, around 69 seconds, the alarm stops and the screen is normal again. "Back to normal" is recorded with top speed 75 and a duration of about 30 seconds.
6. The loop repeats every two minutes. When you have seen it once, tap **End trip (test mode)**. This button also exists only in the debug build. The real **End Trip** button appears only when the admin allows it, which comes in Phase 3.
7. Turn **Simulate drive** off.

## Step 5: See the data in Firebase

1. Firebase console > **Firestore Database** > **Data**.
2. Click **drivers**, then the driver's ID. At the top of the document column, under **+ Start collection**, you see the subcollections **events** and **trips**. Under them, after **+ Add field**, are the fields: **live** with the current status, last speed and battery, and **settings** with the defaults (speed limit 60, auto-end 15 minutes).
3. In **events** you find trip_started, overspeed_started (with speed, top speed, battery, address if available), back_to_normal (with duration) and trip_ended. In **trips** the trip summary shows status, distance, top speed, duration, the number of overspeeds that reached the admins and the number of short ones, with a **points** subcollection holding the GPS points in batches of up to 60.

## Step 6: A real drive

Do this as a passenger, or let someone drive. Never exceed the speed limit on a public road to test the alarm; the simulator already proved it works.

1. Simulate drive off. Tap **Start Trip** outdoors. "GPS: searching…" becomes "GPS: good" within a minute under open sky and the number shows the real speed.
2. Lock the screen and put the phone in a pocket or holder. The trip keeps running; the notification shows the speed.
3. Park. After 15 minutes without movement the trip ends by itself: the screen says "Tap Start Trip when you begin driving." and the "Monitoring active" notification is back.
4. If the phone had no network during the drive, "items waiting to upload" shows a count; it drops to zero within a couple of minutes of the phone being online again, even if the app is closed.

## Test checklist for Phase 2

1. The Location card appears once (and on Android 13 or newer a Notifications card after it), and after Allow they are gone.
2. "Monitoring active" notification shows while linked and idle.
3. Simulated trip: the number climbs, the alarm starts about 3 seconds after passing 60, screen red, beep and voice.
4. The alarm stops about 5 seconds after the speed is back under 60.
5. Hindi: switch the phone to Hindi and run the simulation again; the voice says "Kripya speed kam karein" (if your phone has a Hindi voice installed; otherwise it says it in English).
6. Firebase shows events overspeed_started and back_to_normal with top speed 75, plus trip_started and trip_ended.
7. The trip summary in Firebase has status "ended", a distance and top speed.
8. Real drive: live speed matches the car's speedometer within a few km/h.
9. Real drive: the trip survives the screen being off for at least 10 minutes.
10. Parking for 15 minutes ends the trip by itself.
11. Turn on Airplane mode (Wi-Fi alone keeps the phone online, so turning off mobile data is not enough) for about two minutes during the simulation. "N items waiting to upload" appears within a minute. Turn Airplane mode off; the count clears within about a minute.

Tell me which steps worked, with the exact text of any message you did not expect.

## Troubleshooting

**The number stays "--" outdoors.** GPS needs open sky and up to a minute for the first fix. Phones with Location set to "Battery saving" mode have no GPS: Settings > Location > Location services or Mode > choose **High accuracy** (names vary by brand).

**No sound during the alarm.** The alarm uses the phone's alarm volume, not the media volume. Also check that Do Not Disturb does not silence alarms.

**The voice is English although the phone is in Hindi.** The phone has no Hindi text-to-speech voice. Search "Text-to-speech" in the phone's Settings, open the preferred engine's settings (usually Google's), choose **Install voice data** and add Hindi. Until then the English voice is used.

**The trip stops after a few minutes with the screen off (Xiaomi, Redmi, Poco, Vivo, Oppo, Realme, OnePlus).** The phone's battery saver killed the app. In the phone's Settings > Apps > Driver Safety Monitor > Battery, choose **No restrictions** (Xiaomi) or turn off **Optimize battery usage** and allow background activity (Vivo, Oppo, Realme, OnePlus). Phase 4 adds an in-app guide for each brand. Samsung: Settings > Apps > the app > Battery > **Unrestricted**.

**"Start Trip" is greyed out, or the Location card keeps coming back.** Location was denied or set to Approximate. Tap **Allow** on the card and choose **While using the app** with **Precise** on. If Android no longer shows the dialog, an **Open settings** button appears under the card after you tap Allow: Permissions > Location > **Allow only while using the app**, and on Android 12 or newer turn on **Use precise location**. Come back to the app; the card disappears and Start Trip is enabled.

**Events do not appear in Firebase.** Look at the status line under the admin names. "Not synced yet" with "items waiting to upload" means the phone has no connection or the rules were not deployed (step 1). If the phone is online and the count does not drop, send me a screenshot.

**The rules deploy says "Error: Not in a Firebase app directory".** The terminal is not in the project folder. Use the Android Studio Terminal, which opens there, or run the `cd` line from step 1 first.

## What to send me when done

"Phase 2 works" plus anything from the checklist that did not match. Then I start Phase 3: Cloud Functions that push alerts to both admins, the admin dashboard with the live map, driver detail with settings (speed limit, tolerance, alert delay, auto-end, driver can end trip), request trip start, end trip now, and the trip list.
