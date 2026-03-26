from __future__ import annotations

import sys
import types
import unittest
from datetime import date
from pathlib import Path
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parent))

from sync_worker.connectors.garmin import GarminConnectorAdapter


class GarminConnectorAdapterTest(unittest.TestCase):
    def test_fetch_profile_merges_height_from_user_settings(self) -> None:
        garth = types.SimpleNamespace(profile={"profileId": "profile-1", "displayName": "demo"})
        client = types.SimpleNamespace(
            garth=garth,
            get_user_profile=lambda: {
                "userData": {
                    "height": 1.72,
                    "measurementSystem": "metric",
                }
            },
            get_userprofile_settings=lambda: {"privacy": {"showHeight": False}},
        )
        adapter = GarminConnectorAdapter("demo@example.com", "secret123")
        adapter._client = client
        adapter._local_today = lambda: date(2026, 3, 24)  # type: ignore[method-assign]

        with patch("sync_worker.connectors.garmin.settings", types.SimpleNamespace(garmin_mock_mode=False)):
            record = adapter.fetch_profile()

        self.assertEqual(record["externalId"], "profile-1")
        self.assertEqual(record["sourceRecordDate"], "2026-03-24")
        self.assertEqual(record["payload"]["heightCm"], 172.0)
        self.assertEqual(record["payload"]["userSettings"]["userData"]["height"], 1.72)
        self.assertEqual(record["payload"]["profileSettings"]["privacy"]["showHeight"], False)

    def test_fetch_profile_falls_back_to_profile_settings_height(self) -> None:
        garth = types.SimpleNamespace(profile={"profileId": "profile-2"})
        client = types.SimpleNamespace(
            garth=garth,
            get_user_profile=lambda: {"userData": {}},
            get_userprofile_settings=lambda: {"heightCm": 181},
        )
        adapter = GarminConnectorAdapter("demo@example.com", "secret123")
        adapter._client = client
        adapter._local_today = lambda: date(2026, 3, 24)  # type: ignore[method-assign]

        with patch("sync_worker.connectors.garmin.settings", types.SimpleNamespace(garmin_mock_mode=False)):
            record = adapter.fetch_profile()

        self.assertEqual(record["payload"]["heightCm"], 181.0)

    def test_fetch_profile_tolerates_settings_errors(self) -> None:
        garth = types.SimpleNamespace(profile={"profileId": "profile-3"})
        client = types.SimpleNamespace(
            garth=garth,
            get_user_profile=lambda: (_ for _ in ()).throw(RuntimeError("settings unavailable")),
            get_userprofile_settings=lambda: (_ for _ in ()).throw(RuntimeError("profile settings unavailable")),
        )
        adapter = GarminConnectorAdapter("demo@example.com", "secret123")
        adapter._client = client
        adapter._local_today = lambda: date(2026, 3, 24)  # type: ignore[method-assign]

        with patch("sync_worker.connectors.garmin.settings", types.SimpleNamespace(garmin_mock_mode=False)):
            record = adapter.fetch_profile()

        self.assertIsNone(record["payload"]["heightCm"])
        self.assertEqual(record["payload"]["userSettings"], {})
        self.assertEqual(record["payload"]["profileSettings"], {})

    def test_normalize_height_cm_handles_supported_units(self) -> None:
        self.assertEqual(GarminConnectorAdapter._normalize_height_cm(172, "cm"), 172.0)
        self.assertEqual(GarminConnectorAdapter._normalize_height_cm(1.72, "m"), 172.0)
        self.assertEqual(GarminConnectorAdapter._normalize_height_cm(68, "imperial"), 172.7)
        self.assertIsNone(GarminConnectorAdapter._normalize_height_cm(68, None))


if __name__ == "__main__":
    unittest.main()
