# Android FCM Setup

Soll uses Firebase Cloud Messaging only for true closed-app chat notifications. WorkManager sync remains a fallback, but it cannot wake a fully stopped app as reliably as FCM.

## Android config

1. In Firebase Console, create or open the Soll Firebase project.
2. Add an Android app with package name `com.soll.debug` for the debug build.
3. Download `google-services.json`.
4. Put the real file here:

```text
D:\Projects\soll_app\app\src\debug\google-services.json
```

Do not commit the real file. `app/src/debug/google-services.example.json` shows the expected shape only.

## Server config

1. In Firebase Console, open Project settings -> Service accounts.
2. Generate a new private key.
3. Put the real service account file here:

```text
D:\Projects\Soll\server\secrets\firebase-service-account.json
```

4. Enable FCM in `D:\Projects\Soll\server\.env`:

```dotenv
SOLL_FCM_ENABLED=true
SOLL_FCM_PROJECT_ID=your-firebase-project-id
SOLL_FCM_SERVICE_ACCOUNT_FILE=server/secrets/firebase-service-account.json
SOLL_FCM_TIMEOUT_SECONDS=8
```

5. Restart the Soll server.
6. Rebuild and install the debug APK, then open the app once so it can register the FCM token.

## Verification

Check that Android no longer logs `Firebase project config is missing` after launch.

On the server, the token registry should appear after the first successful registration:

```text
D:\Projects\Soll\.soll\runtime\android_push_tokens.json
```

`GET /api/v1/system/health-summary` includes `android_push` with config and token status.
