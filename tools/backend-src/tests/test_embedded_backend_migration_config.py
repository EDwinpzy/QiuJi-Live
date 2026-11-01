import importlib.util
import os
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[3]
MIGRATION_ORIGIN = "https://tv.example.com"
ENVIRONMENT_KEY = "OTV_APK_UPDATE_OBJECT_BASE"


class EmbeddedBackendMigrationConfigTests(unittest.TestCase):
    def _load_runner(self, app_dir):
        path = app_dir / "src" / "main" / "python" / "proxy_runner.py"
        spec = importlib.util.spec_from_file_location(f"proxy_runner_{app_dir.name}", path)
        module = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(module)
        return module

    def test_serverless_startup_does_not_require_api_url_or_private_ca(self):
        app_dir = ROOT / "android" / "app"
        runner = self._load_runner(app_dir)
        original_value = os.environ.get(ENVIRONMENT_KEY)
        try:
            os.environ[ENVIRONMENT_KEY] = "https://stale.invalid/object"
            runner.configure_api_environment("", "")
            self.assertNotIn(ENVIRONMENT_KEY, os.environ)
        finally:
            if original_value is None:
                os.environ.pop(ENVIRONMENT_KEY, None)
            else:
                os.environ[ENVIRONMENT_KEY] = original_value

    def test_partial_api_configuration_is_rejected(self):
        runner = self._load_runner(ROOT / "android" / "app")
        with self.assertRaisesRegex(ValueError, "configured together"):
            runner.configure_api_environment(MIGRATION_ORIGIN, "")
        with self.assertRaisesRegex(ValueError, "configured together"):
            runner.configure_api_environment("", "/tmp/nonexistent.pem")

    def test_release_packages_no_private_migration_ca(self):
        app_dir = ROOT / "android" / "app"
        self.assertFalse(
            (app_dir / "src" / "main" / "res" / "raw" / "mytv_migration_ca.pem").exists()
        )
        backend = (app_dir / "src" / "main" / "java" / "com" / "qiubo" / "optimaltv"
                   / "EmbeddedBackend.kt").read_text(encoding="utf-8")
        self.assertNotIn("mytv_migration_ca", backend)
        self.assertNotIn("writeMigrationCa", backend)


if __name__ == "__main__":
    unittest.main()
