# S24 Backup v0.1.0

Small, advertisement-free Android utility to back up local folders to a Samba share. Intended for personal sideloading onto a Samsung Galaxy S24.

## Design

- \`DCIM\` included by default. Add/enable/disable/remove additional internal-storage folders.
- One-way *copy* to SMB, preserving paths relative to internal storage.
- Deleting a phone file **never** deletes a backup. No server-side synchronization or remote deletion.
- Existing files are skipped when a saved successful-upload record matches local size and modification time *and* the remote file exists.
- An interrupted upload only affects a \`.s24backup.part\` temporary file. A successful upload replaces the destination through a rename.
- Auto backup roughly every 6 hours, subject to Android WorkManager's scheduling, on an unmetered network. The SMB server also must be reachable.
- "Back Up Now" for manual transfers.
- Real SMB **Save Settings** button, distinct from **Test Connection**, with unsaved-changes warning.
- Samba credentials stay on the device. Password is encrypted using an Android Keystore AES-GCM key.
- Foreground notification during backups, status and error report in the app.
- No subscription, analytics, ads, tracking, cloud service, or remote relay.

## First use

1. Install the debug APK by sideloading it.
2. Open **SMB Settings**, enter host, share, Samba user, password, and destination folder; tap **Test Connection**, then **Save Settings**.
3. Grant **All files access** when prompted; it is required to read and traverse DCIM and arbitrary folders via File APIs on Android 11+.
4. Tap **Back Up Now** for initial upload. It may take a while.
5. Additional directories can be selected using **Add folder**.
6. Optional: set this app's battery usage to **Unrestricted** in Android settings if background work is being delayed.

Default destination folder is \`S24 Backup\`, so camera pictures go to \`\\\\host\\ShareDrive\\S24 Backup\\DCIM\\...\`. It is a proposed folder; the user can edit it.

## Notes / limitations

- First test on an actual S24/Samba share is still required. A successful CI build does **not** certify runtime operation.
- Periodic work is best-effort, not a guaranteed exact clock schedule. Android may defer it for Doze, battery, or network conditions.
- Large initial backups are a foreground data-sync operation. Android 15+ may impose time limits; successful files are recorded individually so the next pass continues.
- The app uses a special all-files permission for personal sideloading. It is not designed for Play Store distribution.
- The target folder should be writable by the Samba account. A saved connection test does not imply write access.
- Android may restrict some system-managed directories even with all-files access.
- Per-file retries start from byte zero for that file, not byte-level resume.
- If remote filenames are modified independently, the uploader may overwrite files at the same path when it detects a changed local file. Deleted local files are never propagated.
- Backup DB and password key are device-local. Uninstalling clears these settings.
- Debug APK is signed with a development key; reinstalling an APK built with a different debug key may require uninstall/reinstall, which clears settings.

## Build

Android Gradle Plugin 8.9.2, Gradle 8.11.1, JDK 17, Android SDK 36.
\`gradle :app:assembleDebug\`

Source lives on isolated \`s24-backup-build\` branch so existing unrelated applications on main stay untouched.
