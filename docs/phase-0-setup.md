# Phase 0: from an empty laptop to "Hello" on your phone

By the end of this guide you will have Android Studio installed on your Windows laptop, this project open in it, a Firebase project with a ₹500 monthly budget alert, your Android phone connected over USB, and the app's Hello screen running on that phone.

Plan for 2 to 3 hours. Most of that is waiting for downloads. You can do the Firebase steps (4 and 5) while Android Studio downloads.

Button names in Android Studio, Firebase and Google Cloud change a little from version to version. If a label in this guide is not exactly what you see, look for the closest match. If you get stuck, copy the exact text on your screen and send it to me.

## What you need

A Windows 10 or 11 laptop, 64-bit, with at least 8 GB of RAM and about 15 GB of free disk space. A good internet connection, because the downloads add up to several GB. An Android phone running Android 8.0 or newer. A USB cable that carries data, not a charge-only cable (the one that came with the phone is usually fine). A Google account, ideally the one you will later use for the Play Console. A debit or credit card for the Firebase billing account in step 5. Google will place a small temporary charge to verify the card and refund it.

## Step 1: Install Android Studio

1. Open https://developer.android.com/studio in your browser.
2. Click the big **Download Android Studio** button. Tick the box to accept the terms and click **Download**. The file is about 1.2 GB and ends in `.exe`.
3. When the download finishes, open the `.exe`. If Windows asks "Do you want to allow this app to make changes?", click **Yes**.
4. In the installer click **Next**. Leave **Android Studio** and **Android Virtual Device** ticked and click **Next**. Keep the default install location and click **Next**, then **Install**. When it finishes, leave **Start Android Studio** ticked and click **Finish**.
5. Android Studio starts and asks about importing settings. Choose **Do not import settings** and click **OK**.
6. It may ask whether to send usage statistics to Google. Either answer is fine.
7. The Setup Wizard opens. Click **Next**. Choose **Standard** and click **Next**. Pick a light or dark theme and click **Next**. On the "Verify Settings" page click **Next**.
8. On the "License Agreement" page, click each license in the list on the left and select **Accept** for each one. The **Finish** button turns on only after all are accepted. Click **Finish**.
9. Wait while it downloads the Android SDK. This takes 10 to 30 minutes. When it says "Done", click **Finish**.
10. You now see the **Welcome to Android Studio** window. Leave it open.

## Step 2: Get the project onto your laptop

The code lives on GitHub at https://github.com/nitindhiman8434-debug/LifeSafety. Android Studio can download ("clone") it for you and later pull updates when I push new phases.

1. In the Welcome window click **Clone Repository** (in older versions it is called **Get from VCS**).
2. If Android Studio says Git is not installed, click the **Download and install** link in that dialog and wait for it to finish. If that link does not appear and cloning fails later with "git not found", install Git from https://git-scm.com/download/win with all default options, then restart Android Studio.
3. In the **URL** field paste exactly:

   ```
   https://github.com/nitindhiman8434-debug/LifeSafety.git
   ```

4. Leave the **Directory** as suggested. It will be something like `C:\Users\<your name>\AndroidStudioProjects\LifeSafety`. Click **Clone**.
5. If the repository is private, Android Studio asks you to log in to GitHub. Click **Log In via GitHub**, approve in the browser window that opens, and come back.
6. When it asks "Trust and Open Project?", click **Trust Project**.

## Step 3: Let Android Studio set up the project

1. Android Studio opens the project and starts a **Gradle sync**. You see a progress bar at the bottom. The first sync downloads the build tools and libraries, so it can take 5 to 20 minutes. Do not close the window.
2. If a message in the lower **Build** panel says something is missing and shows a link such as **Install missing SDK package(s)**, click that link, accept the license and click **Finish**. Then let the sync run again.
3. If a popup offers to upgrade the **Android Gradle Plugin**, choose **Remind me tomorrow** or close it. We will update versions deliberately, not from popups.
4. Sync is done when the progress bar disappears and the Build panel shows **BUILD SUCCESSFUL** or **Gradle sync finished**.
5. At the top left of the project tree there is a dropdown that says **Android**. Click it and switch to **Project**. This view shows the real folders on disk, which the next step needs.

## Step 4: Create the Firebase project and connect the app

Firebase is the backend. In Phase 0 we only connect the app to it, so that later phases can add login and the database without any setup.

### 4a. Create the project

1. Open https://console.firebase.google.com and sign in with your Google account.
2. Click **Create a Firebase project** (or **Add project**).
3. Project name: `Driver Safety Monitor`. Click **Continue**.
4. Leave **Enable Google Analytics for this project** turned on. Click **Continue**. We use Analytics only to confirm the app is connected.
5. For the Analytics account choose **Default Account for Firebase**. Tick the terms boxes if shown. Click **Create project**.
6. Wait a minute, then click **Continue**. You are on the project overview page.

