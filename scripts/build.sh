#!/bin/sh
set -eu
cd "$(dirname "$0")/.."
if [ -x /usr/libexec/java_home ]; then
    JAVA_HOME="$(/usr/libexec/java_home -v '17+')"
    export JAVA_HOME
    PATH="$JAVA_HOME/bin:$PATH"
    export PATH
fi
./gradlew :app:assembleDebug :fixture:assembleDebug :app:lintDebug :fixture:lintDebug --console=plain
sh scripts/test-core.sh
mkdir -p dist
cp app/build/outputs/apk/debug/app-debug.apk dist/TopTap-1.0.0-debug.apk
python3 - <<'PY'
import hashlib
from pathlib import Path
p = Path('dist/TopTap-1.0.0-debug.apk')
Path('dist/SHA256SUMS').write_text(hashlib.sha256(p.read_bytes()).hexdigest() + '  ' + p.name + '\n')
print('APK:', p.resolve())
PY
