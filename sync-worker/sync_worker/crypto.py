from __future__ import annotations

import base64
import json
import os
from datetime import date, datetime
from typing import Any

from cryptography.hazmat.primitives.ciphers.aead import AESGCM

from .config import settings


def decrypt_config(payload: str | None) -> dict[str, str]:
    if not payload:
        return {}

    iv_b64, cipher_b64 = payload.split(".", 1)
    aesgcm = AESGCM(settings.connector_secret_key)
    plaintext = aesgcm.decrypt(base64.b64decode(iv_b64), base64.b64decode(cipher_b64), None)
    return json.loads(plaintext.decode("utf-8"))


def payload_hash(value: Any) -> str:
    normalized = json.dumps(
        value,
        ensure_ascii=True,
        sort_keys=True,
        separators=(",", ":"),
        default=_json_default,
    ).encode("utf-8")
    return __import__("hashlib").sha256(normalized).hexdigest()


def _json_default(value: Any) -> str:
    if isinstance(value, (datetime, date)):
        return value.isoformat()
    raise TypeError(f"Object of type {value.__class__.__name__} is not JSON serializable")
