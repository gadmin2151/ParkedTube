#!/usr/bin/env bash
set -euo pipefail

PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
BUILD_MODE="${1:-debug}"
PUBLISH_MODE="${2:-build-only}"
BUILDER_IMAGE="${PARKEDTUBE_BUILDER_IMAGE:-parkedtube-builder:local}"
DOCKER_PLATFORM="${PARKEDTUBE_DOCKER_PLATFORM:-linux/amd64}"

case "${BUILD_MODE}" in
    debug|release)
        ;;
    *)
        echo "Usage: $0 [debug|release] [build-only|publish]" >&2
        exit 2
        ;;
esac

case "${PUBLISH_MODE}" in
    build-only|publish)
        ;;
    *)
        echo "Usage: $0 [debug|release] [build-only|publish]" >&2
        exit 2
        ;;
esac

if [[ "${PUBLISH_MODE}" == "publish" && "${BUILD_MODE}" != "release" ]]; then
    echo "Only release APKs can be published." >&2
    exit 2
fi

docker build \
    --platform "${DOCKER_PLATFORM}" \
    --tag "${BUILDER_IMAGE}" \
    "${PROJECT_ROOT}"

docker_args=(
    run
    --rm
    --platform "${DOCKER_PLATFORM}"
    --user "$(id -u):$(id -g)"
    --env ANDROID_USER_HOME=/workspace/.android
    --env GRADLE_USER_HOME=/workspace/.gradle-docker
    --env HOME=/workspace/.container-home
    --env JAVA_TOOL_OPTIONS=-Duser.home=/workspace/.container-home
    --volume "${PROJECT_ROOT}:/workspace"
)

if [[ "${BUILD_MODE}" == "release" ]]; then
    required_variables=(
        PARKEDTUBE_KEYSTORE_PATH
        PARKEDTUBE_KEYSTORE_PASSWORD
        PARKEDTUBE_KEY_ALIAS
        PARKEDTUBE_KEY_PASSWORD
    )

    for variable_name in "${required_variables[@]}"; do
        if [[ -z "${!variable_name:-}" ]]; then
            echo "Missing required release variable: ${variable_name}" >&2
            exit 2
        fi
    done

    if [[ ! -f "${PARKEDTUBE_KEYSTORE_PATH}" ]]; then
        echo "Keystore not found: ${PARKEDTUBE_KEYSTORE_PATH}" >&2
        exit 2
    fi

    docker_args+=(
        --volume "${PARKEDTUBE_KEYSTORE_PATH}:/run/secrets/parkedtube-release.jks:ro"
        --env ANDROID_KEYSTORE_PATH=/run/secrets/parkedtube-release.jks
        --env ANDROID_KEYSTORE_PASSWORD
        --env ANDROID_KEY_ALIAS
        --env ANDROID_KEY_PASSWORD
    )

    export ANDROID_KEYSTORE_PASSWORD="${PARKEDTUBE_KEYSTORE_PASSWORD}"
    export ANDROID_KEY_ALIAS="${PARKEDTUBE_KEY_ALIAS}"
    export ANDROID_KEY_PASSWORD="${PARKEDTUBE_KEY_PASSWORD}"
fi

if [[ "${PUBLISH_MODE}" == "publish" ]]; then
    required_variables=(GITHUB_ACTOR GITHUB_TOKEN GITHUB_REPOSITORY)
    for variable_name in "${required_variables[@]}"; do
        if [[ -z "${!variable_name:-}" ]]; then
            echo "Missing required publishing variable: ${variable_name}" >&2
            exit 2
        fi
    done

    docker_args+=(
        --env GITHUB_ACTOR
        --env GITHUB_TOKEN
        --env GITHUB_REPOSITORY
    )
fi

docker "${docker_args[@]}" "${BUILDER_IMAGE}" "${BUILD_MODE}" "${PUBLISH_MODE}"
