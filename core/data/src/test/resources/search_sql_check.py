"""Host SQLite checks of production-generated search queries; not Android timing."""
import base64
import datetime
import json
import sqlite3
import statistics
import sys
import time
import uuid


def identifier(n):
    return str(uuid.UUID(int=n))


def millis(date):
    return int(datetime.datetime.fromisoformat(date).replace(tzinfo=datetime.timezone.utc).timestamp() * 1000)


queries = {}
for line in open(sys.argv[1], encoding="utf-8"):
    name, count, page, values = line.rstrip("\n").split("\t")
    arguments = [int(v[1:]) if v.startswith("n") else base64.b64decode(v[1:]).decode() for v in values.split(",") if v]
    queries[name] = (base64.b64decode(count).decode(), base64.b64decode(page).decode(), arguments)

db = sqlite3.connect(":memory:")
db.execute("PRAGMA foreign_keys=ON")
schema = json.load(open(sys.argv[2]))["database"]
for entity in schema["entities"]:
    db.execute(entity["createSql"].replace("${TABLE_NAME}", entity["tableName"]))
    for index in entity.get("indices", []):
        db.execute(index["createSql"].replace("${TABLE_NAME}", entity["tableName"]))

at = millis("2026-10-12T12:00:00")


def event(n, title, occurred=at, kind="JOURNAL"):
    db.execute("INSERT INTO life_event(id,type,occurred_at,created_at,updated_at,source,title,note) VALUES (?,?,?,?,?,'MANUAL',?,'envelope-secret')",
               (identifier(n), kind, occurred, at, at, title))


def journal(n, title, occurred=at):
    event(n, title, occurred)
    db.execute("INSERT INTO journal_entry(id,life_event_id,title,body) VALUES (?,?,?,'private-secret')", (identifier(n + 10000), identifier(n), title))


def matches(name, expected, offset=0, limit=50):
    count_sql, page_sql, args = queries[name]
    count = db.execute(count_sql, args).fetchone()[0]
    rows = db.execute(page_sql, args + [limit, offset]).fetchall()
    actual = [r[0] for r in rows]
    assert actual == [identifier(n) for n in expected], (name, actual, expected)
    assert count >= len(rows), (name, count)
    return count


journal(1, "100%_done 'quote' \\path")
journal(2, None)
journal(3, "Unrelated")
journal(4, "Walk")
journal(5, "Walk", at - 86400000)
journal(6, "Envelope title")
db.execute("UPDATE journal_entry SET title='Child title' WHERE life_event_id=?", (identifier(6),))
event(7, "Unknown usage", kind="APP_USAGE")
event(8, "Zero usage", kind="APP_USAGE")
db.execute("INSERT INTO app_usage_record(id,life_event_id,bucket_start,bucket_end,package_name,duration_ms,source) VALUES (?,?,?,?,?,0,'USAGE_STATS')",
           (identifier(10008), identifier(8), at, at + 60000, "org.example"))
for n, name in [(101, "Weekend"), (102, "Work")]:
    db.execute("INSERT INTO tag(id,name,normalized_name) VALUES (?,?,?)", (identifier(n), name, name.lower()))
for n in [201, 202]:
    db.execute("INSERT INTO person(id,name,note) VALUES (?, 'آرش', 'catalog-secret')", (identifier(n),))
db.execute("INSERT INTO place(id,name,note) VALUES (?, 'Park', 'catalog-secret')", (identifier(301),))
for n in [2, 4, 5]:
    for tag in [101, 102]:
        db.execute("INSERT INTO event_tag VALUES (?,?)", (identifier(n), identifier(tag)))
    for person in [201, 202]:
        db.execute("INSERT INTO event_person VALUES (?,?)", (identifier(n), identifier(person)))
    db.execute("INSERT INTO event_place VALUES (?,?)", (identifier(n), identifier(301)))

