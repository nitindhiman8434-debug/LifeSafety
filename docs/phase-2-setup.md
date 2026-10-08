# Phase 2: the trip, live speed and the overspeed alarm

Phase 2 is the heart of the product. The driver taps **Start Trip**, the phone measures speed from GPS, and if the driver stays over the admin's speed limit for 3 seconds the phone beeps, says "Please slow down" (or "Kripya speed kam karein" in Hindi) and the screen turns red. If the speed stays over the limit for the admin alert delay (10 seconds by default), an "Overspeed started" event is recorded. When the speed is back at or under the limit for 5 seconds, the alarm stops and a "Back to normal" event with top speed and duration is recorded. The trip ends by itself when the vehicle has been parked for 15 minutes. Everything is saved on the phone first and uploaded as the network allows, so a dead spot loses nothing.

What is not in Phase 2, on purpose: the push alerts to the admins and the admin's live map arrive in Phase 3, so for now the events are visible in the Firebase console. "Please start trip" requests and "End trip now" from the admin are Phase 3 too. SOS, battery alerts, tracking status and the battery-settings setup screen are Phase 4.

Plan for about an hour, plus a real drive if you can.

## Step 1: Get the code and update the rules

1. Android Studio **☰** > **Git** > **Pull…** > **Pull**. Let Gradle sync; it downloads a few new libraries (2 to 5 minutes).
2. The database rules changed (the phone now writes trips and events). In PowerShell, in the project folder:

   ```
   cd "C:\Users\NiTiN Dhiman\StudioProjects\LifeSafety"
   firebase deploy --only firestore:rules
   ```

   It ends with **Deploy complete!** in under a minute. The functions did not change, so nothing else to deploy.

## Step 2: Build and install

**☰** > **Build** > **Generate App Bundles or APKs** > **Generate APKs**, then **locate**, send `app-debug.apk` to the driver phone and install it over the old version. The driver account must be linked to an admin (Phase 1). If it is not, pair first: admin taps **Add driver**, driver enters the code and taps **Agree**.

## Step 3: Permissions

Open the app as the driver. The trip screen now shows a card asking for **Location**. Tap **Allow**, then in the Android dialog choose **While using the app** and make sure **Precise** is selected. A second card asks for **Notifications**; tap **Allow** there too. A notification "Monitoring active: <admin name> can see your trips" appears and stays while you are linked. That notification is required by Google Play for a monitoring app.

## Step 4: Test the alarm at home with Simulate drive

Because this is the debug build, the bottom of the trip screen has a switch **Simulate drive (test mode)**. It feeds fake speeds so you can test without a car. It does not exist in the release build.

1. Turn the switch on. Turn the phone's **alarm** volume up (not media volume).
2. Tap **Start Trip**. The notification changes to "Trip in progress". The big number starts climbing: 0 to 50 in the first 10 seconds.
3. At about 35 seconds the speed reaches 75, above the limit of 60. Three seconds later the screen turns red with **SLOW DOWN**, the phone beeps every second and says "Please slow down" every few seconds.
4. Ten seconds after the alarm began, the phone records "Overspeed started". Nothing visible changes on the phone; you check it in step 5.
5. At about 60 seconds the fake speed drops to 40. Five seconds later the alarm stops and the screen is normal again. "Back to normal" is recorded with top speed 75.
6. The loop repeats every two minutes. When you have seen it once, tap **End trip (test mode)**. This button also exists only in the debug build. The real **End Trip** button appears only when the admin allows it, which comes in Phase 3.
7. Turn **Simulate drive** off.

## Step 5: See the data in Firebase

1. Firebase console > **Firestore Database** > **Data**.
2. Click **drivers**, then the driver's ID. The document has a **live** field with the current status, last speed and battery, and a **settings** field with the defaults (speed limit 60, auto-end 15 minutes).
3. Below the fields you see the subcollections **events** and **trips**. In **events** you find trip_started, overspeed_started (with speed, top speed, battery, address if available), back_to_normal (with duration) and trip_ended. In **trips** the trip summary shows status, distance, top speed, duration and the number of overspeeds, with a **points** subcollection holding the GPS points in batches of up to 60.

