# TV Launcher (Apple-TV-style home screen for Android 4.4)

A replacement home screen: dark gradient, big clock, rows of rounded tiles that
grow when selected with the remote.

- **Favorites** row (hold OK on any app to add or remove it)
- **Apps** row (everything installed, A to Z)
- **Quick Settings** row (Settings, Wi-Fi, Display, Storage, Manage Apps)

Original design. It copies the layout idea, not Apple's logos or assets.

## Build the APK from your phone (no PC)

You need a free GitHub account. Everything below works in your phone's browser.

1. Go to github.com, sign in, tap **+ > New repository**. Name it `tv-launcher`,
   keep it **Public**, tap **Create repository**.
2. Tap **creating a new file**. In the name box type the path exactly (typing `/`
   makes folders), paste the file's contents, then tap **Commit changes**.
   Repeat for all five files:

   | Path to type | Contents from |
   |---|---|
   | `settings.gradle` | settings.gradle |
   | `build.gradle` | build.gradle |
   | `src/main/AndroidManifest.xml` | AndroidManifest.xml |
   | `src/main/java/com/tvlauncher/home/LauncherActivity.java` | LauncherActivity.java |
   | `.github/workflows/build.yml` | build.yml |

   Commit the `build.yml` file last. Each commit starts a build, so the early ones
   will fail. That is normal. Only the last one has to succeed.
3. Open the **Actions** tab. Wait 3 to 5 minutes for the green tick.
4. Open the **Releases** page of your repository (right side on desktop view, or
   the "Releases" link on the repo page). Download **tv-launcher.apk**.

If the build fails (red cross), open the run, tap the failed step, and send me
the last 20 lines of the log. I'll fix it.

## Install on the TV

1. TV: **Settings > Security > Unknown sources: ON**.
2. Get `tv-launcher.apk` onto the TV by any of these:
   - copy it to a USB pendrive (phone with OTG, or any card reader) and plug it into the TV
   - use a file-transfer app on both phone and TV (for example "Send Files to TV")
   - open the Releases page in the TV's own browser, if it can load GitHub
3. Open the APK with a file manager on the TV and tap **Install**.
4. Press **Home** on the remote. Choose **TV Launcher**, then **Always**.

## Go back to the old home screen

TV: **Settings > Apps > TV Launcher > Clear defaults**, then press Home and pick
the old launcher. Or uninstall TV Launcher from the same screen.

## Controls

- Arrow keys: move between tiles and rows
- OK: open app
- Hold OK on an app: add or remove from Favorites
- Home: jump back to the top
