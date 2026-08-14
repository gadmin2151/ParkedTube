FROM gradle:8.10.2-jdk17@sha256:c2900027f3f0681c2cbfb09d527813851ad67aeafbb409997297efa2df20e748

ARG ANDROID_COMMAND_LINE_TOOLS_VERSION=11076708
ARG ANDROID_COMMAND_LINE_TOOLS_SHA1=d313adb7aedccf6cf0cfca51ec180f0059f5f8f8

ENV ANDROID_SDK_ROOT=/opt/android-sdk
ENV ANDROID_HOME=/opt/android-sdk
ENV PATH="${ANDROID_SDK_ROOT}/cmdline-tools/latest/bin:${PATH}"

USER root

RUN mkdir -p "${ANDROID_SDK_ROOT}/cmdline-tools" \
    && wget -q "https://dl.google.com/android/repository/commandlinetools-linux-${ANDROID_COMMAND_LINE_TOOLS_VERSION}_latest.zip" -O /tmp/cmdline-tools.zip \
    && echo "${ANDROID_COMMAND_LINE_TOOLS_SHA1}  /tmp/cmdline-tools.zip" | sha1sum -c - \
    && unzip -q /tmp/cmdline-tools.zip -d /tmp/android-cmdline-tools \
    && mv /tmp/android-cmdline-tools/cmdline-tools "${ANDROID_SDK_ROOT}/cmdline-tools/latest" \
    && rm -rf /tmp/cmdline-tools.zip /tmp/android-cmdline-tools

RUN yes | sdkmanager --licenses >/dev/null \
    && sdkmanager "platforms;android-35" "build-tools;35.0.0" \
    && chmod -R a+rX "${ANDROID_SDK_ROOT}"

RUN sdkmanager "platform-tools" \
    && chmod -R a+rX "${ANDROID_SDK_ROOT}"

WORKDIR /workspace

USER gradle

ENTRYPOINT ["bash", "scripts/container-build.sh"]
CMD ["debug"]
