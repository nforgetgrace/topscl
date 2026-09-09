#!/bin/sh
set -eu
cd "$(dirname "$0")/.."
mkdir -p .tools/core-tests
javac --release 17 -d .tools/core-tests app/src/main/java/kr/toptap/android/core/*.java tests/*Test.java
java -cp .tools/core-tests TapRecognizerTest
java -cp .tools/core-tests ScrollCoastTest
