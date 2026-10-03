#!/usr/bin/env bash

# The emulator action executes each script line in a separate shell. Keep the test
# exit status and the diagnostic collection together, including when tests fail.
set +e
mkdir -p app/build/diagnostics

# Copy while the test APK is installed, before runner cleanup removes app-owned files.
copy_playlist_previews() {
    for preview_package in dev.behradhz.meowzix dev.behradhz.meowzix.test; do
        preview_path="/sdcard/Android/data/$preview_package/files/playlist-previews"
        if adb shell test -d "$preview_path" 2>/dev/null; then
            adb pull "$preview_path" app/build/diagnostics/ >/dev/null 2>&1 || true
        fi
    done
}

gradle :app:connectedDebugAndroidTest --stacktrace &
instrumentation_pid=$!
while kill -0 "$instrumentation_pid" 2>/dev/null; do
    copy_playlist_previews
    sleep 2
done
wait "$instrumentation_pid"
instrumentation_status=$?
copy_playlist_previews
adb logcat -d -v threadtime > app/build/diagnostics/instrumentation-logcat.txt || true
exit "$instrumentation_status"
