<p align="center">
  <img src="docs/assets/parkedtube-logo.png" width="176" alt="ParkedTube logo">
</p>

<h1 align="center">ParkedTube</h1>

<p align="center">
  <strong>A focused YouTube experience for supported Android Auto parked-app screens.</strong>
</p>

<p align="center">
  Full-screen playback on the car display, an integrated Russian/English keyboard,
  and a normal adaptive experience on the phone.
</p>

<p align="center">
  <a href="https://github.com/gadmin2151/ParkedTube/actions/workflows/ci.yml"><img alt="CI status" src="https://img.shields.io/github/actions/workflow/status/gadmin2151/ParkedTube/ci.yml?branch=main&style=for-the-badge&label=CI&labelColor=18181b"></a>
  <a href="https://github.com/gadmin2151/ParkedTube/releases/latest"><img alt="Latest release" src="https://img.shields.io/github/v/release/gadmin2151/ParkedTube?style=for-the-badge&label=Release&labelColor=18181b&color=ff1f2d"></a>
  <img alt="Android 9 or newer" src="https://img.shields.io/badge/Android-9%2B-3ddc84?style=for-the-badge&logo=android&logoColor=white&labelColor=18181b">
  <a href="LICENSE"><img alt="PolyForm Noncommercial license" src="https://img.shields.io/badge/License-PolyForm_Noncommercial-f4f4f5?style=for-the-badge&labelColor=18181b"></a>
</p>

<p align="center">
  <a href="https://github.com/gadmin2151/ParkedTube/releases/latest"><strong>Download APK</strong></a>
  &nbsp;&middot;&nbsp;
  <a href="#build-in-docker">Build in Docker</a>
  &nbsp;&middot;&nbsp;
  <a href="#android-auto-setup">Android Auto setup</a>
  &nbsp;&middot;&nbsp;
  <a href="#license">License</a>
</p>

---

ParkedTube is a small native Android application that opens YouTube in a hardened,
full-screen WebView. On a compatible Android Auto host it uses a landscape layout and
its own large search keyboard. On the phone it follows the device orientation and uses
the normal system keyboard.

> [!IMPORTANT]
> ParkedTube is designed for use while the vehicle is parked. It does not disable,
> spoof, or bypass Android Auto or vehicle driver-distraction restrictions.

## Highlights

| | Capability |
|---|---|
| **Car display** | Full-screen landscape experience on compatible parked-app hosts |
| **Phone** | Adaptive orientation with the regular Android keyboard |
| **Search** | Large integrated Russian/English keyboard on the car display |
| **Playback** | Full-screen video with media playback kept inside the app |
| **Network** | HTTPS-only navigation restricted to YouTube and Google sign-in pages |
| **Build** | Reproducible debug and signed release builds entirely inside Docker |
| **Privacy** | No ParkedTube backend, account system, telemetry, or analytics |

## Compatibility

| Component | Requirement |
|---|---|
| Phone | Android 9 (API 28) or newer |
| Android Auto | Developer mode enabled for applications installed outside Google Play |
| Car host | Must expose Android Auto parked apps in its launcher |
| Build machine | Docker Desktop or another Docker Engine |

Support for parked apps and their availability while driving are controlled by Android
Auto and the vehicle head unit. Compatibility therefore varies between vehicles and
Android Auto versions.

## Download and install

Download the signed APK from the
[latest GitHub Release](https://github.com/gadmin2151/ParkedTube/releases/latest), then
install it on the phone connected to Android Auto:

```bash
adb install -r ParkedTube-1.4.1.apk
```

Each release includes a `.sha256` checksum, a GitHub artifact attestation, the license,
and the required copyright notice. If Android reports a signing conflict with an older
test build, uninstall that build once and install the current release again.

## Android Auto setup

1. Install ParkedTube on the phone.
2. Open Android Auto settings and enable developer mode.
3. Open the Android Auto developer settings and enable **Unknown sources**.
4. Open **Customize launcher** and enable **ParkedTube**.
5. Reconnect the phone to the car while parked.

If ParkedTube does not appear immediately, stop the Android Auto host cache and reopen
its launcher settings:

```bash
./scripts/reset-android-auto-cache.sh
```

For a detailed device and Android Auto report:

```bash
./scripts/diagnose-android-auto.sh
```

Diagnostics are written under `outputs/diagnostics/` and are ignored by Git. Review a
report before sharing it because device logs can contain personal data.

## Build in Docker

The complete Android SDK and Gradle build run inside Docker. No local Android SDK,
Gradle, or Java installation is required.

### Debug APK

```bash
./scripts/build-apk.sh debug
```

Output:

```text
outputs/ParkedTube-debug.apk
outputs/ParkedTube-debug.apk.sha256
```

The build runs Android lint, unit tests, APK assembly, signature verification, package
name verification, and checksum generation.

### Signed release APK

Keep the keystore and credentials outside the repository and pass them at build time:

```bash
PARKEDTUBE_KEYSTORE_PATH=/absolute/path/parkedtube-release.jks \
PARKEDTUBE_KEYSTORE_PASSWORD='...' \
PARKEDTUBE_KEY_ALIAS='...' \
PARKEDTUBE_KEY_PASSWORD='...' \
./scripts/build-apk.sh release
```

Output:

```text
outputs/ParkedTube-<version>.apk
outputs/ParkedTube-<version>.apk.sha256
```

## Releases and packages

Every push and pull request is verified by
[GitHub Actions](https://github.com/gadmin2151/ParkedTube/actions). A tag matching the
version in `gradle.properties`, such as `v1.4.1`, starts the release workflow. It:

1. builds and verifies the signed APK inside Docker;
2. creates a GitHub artifact attestation;
3. publishes `dev.codex:parkedtube:<version>` to GitHub Packages;
4. creates a GitHub Release with the APK, checksum, license, and notice.

Release signing uses these GitHub Actions secrets:

```text
ANDROID_KEYSTORE_BASE64
ANDROID_KEYSTORE_PASSWORD
ANDROID_KEY_ALIAS
ANDROID_KEY_PASSWORD
```

Never commit keystores, passwords, tokens, cookies, or device diagnostics.

## Project layout

```text
app/src/main/          Android application and resources
app/src/test/          URL-policy unit tests
scripts/               Docker build and Android Auto diagnostics
.github/workflows/     CI and release automation
outputs/               Local APKs, checksums, and diagnostics
docs/assets/           Repository branding
```

## Security

WebView navigation is limited to HTTPS YouTube and Google authentication pages.
Cleartext traffic, mixed content, file access, and content access are disabled. See
[SECURITY.md](SECURITY.md) for reporting guidance.

## License

ParkedTube is source-available under the
[PolyForm Noncommercial License 1.0.0](LICENSE). You may use, study, modify, and
redistribute it for noncommercial purposes. Commercial use, paid distribution, and
resale require a separate written commercial license from the copyright holder.

Copies and modified versions must retain the license terms and the required notice in
[NOTICE](NOTICE). PolyForm Noncommercial is not an OSI-approved open-source license.

## Disclaimer

ParkedTube is an independent project. It is not affiliated with, sponsored by, or
endorsed by Google, YouTube, Android Auto, or any vehicle manufacturer. YouTube usage
remains subject to YouTube's terms and availability.
