#!/bin/bash
# Builds a signed, installable APK without Android Studio, Gradle, or a download
# from Google's servers.
#
# Tested toolchain (Debian and Ubuntu packages):
#   sudo apt-get install aapt apksigner zipalign android-sdk-build-tools \
#                        android-sdk-platform-23 dalvik-exchange default-jdk-headless
#
# Overrides, useful if you already have a real Android SDK installed:
#   ANDROID_JAR=/path/to/android.jar   platform to compile against
#   DEXER=d8                           dexer to use: dalvik-exchange, d8 or dx
#   KEYSTORE=/path/to/my.keystore      signing key (created here if absent)
#   KS_PASS=android KEY_ALIAS=ecmonitor
set -e
cd "$(dirname "$0")"

OUT=build
APK="$OUT/ec-monitor.apk"
KEYSTORE=${KEYSTORE:-debug.keystore}
KS_PASS=${KS_PASS:-android}
KEY_ALIAS=${KEY_ALIAS:-ecmonitor}

# ---- locate the platform jar ------------------------------------------------
if [ -z "$ANDROID_JAR" ]; then
    if [ -f /usr/lib/android-sdk/platforms/android-23/android.jar ]; then
        ANDROID_JAR=/usr/lib/android-sdk/platforms/android-23/android.jar
    elif [ -n "$ANDROID_HOME" ]; then
        ANDROID_JAR=$(ls -1 "$ANDROID_HOME"/platforms/*/android.jar 2>/dev/null | sort -V | tail -1)
    fi
fi
if [ ! -f "$ANDROID_JAR" ]; then
    echo "No android.jar found. Install android-sdk-platform-23 or set ANDROID_JAR." >&2
    exit 1
fi

# ---- locate the dexer -------------------------------------------------------
# dalvik-exchange is the tested path. d8 works too if you have a real SDK.
if [ -z "$DEXER" ]; then
    for candidate in dalvik-exchange d8 dx; do
        if command -v "$candidate" > /dev/null 2>&1; then DEXER=$candidate; break; fi
    done
fi
if [ -z "$DEXER" ]; then
    echo "No dexer found. Install dalvik-exchange, or set DEXER to a d8 on your PATH." >&2
    exit 1
fi

echo "== toolchain"
echo "   platform : $ANDROID_JAR"
echo "   dexer    : $DEXER"

echo "== clean"
rm -rf "$OUT" gen classes
mkdir -p "$OUT" gen classes

echo "== resources"
aapt package -f -m -M AndroidManifest.xml -S res -I "$ANDROID_JAR" -J gen -F "$OUT/app.ap_"

echo "== compile"
javac --release 8 -nowarn -Xlint:-options -classpath "$ANDROID_JAR" -d classes \
    $(find src gen -name '*.java') 2>&1 | grep -v "^Picked up" || true
if [ ! -f classes/ca/uqam/ecmonitor/MainActivity.class ]; then
    echo "compile failed" >&2; exit 1
fi

echo "== dex"
case "$DEXER" in
    d8)
        "$DEXER" --release --min-api 21 --output "$OUT" $(find classes -name '*.class')
        ;;
    *)
        "$DEXER" --dex --min-sdk-version=21 --output="$OUT/classes.dex" classes 2>&1 \
            | grep -v "^Picked up" || true
        ;;
esac
if [ ! -f "$OUT/classes.dex" ]; then
    echo "dex step produced no classes.dex" >&2; exit 1
fi

echo "== package"
cp "$OUT/app.ap_" "$OUT/unsigned.apk"
( cd "$OUT" && aapt add -f unsigned.apk classes.dex > /dev/null )

if [ ! -f "$KEYSTORE" ]; then
    echo "== keystore (new, keep it: updates must reuse the same signature)"
    keytool -genkeypair -keystore "$KEYSTORE" -storepass "$KS_PASS" -keypass "$KS_PASS" \
        -alias "$KEY_ALIAS" -keyalg RSA -keysize 2048 -validity 10000 \
        -dname "CN=EC Monitor, OU=Field, O=Self signed" 2>&1 | grep -v "^Picked up" || true
fi

echo "== align and sign"
zipalign -f 4 "$OUT/unsigned.apk" "$OUT/aligned.apk"
apksigner sign --ks "$KEYSTORE" --ks-pass "pass:$KS_PASS" --key-pass "pass:$KS_PASS" \
    --min-sdk-version 21 --out "$APK" "$OUT/aligned.apk" 2>&1 | grep -v "^Picked up" || true

apksigner verify "$APK" 2>&1 | grep -v "^Picked up" || true
echo
aapt dump badging "$APK" | head -3
ls -la "$APK"
