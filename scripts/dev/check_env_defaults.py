#!/usr/bin/env python3
from __future__ import annotations

import re
import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
ENV_EXAMPLE_PATH = ROOT / ".env.example"
ENV_PATH = ROOT / ".env"
DOCKER_COMPOSE_PATH = ROOT / "docker-compose.yml"
APPLICATION_YML_PATH = ROOT / "admin-api" / "src" / "main" / "resources" / "application.yml"
SYNC_WORKER_CONFIG_PATH = ROOT / "sync-worker" / "sync_worker" / "config.py"
FRONTEND_NUXT_CONFIG_PATH = ROOT / "frontend" / "nuxt.config.ts"
COMPOSE_DEFAULT_PATTERN = re.compile(r"\$\{([A-Z0-9_]+):-([^}]+)\}")
SPRING_DEFAULT_PATTERN = re.compile(r"\$\{([A-Z0-9_]+):([^}]+)\}")
PYTHON_GETENV_PATTERN = re.compile(r'os\.getenv\("([A-Z0-9_]+)", "([^"]+)"\)')
PYTHON_GETENV_REFERENCE_PATTERN = re.compile(r'os\.getenv\("([A-Z0-9_]+)", ([A-Z0-9_]+)\)')
PYTHON_STRING_ASSIGNMENT_PATTERN = re.compile(r'^([A-Z0-9_]+)\s*=\s*"([^"]+)"', re.MULTILINE)
NUXT_DEFAULT_PATTERN = re.compile(r"env\.([A-Z0-9_]+)\s*\?\?\s*'([^']+)'")
TRACKED_SAFE_DEFAULTS = {
    "JWT_SECRET": "REPLACE_WITH_A_STRONG_JWT_SECRET_BEFORE_DEPLOYMENT",
    "CONNECTOR_SECRET_KEY": "UkVQTEFDRV9XSVRIXzMyX0JZVEVfQUVTX0tFWV9OT1c=",
    "INTERNAL_API_TOKEN": "REPLACE_WITH_A_RANDOM_INTERNAL_API_TOKEN_NOW",
    "AUTH_COOKIE_SECURE": "true",
    "NUXT_PUBLIC_API_BASE": "https://api.example.com/api/v1",
}
PUBLISHED_DEFAULTS = {
    "JWT_SECRET": "8155b62907ead5659687e2b16f8a453091296da82904b14c75ecaf5e23d69f7c",
    "CONNECTOR_SECRET_KEY": "97f52fab4e515dc1d849dc2ecbb77f65e8ca1dcdb8588afa57168e9bcd4137ed",
    "INTERNAL_API_TOKEN": "2a44e4449440bc59034a53b62e4bb5838f881f06e84e22a52201f80bc19e24df",
}
SENSITIVE_ENV_KEYS = {
    "JWT_SECRET",
    "CONNECTOR_SECRET_KEY",
    "INTERNAL_API_TOKEN",
}


def parse_env_file(path: Path) -> dict[str, str]:
    values: dict[str, str] = {}
    for line_number, raw_line in enumerate(path.read_text().splitlines(), start=1):
        line = raw_line.strip()
        if not line or line.startswith("#"):
            continue
        if "=" not in raw_line:
            raise ValueError(f"{path}: line {line_number} does not contain '='")
        key, value = raw_line.split("=", 1)
        key = key.strip()
        if not key:
            raise ValueError(f"{path}: line {line_number} has an empty variable name")
        if key in values:
            raise ValueError(f"{path}: duplicate variable '{key}'")
        values[key] = value.strip()
    return values


def parse_regex_defaults(path: Path, pattern: re.Pattern[str]) -> dict[str, str]:
    defaults: dict[str, str] = {}
    for match in pattern.finditer(path.read_text()):
        key, value = match.group(1), match.group(2)
        existing = defaults.get(key)
        if existing is not None and existing != value:
            raise ValueError(f"{path}: variable '{key}' has conflicting defaults '{existing}' and '{value}'")
        defaults[key] = value
    return defaults


def parse_python_getenv_defaults(path: Path) -> dict[str, str]:
    content = path.read_text()
    defaults = parse_regex_defaults(path, PYTHON_GETENV_PATTERN)
    constants = {match.group(1): match.group(2) for match in PYTHON_STRING_ASSIGNMENT_PATTERN.finditer(content)}

    for match in PYTHON_GETENV_REFERENCE_PATTERN.finditer(content):
        key, reference = match.group(1), match.group(2)
        value = constants.get(reference)
        if value is None:
            raise ValueError(f"{path}: getenv default for '{key}' references unknown constant '{reference}'")
        existing = defaults.get(key)
        if existing is not None and existing != value:
            raise ValueError(f"{path}: variable '{key}' has conflicting defaults '{existing}' and '{value}'")
        defaults[key] = value

    return defaults


