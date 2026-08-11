import urllib.request
import json
url = 'https://gitlab.com/fdroid/fdroiddata/-/merge_requests/40890/discussions.json'
req = urllib.request.Request(url, headers={'User-Agent': 'Mozilla/5.0'})
resp = urllib.request.urlopen(req)
data = json.loads(resp.read().decode())
for disc in data:
    for note in disc.get('notes', []):
        if '3663472199' in str(note.get('id')):
            print(f"FOUND NOTE: {note.get('author', {}).get('username')}: {note.get('note')}")
        if 'linsui' in str(note.get('author', {}).get('username')):
            print(f"LINSUI: {note.get('note')}")