# ParkedTube

ParkedTube is a small Android app that opens YouTube in a full-screen WebView on a
phone and on supported Android Auto parked-app hosts. The car display gets a landscape
layout and an integrated Russian/English search keyboard; the phone keeps its normal
orientation and system keyboard.

This project is intended for personal testing. It is not affiliated with or endorsed
by Google or YouTube. Playback is for parked use only: the app does not disable or
bypass Android Auto driver-distraction restrictions.

## Requirements

- Docker Desktop or another Docker Engine
- An Android phone with Android Auto developer mode enabled for apps installed outside
  Google Play
- A compatible Android Auto host that exposes parked apps

## Build

The complete APK build runs inside Docker. No local Android SDK or Gradle installation
is required.

```bash
./scripts/build-apk.sh debug
```

The result is written to `outputs/ParkedTube-debug.apk` after the container exits.

For a signed release build, provide the signing key through environment variables:

```bash
PARKEDTUBE_KEYSTORE_PATH=/absolute/path/parkedtube-release.jks \
PARKEDTUBE_KEYSTORE_PASSWORD='...' \
PARKEDTUBE_KEY_ALIAS='...' \
PARKEDTUBE_KEY_PASSWORD='...' \
./scripts/build-apk.sh release
```

The result is written to `outputs/ParkedTube-<version>.apk` together with its SHA-256
checksum. Keystores and passwords must never be committed to the repository.

## Install

```bash
adb install -r outputs/ParkedTube-1.4.1.apk
```

If an older build was signed with a different key, Android requires uninstalling that
build once before installing the release APK.

## GitHub release

Every push and pull request builds and tests a debug APK in Docker. A tag matching the
version in `gradle.properties`, for example `v1.4.1`, starts the release workflow. It:

1. builds and verifies a signed release APK in Docker;
2. uploads the APK as a workflow artifact;
3. creates a GitHub artifact attestation;
4. publishes `dev.codex:parkedtube:<version>` to GitHub Packages;
5. creates a GitHub Release with the APK and checksum.

The repository must define these Actions secrets:

- `ANDROID_KEYSTORE_BASE64`
- `ANDROID_KEYSTORE_PASSWORD`
- `ANDROID_KEY_ALIAS`
- `ANDROID_KEY_PASSWORD`

## Android Auto

Install the APK on the phone, enable developer mode in Android Auto, allow unknown
sources, and reconnect to the car. ParkedTube should then appear in the Android Auto
launcher customization list. Availability and driving restrictions are controlled by
Android Auto and the vehicle host.

## License

ParkedTube is available under the
[PolyForm Noncommercial License 1.0.0](LICENSE). You may use, study, modify, and
redistribute it for noncommercial purposes. Commercial use, paid distribution, and
resale are not permitted without a separate written commercial license from the
copyright holder.

This is a source-available license, not an OSI-approved open-source license. Copies and
modified versions must retain the license terms and the required notice in `NOTICE`.
