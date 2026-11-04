#!/usr/bin/env python3
"""Execute the real Room 9→10 SQL against SQLite fixtures without Gradle."""
from pathlib import Path
import re
import sqlite3
import uuid

ROOT = Path(__file__).resolve().parents[1]
DATABASE_SOURCE = ROOT / 'core/data/src/main/kotlin/com/luma/lifeos/data/db/LumaDatabase.kt'
ARCHIVE_ADAPTER_SOURCE = ROOT / 'core/data/src/main/kotlin/com/luma/lifeos/data/backup/FocusMetadataArchiveAdapter.kt'


def migration_statements() -> list[str]:
    source = DATABASE_SOURCE.read_text(encoding='utf-8')
    start = source.index('object : Migration(9, 10)')
    end = source.index('object : Migration(1, 2)', start)
    block = source[start:end]
    calls = re.findall(
        r'db\.execSQL\(\s*(?:"""(.*?)"""|"((?:\\.|[^"\\])*)")\s*\)',
        block,
        flags=re.DOTALL,
    )
    statements = [triple if triple else bytes(quoted, 'utf-8').decode('unicode_escape') for triple, quoted in calls]
    if len(statements) != 5:
        raise AssertionError(f'Expected to extract all 5 production migration statements, found {len(statements)}')
    return [statement.strip() for statement in statements]


def focus_session_export_queries() -> tuple[str, str]:
    source = ARCHIVE_ADAPTER_SOURCE.read_text(encoding='utf-8')
    queries = []
    for name in ('FOCUS_SESSION_EXPORT_QUERY', 'FOCUS_SESSION_EXPORT_EXCLUDING_QUERY'):
        match = re.search(rf'internal const val {name}\s*=\s*"""(.*?)"""', source, flags=re.DOTALL)
        if not match:
            raise AssertionError(f'Could not extract production query {name}')
        queries.append(match.group(1).strip())
    return queries[0], queries[1]


