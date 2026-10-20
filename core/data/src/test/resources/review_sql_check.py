"""Execute production review metric SQL against the exported schema; host-only timings."""
import base64, datetime, json, sqlite3, statistics, sys, time, uuid

def ident(n): return str(uuid.UUID(int=n))
def ms(text): return int(datetime.datetime.fromisoformat(text).replace(tzinfo=datetime.timezone.utc).timestamp()*1000)
queries = {}
for line in open(sys.argv[1]):
    name, count, page, values = line.rstrip('\n').split('\t')
    queries[name] = (base64.b64decode(count).decode(), base64.b64decode(page).decode(),
                     [int(v[1:]) if v[0]=='n' else base64.b64decode(v[1:]).decode() for v in values.split(',') if v])
db = sqlite3.connect(':memory:'); db.execute('PRAGMA foreign_keys=ON')
for entity in json.load(open(sys.argv[2]))['database']['entities']:
    db.execute(entity['createSql'].replace('${TABLE_NAME}',entity['tableName']))
    for idx in entity.get('indices',[]): db.execute(idx['createSql'].replace('${TABLE_NAME}',entity['tableName']))
at=ms('2026-10-12T12:00:00')
def event(n, kind='MOOD', stamp=at):
    db.execute("INSERT INTO life_event(id,type,occurred_at,created_at,updated_at,source) VALUES (?,?,?,?,?,'MANUAL')",(ident(n),kind,stamp,at,at))
def check(name, expected, offset=0, limit=50, count=None):
    c,p,args=queries[name]; actual=db.execute(c,args).fetchone()[0]
    rows=db.execute(p,args+[limit,offset]).fetchall()
    assert [r[0] for r in rows]==[ident(n) for n in expected],(name,rows,expected)
    assert actual==(len(expected) if count is None else count),(name,actual,count)
for n in [1,2,3]:
    event(n); db.execute('INSERT INTO mood_entry(id,life_event_id,mood_score,energy,stress) VALUES (?,?,?,?,?)',(ident(1000+n),ident(n),4,2 if n==1 else None,3 if n==2 else None))
event(4,'JOURNAL'); db.execute("INSERT INTO journal_entry(id,life_event_id,body) VALUES (?,?,'private')",(ident(1004),ident(4)))
db.execute('INSERT INTO memory(id,life_event_id) VALUES (?,?)',(ident(2004),ident(4)))
event(5,'ACTIVITY'); db.execute("INSERT INTO activity_record(id,life_event_id,activity_type,value,unit,source) VALUES (?,?,'steps',0,'count','MANUAL')",(ident(1005),ident(5)))
event(6,'APP_USAGE'); event(7,'MOOD',ms('2026-10-15T00:00:00')); event(8,'MOOD',at-7*86400000)
for table,name in [('emotion','Calm'),('person','آرش'),('place','Park')]: db.execute(f'INSERT INTO {table}(id,name) VALUES (?,?)',(ident(100),name))
db.execute('INSERT INTO mood_emotion VALUES (?,?)',(ident(1001),ident(100)))
db.execute('INSERT INTO event_person VALUES (?,?)',(ident(1),ident(100)))
db.execute('INSERT INTO event_place VALUES (?,?)',(ident(1),ident(100)))
check('ENTRIES',[1,2,3,4,5]); check('MOOD',[1,2,3]); check('ENERGY',[1]); check('STRESS',[2])
check('JOURNALS',[4]); check('MEMORIES',[4]); check('EMOTION',[1]); check('PERSON',[1]); check('PLACE',[1]); check('ACTIVITY',[5]); check('INJECTION',[])
check('ENTRIES',[3,4],offset=2,limit=2,count=5)
db.execute('DELETE FROM person WHERE id=?',(ident(100),)); check('PERSON',[])
db.execute('UPDATE mood_entry SET energy=NULL WHERE life_event_id=?',(ident(1),)); check('ENERGY',[])
db.execute('DELETE FROM life_event WHERE id=?',(ident(4),)); check('MEMORIES',[]); check('JOURNALS',[])
print('PASS: exact typed/emotion/catalog/activity sources, civil-date bounds, zero activity, deletion, correction, injection and paging')
# 50,000 events outside the period must not force full-graph reads.
for n in range(10000,60000): event(n,'JOURNAL',at-(30+n%1800)*86400000)
for name in ['ENTRIES','MOOD','PERSON','ACTIVITY']:
    c,p,args=queries[name]; samples=[]
    for _ in range(20):
        t=time.perf_counter(); db.execute(c,args).fetchone(); db.execute(p,args+[50,0]).fetchall(); samples.append((time.perf_counter()-t)*1000)
    print(f'{name} 50,000-history query median={statistics.median(samples):.3f}ms p95={sorted(samples)[18]:.3f}ms (excludes Room/device)')
