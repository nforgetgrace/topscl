#!/bin/sh
set -eu
cd "$(dirname "$0")/.."
mkdir -p .tools/core-tests
javac --release 17 -d .tools/core-tests app/src/main/java/kr/toptap/android/core/TapRecognizer.java tests/TapRecognizerTest.java
java -cp .tools/core-tests TapRecognizerTest
