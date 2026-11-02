#!/usr/bin/env python3
import hashlib
import importlib.util
import json
from pathlib import Path
import tempfile
import unittest

spec = importlib.util.spec_from_file_location('update_metadata', Path(__file__).with_name('make-update-metadata.py'))
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)


class UpdateMetadataTests(unittest.TestCase):
    def fixture(self, directory, **overrides):
        (directory / 'app-release.apk').write_bytes(b'locally built apk')
        element = dict(versionName='1.3', versionCode=108, outputFile='app-release.apk')
        element.update(overrides)
        (directory / 'output-metadata.json').write_text(json.dumps(dict(applicationId='de.lowfin.tv', elements=[element])))

    def test_generated_metadata_and_named_apk_match_the_build(self):
        with tempfile.TemporaryDirectory() as folder:
            directory = Path(folder)
            self.fixture(directory)
            result = module.generate(directory)
            apk = (directory / 'Lowfin-1.3-release.apk').read_bytes()
            self.assertEqual(hashlib.sha256(apk).hexdigest(), result['sha256'])
            self.assertEqual(108, result['versionCode'])
            self.assertEqual(len(apk), result['size'])
            self.assertEqual(result, json.loads((directory / 'update.json').read_text()))

    def test_rejects_path_traversal_and_invalid_version(self):
        for invalid in (dict(outputFile='../other.apk'), dict(versionName='../version'), dict(versionCode=0)):
            with tempfile.TemporaryDirectory() as folder:
                directory = Path(folder)
                self.fixture(directory, **invalid)
                with self.assertRaises(ValueError):
                    module.generate(directory)
                self.assertFalse((directory / 'update.json').exists())


if __name__ == '__main__':
    unittest.main()