def create_v9_database(connection: sqlite3.Connection) -> None:
    connection.execute('PRAGMA foreign_keys = ON')
    connection.executescript('''
        CREATE TABLE focus_session (
            id TEXT NOT NULL PRIMARY KEY, state TEXT NOT NULL, revision INTEGER NOT NULL,
            started_at_seconds INTEGER NOT NULL, started_at_nanos INTEGER NOT NULL,
            ended_at_seconds INTEGER, ended_at_nanos INTEGER,
            created_at_seconds INTEGER NOT NULL, created_at_nanos INTEGER NOT NULL,
            updated_at_seconds INTEGER NOT NULL, updated_at_nanos INTEGER NOT NULL,
            terminal_reason TEXT, recovered_or_uncertain INTEGER NOT NULL, open_slot INTEGER
        );
        CREATE UNIQUE INDEX index_focus_session_open_slot ON focus_session(open_slot);
        CREATE INDEX index_focus_session_state_started_at_seconds_started_at_nanos
            ON focus_session(state, started_at_seconds, started_at_nanos);
        CREATE TABLE focus_run (
            id TEXT NOT NULL PRIMARY KEY, session_id TEXT NOT NULL, ordinal INTEGER NOT NULL,
            started_at_seconds INTEGER NOT NULL, started_at_nanos INTEGER NOT NULL,
            ended_at_seconds INTEGER, ended_at_nanos INTEGER, credited_millis INTEGER NOT NULL,
            raw_started_at_seconds INTEGER NOT NULL, raw_started_at_nanos INTEGER NOT NULL,
            raw_ended_at_seconds INTEGER, raw_ended_at_nanos INTEGER, open_session_id TEXT,
            FOREIGN KEY(session_id) REFERENCES focus_session(id) ON UPDATE NO ACTION ON DELETE CASCADE
        );
        CREATE UNIQUE INDEX index_focus_run_session_id_ordinal ON focus_run(session_id, ordinal);
        CREATE UNIQUE INDEX index_focus_run_id_session_id ON focus_run(id, session_id);
        CREATE UNIQUE INDEX index_focus_run_open_session_id ON focus_run(open_session_id);
        CREATE INDEX index_focus_run_started_at_seconds_started_at_nanos ON focus_run(started_at_seconds, started_at_nanos);
        CREATE INDEX index_focus_run_ended_at_seconds_ended_at_nanos ON focus_run(ended_at_seconds, ended_at_nanos);
        CREATE TABLE focus_runtime (
            slot_id INTEGER NOT NULL PRIMARY KEY, session_id TEXT NOT NULL, run_id TEXT NOT NULL,
            run_started_elapsed_millis INTEGER NOT NULL, checkpoint_elapsed_millis INTEGER NOT NULL,
            checkpoint_wall_seconds INTEGER NOT NULL, checkpoint_wall_nanos INTEGER NOT NULL,
            boot_id TEXT, process_instance_id TEXT NOT NULL, recovery_required INTEGER NOT NULL,
            revision INTEGER NOT NULL,
            FOREIGN KEY(session_id) REFERENCES focus_session(id) ON UPDATE NO ACTION ON DELETE CASCADE,
            FOREIGN KEY(run_id, session_id) REFERENCES focus_run(id, session_id) ON UPDATE NO ACTION ON DELETE CASCADE
        );
        CREATE UNIQUE INDEX index_focus_runtime_session_id ON focus_runtime(session_id);
        CREATE UNIQUE INDEX index_focus_runtime_run_id ON focus_runtime(run_id);
        CREATE INDEX index_focus_runtime_run_id_session_id ON focus_runtime(run_id, session_id);
        CREATE TABLE focus_command_receipt (
            sequence INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, command_id TEXT NOT NULL,
            database_generation TEXT NOT NULL, payload TEXT NOT NULL, result_type TEXT NOT NULL,
            result_session_id TEXT, result_invalid_reason TEXT,
            created_at_seconds INTEGER NOT NULL, created_at_nanos INTEGER NOT NULL
        );
        CREATE UNIQUE INDEX index_focus_command_receipt_database_generation_command_id
            ON focus_command_receipt(database_generation, command_id);
        CREATE TABLE focus_deleted_session (session_id TEXT NOT NULL PRIMARY KEY, database_generation TEXT NOT NULL);
        CREATE INDEX index_focus_deleted_session_database_generation ON focus_deleted_session(database_generation);
        CREATE TABLE focus_storage_state (singleton_id INTEGER NOT NULL PRIMARY KEY, generation_id TEXT NOT NULL);
    ''')
def seed_state(connection: sqlite3.Connection, state: str) -> tuple[str, str]:
    session_id, run_id, receipt_id = (str(uuid.uuid4()) for _ in range(3))
    terminal = state in ('FINISHED', 'INTERRUPTED')
    ended_seconds = 120 if terminal else None
    ended_nanos = 0 if terminal else None
    terminal_reason = 'USER_STOP' if state == 'FINISHED' else ('PROCESS_RESTART_GAP' if state == 'INTERRUPTED' else None)
    open_slot = None if terminal else 1
    run_end = None if state == 'RUNNING' else 110
    raw_end = None if run_end is None else 111
    open_session_id = session_id if state == 'RUNNING' else None
    connection.execute('''INSERT INTO focus_session VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)''', (
        session_id, state, 4, 100, 0, ended_seconds, ended_nanos, 90, 0,
        ended_seconds or 110, 0, terminal_reason, 1 if state == 'INTERRUPTED' else 0, open_slot,
    ))
    connection.execute('''INSERT INTO focus_run VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)''', (
        run_id, session_id, 1, 100, 0, run_end, 0 if run_end is not None else None,
        10_000, 100, 0, raw_end, 0 if raw_end is not None else None, open_session_id,
    ))
    if state == 'RUNNING':
        connection.execute('''INSERT INTO focus_runtime VALUES (1, ?, ?, 5000, 5000, 110, 0, 'boot', 'process', 0, 4)''',
                           (session_id, run_id))
    connection.execute('''INSERT INTO focus_command_receipt
        (command_id, database_generation, payload, result_type, result_session_id,
         result_invalid_reason, created_at_seconds, created_at_nanos)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?)''',
        (receipt_id, 'generation', '{"action":"START"}', 'APPLIED', session_id, None, 111, 12))
    connection.execute('INSERT INTO focus_deleted_session VALUES (?, ?)', (str(uuid.uuid4()), 'generation'))
    connection.execute('INSERT INTO focus_storage_state VALUES (1, ?)', ('generation',))
    connection.commit()
    return session_id, run_id