### 4b. Register the Android app

1. On the overview page, click the **Android** icon (a small robot) under "Get started by adding Firebase to your app".
2. **Android package name**: type exactly `com.lifesafety.driversafety`. One wrong letter and the app will not connect.
3. **App nickname**: `Driver Safety Monitor`.
4. **Debug signing certificate SHA-1**: leave empty. We add it in Phase 1 when we set up Google login.
5. Click **Register app**.
6. Click **Download google-services.json**. Save it. It lands in your Downloads folder.
7. Click **Next**, **Next** again (the SDK is already in the project), then **Continue to console**.

### 4c. Put the file into the project

1. Open Windows File Explorer and go to **Downloads**. Right-click `google-services.json` and choose **Copy**.
2. Go to `C:\Users\<your name>\AndroidStudioProjects\LifeSafety\app` (the same folder that contains `build.gradle.kts`). Right-click an empty area and choose **Paste**.
3. Back in Android Studio, in the Project view, expand **LifeSafety > app**. You should see `google-services.json` next to `build.gradle.kts`. If you do not, right-click **app** and choose **Reload from Disk**.
4. Click the small elephant icon in the toolbar, **Sync Project with Gradle Files**, or use the menu **File > Sync Project with Gradle Files**.

This file identifies your Firebase project. It is deliberately not uploaded to GitHub (it is listed in `.gitignore`), so it stays on your laptop. If you ever lose it, download it again from Firebase: gear icon next to **Project Overview** > **Project settings** > scroll to **Your apps** > **google-services.json**.

## Step 5: Upgrade to the Blaze plan and set a ₹500 budget alert

Cloud Functions (Phase 3) only run on the Blaze pay-as-you-go plan. Blaze still includes a free quota, and during development this project should cost close to ₹0. The budget alert emails you at 50%, 90% and 100% of ₹500 so you are never surprised.

One honest limit: a budget alert only sends emails. It does not switch anything off. If you ever get a 100% email, tell me and we look at usage together.

1. In the Firebase console, at the bottom of the left menu, click **Spark plan** (or the gear icon > **Usage and billing** > **Details & settings** > **Modify plan**).
2. On the **Blaze** card click **Select plan** (or **Continue**).
3. Choose or create a Cloud Billing account. For a new one: country **India**, account type **Individual** unless you have a GST-registered business, your name and address, then your card details. If the card is refused, try another card or ask your bank to allow online recurring payments on it. Indian banks sometimes block these by default.
4. Firebase then asks you to **Set a billing budget**. Enter `500` and click **Continue**. If it does not ask, finish the upgrade and continue with step 6 below.
5. Click **Purchase** or **Confirm**. The plan badge at the bottom left now says **Blaze plan**.
6. To check or create the budget directly in Google Cloud: open https://console.cloud.google.com/billing, click your billing account, then **Budgets & alerts** in the left menu. If you see a budget of ₹500, you are done. If not, click **Create budget**, name it `Driver Safety 500`, under **Projects** pick **Driver Safety Monitor**, click **Next**, choose **Specified amount** and type `500`, click **Next**, keep the thresholds 50%, 90% and 100%, keep **Email alerts to billing admins and users** ticked, and click **Finish**.

## Step 6: Turn on USB debugging on your phone

Android hides the developer settings until you unlock them.

1. Open **Settings** on the phone and find **About phone** (sometimes under **System**).
2. Tap the **Build number** line seven times, quickly. After the last tap the phone says "You are now a developer!". It may ask for your PIN. On some brands the line is named differently; see the table below.
3. Go back to the main Settings screen. Open **Developer options**. It is usually under **System**, **Additional settings** or at the bottom of the main list. On Samsung it is at the very bottom of Settings.
4. Turn on **USB debugging**. Confirm with **OK**.
5. Xiaomi, Redmi and POCO only: in the same Developer options also turn on **Install via USB** (it needs a Mi account and a SIM card inserted) and **USB debugging (Security settings)**. Without these the phone refuses to install apps from the laptop.
6. Connect the phone to the laptop with the USB cable. On the phone, pull down the notifications and tap the USB notification. Choose **File transfer** or **Transferring files**. If the phone asks "Allow USB debugging?" with the laptop's fingerprint, tick **Always allow from this computer** and tap **Allow**.

| Brand | Where the Build number is | Where Developer options appear |
|---|---|---|
| Samsung | Settings > About phone > Software information > Build number | Bottom of Settings |
| Xiaomi, Redmi, POCO | Settings > About phone > tap **MIUI version** or **OS version** 7 times | Settings > Additional settings > Developer options |
| OnePlus, Oppo, Realme | Settings > About device > Version > tap **Build number** (or **Version number**) 7 times | Settings > System settings (or Additional settings) > Developer options |
| Vivo, iQOO | Settings > About phone > Software version > tap **Software version** 7 times | Settings > System management > Developer options |
| Motorola, Nokia, Pixel, other stock Android | Settings > About phone > Build number | Settings > System > Developer options |

