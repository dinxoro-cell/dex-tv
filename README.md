# DeX TV — Samsung DeX TV-style launcher (prototype v0.1)

A clean, ad-free, original Android TV-style interface for a Galaxy S24 Ultra connected to a TV/projector through Samsung DeX. Visual inspiration: spacious TV launchers. Not affiliated with Apple or Samsung; no proprietary Apple assets.

## Features
- Fullscreen, landscape Android launcher **app** (does not replace the Samsung DeX desktop or system launcher).
- Dark interface with large installed-app tiles, 5 columns, 2 rows per page.
- DPAD/keyboard arrow keys + Enter, mouse/touch selection.
- Common streaming apps listed first if installed; all other launchable apps follow alphabetically.
- No ads, trackers, analytics, account, or monthly fee.
- Built-in remote-control webpage accessible from a **second** phone on the same trusted Wi-Fi or local network.
- Pairing PIN displayed on TV; remote can navigate the **DeX TV interface only**.

## Build an APK
### Android Studio
1. Install current Android Studio with Android SDK platform 35 and JDK 17.
2. Open this folder (`DeX-TV`) as a project, let Gradle synchronize.
3. Select **Build > Build APK(s)**.
4. Find `app/build/outputs/apk/debug/app-debug.apk`.

### GitHub Actions (no Android Studio required)
1. Create a new GitHub repository and upload **all** files from this folder, including `.github/workflows/build-apk.yml`.
2. In GitHub select **Actions > Build Android APK > Run workflow** (or push to `main`).
3. Once it succeeds, download the artifact **DeX-TV-debug-APK** and unzip to obtain `app-debug.apk`.
4. Transfer `app-debug.apk` to the Samsung phone and install it, approving install-from-this-source when prompted.

This prototype has not been compiled or tested on a physical S24 Ultra in this environment.

## Run on TV
1. Connect the Galaxy S24 Ultra to a TV/projector using a compatible USB-C HDMI adapter/hub.
2. Start Samsung DeX.
3. Open **DeX TV** and use the DeX window's maximize/fullscreen control when needed.
4. Use Bluetooth keyboard/mouse, or a second phone as remote.
5. To pair the second phone, connect it to the same local Wi-Fi as the Galaxy, type the TV-displayed `http://IP:8765` address into its browser, and enter the 6-digit PIN. Bookmark or add the page to the phone's home screen.

## Limitations / security
- Android cannot grant a normal app permission to replace DeX globally. Opening a streaming app switches away from this home screen. Press Back or return to DeX TV to navigate here again.
- The browser remote navigates **only within DeX TV**, not Netflix/YouTube/other apps, nor global Android/Home buttons. Android requires separate accessibility/companion mechanisms for broader control; this prototype does not request such access.
- The remote uses HTTP on port 8765 and a randomly generated pairing PIN/session cookie; HTTP is **not encrypted**. Use only a trusted home network. Do not open/forward port 8765 to the internet. If a captive, client-isolated, or restricted network blocks local devices, remote access will not work.
- If multiple local IPs are present, the displayed one may not be reachable. Use your Wi-Fi network's actual device IPv4 address if needed.
- The remote server runs only while the app's process is alive. Android may stop it when app is in background.
- Some DRM streaming apps may restrict display or playback over HDMI. This app cannot remove those restrictions.
- Debug APK is developer-signed. For long-term distribution, configure proper release signing.
- Refresh Apps on remote is available after installing new apps.

## Future enhancements
Per-app shortcuts, TV-specific artwork, second-screen native Android remote, global transport control via Android media sessions where permitted, onboarding, configurable favorites, Wi-Fi network status and test coverage.
