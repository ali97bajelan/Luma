import sys, sqlite3, json, base64
schema = json.load(open(sys.argv[2]))['database']
db = sqlite3.connect(':memory:')
db.execute('PRAGMA foreign_keys=ON')
for entity in schema['entities']:
    db.execute(entity['createSql'].replace('${TABLE_NAME}', entity['tableName']))
    for index in entity.get('indices', []):
        db.execute(index['createSql'].replace('${TABLE_NAME}', entity['tableName']))
instructions = [(line.split('\t')[0], base64.b64decode(line.split('\t')[1]).decode()) for line in open(sys.argv[1]).read().splitlines()]
deletes = [sql for kind, sql in instructions if kind == 'D']
validations = [sql for kind, sql in instructions if kind == 'V']
insert = next(sql for kind, sql in instructions if kind == 'I')
db.execute(insert, ('e', 'JOURNAL', 1, 1, 1, 'MANUAL'))
db.execute("INSERT INTO journal_entry(id,life_event_id,body) VALUES ('j','e','before')")
db.execute("INSERT INTO person(id,name) VALUES ('p','فارسی')")
db.execute("INSERT INTO event_person(event_id,person_id) VALUES ('e','p')")
db.commit()
assert all(not db.execute(sql).fetchone() for sql in validations)
# Saved usage supports native bounds independently from an older/displaced envelope.
db.execute(insert, ('usage', 'APP_USAGE', 1, 1, 1, 'USAGE_STATS'))
db.execute("UPDATE life_event SET source_ref='usage:legacy', ended_at=2000 WHERE id='usage'")
db.execute("INSERT INTO app_usage_record(id,life_event_id,bucket_start,bucket_end,package_name,duration_ms,source) VALUES ('usage-child','usage',1000,2000,'example.app',100,'USAGE_STATS')")
assert all(not db.execute(sql).fetchone() for sql in validations)
db.commit()
# FK-valid typed-child mismatch must still fail graph validation.
db.execute("UPDATE life_event SET type='MOOD'")
assert any(db.execute(sql).fetchone() for sql in validations)
db.rollback()
before = {e['tableName']: db.execute('SELECT * FROM ' + e['tableName']).fetchall() for e in schema['entities']}
try:
    with db:
        for sql in deletes: db.execute(sql)
        db.execute(insert, ('new', 'JOURNAL', 2, 2, 2, 'MANUAL'))
        db.execute("INSERT INTO journal_entry(id,life_event_id,body) VALUES ('broken','missing','must roll back')")
except sqlite3.IntegrityError:
    pass
else:
    raise AssertionError('expected broken FK failure')
after = {e['tableName']: db.execute('SELECT * FROM ' + e['tableName']).fetchall() for e in schema['entities']}
assert before == after, (before, after)
with db:
    for sql in deletes: db.execute(sql)
    db.execute(insert, ('replacement', 'JOURNAL', 3, 3, 3, 'MANUAL'))
    db.execute("INSERT INTO journal_entry(id,life_event_id,body) VALUES ('replacement-body','replacement','after')")
assert db.execute('SELECT body FROM journal_entry').fetchall() == [('after',)]
assert not db.execute('SELECT * FROM person').fetchall()
assert not db.execute('PRAGMA foreign_key_check').fetchall()
print('production backup SQL: valid graph, child mismatch, all-table rollback and replacement verified')
