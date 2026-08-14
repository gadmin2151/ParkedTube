#!/usr/bin/env bash
set -euo pipefail

cd /workspace

mkdir -p outputs "${ANDROID_USER_HOME}" "${HOME}"

BUILD_MODE="${1:-debug}"
PUBLISH_MODE="${2:-build-only}"
VERSION_NAME="$(sed -n 's/^PARKEDTUBE_VERSION_NAME=//p' gradle.properties)"
APK_SIGNER="${ANDROID_SDK_ROOT}/build-tools/35.0.0/apksigner"
AAPT="${ANDROID_SDK_ROOT}/build-tools/35.0.0/aapt"

if [[ -z "${VERSION_NAME}" ]]; then
    echo "PARKEDTUBE_VERSION_NAME is missing from gradle.properties." >&2
    exit 2
fi

case "${BUILD_MODE}" in
    debug)
        gradle --no-daemon --console=plain \
            clean \
            :app:lintDebug \
            :app:testDebugUnitTest \
            :app:assembleDebug
        SOURCE_APK="app/build/outputs/apk/debug/app-debug.apk"
        OUTPUT_APK="outputs/ParkedTube-debug.apk"
        EXPECTED_PACKAGE="dev.codex.aaparkedyoutube.debug"
        ;;
    release)
        required_variables=(
            ANDROID_KEYSTORE_PATH
            ANDROID_KEYSTORE_PASSWORD
            ANDROID_KEY_ALIAS
            ANDROID_KEY_PASSWORD
        )
        for variable_name in "${required_variables[@]}"; do
            if [[ -z "${!variable_name:-}" ]]; then
                echo "Missing required release variable: ${variable_name}" >&2
                exit 2
            fi
        done

        gradle_tasks=(
            clean
            :app:lintRelease
            :app:testReleaseUnitTest
            :app:assembleRelease
        )
        if [[ "${PUBLISH_MODE}" == "publish" ]]; then
            gradle_tasks+=(
                :app:publishReleaseApkPublicationToGitHubPackagesRepository
            )
        fi

        gradle --no-daemon --console=plain "${gradle_tasks[@]}"
        SOURCE_APK="app/build/outputs/apk/release/app-release.apk"
        OUTPUT_APK="outputs/ParkedTube-${VERSION_NAME}.apk"
        EXPECTED_PACKAGE="dev.codex.aaparkedyoutube"
        ;;
    *)
        echo "Unsupported build mode: ${BUILD_MODE}" >&2
        exit 2
        ;;
esac

if [[ ! -f "${SOURCE_APK}" ]]; then
    echo "APK was not created: ${SOURCE_APK}" >&2
    exit 1
fi

cp "${SOURCE_APK}" "${OUTPUT_APK}"
"${APK_SIGNER}" verify --verbose --print-certs "${OUTPUT_APK}"

PACKAGE_NAME="$("${AAPT}" dump badging "${OUTPUT_APK}" | sed -n "s/^package: name='\([^']*\)'.*/\1/p")"
if [[ "${PACKAGE_NAME}" != "${EXPECTED_PACKAGE}" ]]; then
    echo "Unexpected package name: ${PACKAGE_NAME}" >&2
    exit 1
fi

sha256sum "${OUTPUT_APK}" > "${OUTPUT_APK}.sha256"
ls -lh "${OUTPUT_APK}" "${OUTPUT_APK}.sha256"
