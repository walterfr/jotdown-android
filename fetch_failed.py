import urllib.request
import json
url = 'https://gitlab.com/api/v4/projects/walterfr1%2Ffdroiddata/pipelines'
req = urllib.request.Request(url, headers={'User-Agent': 'Mozilla/5.0'})
resp = urllib.request.urlopen(req)
pipelines = json.loads(resp.read().decode('utf-8'))
pl_id = pipelines[0]['id']
url_jobs = f'https://gitlab.com/api/v4/projects/walterfr1%2Ffdroiddata/pipelines/{pl_id}/jobs'
req_jobs = urllib.request.Request(url_jobs, headers={'User-Agent': 'Mozilla/5.0'})
resp_jobs = urllib.request.urlopen(req_jobs)
jobs = json.loads(resp_jobs.read().decode('utf-8'))
for job in jobs:
    if job['name'] == 'fdroid rewritemeta' and job['status'] == 'failed':
        print(f"FAILED JOB ID: {job['id']}")