"""Sync only the H2 test schema from the V3 MySQL initialization SQL.

Run at the repository root. Published Flyway migrations are immutable; never regenerate V1. H2 runs in MySQL mode; indexes without constraints
are omitted while table columns, keys, checks, and seed permissions are retained.
"""

from pathlib import Path
import re

source = Path("database/database.sql").read_text(encoding="utf-8")
source = source[source.index("CREATE TABLE sys_role ("):]
source = source.split("-- FLYWAY SNAPSHOT BASELINE:", 1)[0]
source = re.sub(r"^\s*--[^\n]*\n", "", source, flags=re.M)
source = re.sub(r"\bBIGINT UNSIGNED\b", "BIGINT", source)
source = re.sub(r"\bINT UNSIGNED\b", "INT", source)
source = re.sub(r"\bTINYINT UNSIGNED\b", "TINYINT", source)
source = source.replace("DATETIME(3)", "TIMESTAMP")
source = re.sub(r"\bCURRENT_TIMESTAMP\(3\)", "CURRENT_TIMESTAMP", source)
source = re.sub(r"\bJSON\b", "VARCHAR(4000)", source)
source = re.sub(r"\s+ON UPDATE CURRENT_TIMESTAMP", "", source)
source = re.sub(r"\s+STORED\b", "", source)
source = re.sub(r"\) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;", ");", source)
source = re.sub(r"^\s+KEY \w+ \([^\n]*\),?\n", "", source, flags=re.M)
source = re.sub(r"\bUNIQUE KEY (\w+) ", r"CONSTRAINT \1 UNIQUE ", source)
source = re.sub(r",\s*\);", "\n);", source)
source = source.replace("INSERT IGNORE INTO", "INSERT INTO")
Path("backend/src/test/resources/schema-auth-test.sql").write_text(
    "-- Generated from database/database.sql for H2 MySQL mode.\n" + source,
    encoding="utf-8",
)
