import urllib.request
import re
from html.parser import HTMLParser

url = 'https://gitlab.com/fdroid/fdroiddata/-/merge_requests/40890'
req = urllib.request.Request(url, headers={'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36'})
resp = urllib.request.urlopen(req)
html = resp.read().decode('utf-8', errors='ignore')

# find the block with the note id
pos = html.find('3663472199')
if pos != -1:
    start = max(0, pos - 500)
    end = min(len(html), pos + 1000)
    print("Context around note:")
    print(html[start:end])
else:
    print("Note ID not found in HTML")