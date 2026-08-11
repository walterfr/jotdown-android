import urllib.request
import json
url = 'https://gitlab.com/api/v4/projects/36528/merge_requests/40890/notes'
req = urllib.request.Request(url, headers={'User-Agent': 'Mozilla/5.0'})
try:
    resp = urllib.request.urlopen(req)
    data = json.loads(resp.read().decode())
    for note in data:
        if str(note.get('id')) == '3663472199':
            print(f"FOUND 3663472199: {note.get('author', {}).get('username')}: {note.get('body')}")
            break
        if note.get('author', {}).get('username') == 'linsui' and not note.get('system'):
            print(f"LINSUI: {note.get('body')}")
except Exception as e:
    print("Error:", e)