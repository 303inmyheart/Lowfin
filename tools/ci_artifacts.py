#!/usr/bin/env python3
"""Package only verified CI outputs; never include signing keys in artifacts."""
from pathlib import Path
import hashlib
import json
import os
import re
import shutil
import subprocess
import xml.etree.ElementTree as ET
import zipfile

root = Path(__file__).resolve().parents[1]
apk_dir = root / 'app/build/outputs/apk/release'
metadata = json.loads((apk_dir / 'output-metadata.json').read_text())['elements'][0]
version = metadata['versionName']
if not re.fullmatch(r'[0-9]+(?:\.[0-9]+)+(?:-[a-zA-Z0-9.-]+)?', version):
    raise SystemExit('Invalid version in APK metadata')
apk = apk_dir / metadata['outputFile']
results = list((root / 'app/build/test-results/testDebugUnitTest').glob('TEST-*.xml'))
counts = {key: sum(int(ET.parse(p).getroot().get(key, 0)) for p in results)
          for key in ('tests', 'failures', 'errors', 'skipped')}
if not counts['tests'] or counts['failures'] or counts['errors']:
    raise SystemExit(f'Tests not successful: {counts}')
for variant in ('debug', 'release'):
    lint = ET.parse(root / f'app/build/reports/lint-results-{variant}.xml').getroot()
    if any(issue.get('severity') in ('Error', 'Fatal') for issue in lint):
        raise SystemExit(f'Lint errors present in {variant}')
sdk = Path(os.environ.get('ANDROID_HOME') or os.environ.get('ANDROID_SDK_ROOT') or root / '.tools/android')
java_home = Path(os.environ.get('JAVA_HOME') or root / '.tools/jdk')
verified = subprocess.check_output([
    str(java_home / 'bin/java'), '-jar', str(sdk / 'build-tools/35.0.0/lib/apksigner.jar'),
    'verify', '--print-certs', str(apk)], text=True)
match = re.search(r'Signer #1 certificate SHA-256 digest: ([a-f0-9]+)', verified)
if not match:
    raise SystemExit('APK signing certificate not found')
signer = match.group(1)
expected = json.loads((root / 'dist/verification.json').read_text())['certificate_sha256']
compatible = signer == expected
if os.environ.get('LOWFIN_REQUIRE_EXISTING_SIGNER') == 'true' and not compatible:
    raise SystemExit('APK certificate differs from the existing installation')
with zipfile.ZipFile(apk) as archive:
    abis = sorted({name.split('/')[1] for name in archive.namelist() if name.endswith('/libffmpegJNI.so')})
if abis != ['arm64-v8a', 'armeabi-v7a', 'x86', 'x86_64']:
    raise SystemExit(f'Missing native decoder ABI: {abis}')
output = root / 'ci-artifacts'
output.mkdir(exist_ok=True)
filename = f'Lowfin-{version}.apk'
shutil.copy2(apk, output / filename)
digest = hashlib.sha256(apk.read_bytes()).hexdigest()
(output / 'SHA256SUMS').write_text(f'{digest}  {filename}\n')
report = {'version': version, 'version_code': metadata['versionCode'],
          'source_commit': os.environ.get('GITHUB_SHA') or subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=root, text=True).strip(),
          'build_type': 'release', 'tests': counts, 'lint_errors': 0, 'apk_bytes': apk.stat().st_size,
          'apk_sha256': digest, 'certificate_sha256': signer,
          'compatible_with_current_release': compatible, 'ffmpeg_abis': abis,
          'physical_tv_tested': False}
(output / 'BUILD.json').write_text(json.dumps(report, indent=2) + '\n')
if os.environ.get('GITHUB_OUTPUT'):
    with open(os.environ['GITHUB_OUTPUT'], 'a') as stream:
        stream.write(f'version={version}\n')
print(json.dumps(report, indent=2))
