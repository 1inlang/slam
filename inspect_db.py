import sqlite3
import os

db_path = r'e:\Projects\室内定位\corridor_20260514_210944\corridor_20260514_210944_0.db3'
print(f"DB exists: {os.path.exists(db_path)}")

conn = sqlite3.connect(db_path)

print("\n=== 所有表 ===")
for t in conn.execute("SELECT name FROM sqlite_master WHERE type='table'").fetchall():
    print(f"  {t[0]}")

print("\n=== 所有话题 ===")
for t in conn.execute("SELECT * FROM topics ORDER BY id").fetchall():
    cnt = conn.execute("SELECT COUNT(*) FROM messages WHERE topic_id=?", (t[0],)).fetchone()[0]
    print(f"  id={t[0]} name={t[1]} type={t[2]} count={cnt}")

print("\n=== 各topic消息统计 ===")
for row in conn.execute("SELECT topic_id, COUNT(*), MIN(timestamp), MAX(timestamp) FROM messages GROUP BY topic_id ORDER BY topic_id").fetchall():
    print(f"  topic_id={row[0]} count={row[1]} ts={row[2]} ~ {row[3]}")

# Check if there's any other data we might have missed
print("\n=== messages 表结构 ===")
col_info = conn.execute("PRAGMA table_info(messages)").fetchall()
for c in col_info:
    print(f"  {c}")
