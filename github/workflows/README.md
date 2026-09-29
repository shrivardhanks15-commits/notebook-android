NoteBook for Android (phones + tablets)
Kotlin + Jetpack Compose port of the iPad GoodNotes-style app.
Features: notebook library (create/rename/delete, cover colors), page grid, full-screen drawing editor (pen, highlighter, eraser, undo, colors, thickness), stylus-only mode (turn off "Finger draw"), import PDF as background, export notebook as PDF, auto-save.
Run it (works on Windows, Mac, Linux)
Install Android Studio (free): https://developer.android.com/studio
File > Open > select this folder, wait for Gradle sync.
Run on an emulator, or enable Developer options + USB debugging on your device.
To get an installable APK: Build > Build Bundle(s)/APK(s) > Build APK(s). Copy app-debug.apk to the device and open it (allow "install unknown apps"). Requires Android 8.0 (API 26) or newer.
Build the APK in the cloud (no PC needed)
Create a free GitHub repo and upload ALL files in this folder (keep the folder structure, including the hidden .github/workflows/build.yml).
Open the repo's Actions tab > "Build APK" > Run workflow (it also runs on every push).
When it finishes (~5 min), open the run and download the "NoteBook-APK" artifact.
Unzip it, open app-debug.apk on your phone and allow "install unknown apps".
