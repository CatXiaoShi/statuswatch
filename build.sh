#!/data/data/com.termux/files/usr/bin/bash
# Watch 一键编译 + 安装（Termux）
#   ./build.sh              编译 release（R8 压缩，体积小）并安装
#   ./build.sh --debug      编译 debug（速度快，体积大，含 Compose 工具）
#   ./build.sh --no-install 只编译不安装
set -e

HERE="$(cd "$(dirname "$0")" && pwd)"
cd "$HERE"

MODE=release
DO_INSTALL=1
for arg in "$@"; do
  case "$arg" in
    --debug) MODE=debug ;;
    --release) MODE=release ;;
    --no-install) DO_INSTALL=0 ;;
    *) echo "未知参数: $arg"; exit 1 ;;
  esac
done

# 没有自定义签名私钥时，release 无法签名，回退到 debug 构建
if [ "$MODE" = "release" ] && [ ! -f "$HERE/sign/LewoWoff.jks" ]; then
  echo "未找到 sign/LewoWoff.jks，release 将无法签名，改为 debug 构建"
  MODE=debug
fi

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
echo "MODE=$MODE"
echo

if [ "$MODE" = "release" ]; then
  "$GRADLE" assembleRelease --no-daemon --console=plain
  APK="$HERE/app/build/outputs/apk/release/app-release.apk"
else
  "$GRADLE" assembleDebug --no-daemon --console=plain
  APK="$HERE/app/build/outputs/apk/debug/app-debug.apk"
fi

if [ ! -f "$APK" ]; then
  echo "未找到 APK: $APK"
  exit 1
fi
echo
echo "编译完成: $APK"
ls -lh "$APK" | awk '{print "文件大小: " $5}'

if [ "$DO_INSTALL" = "0" ]; then
  exit 0
fi

# sdcard 上的 APK 无法被 system_server 读取，先复制到 /data/local/tmp
su -c "cp '$APK' /data/local/tmp/watch.apk && chmod 644 /data/local/tmp/watch.apk"
su -c "pm install -r /data/local/tmp/watch.apk"
echo "已安装 luolang.status"
