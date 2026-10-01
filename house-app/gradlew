#!/bin/sh
set -e
APP_HOME=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
JAR="$APP_HOME/gradle/wrapper/gradle-wrapper.jar"
URL="https://raw.githubusercontent.com/gradle/gradle/v9.6.0/gradle/wrapper/gradle-wrapper.jar"
EXPECTED="497c8c2a7e5031f6aa847f88104aa80a93532ec32ee17bdb8d1d2f67a194a9c7"
if [ ! -f "$JAR" ]; then
  if command -v curl >/dev/null 2>&1; then curl -L "$URL" -o "$JAR"; else wget -O "$JAR" "$URL"; fi
fi
ACTUAL=$(sha256sum "$JAR" | awk '{print $1}')
[ "$ACTUAL" = "$EXPECTED" ] || { echo "Gradle wrapper checksum failed" >&2; exit 1; }
exec "${JAVA_HOME:+$JAVA_HOME/bin/}java" -classpath "$JAR" org.gradle.wrapper.GradleWrapperMain "$@"
