import sqlite3
import sys

db='powerai.db'
try:
    conn=sqlite3.connect(db)
except Exception as e:
    print('ERROR opening db:', e)
    sys.exit(2)
cur=conn.cursor()
# list tables
cur.execute("SELECT name FROM sqlite_master WHERE type='table';")
tables=[r[0] for r in cur.fetchall()]
print('TABLES:')
for t in tables:
    print(' -', t)

# for each table, try select id,title
for t in tables:
    try:
        cur.execute(f"PRAGMA table_info('{t}');")
        cols=cur.fetchall()
        col_info = {c[1]: c for c in cols}
        has_id = 'id' in col_info
        print(f"\nTABLE {t}: has_id={has_id}")
        if has_id:
            print('  id column info:', col_info['id'])
        # try select
        cur.execute(f"SELECT id, title FROM {t} LIMIT 5;")
        rows=cur.fetchall()
        if rows:
            print('  SAMPLE ROWS:')
            for r in rows:
                print('   ', r, ' (types:', [type(x).__name__ for x in r], ')')
        else:
            print('  (no rows returned or no title column)')
    except Exception as e:
        # skip tables without id/title
        # print minimal info
        print(f"\nTABLE {t}: could not query id,title — {e}")

# check for specific id 1001
try:
    cur.execute("SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name='knowledge';")
    if cur.fetchone()[0]==1:
        target_tables=['knowledge']
    else:
        target_tables=tables
    for t in target_tables:
        try:
            cur.execute(f"SELECT id, title FROM {t} WHERE id=1001 LIMIT 1;")
            r=cur.fetchone()
            if r:
                print('\nFOUND id=1001 in table', t, '->', r, ' (types:', [type(x).__name__ for x in r], ')')
            else:
                print('\nNOT FOUND id=1001 in table', t)
        except Exception as e:
            print('\nSKIP checking id in', t, '->', e)
except Exception as e:
    print('error checking specific id:', e)

conn.close()