def snapshot(connection: sqlite3.Connection, query: str, parameters: tuple = ()) -> list[tuple]:
    return connection.execute(query, parameters).fetchall()


def check_state(state: str, statements: list[str]) -> None:
    connection = sqlite3.connect(':memory:')
    try:
        create_v9_database(connection)
        session_id, run_id = seed_state(connection, state)
        old_session = snapshot(connection, 'SELECT id, state, revision, started_at_seconds, started_at_nanos, ended_at_seconds, ended_at_nanos, created_at_seconds, created_at_nanos, updated_at_seconds, updated_at_nanos, terminal_reason, recovered_or_uncertain, open_slot FROM focus_session')
        old_run = snapshot(connection, 'SELECT * FROM focus_run')
        old_runtime = snapshot(connection, 'SELECT * FROM focus_runtime')
        old_receipts = snapshot(connection, 'SELECT * FROM focus_command_receipt')
        old_deleted = snapshot(connection, 'SELECT * FROM focus_deleted_session')
        old_storage = snapshot(connection, 'SELECT * FROM focus_storage_state')
        old_indexes = {row[0] for row in snapshot(connection, "SELECT name FROM sqlite_master WHERE type = 'index' AND name IS NOT NULL")}

        for statement in statements:
            connection.execute(statement)
        connection.commit()

        assert snapshot(connection, 'SELECT id, state, revision, started_at_seconds, started_at_nanos, ended_at_seconds, ended_at_nanos, created_at_seconds, created_at_nanos, updated_at_seconds, updated_at_nanos, terminal_reason, recovered_or_uncertain, open_slot FROM focus_session') == old_session
        assert snapshot(connection, 'SELECT * FROM focus_run') == old_run
        assert snapshot(connection, 'SELECT * FROM focus_runtime') == old_runtime
        assert snapshot(connection, 'SELECT * FROM focus_command_receipt') == old_receipts
        assert snapshot(connection, 'SELECT * FROM focus_deleted_session') == old_deleted
        assert snapshot(connection, 'SELECT * FROM focus_storage_state') == old_storage
        new_indexes = {row[0] for row in snapshot(connection, "SELECT name FROM sqlite_master WHERE type = 'index' AND name IS NOT NULL")}
        assert old_indexes <= new_indexes, f'{state}: prior indexes were lost: {old_indexes - new_indexes}'
        assert {'index_focus_tag_normalized_name', 'index_focus_session_tag_id'} <= new_indexes

        columns = {row[1] for row in snapshot(connection, 'PRAGMA table_info(focus_session)')}
        assert {'name', 'tag_id'} <= columns
        assert snapshot(connection, 'SELECT name, tag_id FROM focus_session') == [(None, None)]
        foreign_keys = snapshot(connection, 'PRAGMA foreign_key_list(focus_session)')
        assert any(row[2] == 'focus_tag' and row[3] == 'tag_id' and row[6].upper() == 'SET NULL' for row in foreign_keys)
        assert snapshot(connection, 'PRAGMA foreign_key_check') == []

        tag_id = str(uuid.uuid4())
        connection.execute('INSERT INTO focus_tag VALUES (?, ?, ?)', (tag_id, 'Work', 'work'))
        connection.execute('UPDATE focus_session SET tag_id = ? WHERE id = ?', (tag_id, session_id))
        assert snapshot(connection, 'PRAGMA foreign_key_check') == []
        connection.execute('DELETE FROM focus_tag WHERE id = ?', (tag_id,))
        assert snapshot(connection, 'SELECT tag_id FROM focus_session WHERE id = ?', (session_id,)) == [(None,)]
        assert snapshot(connection, 'PRAGMA integrity_check') == [('ok',)]
        print(f'PASS Room 9→10 fixture: {state} session/run and device-local focus rows preserved')
    finally:
        connection.close()


