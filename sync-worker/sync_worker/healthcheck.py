from __future__ import annotations

import sys

from .db import get_connection


def main() -> int:
    try:
        with get_connection() as connection, connection.cursor() as cursor:
            cursor.execute("SELECT 1")
            cursor.fetchone()
        return 0
    except Exception as exc:
        print(f"healthcheck failed: {exc}")
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