If your phone is not in this table, search the web for "<your phone model> enable developer options".

## Step 7: Run the Hello screen on your phone

1. In the Android Studio toolbar, next to the green Run triangle, there is a device dropdown. It should show your phone's name, for example "Samsung SM-A546E". If it shows a virtual device or "No devices", see Troubleshooting below.
2. Click the green **Run** triangle (or press Shift+F10).
3. The first build takes 2 to 5 minutes. Watch the **Build** panel at the bottom. It ends with **BUILD SUCCESSFUL** and the app opens on the phone by itself.
4. On the phone you should see a blue **Hello!** heading, the line "Phase 0 is complete. The app is running on this phone.", and a card with your phone's maker and model and its Android version.

### Test checklist for Phase 0

1. The app installed and opened by itself after the build.
2. The card shows the correct phone model and Android version.
3. Dark mode: on the phone, turn on dark theme from the quick settings panel. Reopen the app. The screen turns dark with light-blue text. Turn dark theme off again.
4. Hindi: Settings > System > Languages > add **हिन्दी** and drag it to the top (the path varies slightly by brand). Reopen the app. The texts are now in Hindi, including the app name under the icon. Move English back to the top afterwards.
5. The app icon (blue shield with a needle) is in the phone's app list under the name **Driver Safety Monitor**.

## Step 8: Confirm Firebase sees the app

1. In the Firebase console open **Analytics > Realtime** in the left menu (you may need to expand **Analytics** or **Run**).
2. With the app open on your phone, wait up to five minutes. **Users in the last 30 minutes** should show 1 and the device list should show an Android device.
3. If nothing shows after ten minutes, do not worry. Analytics can be slow on the first day, and Phase 1 gives a stronger check when login works. Tell me and continue.

## Troubleshooting

**"File google-services.json is missing. The Google Services Plugin cannot function without it."** The file is not at `app/google-services.json`. Redo step 4c. Make sure it is in the `app` folder, not in the `LifeSafety` folder next to it, and that the name is exactly `google-services.json` (Windows sometimes adds "(1)" to a second download).

**The phone does not appear in the device dropdown.** In order: try another USB cable or port. On the phone set the USB mode to **File transfer**. Unplug, turn USB debugging off and on, plug in again and accept the "Allow USB debugging" prompt. In Android Studio open **View > Tool Windows > Device Manager**, click the three dots and choose **Troubleshoot Device Connections**, then follow its steps. Samsung phones sometimes need the Samsung USB driver from https://developer.samsung.com/android-usb-driver. Other brands sometimes need the Google USB Driver: **Tools > SDK Manager > SDK Tools** tab, tick **Google USB Driver**, click **Apply**.

**Wireless fallback if USB never works (Android 11 or newer).** On the phone, in Developer options turn on **Wireless debugging**. Phone and laptop must be on the same Wi-Fi. In Android Studio's device dropdown choose **Pair Devices Using Wi-Fi**. On the phone tap **Wireless debugging > Pair device with QR code** and scan the code shown in Android Studio.

**"Installation did not succeed" or "INSTALL_FAILED_USER_RESTRICTED" on a Xiaomi or Redmi phone.** Turn on **Install via USB** and **USB debugging (Security settings)** in Developer options (step 6, point 5). Both need a Mi account and a SIM card.

**"SDK location not found."** Android Studio normally writes `local.properties` with the SDK path when the project opens. Close the project (**File > Close Project**) and open it again from the Welcome window. If it still fails, open **Tools > SDK Manager**, copy the **Android SDK Location** at the top, and tell me.

**Gradle sync is very slow or fails with a network error.** Wait and press **Try again**. On a slow connection the first sync can take half an hour. If it keeps failing, copy the red error text from the Build panel and send it to me.

**Anything red that you do not understand.** Open the **Build** panel at the bottom, click the lines on the left until you see red text on the right, select all of it and send it to me as it is. I will find the root cause.

## Tell me when you are done

Send me "Phase 0 works" plus the phone model shown on the Hello screen. Also confirm two decisions, because changing them later is painful:

The package name is `com.lifesafety.driversafety`. It becomes the permanent app ID on Google Play and is now registered in Firebase. If you would rather use another one, say so before Phase 1.

The app name shown to users is **Driver Safety Monitor** (Hindi: ड्राइवर सेफ़्टी मॉनिटर). This one is easy to change at any time.

Then I start Phase 1: role selection, Google login, consent screen and pairing codes.
