#!/bin/bash
# Auto-increment build number and build APK
# Usage: ./build_apk.sh [--release]

BUILD_GRADLE="build.gradle.kts"
cd "$(dirname "$0")"

# Extract current versionCode
CURRENT_CODE=$(grep 'versionCode' $BUILD_GRADLE | head -1 | grep -o '[0-9]*')

if [ -z "$CURRENT_CODE" ]; then
    CURRENT_CODE=0
fi

# Increment versionCode
NEW_CODE=$((CURRENT_CODE + 1))

# Update build.gradle.kts
sed -i '' "s/versionCode = $CURRENT_CODE/versionCode = $NEW_CODE/" $BUILD_GRADLE

# Extract versionName for display
VERSION_NAME=$(grep 'versionName' $BUILD_GRADLE | head -1 | grep -o '"[^"]*"' | tr -d '"')

echo "Version: ${VERSION_NAME} (${NEW_CODE})"

# Build from project root
cd ..
if [ "$1" = "--release" ]; then
    ./gradlew :example:assembleRelease
else
    ./gradlew :example:assembleDebug
fi

echo ""
echo "APK built: ${VERSION_NAME} (${NEW_CODE})"
echo "Location: example/build/outputs/apk/"
