# MineSafe Mobile

Android app for reporting mine hazards, incidents, and near misses. Reports and evidence are stored locally so a worker can continue a draft or submit while offline. The app attempts to sync submitted reports when a network is available.

## Current development setup

- Open this project in Android Studio and run the `app` debug build on an emulator.
- The debug API address defaults to `http://10.0.2.2:5080`, which reaches the ASP.NET API from an emulator. For a physical phone on the same network, build with `-PmineSafeApiBaseUrl=http://<PC-LAN-IP>:5080` and install that APK. The backend must listen on the LAN and Windows Firewall must allow the connection.
- `assembleRelease` now creates a signed, non-debuggable APK using the ignored `release-signing.properties` and `release-signing/` files on this development PC. This local release test build connects to `http://10.0.0.101:5080`. Keep the PC and phone on the same network and keep the backend running.
- Install `app/build/outputs/apk/release/app-release.apk` for release-mode testing. It uses a different signing key from the debug APK, so Android cannot install it over the debug app. Sync or save any drafts you need before removing a debug installation.
- The local test signing key and password are not in Git. Back up both ignored signing files if future APKs should update this installed release app.
- This release APK permits local HTTP only to the reserved test server address. Before public distribution, switch the API to HTTPS, remove that HTTP exception, and use the intended long-term signing key and application ID.
- The backend's Development test accounts include `W1001` (worker), `S2001` (supervisor), `O3001` (Safety Officer), and `A9001` (administrator), all with password `Password123!`. These accounts come from the backend's development seeder, not the mobile app.

## Integration status

The temporary Python API and its sample reports have been removed. Login, locations, report submission, status refresh, supervisor review, and Safety Officer incident views now use the ASP.NET backend contract. End-to-end reporting still needs testing on both worker and supervisor devices.

The app's local Room report storage and genuine Android instrumentation tests remain. No report fixtures are packaged into the app.
