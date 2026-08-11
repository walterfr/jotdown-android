import urllib.request
url = 'https://gitlab.com/walterfr1/fdroiddata/-/jobs/15795740124/raw'
req = urllib.request.Request(url, headers={'User-Agent': 'Mozilla/5.0'})
try:
    resp = urllib.request.urlopen(req)
    log = resp.read().decode('utf-8')
    lines = log.split('\n')
    print("\n".join(lines[-30:]))
except Exception as e:
    print("Error:", e)