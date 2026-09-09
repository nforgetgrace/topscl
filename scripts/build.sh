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
python3 - <<'PY'
import hashlib, re, shutil
from pathlib import Path
version = re.search(r"versionName '([^']+)'", Path('app/build.gradle').read_text()).group(1)
p = Path('dist') / ('TopTap-' + version + '-debug.apk')
shutil.copyfile('app/build/outputs/apk/debug/app-debug.apk', p)
Path('dist/SHA256SUMS').write_text(''.join(hashlib.sha256(apk.read_bytes()).hexdigest() + '  ' + apk.name + '\n' for apk in sorted(Path('dist').glob('*.apk'))))
print('APK:', p.resolve())
PY
