#!/data/data/com.termux/files/usr/bin/bash
# Watch 一键编译 + 安装（Termux）
#   ./build.sh              编译并安装
#   ./build.sh --no-install 只编译
set -e

HERE="$(cd "$(dirname "$0")" && pwd)"
cd "$HERE"

# JDK 17（Termux 默认路径，缺失则回退系统 java）
if [ -z "$JAVA_HOME" ]; then
  if [ -d /data/data/com.termux/files/usr/lib/jvm/java-17-openjdk ]; then
    export JAVA_HOME=/data/data/com.termux/files/usr/lib/jvm/java-17-openjdk
  fi
fi
[ -n "$JAVA_HOME" ] && export PATH="$JAVA_HOME/bin:$PATH"

# Android SDK
export ANDROID_HOME="${ANDROID_HOME:-$HOME/Android/Sdk}"
export ANDROID_SDK_ROOT="$ANDROID_HOME"

# Gradle：优先本工程自带的 gradle-8.13，其次 ~/gradle-8.13，最后 PATH 里的 gradle
GRADLE=""
for cand in "$HERE/gradle-8.13/bin/gradle" "$HOME/gradle-8.13/bin/gradle"; do
  [ -x "$cand" ] && GRADLE="$cand" && break
done
[ -z "$GRADLE" ] && GRADLE="$(command -v gradle || true)"
if [ -z "$GRADLE" ]; then
  echo "未找到 gradle，请把 gradle-8.13 放到工程目录，或安装 gradle"
  exit 1
fi

echo "JAVA_HOME=$JAVA_HOME"
echo "ANDROID_HOME=$ANDROID_HOME"
echo "GRADLE=$GRADLE"
echo

"$GRADLE" assembleDebug --no-daemon --console=plain

APK="$HERE/app/build/outputs/apk/debug/app-debug.apk"
if [ ! -f "$APK" ]; then
  echo "未找到 APK"
  exit 1
fi
echo
echo "编译完成: $APK"

if [ "$1" = "--no-install" ]; then
  exit 0
fi

# sdcard 上的 APK 无法被 system_server 读取，先复制到 /data/local/tmp
su -c "cp '$APK' /data/local/tmp/watch.apk && chmod 644 /data/local/tmp/watch.apk"
su -c "pm install -r /data/local/tmp/watch.apk"
echo "已安装 luolang.status"
