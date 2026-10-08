# Phase 1: login, roles, pairing and consent

Phase 1 adds the real start of the app. Everyone signs in with Google and chooses a role. An admin gets a 6-digit code and sends it to their driver. The driver enters it, reads a consent screen that names the admin and lists what they will see, and taps Agree. The primary admin can then add one second admin with another code, and the driver must agree again. Drivers can remove an admin, admins can leave, and both sides see the link status at all times.

Behind the app there are now Cloud Functions (small server programs on Firebase) that do all the linking, and Firestore security rules that keep everyone's data private. Setting those up is most of the work in this phase.

Plan for about 2 hours. Steps 2 to 6 are one-time setup. After that, every later phase is just "pull the code, build the APK, install".

What is deliberately not in Phase 1: push notifications (the driver sees a pending request when they open the app; the push arrives in Phase 3), the "Monitoring active" ongoing notification (Phase 2, with the trip service), and alerts to the other admin when someone is removed (Phase 3).

## Before you start

The project must be on the Blaze plan, because Cloud Functions do not run on the free Spark plan. Open https://console.firebase.google.com, pick Driver Safety Monitor and look at the bottom-left of the menu. If it says **Spark**, do step 5 of `docs/phase-0-setup.md` first.

You also need two Google accounts to test pairing: one plays the admin, one plays the driver. A third account is needed only for the second-admin test. Accounts can all be on the same phone (sign out, sign in with the other one) or on different phones. A family member's phone works well as the driver phone.

## Step 1: Get the new code into Android Studio

1. Open Android Studio with the LifeSafety project.
2. Top-left **☰** menu > **Git** > **Pull…** (in some versions **Update Project**). Keep the defaults and click **Pull** or **OK**.
3. Gradle sync starts by itself. If it does not, click the elephant icon **Sync Project with Gradle Files**. It downloads a few new libraries, 2 to 5 minutes.

## Step 2: Turn on Google sign-in in Firebase

1. Firebase console > left menu **Build** (or **Product categories**) > **Authentication**.
2. Click **Get started**.
3. Open the **Sign-in method** tab. Click **Google**. Turn on **Enable**.
4. Pick a **Project support email** (your Gmail). Click **Save**.

## Step 3: Register your phone's debug signature (SHA-1)

Google sign-in only works for apps whose signature Firebase knows. Your laptop has a "debug key" that signs every APK you build.

1. In Android Studio open the Terminal: left bar **>_** icon, or **☰** > **View** > **Tool Windows** > **Terminal**.
2. Type exactly and press Enter:

   ```
   .\gradlew signingReport
   ```

3. Wait for the text to stop. Scroll up to the block that says **Variant: debug**. Copy the line that starts with **SHA1:** (the 20 pairs of letters and numbers separated by colons, without the word SHA1).
4. Firebase console > gear icon next to **Project Overview** > **Project settings**. Stay on the **General** tab, scroll to **Your apps**. Click **Add fingerprint**, paste the SHA1 value, click **Save**.
5. On the same page click **google-services.json** to download it again. The new file contains the sign-in client IDs.
6. Copy the new file over the old one at `C:\Users\NiTiN Dhiman\StudioProjects\LifeSafety\app\google-services.json`. Replace when asked.
7. In Android Studio click the elephant icon **Sync Project with Gradle Files**.

If you ever build on another laptop, that laptop has its own debug key and needs its own SHA-1 added the same way.

## Step 4: Create the Firestore database

1. Firebase console > **Build** > **Firestore Database** > **Create database**.
2. Edition: **Standard**. Database ID: leave **(default)**.
3. Location: choose **asia-south1 (Mumbai)**. This cannot be changed later.
4. Click **Next**. Security rules: **Start in production mode**. Click **Create**.

Production mode means nobody can read or write anything yet. Step 6 uploads the real rules from `firestore.rules`.

## Step 5: Install the Firebase command-line tool (one time)

The tool uploads the rules and the Cloud Functions. It needs Node.js.

1. Open https://nodejs.org and download the **LTS** version for Windows. Run the installer with all default options. If it asks to install "tools for native modules", leave it unticked.
2. Close Android Studio completely and open it again, so its Terminal sees Node.js.
3. In the Android Studio Terminal check it works:

   ```
   node -v
   ```

   You should see a version like v22.x.x.
4. Install the Firebase tool:

   ```
   npm install -g firebase-tools
   ```

5. Sign in:

   ```
   firebase login
   ```

   Answer Y to the usage question, a browser opens, pick the Google account you used for Firebase, click **Allow**. The terminal says "Success! Logged in as …".

If `firebase` answers "running scripts is disabled on this system", run this once, answer Y, then try again:

```
Set-ExecutionPolicy -Scope CurrentUser RemoteSigned
```

## Step 6: Upload the rules and the Cloud Functions

In the Android Studio Terminal, at the project folder (the prompt shows `LifeSafety`):

```
cd functions
npm install
cd ..
firebase deploy --only firestore:rules,functions
```