## Step 6: A real drive

Do this as a passenger, or let someone drive. Never exceed the speed limit on a public road to test the alarm; the simulator already proved it works.

1. Simulate drive off. Tap **Start Trip** outdoors. "GPS: searching…" becomes "GPS: good" within a minute under open sky and the number shows the real speed.
2. Lock the screen and put the phone in a pocket or holder. The trip keeps running; the notification shows the speed.
3. Park. After 15 minutes without movement the trip ends by itself: the screen says "Tap Start Trip when you begin driving." and the "Monitoring active" notification is back.
4. If the phone had no network during the drive, "items waiting to upload" shows a count; it drops to zero once the phone is online again, even if the app is closed.

## Test checklist for Phase 2

1. Location and notification cards appear once, and after Allow they are gone.
2. "Monitoring active" notification shows while linked and idle.
3. Simulated trip: the number climbs, the alarm starts about 3 seconds after passing 60, screen red, beep and voice.
4. The alarm stops about 5 seconds after the speed is back under 60.
5. Hindi: switch the phone to Hindi and run the simulation again; the voice says "Kripya speed kam karein" (if your phone has a Hindi voice installed; otherwise it says it in English).
6. Firebase shows events overspeed_started and back_to_normal with top speed 75, plus trip_started and trip_ended.
7. The trip summary in Firebase has status "ended", a distance and top speed.
8. Real drive: live speed matches the car's speedometer within a few km/h.
9. Real drive: the trip survives the screen being off for at least 10 minutes.
10. Parking for 15 minutes ends the trip by itself.
11. Turn mobile data off for a minute during the simulation: "items waiting to upload" appears, then clears after data is back on.

Tell me which steps worked, with the exact text of any message you did not expect.

## Troubleshooting

**The number stays "--" outdoors.** GPS needs open sky and up to a minute for the first fix. Phones with Location set to "Battery saving" mode have no GPS: Settings > Location > Location services or Mode > choose **High accuracy** (names vary by brand).

**"GPS: no permission".** Location was denied or set to approximate. Open the app's settings (the card has an **Open settings** button): Permissions > Location > **Allow only while using the app**, and turn on **Use precise location**.

**No sound during the alarm.** The alarm uses the phone's alarm volume, not the media volume. Also check that Do Not Disturb does not silence alarms.

**The voice is English although the phone is in Hindi.** The phone has no Hindi text-to-speech voice. Settings > System > Languages > Text-to-speech output (or search "Text-to-speech" in Settings) > Google Speech Services > install voice data > Hindi. Until then the English voice is used.

**The trip stops after a few minutes with the screen off (Xiaomi, Redmi, Poco, Vivo, Oppo, Realme, OnePlus).** The phone's battery saver killed the app. In the phone's Settings > Apps > Driver Safety Monitor > Battery, choose **No restrictions** (Xiaomi) or turn off **Optimize battery usage** and allow background activity (Vivo, Oppo, Realme, OnePlus). Phase 4 adds an in-app guide for each brand. Samsung: Settings > Apps > the app > Battery > **Unrestricted**.

**"Start Trip" is greyed out.** Location permission is missing. Tap **Allow** on the card.

**Events do not appear in Firebase.** Look at the status line under the admin names. "Not synced yet" with "items waiting to upload" means the phone has no connection or the rules were not deployed (step 1). If the phone is online and the count does not drop, send me a screenshot.

**The rules deploy says "Error: Not in a Firebase app directory".** The PowerShell window is in the wrong folder. Run the `cd` line from step 1 first.

## What to send me when done

"Phase 2 works" plus anything from the checklist that did not match. Then I start Phase 3: Cloud Functions that push alerts to both admins, the admin dashboard with the live map, driver detail with settings (speed limit, tolerance, alert delay, auto-end, driver can end trip), request trip start, end trip now, and the trip list.
