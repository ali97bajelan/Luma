#!/usr/bin/env python3
"""Execute the DAO's history candidate SQL against a small SQLite fixture."""
from pathlib import Path
import sqlite3

ROOT = Path(__file__).resolve().parents[1]
DAO = ROOT / 'core/data/src/main/kotlin/com/luma/lifeos/data/focus/FocusDao.kt'


def main() -> None:
    source = DAO.read_text(encoding='utf-8')
    method_index = source.find('suspend fun historyCandidatesPage')
    query_annotation = source.rfind('@Query(', 0, method_index)
    query_start = source.find('"""', query_annotation)
    query_end = source.find('"""', query_start + 3)
    if method_index < 0 or query_annotation < 0 or query_start < 0 or query_end < 0:
        raise SystemExit('Could not locate historyCandidatesPage SQL in FocusDao.kt')
    query = source[query_start + 3:query_end]
    database = sqlite3.connect(':memory:')
    database.executescript('''
        CREATE TABLE focus_session (
            id TEXT PRIMARY KEY, state TEXT NOT NULL,
            started_at_seconds INTEGER NOT NULL, started_at_nanos INTEGER NOT NULL
        );
        CREATE TABLE focus_run (
            id TEXT PRIMARY KEY, session_id TEXT NOT NULL, credited_millis INTEGER NOT NULL,
            started_at_seconds INTEGER NOT NULL, started_at_nanos INTEGER NOT NULL,
            ended_at_seconds INTEGER NOT NULL, ended_at_nanos INTEGER NOT NULL
        );
    ''')
    sessions = []
    runs = []
    for index in range(60):
        session_id = f'in-range-{index:03d}'
        started_seconds = 1000 + index // 3
        started_nanos = (index % 2) * 100
        sessions.append((session_id, 'FINISHED', started_seconds, started_nanos))
        runs.append((f'run-{index:03d}', session_id, 1, 110, 0, 150, 0))
    sessions.extend([
        ('at-end', 'FINISHED', 9999, 0),
        ('ends-at-start', 'FINISHED', 9998, 0),
        ('open', 'RUNNING', 9997, 0),
        ('zero-credit', 'FINISHED', 9996, 0),
    ])
    runs.extend([
        ('run-at-end', 'at-end', 1, 200, 0, 210, 0),
        ('run-ends-start', 'ends-at-start', 1, 90, 0, 100, 0),
        ('run-open', 'open', 1, 110, 0, 150, 0),
        ('run-zero', 'zero-credit', 0, 110, 0, 150, 0),
    ])
    database.executemany('INSERT INTO focus_session VALUES (?, ?, ?, ?)', sessions)
    database.executemany('INSERT INTO focus_run VALUES (?, ?, ?, ?, ?, ?, ?)', runs)

    bindings = {
        'startSeconds': 100,
        'startNanos': 0,
        'endSeconds': 200,
        'endNanos': 0,
        'offset': 0,
        'limit': 17,
    }
    actual = []
    offset = 0
    while True:
        bindings['offset'] = offset
        page = database.execute(query, bindings).fetchall()
        actual.extend(row[0] for row in page)
        offset += len(page)
        if len(page) < bindings['limit']:
            break
    expected = [row[0] for row in sorted(sessions[:60], key=lambda row: (row[2], row[3], row[0]), reverse=True)]
    if actual != expected:
        raise SystemExit(f'History query mismatch: expected {expected[:5]}…, got {actual[:5]}…')
    if len(actual) != 60:
        raise SystemExit(f'Expected 60 terminal range matches, got {len(actual)}')
    print('SQLite FI06 history query: OK (60 terminal range matches, stable batches, boundary exclusions)')


if __name__ == '__main__':
    main()
