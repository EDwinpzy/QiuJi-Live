"""TV client network-surface contract after the serverless migration."""

import re
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[3]
APP = ROOT / "android" / "app"


class EndpointMigrationContractTests(unittest.TestCase):
    def test_tv_only_surface_has_no_mobile_project(self):
        self.assertTrue(APP.is_dir())
        self.assertFalse((ROOT / "internal" / "mobile").exists())

    def test_release_has_no_runtime_update_dependency(self):
        gradle = (APP / "build.gradle").read_text(encoding="utf-8")
        self.assertIn("buildConfigField 'boolean', 'HOTUPDATE_ENABLED', 'false'", gradle)
        self.assertIn("buildConfigField 'boolean', 'APK_UPDATE_ENABLED', 'false'", gradle)
        self.assertIn("buildConfigField 'String', 'HOTUPDATE_ENDPOINT', '\"\"'", gradle)
        self.assertIn("buildConfigField 'String', 'APK_UPDATE_URL', '\"\"'", gradle)
        self.assertNotRegex(gradle, r"https?://\d{1,3}(?:\.\d{1,3}){3}(?::\d+)?")
        manifest = (APP / "src" / "main" / "AndroidManifest.xml").read_text(encoding="utf-8")
        self.assertNotIn("REQUEST_INSTALL_PACKAGES", manifest)
        self.assertNotIn("ApkInstallStatusReceiver", manifest)
        self.assertNotIn("FileProvider", manifest)

    def test_embedded_backend_remains_local(self):
        settings = (
            APP / "src" / "main" / "java" / "com" / "qiubo" / "optimaltv"
            / "data" / "prefs" / "SettingsStore.kt"
        ).read_text(encoding="utf-8")
        backend = (
            APP / "src" / "main" / "java" / "com" / "qiubo" / "optimaltv"
            / "EmbeddedBackend.kt"
        ).read_text(encoding="utf-8")
        self.assertIn('"http://127.0.0.1:8090"', settings)
        self.assertIn('"http://127.0.0.1:$PORT"', backend)


if __name__ == "__main__":
    unittest.main()
