import gzip
import json
import sqlite3
import urllib.request
from pathlib import Path
import time
import os

URL = "https://kaikki.org/dictionary/raw-wiktextract-data.jsonl.gz"
GZ_FILE = Path("kaikki_raw.jsonl.gz")
OUTPUT_DB = Path("dict_en.db")

def report_hook(count, block_size, total_size):
    global start_time
    if count == 0:
        start_time = time.time()
        return
    duration = time.time() - start_time
    progress_size = int(count * block_size)
    if count % 10000 == 0:  # Print every ~80MB
        speed = int(progress_size / (1024 * 1024 * duration)) if duration > 0 else 0
        percent = int(count * block_size * 100 / total_size) if total_size > 0 else 0
        print(f"Downloading... {progress_size / (1024*1024):.0f} MB / {total_size / (1024*1024):.0f} MB ({percent}%) at {speed} MB/s", flush=True)

def init_db(db_path: Path):
    if db_path.exists():
        db_path.unlink()
    conn = sqlite3.connect(db_path)
    conn.execute("""
        CREATE TABLE entries (
            word TEXT NOT NULL,
            wordtype TEXT,
            definition TEXT,
            translation TEXT
        )
    """)
    conn.execute("CREATE INDEX idx_entries_word ON entries(word COLLATE NOCASE)")
    return conn

def build_db(conn):
    print(f"\nProcessing {GZ_FILE}...", flush=True)
    start_time = time.time()
    
    with gzip.open(GZ_FILE, mode="rt", encoding="utf-8") as f:
        batch = []
        count = 0
        
        for line in f:
            if not line.strip(): continue
            
            try:
                obj = json.loads(line)
            except json.JSONDecodeError:
                continue
            
            if obj.get("lang_code") != "en":
                continue
            
            word = obj.get("word")
            if not word: continue
            
            wordtype = obj.get("pos", "")
            
            translations = []
            
            # Check for root level translations (new schema)
            if "translations" in obj:
                translations.extend(obj["translations"])
            
            # Check inside senses (old schema)
            for sense in obj.get("senses", []):
                if "translations" in sense:
                    translations.extend(sense["translations"])
            
            pt_translations = [t.get("word") for t in translations if t.get("code") == "pt" and t.get("word")]
            
            if pt_translations:
                definition = ""
                senses = obj.get("senses", [])
                if senses and "glosses" in senses[0] and senses[0]["glosses"]:
                    definition = senses[0]["glosses"][0]
                    
                translation_text = ", ".join(dict.fromkeys(pt_translations)) # remove duplicates
                batch.append((word, wordtype, definition, translation_text))
            
            if len(batch) >= 5000:
                conn.executemany("INSERT INTO entries (word, wordtype, definition, translation) VALUES (?, ?, ?, ?)", batch)
                count += len(batch)
                batch = []
                if count % 20000 == 0:
                    print(f"Extracted {count} Portuguese translations... (elapsed: {int(time.time() - start_time)}s)", flush=True)
        
        if batch:
            conn.executemany("INSERT INTO entries (word, wordtype, definition, translation) VALUES (?, ?, ?, ?)", batch)
            count += len(batch)
        
        conn.commit()
        print(f"Done! Extracted {count} translations to {OUTPUT_DB} in {int(time.time() - start_time)}s.", flush=True)

if __name__ == "__main__":
    req = urllib.request.Request(URL, headers={'User-Agent': 'Mozilla/5.0'})
    try:
        head_resp = urllib.request.urlopen(urllib.request.Request(URL, method='HEAD'))
        total_size = int(head_resp.headers.get('Content-Length', 0))
    except Exception:
        total_size = 0
        
    downloaded = GZ_FILE.stat().st_size if GZ_FILE.exists() else 0
    
    if downloaded < total_size or total_size == 0:
        print(f"Starting/Resuming download of {URL}...")
        print(f"Already downloaded: {downloaded / (1024*1024):.0f} MB")
        
        req.headers['Range'] = f'bytes={downloaded}-'
        with urllib.request.urlopen(req) as response:
            with open(GZ_FILE, "ab") as f:
                start_time = time.time()
                while True:
                    chunk = response.read(8192 * 10)
                    if not chunk: break
                    f.write(chunk)
                    downloaded += len(chunk)
                    
                    # Print progress occasionally
                    if (downloaded // (8192 * 10)) % 1000 == 0:
                        percent = int(downloaded * 100 / total_size) if total_size else 0
                        speed = len(chunk) / (time.time() - start_time + 0.001)
                        print(f"Downloading... {downloaded / (1024*1024):.0f} MB / {total_size / (1024*1024):.0f} MB ({percent}%)", flush=True)
                        start_time = time.time()
        print("\nDownload complete.")
    else:
        print(f"File {GZ_FILE} already fully downloaded, skipping.")
        
    conn = init_db(OUTPUT_DB)
    try:
        build_db(conn)
    finally:
        conn.close()
        
    print(f"\nDeleting {GZ_FILE} to free space...")
    GZ_FILE.unlink()
    print("All done!")