def check_stale_excluded_tag_export(statements: list[str]) -> None:
    connection = sqlite3.connect(':memory:')
    try:
        create_v9_database(connection)
        for statement in statements:
            connection.execute(statement)
        connection.commit()

        excluded_session_id, included_session_id = str(uuid.uuid4()), str(uuid.uuid4())
        excluded_tag_id, included_tag_id = str(uuid.uuid4()), str(uuid.uuid4())
        connection.executemany('INSERT INTO focus_tag VALUES (?, ?, ?)', [
            (excluded_tag_id, 'Excluded', 'excluded'),
            (included_tag_id, 'Included', 'included'),
        ])
        for index, (session_id, tag_id) in enumerate(((excluded_session_id, excluded_tag_id), (included_session_id, included_tag_id))):
            connection.execute('''INSERT INTO focus_session (
                id, state, revision, started_at_seconds, started_at_nanos, ended_at_seconds, ended_at_nanos,
                created_at_seconds, created_at_nanos, updated_at_seconds, updated_at_nanos, terminal_reason,
                recovered_or_uncertain, open_slot, name, tag_id
            ) VALUES (?, 'FINISHED', 1, 100, 0, 120, 0, 90, 0, 120, 0, 'USER_STOP', 0, NULL, ?, ?)''',
                (session_id, f'Session {index}', tag_id))
            run_id = str(uuid.uuid4())
            connection.execute('''INSERT INTO focus_run (
                id, session_id, ordinal, started_at_seconds, started_at_nanos, ended_at_seconds, ended_at_nanos,
                credited_millis, raw_started_at_seconds, raw_started_at_nanos, raw_ended_at_seconds,
                raw_ended_at_nanos, open_session_id
            ) VALUES (?, ?, 1, 100, 0, 110, 0, 10000, 100, 0, 111, 0, NULL)''', (run_id, session_id))
        connection.commit()

        include_query, exclude_query = focus_session_export_queries()
        all_rows = connection.execute(include_query + ' ORDER BY id').fetchall()
        exported_rows = connection.execute(exclude_query + ' ORDER BY id', (excluded_session_id,)).fetchall()
        tag_index = next(index for index, column in enumerate(connection.execute(exclude_query + ' LIMIT 0', (excluded_session_id,)).description) if column[0] == 'tag_id')
        tag_ids = {row[tag_index] for row in exported_rows if row[tag_index] is not None}
        exported_tag_ids = {row[0] for row in connection.execute('SELECT id FROM focus_tag') if row[0] in tag_ids}

        assert len(all_rows) == 2
        assert [row[0] for row in exported_rows] == [included_session_id]
        assert exported_tag_ids == {included_tag_id}
        assert excluded_tag_id not in exported_tag_ids
        print('PASS stale EXCLUDE_PAUSED choice excludes a now-terminal session and its tag')
    finally:
        connection.close()


def main() -> None:
    statements = migration_statements()
    for state in ('RUNNING', 'PAUSED', 'FINISHED', 'INTERRUPTED'):
        check_state(state, statements)
    check_stale_excluded_tag_export(statements)
    print(f'PASS executed {len(statements)} SQL statements extracted from {DATABASE_SOURCE.relative_to(ROOT)}')


if __name__ == '__main__':
    main()
