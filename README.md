# Fieldnote

Fieldnote records mono microphone audio on Android 10 and newer. Tap **● Record** to begin and **■ Stop** to save. Fieldnote keeps its own screen awake during a take. You can switch to a browser, close the recorder window, rotate the device or lock the screen during a take. Tap **Stop** in Fieldnote or its recording notification to finish. A notification from an earlier take cannot stop a newer one. The most recent file has a playback button. All completed recordings are visible in **Music/Fieldnote**.

## Recording profiles

Automatic mode checks the microphone buffer requirements and the device’s advertised AAC-LC and Opus encoder configurations. It ranks hardware-accelerated encoders first, then prefers 128 kbps, AAC and 48 kHz, in that order. It also considers 64 kbps and 44.1 kHz. Ties use the encoder name. Selecting a specific profile pins that encoder, format, sample rate and bitrate across launches while it remains available.

The app opens the exact named encoder. In Automatic mode, if microphone or encoder startup rejects a candidate, it tries the next one. The recording screen displays the active profile. AAC recordings use M4A and Opus recordings use Ogg. Acceleration is a manufacturer-reported property. The app does not measure power consumption. [Android codec capabilities](https://developer.android.com/reference/android/media/MediaCodecInfo#isHardwareAccelerated())

## Permissions and storage

The first tap on Record requests microphone access. On Android 13 and newer it also offers notification access so the recording notification can provide Stop while you use another app. If you decline notifications, you can still record and reopen Fieldnote to stop. The app declares the standard foreground-service permission and the microphone foreground-service type permission. Each recording starts from a visible user action. [Android microphone foreground services](https://developer.android.com/develop/background-work/services/fgs/service-types#microphone) · [Android notification permission](https://developer.android.com/develop/ui/compose/notifications/notification-permission)

Fieldnote uses Android MediaStore to write its own files without storage permission. Recordings become public after the muxer finalizes and the file descriptor closes. A failed take discards its pending row. After a force-stop or process kill, an unfinished recording can be lost. The service does not restart capture automatically. The last-recording control reads the latest published audio owned by Fieldnote and follows renames and deletions. Playback grants the Android player read access to the selected recording. [Android shared media storage](https://developer.android.com/training/data-storage/shared/media)

## Build and verify

Use JDK 21, the Android SDK platform 36 and build tools 35.0.0. Android Studio can open the project root. From the command line, set `ANDROID_HOME` to the SDK or set `sdk.dir` in `local.properties`.

```sh
./gradlew :app:assembleDebug :app:lintDebug checkComplexity
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Run the device tests on an unlocked Android device or emulator with microphone input. The verification build has its own application ID. Android Test Orchestrator isolates each test and test storage retains coverage counters across application-data resets. The tests create short recordings, exercise Android playback at zero volume and remove their clips.

```sh
ANDROID_SERIAL=emulator-5554 ./gradlew :app:checkDeviceCoverage
```

Replace `emulator-5554` with the serial from `adb devices`. SonarJava 8.46 measures every explicit Java method and constructor with a body in the app, tests and complexity checker. The build fails above 9. Per-method values are in `build/reports/complexity.tsv`. The coverage task requires at least 80% executable-line coverage in every production method with JaCoCo line counters. Device coverage and test details are under `app/build/reports/`.

## Ownership and contracts

The Android-independent `api` package defines profiles, clip identity, recording admission, stop and publication. The pure Java `domain` package ranks candidates. The `audio` package implements these contracts with Android audio and storage APIs. The `ui` package owns the visible session, permission flow and user preferences.

One capture worker owns the microphone, codec, muxer and pending file for a take. The foreground service promotes itself before the worker opens the microphone and stays active through finalization. The UI and notification request a stop on the same session. At cutoff the worker stops the microphone, queues end-of-stream, drains output and releases native handles. Each borrowed output slot is returned after its synchronous write. At most one input slot and one output slot are held. No PCM history is retained by the app. A ten-second absence of stream progress ends the take with an error. The next take is admitted after the previous worker settles its handles.
