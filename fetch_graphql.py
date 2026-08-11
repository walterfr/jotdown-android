import urllib.request
import json

query = '''
query {
  project(fullPath: "fdroid/fdroiddata") {
    mergeRequest(iid: "40890") {
      notes(first: 20, sort: CREATED_DESC) {
        nodes {
          id
          body
          author {
            username
          }
        }
      }
    }
  }
}
'''
url = 'https://gitlab.com/api/graphql'
req = urllib.request.Request(url, data=json.dumps({'query': query}).encode('utf-8'), headers={'Content-Type': 'application/json', 'User-Agent': 'Mozilla/5.0'})
try:
    resp = urllib.request.urlopen(req)
    data = json.loads(resp.read().decode('utf-8'))
    notes = data['data']['project']['mergeRequest']['notes']['nodes']
    for note in notes:
        print(f"[{note['id']}] {note['author']['username']}: {note['body']}")
except Exception as e:
    print("Error:", e)