assert matches("all", [7, 6, 4, 3, 2, 1, 5]) == 7
for name in ["wildcard", "quote", "slash"]:
    assert matches(name, [1]) == 1
for name in ["person", "tag", "place"]:
    assert matches(name, [4, 2, 5]) == 3
for name in ["body", "note", "injection", "missingPerson"]:
    assert matches(name, []) == 0
assert matches("journalTitle", [6]) == 1
assert matches("combined", [4]) == 1
assert matches("all", [4, 3], offset=2, limit=2) == 7
db.execute("UPDATE person SET name='Renamed'")
assert matches("person", []) == 0
db.execute("DELETE FROM life_event WHERE id=?", (identifier(4),))
assert matches("combined", []) == 0
print("PASS: actual generated SQL, literals, Persian, body/note exclusion, combined filters, paging, edits and deletion")

# Independent query-only benchmark. Room relation mapping and device UI are excluded.
db.close()
db = sqlite3.connect(":memory:")
for entity in schema["entities"]:
    db.execute(entity["createSql"].replace("${TABLE_NAME}", entity["tableName"]))
    for index in entity.get("indices", []):
        db.execute(index["createSql"].replace("${TABLE_NAME}", entity["tableName"]))
db.executemany("INSERT INTO life_event(id,type,occurred_at,created_at,updated_at,source,title) VALUES (?,'JOURNAL',?,?,?,'MANUAL',?)",
               ((identifier(n + 100000), at - (n % 1825) * 86400000, at, at, f"Fixture {n}") for n in range(50000)))
db.executemany("INSERT INTO journal_entry(id,life_event_id,title,body) VALUES (?,?,?,?)",
               ((identifier(n + 200000), identifier(n + 100000), f"Fixture {n}", "Private body " * 100) for n in range(50000)))
for n, name in [(101, "Weekend"), (102, "Work")]:
    db.execute("INSERT INTO tag(id,name,normalized_name) VALUES (?,?,?)", (identifier(n), name, name.lower()))
for n in [201, 202]:
    db.execute("INSERT INTO person(id,name) VALUES (?, 'Friend')", (identifier(n),))
db.execute("INSERT INTO place(id,name) VALUES (?, 'Park')", (identifier(301),))
db.executemany("INSERT INTO event_tag VALUES (?,?)", ((identifier(n + 100000), identifier(101 + n % 2)) for n in range(0, 50000, 4)))
db.executemany("INSERT INTO event_person VALUES (?,?)", ((identifier(n + 100000), identifier(201 + n % 2)) for n in range(0, 50000, 3)))
db.executemany("INSERT INTO event_place VALUES (?,?)", ((identifier(n + 100000), identifier(301)) for n in range(0, 50000, 5)))
db.commit()
for name in ["all", "benchTitle", "benchMissing", "benchDate", "benchCombined"]:
    count_sql, page_sql, args = queries[name]
    samples = []
    for n in range(12):
        started = time.perf_counter()
        count = db.execute(count_sql, args).fetchone()[0]
        rows = db.execute(page_sql, args + [50, 0 if n % 2 == 0 else 200]).fetchall()
        samples.append((time.perf_counter() - started) * 1000)
        assert len(rows) <= 50
    expected_counts = {"all": 50000, "benchTitle": 1111, "benchMissing": 0, "benchDate": 336}
    if name in expected_counts:
        assert count == expected_counts[name], (name, count)
    p95 = sorted(samples)[-1]
    print(f"HOST_QUERY_ONLY sqlite={sqlite3.sqlite_version} rows=50000 query={name} count={count} median_ms={statistics.median(samples):.2f} p95_ms={p95:.2f}")
plan = [r[3] for r in db.execute("EXPLAIN QUERY PLAN " + queries["benchDate"][1], queries["benchDate"][2] + [50, 0])]
assert any("index_life_event_occurred_at_id" in row for row in plan), plan
assert not any("TEMP B-TREE" in row for row in plan), plan
print("PASS: date query uses occurrence/UUID index without sorting the history")
