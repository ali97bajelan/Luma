"""Run compiler-generated Timeline SQL on a mixed five-year disk fixture."""
import datetime
import json
import re
import sqlite3
import statistics
import sys
import tempfile
import time
import uuid
from pathlib import Path

generated = Path(sys.argv[1]).read_text()
schema = json.load(open(sys.argv[2]))["database"]


def query(method):
    # Room emits literal SQL as a Kotlin string or trimMargin block. Fail loudly
    # if compiler output changes; never fall back to a test-owned SQL copy.
    body = generated.split(f"suspend fun {method}(", 1)[1]
    match = re.search(r'val _sql: String = (""".*?"""\.trimMargin\(\)|"(?:[^"\\]|\\.)*")', body, re.S)
    assert match, f"Cannot read generated SQL for {method}"
    value = match.group(1)
    if value.startswith('"""'):
        return "\n".join(line.split("|", 1)[1] for line in value[3:value.rfind('"""')].splitlines() if "|" in line)
    return json.loads(value)


page = query("pageDetails")
count = query("countTimelineRows")
events = query("countDayEvents")
DAY = 86400000
AT = int(datetime.datetime(2026, 10, 12, tzinfo=datetime.timezone.utc).timestamp() * 1000)


def identifier(n):
    return str(uuid.UUID(int=n))


with tempfile.TemporaryDirectory(prefix="luma-timeline-history-") as directory:
    db = sqlite3.connect(Path(directory) / "history.db")
    db.execute("PRAGMA foreign_keys=ON")
    for entity in schema["entities"]:
        db.execute(entity["createSql"].replace("${TABLE_NAME}", entity["tableName"]))
        for index in entity.get("indices", []):
            db.execute(index["createSql"].replace("${TABLE_NAME}", entity["tableName"]))
    # Five years with larger journal bodies, plus a 2,000-row same-time burst.
    db.executemany("INSERT INTO life_event(id,type,occurred_at,created_at,updated_at,source,title) VALUES (?,'JOURNAL',?,?,?,'MANUAL',?)",
                   ((identifier(n + 100000), AT - (1 + n % 1825) * DAY, AT, AT, f"Fixture {n}") for n in range(50000)))
    db.executemany("INSERT INTO journal_entry(id,life_event_id,body) VALUES (?,?,?)",
                   ((identifier(n + 200000), identifier(n + 100000), "Private body\n" * 100) for n in range(50000)))
    # Reverse insertion proves ordering is by UUID, independent of insertion.
    db.executemany("INSERT INTO life_event(id,type,occurred_at,created_at,updated_at,source) VALUES (?,'JOURNAL',?, ?,?,'MANUAL')",
                   ((identifier(n), AT, AT, AT) for n in range(1999, -1, -1)))
    db.executemany("INSERT INTO journal_entry(id,life_event_id,body) VALUES (?,?,?)",
                   ((identifier(n + 300000), identifier(n), "Burst") for n in range(2000)))
    for n, duration in [(2000, 0), (2001, 60001), (2002, None)]:
        db.execute("INSERT INTO life_event(id,type,occurred_at,created_at,updated_at,source) VALUES (?,'APP_USAGE',?,?,?,'USAGE_STATS')",
                   (identifier(n), AT, AT, AT))
        if duration is not None:
            db.execute("INSERT INTO app_usage_record(id,life_event_id,bucket_start,bucket_end,package_name,duration_ms,source) VALUES (?,?,?,?,?,?,'USAGE_STATS')",
                       (identifier(n + 300000), identifier(n), AT, AT + 120000, f"example.app{n}", duration))
    db.commit()

    bounds = (AT, AT + DAY)
    args = bounds + (True,)
    assert db.execute(count, args).fetchone() == (2001,)
    assert db.execute(events, bounds).fetchone() == (2000,)
    collected = []
    for offset in range(0, 2050, 50):
        rows = db.execute(page, args + (50, offset)).fetchall()
        assert len(rows) <= 50
        collected.extend(row[0] for row in rows)
    assert collected == [identifier(2001)] + [identifier(n) for n in range(1999, -1, -1)]
    # Only measured usage over one minute is visible; zero, exact-minute, and unknown rows are excluded.
    assert db.execute(page, (AT + DAY, AT + 2 * DAY, True, 50, 0)).fetchall() == []
    assert len(db.execute(page, (AT - DAY, AT, True, 50, 0)).fetchall()) == 28
    hidden_usage = db.execute(page, bounds + (False, 50, 0)).fetchall()
    assert [row[0] for row in hidden_usage] == [identifier(n) for n in range(1999, 1949, -1)]
    print("PASS: generated Timeline SQL, half-open days, deterministic ties, paging, visibility toggle and >1-minute usage")

    for name, bounds, offset in [("burst_first", (AT, AT + DAY), 0), ("burst_last", (AT, AT + DAY), 1950),
                                  ("old_day", (AT - 1825 * DAY, AT - 1824 * DAY), 0),
                                  ("empty", (AT + DAY, AT + 2 * DAY), 0)]:
        plan = [row[3] for row in db.execute("EXPLAIN QUERY PLAN " + page, bounds + (True, 50, offset))]
        assert any("index_life_event_occurred_at_id" in row for row in plan), plan
        assert not any("TEMP B-TREE" in row for row in plan), plan
        samples = []
        for _ in range(20):
            start = time.perf_counter()
            db.execute(count, bounds + (True,)).fetchone()
            db.execute(events, bounds).fetchone()
            db.execute(page, bounds + (True, 50, offset)).fetchall()
            samples.append((time.perf_counter() - start) * 1000)
        print(f"HOST_QUERY_ONLY sqlite={sqlite3.sqlite_version} rows=52003 query={name} median_ms={statistics.median(samples):.2f} p95_ms={sorted(samples)[18]:.2f}")
    db.close()