The first deploy takes 5 to 10 minutes. It turns on a few Google Cloud services and uploads nine functions. If it asks whether to set up an "artifact cleanup policy" or to enable an API, answer **Y** or press Enter. It ends with **Deploy complete!**.

Two errors you might see. "Your project must be on the Blaze (pay as you go) plan" means step 5 of Phase 0 is not done. "HTTP Error: 403" or "permission denied" usually means `firebase login` used a different Google account than the Firebase project; run `firebase logout`, then `firebase login` again with the right account.

You repeat only the last command whenever I change the functions or the rules in a later phase. I will tell you when.

## Step 7: Build the APK and install it

1. Android Studio **☰** > **Build** > **Build App Bundle(s) / APK(s)** > **Build APK(s)**. Wait for "Generate APKs: Build completed successfully".
2. Click **locate**, send `app-debug.apk` to the phone (WhatsApp to yourself or Google Drive) and install it, as in Phase 0. Installing over the old version keeps it as an update.
3. For a second phone, send the same file there too.

## Step 8: Test

Use account A as the admin and account B as the driver.

1. Open the app. You see the sign-in screen. Tap **Continue with Google**, pick account A. You land on **Who are you?**. Tap **Admin**. The admin dashboard opens with "No drivers yet".
2. Tap **Add driver**. A 6-digit code appears with a countdown from 10:00. Tap **Share code** to see the WhatsApp message. Note the code.
3. Driver side (other phone, or ☰ menu > **Sign out** on this phone): sign in with account B, tap **Driver**. The screen says "Not linked yet". Type the code, tap **Join**.
4. The consent screen opens and names account A. Read it. Tap **Agree**. The screen changes to "Monitoring active" with the primary admin's name.
5. Admin side: the dashboard now lists the driver with "You: Primary admin" and "Active". Before the driver agreed it said "Waiting for driver consent". Tap the driver to open the detail screen.
6. Wrong-code check: on a driver account that is not linked, enter a wrong code. You see "Wrong code". Enter six wrong codes in a row and the message changes to "Too many wrong codes. Wait 15 minutes". Codes also stop working after 10 minutes and after one use; try an old code to see the message.
7. Second admin (needs account C): admin A opens the driver's detail screen, taps **Add second admin**, gets a code. Account C signs in, taps **Admin**, then **Join as second admin**, enters the code. C's dashboard shows the driver with "You: Second admin" and "Waiting for driver consent". C cannot open any driver data yet.
8. Driver B opens the app and sees a second consent screen naming C. Tap **Agree**. C's dashboard changes to "Active". A's detail screen shows "Second admin: C".
9. Driver removes an admin: driver B taps **Who can see my data**. Both admins are listed with what they see. Tap **Remove** under the second admin, confirm. C's dashboard becomes empty. Tap **Remove** under the primary admin, confirm. The driver is back to "Not linked yet" and A's dashboard is empty too.
10. Admin leaves: pair again (new code), then on admin A's detail screen tap **Remove driver**. Driver goes back to "Not linked yet".
11. Hindi: switch the phone to Hindi and open the consent flow once more. Everything, including the consent text, is in Hindi.
12. Change role: an unlinked account can switch role from the ☰ menu. A linked one cannot see that option.

Tell me which of these 12 steps worked and which did not, with the exact message if something failed.

## Troubleshooting

**Build error: Unresolved reference 'default_web_client_id'.** The `google-services.json` in the `app` folder is the old one from Phase 0. Redo step 3, points 5 to 7.

**Sign-in fails with "Sign-in failed: … 10" or "DEVELOPER_ERROR" or "[16] Account reauth failed".** Firebase does not know your debug SHA-1, or the json file was not replaced after adding it. Redo step 3. Then uninstall the app from the phone and install the new APK.

**"No Google account found on this phone".** Add a Google account in the phone's Settings > Accounts and try again.

**Any action shows "Server function not found".** The functions are not deployed, or the deploy failed. Run step 6 again and read its last lines.

**"No connection" although the phone is online.** The first call after a long idle can take a few seconds while the function starts. Tap again. If it keeps failing, check in the Firebase console under **Build** > **Functions** that nine functions are listed in region asia-south1.

**Deploy says "Error: Failed to get Firebase project driver-safety-monitor-58fff".** You are logged in with a different Google account. `firebase logout`, then `firebase login`.

**Deploy says "ESLint" or "lint" errors.** The project does not use lint in functions. Make sure you are in the `LifeSafety` folder (not inside `functions`) when running `firebase deploy`.

**The driver sees "Could not load data: PERMISSION_DENIED".** The rules were not deployed. Run `firebase deploy --only firestore:rules`.

**I removed the app and reinstalled it, now the role is wrong.** The role lives in Firebase, not on the phone. Use ☰ > **Change role** while unlinked.

## What to send me when done

"Phase 1 works" plus anything from the 12 test steps that did not match the description. Then I start Phase 2: the trip screen with live speed, the overspeed alarm with voice, auto-end, the offline queue and the Simulate drive mode.