def validate_expected_defaults(label: str, actual: dict[str, str], expected: dict[str, str]) -> list[str]:
    errors: list[str] = []
    for key, expected_value in expected.items():
        if key not in actual:
            errors.append(f"{label}: missing tracked default for {key}")
            continue
        if actual[key] != expected_value:
            errors.append(
                f"{label}: unsafe tracked default for {key}: "
                f"expected '{expected_value}' actual '{actual[key]}'"
            )
    return errors


def sha256_hex(value: str) -> str:
    return __import__("hashlib").sha256(value.encode("utf-8")).hexdigest()


def compare_sets(label: str, expected: dict[str, str], actual: dict[str, str]) -> list[str]:
    errors: list[str] = []
    missing = sorted(set(expected) - set(actual))
    extra = sorted(set(actual) - set(expected))
    if missing:
        errors.append(f"{label}: missing variables: {', '.join(missing)}")
    if extra:
        errors.append(f"{label}: unexpected variables: {', '.join(extra)}")
    return errors


def main() -> int:
    env_example = parse_env_file(ENV_EXAMPLE_PATH)
    compose_defaults = parse_regex_defaults(DOCKER_COMPOSE_PATH, COMPOSE_DEFAULT_PATTERN)
    application_defaults = parse_regex_defaults(APPLICATION_YML_PATH, SPRING_DEFAULT_PATTERN)
    sync_worker_defaults = parse_python_getenv_defaults(SYNC_WORKER_CONFIG_PATH)
    frontend_defaults = parse_regex_defaults(FRONTEND_NUXT_CONFIG_PATH, NUXT_DEFAULT_PATTERN)

    errors = compare_sets("docker-compose defaults", env_example, compose_defaults)
    errors.extend(validate_expected_defaults(".env.example", env_example, TRACKED_SAFE_DEFAULTS))
    errors.extend(validate_expected_defaults("docker-compose defaults", compose_defaults, TRACKED_SAFE_DEFAULTS))
    errors.extend(
        validate_expected_defaults(
            "admin-api application defaults",
            application_defaults,
            {
                "JWT_SECRET": TRACKED_SAFE_DEFAULTS["JWT_SECRET"],
                "CONNECTOR_SECRET_KEY": TRACKED_SAFE_DEFAULTS["CONNECTOR_SECRET_KEY"],
                "INTERNAL_API_TOKEN": TRACKED_SAFE_DEFAULTS["INTERNAL_API_TOKEN"],
                "AUTH_COOKIE_SECURE": TRACKED_SAFE_DEFAULTS["AUTH_COOKIE_SECURE"],
            },
        )
    )
    errors.extend(
        validate_expected_defaults(
            "sync-worker defaults",
            sync_worker_defaults,
            {
                "CONNECTOR_SECRET_KEY": TRACKED_SAFE_DEFAULTS["CONNECTOR_SECRET_KEY"],
                "INTERNAL_API_TOKEN": TRACKED_SAFE_DEFAULTS["INTERNAL_API_TOKEN"],
            },
        )
    )
    errors.extend(
        validate_expected_defaults(
            "frontend runtime defaults",
            frontend_defaults,
            {
                "NUXT_PUBLIC_API_BASE": TRACKED_SAFE_DEFAULTS["NUXT_PUBLIC_API_BASE"],
            },
        )
    )
    for key in sorted(set(env_example) & set(compose_defaults)):
        if env_example[key] != compose_defaults[key]:
            errors.append(
                f"docker-compose defaults: value mismatch for {key}: "
                f".env.example='{env_example[key]}' docker-compose='{compose_defaults[key]}'"
            )

    if ENV_PATH.exists():
        env_values = parse_env_file(ENV_PATH)
        errors.extend(compare_sets(".env", env_example, env_values))
        for key in sorted(SENSITIVE_ENV_KEYS):
            actual_value = env_values.get(key)
            if actual_value is None:
                continue
            if actual_value == env_example.get(key) or sha256_hex(actual_value) == PUBLISHED_DEFAULTS[key]:
                errors.append(
                    f".env: sensitive variable {key} must not reuse the tracked placeholder or published default value"
                )

    if errors:
        for error in errors:
            print(error, file=sys.stderr)
        return 1

    print("Environment defaults are in sync.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
