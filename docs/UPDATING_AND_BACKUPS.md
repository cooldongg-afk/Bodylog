# BODYLOG — safe updates and records

## Starting with v1.0.0

- **Permanent Android application ID:** `com.bodylog.personal.stable`
- Never change the package ID, JavaScript `bodylog_v1` localStorage key,
  on-device `body_photos` folder, or widget SharedPreferences key.
- Increment `versionCode` for every update.
- Every release **must be signed by the same permanent private keystore**.
- Release signing is only done with GitHub Actions repository secrets.
  No debug or newly generated per-build keys can be used for updates.
- Existing Android WebView localStorage, app-private photos, and native records
  survive **in-place upgrades**. Clearing app data or uninstalling does not.
- An additional private native JSON mirror recovers diary/fasting/weight
  information if WebView localStorage becomes missing while app-private data
  remains intact. Photos remain in private app file storage.

## One-time GitHub setup

Download and keep the private bundle supplied separately in your ChatGPT
conversation. **Never commit any key/password/base64 file.**

Go to https://github.com/cooldongg-afk/Bodylog/settings/secrets/actions
and create two Actions repository secrets:

1. `BODYLOG_KEYSTORE_B64`: entire text from `BODYLOG_KEYSTORE_B64.txt`
2. `BODYLOG_SIGNING_PASSWORD`: entire text from `BODYLOG_SIGNING_PASSWORD.txt`

Then Actions > Build BODYLOG release APK > Run workflow.
The workflow will fail before compilation if the signing secrets are missing.

**Keep the original key files in secure personal storage. If the key is lost,
Android cannot accept a future update under the same app ID.**

## Initial migration from v0.8 or v0.9

Previous prototype app versions used independent app IDs and temporary debug
signatures. They cannot be updated in place by v1.0.

**One-time migration is necessary**:

1. Do **not** uninstall the old BODYLOG.
2. In the old app, open Analysis > Data Management > JSON backup.
3. Separately back up photos using Photos > ZIP backup.
4. Install v1.0 stable as a separate app.
5. Import JSON in the new app, then restore the photo ZIP.
6. Verify actual weight, diary, fasting and body pictures before deleting old
   versions.

From v1.0 to future versions, install the newly signed APK *over the existing
stable app* and never uninstall or clear data.

## Security notes
- The repository is public at present. Keep only source code here.
- Never place body photos, weight diaries or release keys in Git history.
- GitHub Actions secrets must not be printed in build logs.
