#!/bin/sh
# Docs: docs/architecture/client-side-oauth-libraries.md
#
# Compile + run the pure-JVM unit tests for the MailKite Kotlin client WITHOUT an
# Android device or Gradle. It compiles only the Android-free core (everything in
# src/main except the three Android files) plus src/test, then runs them with the
# JUnit 5 console launcher. Needs kotlinc on PATH (`brew install kotlin`).
#
#   sdks/clients/kotlin/scripts/run-jvm-tests.sh
set -e
cd "$(dirname "$0")/.."

KOTLINC="$(command -v kotlinc || true)"
if [ -z "$KOTLINC" ]; then
  echo "kotlinc not found — install with: brew install kotlin" >&2
  exit 127
fi

# Locate kotlin-stdlib.jar (needed on the runtime classpath).
REAL="$(readlink -f "$KOTLINC" 2>/dev/null || echo "$KOTLINC")"
KROOT="$(dirname "$(dirname "$REAL")")"
STDLIB="$(find "$KROOT" -name 'kotlin-stdlib.jar' 2>/dev/null | head -1)"

DEPS=".deps"
mkdir -p "$DEPS"
COROUTINES="$DEPS/kotlinx-coroutines-core-jvm-1.8.1.jar"
JUNIT="$DEPS/junit-platform-console-standalone-1.10.2.jar"
fetch() { # url dest
  [ -f "$2" ] || { echo "fetching $(basename "$2")"; curl -fsSL "$1" -o "$2"; }
}
fetch "https://repo1.maven.org/maven2/org/jetbrains/kotlinx/kotlinx-coroutines-core-jvm/1.8.1/kotlinx-coroutines-core-jvm-1.8.1.jar" "$COROUTINES"
fetch "https://repo1.maven.org/maven2/org/junit/platform/junit-platform-console-standalone/1.10.2/junit-platform-console-standalone-1.10.2.jar" "$JUNIT"

# Android-free core sources (exclude the three Android-only files).
SRC="src/main/kotlin/dev/mailkite/client"
CORE="$SRC/Json.kt $SRC/MailKiteException.kt $SRC/Http.kt $SRC/TokenStore.kt \
      $SRC/OAuth.kt $SRC/GeneratedMethods.kt $SRC/Webhook.kt $SRC/MailKiteClient.kt"
TESTS="src/test/kotlin/dev/mailkite/client/MailKiteClientTest.kt"

CP="$COROUTINES:$JUNIT"
rm -rf .test-build
mkdir -p .test-build

echo "compiling…"
"$KOTLINC" -cp "$CP" -d .test-build $CORE $TESTS

echo "running tests…"
java -jar "$JUNIT" execute \
  --class-path ".test-build:$CP:$STDLIB" \
  --scan-class-path \
  --details=tree \
  --disable-banner
