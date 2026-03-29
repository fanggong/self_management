from __future__ import annotations

import os
import sys
import unittest
from pathlib import Path
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parent))

os.environ.setdefault("CONNECTOR_SECRET_KEY", "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=")
os.environ.setdefault("INTERNAL_API_TOKEN", "unit-test-internal-token-123456")

from sync_worker.config import (
    CONNECTOR_SECRET_KEY_PLACEHOLDER,
    INTERNAL_API_TOKEN_PLACEHOLDER,
    Settings,
)


class SettingsSecurityTest(unittest.TestCase):
    def test_rejects_tracked_placeholders(self) -> None:
        with patch.dict(
            os.environ,
            {
                "CONNECTOR_SECRET_KEY": CONNECTOR_SECRET_KEY_PLACEHOLDER,
                "INTERNAL_API_TOKEN": INTERNAL_API_TOKEN_PLACEHOLDER,
            },
            clear=False,
        ):
            with self.assertRaisesRegex(ValueError, "tracked placeholder"):
                Settings.from_env()

    def test_rejects_published_defaults(self) -> None:
        with patch.dict(
            os.environ,
            {
                "CONNECTOR_SECRET_KEY": "ZpbWkYr/Lhf2an422+7liSjKuN4KkbOEx9" + "f2oll8Nv4=",
                "INTERNAL_API_TOKEN": "6f988cfd1b0c448b182082f4a4f79273" + "70a07699c0258d2e",
            },
            clear=False,
        ):
            with self.assertRaisesRegex(ValueError, "published default value"):
                Settings.from_env()


if __name__ == "__main__":
    unittest.main()
