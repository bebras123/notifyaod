# NotifyAOD

<img src="https://github.com/bebras123/notifyaod/blob/main/docs/screenshot.png" width="300" height="800">

**NotifyAOD** is an Android utility that automatically turns **Always-On Display (AOD)** on or off
depending on incoming notifications.

This app is useful if there is no such option on your device.
This app just turns on/off your devices' AOD, it does not provide AOD functionality itself.
This app does not modify how AOD looks.

When you receive a notification:

- The app **enables AOD** so you can see it immediately on your lock screen.
- When all notifications are cleared, the app **disables AOD**.

By default all notifications will enable AOD.

You decide which apps are considered important and whether to use **whitelist mode**:

- In **whitelist mode**, **only** notifications from selected apps enable AOD.
- In normal mode (whitelist off), all notifications from **non-listed** apps will trigger AOD.

---

## ⚙️ How to Set Up

### 1. **Install the App**

Install the [notifyAOD.apk](https://github.com/bebras123/notifyaod/blob/main/notifyAOD.apk) on your device.

To make NotifyAOD work your device must meet these conditions:

### 2. **Root Access or grant WRITE_SECURE_SETTINGS**

Changing AOD settings programmatically requires modifying **`Settings.Secure`**, which is restricted
on most Android devices.  
📌 **Your device must be rooted** OR you must grant **`WRITE_SECURE_SETTINGS`** using adb:

1. Download and extract https://developer.android.com/tools/releases/platform-tools
2. Enable Developer Options on your phone:
    * Go to Settings → About phone → Tap Build Number 7 times
    * Enable `USB Debugging` inside `Developer Options`
    * Plug your phone into computer
    * Accept the computer prompt when plugging in your phone
3. Go to extacted `platform-tools` directory and run

```
adb shell pm grant com.bebras123.notifyaod android.permission.WRITE_SECURE_SETTINGS
```

4. You can disable Developer Options now (if no errors)

### 3. **Notification Access**

You need to grant the app **Notification Access** so it can monitor incoming notifications.
App will redirect to these settings, you need to enable it.

To enable it:

1. Open your phone **Settings**
2. Go to **Apps & Notifications** → **Special App Access**
3. Find and tap on **Notification Access**
4. Enable it for **NotifyAOD**

---

## 🛠 Troubleshooting

See `App log` in app



