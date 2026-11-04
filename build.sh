#!/usr/bin/env bash
# 词光 Word Atlas 安卓版构建脚本
# 创建者: zhenghq
set -euo pipefail

cd "$(dirname "$0")"

export JAVA_HOME="${JAVA_HOME:-/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home}"
export ANDROID_HOME="${ANDROID_HOME:-$HOME/Library/Android/sdk}"
export ANDROID_SDK_ROOT="$ANDROID_HOME"

GRADLE="./tools/gradle/gradle-8.11.1/bin/gradle"

if [ ! -x "$GRADLE" ]; then
  echo "缺少 Gradle，请先解压 gradle-8.11.1 到 tools/gradle/" >&2
  exit 1
fi

"$GRADLE" :app:assembleDebug --console=plain "$@"

APK="app/build/outputs/apk/debug/app-debug.apk"
if [ -f "$APK" ]; then
  echo
  echo "APK: $(pwd)/$APK"
  ls -lh "$APK"
fi
