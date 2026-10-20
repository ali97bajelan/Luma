"""Execute registered migration SQL against a full v1 graph on disk, then reopen."""
import base64
import json
import sqlite3
import sys
import tempfile
from pathlib import Path

v1 = json.load(open(sys.argv[2]))["database"]
v2 = json.load(open(sys.argv[3]))["database"]
statements = [base64.b64decode(line).decode() for line in open(sys.argv[1])]


def create(db, schema):
    for entity in schema["entities"]:
        db.execute(entity["createSql"].replace("${TABLE_NAME}", entity["tableName"]))
        for index in entity.get("indices", []):
            db.execute(index["createSql"].replace("${TABLE_NAME}", entity["tableName"]))


def insert(db, table, **values):
    db.execute(f"INSERT INTO {table} ({','.join(values)}) VALUES ({','.join('?' for _ in values)})", list(values.values()))


def snapshot(db, schema):
    return {e["tableName"]: sorted(db.execute(f'SELECT * FROM {e["tableName"]}').fetchall(), key=repr)
            for e in schema["entities"]}


def structure(db, schema):
    result = {}
    for e in schema["entities"]:
        name = e["tableName"]
        indices = sorted(db.execute(f"PRAGMA index_list('{name}')").fetchall(), key=lambda x: x[1])
        result[name] = (db.execute(f"PRAGMA table_info('{name}')").fetchall(),
                        sorted(db.execute(f"PRAGMA foreign_key_list('{name}')").fetchall()),
                        [(i[1:], db.execute(f"PRAGMA index_info('{i[1]}')").fetchall()) for i in indices])
    return result


with tempfile.TemporaryDirectory(prefix="luma-migration-graph-") as directory:
    path = Path(directory) / "fixture.db"
    db = sqlite3.connect(path)
    db.execute("PRAGMA foreign_keys=ON")
    create(db, v1)
    for n, kind, source, ref in [(1, "MOOD", "MANUAL", None), (2, "JOURNAL", "MANUAL", None),
                                  (3, "ACTIVITY", "HEALTH_CONNECT", "steps:2026-10-12"),
                                  (4, "APP_USAGE", "USAGE_STATS", "usage:daily:app")]:
        insert(db, "life_event", id=f"event{n}", type=kind, occurred_at=1000 + n, created_at=900,
               updated_at=2000, ended_at=3000 if n == 4 else None, source=source, source_ref=ref,
               title="فارسی / Français", note="Private\ncontent", metadata_json='{"keep":true}')
    insert(db, "mood_entry", id="mood", life_event_id="event1", mood_score=4, energy=2, stress=None, note="Keep")
    insert(db, "emotion", id="emotion", name="Calm")
    insert(db, "mood_emotion", mood_id="mood", emotion_id="emotion")
    insert(db, "journal_entry", id="journal", life_event_id="event2", body="Line one\nLine two", title=None)
    insert(db, "memory", id="marker", life_event_id="event2", importance=5, note="Same underlying journal")
    insert(db, "person", id="person", name="آرش", note="", photo_uri=None)
    insert(db, "event_person", event_id="event2", person_id="person")
    insert(db, "place", id="place", name="Paris", note=None, latitude=48.8566, longitude=2.3522)
    insert(db, "event_place", event_id="event2", place_id="place")
    insert(db, "media_attachment", id="photo", life_event_id="event2", relative_path="original.img",
           type="image", metadata_json='{"width":3,"height":2}')
    insert(db, "tag", id="tag", name="Travel", normalized_name="travel")
    insert(db, "event_tag", event_id="event2", tag_id="tag")
    insert(db, "activity_record", id="activity", life_event_id="event3", activity_type="steps", value=0.0,
           unit="count", source="HEALTH_CONNECT", source_ref="steps:2026-10-12")
    insert(db, "app_usage_record", id="usage", life_event_id="event4", bucket_start=1000, bucket_end=3000,
           package_name="example.app", duration_ms=1200, source="USAGE_STATS")
    insert(db, "daily_summary", date="2026-10-12", zone_id="UTC", metrics_json='{"keep":1}',
           algorithm_version=1, computed_at=4000, freshness="FRESH")
    insert(db, "integration_state", source="HEALTH_CONNECT", status="READY", last_sync_at=5000,
           checkpoint="2026-10-12")
    db.execute("PRAGMA user_version=1")
    db.commit()
    before = snapshot(db, v1)
    assert all(before.values()), "Every v1 table must have a preservation fixture"
    with db:
        for statement in statements:
            db.execute(statement)
        db.execute("PRAGMA user_version=2")
    db.close()

    db = sqlite3.connect(path)
    expected = dict(before)
    summary = list(expected["daily_summary"][0])
    summary[-1] = "STALE"
    expected["daily_summary"] = [tuple(summary)]
    assert snapshot(db, v1) == expected, "Migration changed source records, identities, relationships or metadata"
    assert db.execute("PRAGMA user_version").fetchone() == (2,)
    assert db.execute("PRAGMA foreign_key_check").fetchall() == []
    assert db.execute("PRAGMA integrity_check").fetchone() == ("ok",)
    assert db.execute("SELECT * FROM usage_session").fetchall() == []
    assert db.execute("SELECT * FROM usage_session_window").fetchall() == []
    fresh = sqlite3.connect(":memory:")
    create(fresh, v2)
    assert structure(db, v2) == structure(fresh, v2), "Migrated tables/FKs/indexes differ from exported v2"
    fresh.close()
    db.close()
print("PASS: production v1->v2 migration, all 17 v1 tables, complete graph, cache invalidation, disk reopen and exported v2 structure")
