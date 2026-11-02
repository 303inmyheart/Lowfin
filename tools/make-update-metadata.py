#!/usr/bin/env python3
"""Prepare the public updater sidecar locally. Does not upload or publish anything."""
from pathlib import Path
import hashlib
import json
import re
import shutil


def generate(directory):
    directory = Path(directory)
    metadata = json.loads((directory / 'output-metadata.json').read_text())
    if metadata['applicationId'] != 'de.lowfin.tv' or len(metadata['elements']) != 1:
        raise ValueError('Expected one Lowfin APK')
    element = metadata['elements'][0]
    version = element['versionName']
    if not re.fullmatch(r'[0-9]+(?:\.[0-9]+){1,3}', version):
        raise ValueError('Invalid public version')
    source = directory / element['outputFile']
    if source.parent.resolve() != directory.resolve() or source.suffix != '.apk':
        raise ValueError('Invalid APK path')
    if not 0 < source.stat().st_size <= 64 * 1024 * 1024 or int(element['versionCode']) <= 0:
        raise ValueError('Invalid APK size/versionCode')
    result = dict(packageName=metadata['applicationId'], versionName=version,
                  versionCode=int(element['versionCode']), size=source.stat().st_size,
                  sha256=hashlib.sha256(source.read_bytes()).hexdigest())
    target = directory / f'Lowfin-{version}-release.apk'
    if target != source:
        shutil.copy2(source, target)
    (directory / 'update.json').write_text(json.dumps(result, indent=2) + '\n')
    return result


if __name__ == '__main__':
    print(json.dumps(generate(Path(__file__).resolve().parents[1] / 'app/build/outputs/apk/release'), indent=2))
