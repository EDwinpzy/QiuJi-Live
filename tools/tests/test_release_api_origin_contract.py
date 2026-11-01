"""Serverless TV release contract tests."""

import re
import unittest
from pathlib import Path
from xml.etree import ElementTree


ROOT = Path(__file__).resolve().parents[2]
APP = ROOT / "android" / "app"


class ServerlessReleaseContractTests(unittest.TestCase):
    def test_release_compiles_no_remote_update_endpoint(self):
        gradle = (APP / "build.gradle").read_text(encoding="utf-8")
        self.assertIn("def apiBaseUrl = ''", gradle)
        self.assertIn("buildConfigField 'boolean', 'HOTUPDATE_ENABLED', 'false'", gradle)
        self.assertIn("buildConfigField 'boolean', 'APK_UPDATE_ENABLED', 'false'", gradle)
        self.assertIn("buildConfigField 'String', 'HOTUPDATE_ENDPOINT', '\"\"'", gradle)
        self.assertIn("buildConfigField 'String', 'APK_UPDATE_URL', '\"\"'", gradle)
        self.assertNotRegex(gradle, r"https?://\d{1,3}(?:\.\d{1,3}){3}(?::\d+)?")
        self.assertNotIn("sourceSets.release.res.srcDir 'src/ipRelease/res'", gradle)

    def test_release_keeps_stable_install_identity_and_forward_version(self):
        gradle = (APP / "build.gradle").read_text(encoding="utf-8")
        self.assertIn('applicationId "com.qiubo.optimaltv.public"', gradle)
        code = re.search(r"Integer\.parseInt\(apkVersionCodeOverride\.toString\(\)\)\s*:\s*(\d+)", gradle)
        name = re.search(r'apkVersionNameOverride\.toString\(\)\s*:\s*"(\d+\.\d+(?:\.\d+)?)"', gradle)
        self.assertIsNotNone(code)
        self.assertIsNotNone(name)
        self.assertGreaterEqual(int(code.group(1)), 336)
        self.assertGreaterEqual(tuple(map(int, name.group(1).split("."))), (3, 0, 26))

    def test_release_network_config_has_no_private_vps_trust(self):
        config = ElementTree.parse(
            APP / "src" / "main" / "res" / "xml" / "network_security_config.xml"
        ).getroot()
        self.assertEqual(config.findall("domain-config"), [])
        anchors = [
            item.attrib.get("src")
            for item in config.findall("base-config/trust-anchors/certificates")
        ]
        self.assertEqual(anchors, ["system"])

    def test_release_manifest_has_no_update_install_surface(self):
        manifest = ElementTree.parse(APP / "src" / "main" / "AndroidManifest.xml").getroot()
        android = "{http://schemas.android.com/apk/res/android}"
        permissions = {
            item.attrib.get(android + "name")
            for item in manifest.findall("uses-permission")
        }
        self.assertNotIn("android.permission.REQUEST_INSTALL_PACKAGES", permissions)
        providers = [
            item.attrib.get(android + "name")
            for item in manifest.findall("application/provider")
        ]
        receivers = [
            item.attrib.get(android + "name")
            for item in manifest.findall("application/receiver")
        ]
        self.assertNotIn("androidx.core.content.FileProvider", providers)
        self.assertNotIn(".apkupdate.ApkInstallStatusReceiver", receivers)

    def test_release_no_longer_packages_migration_ca(self):
        self.assertFalse((APP / "src" / "main" / "res" / "raw" / "mytv_migration_ca.pem").exists())
        ip_release = APP / "src" / "ipRelease"
        self.assertEqual(
            [path for path in ip_release.rglob("*") if path.is_file()] if ip_release.exists() else [],
            [],
        )
        self.assertFalse((APP / "src" / "debug" / "res" / "xml" / "network_security_config.xml").exists())


if __name__ == "__main__":
    unittest.main()
