#!/usr/bin/env python3
"""Obtain a genuine client-credentials JWT. No third-party Python packages needed."""
import argparse, base64, json, os, urllib.request, urllib.parse
from pathlib import Path

def token(client='lab-client', secret=None, scope=None):
    secret = secret or {'lab-client': os.getenv('LAB_CLIENT_SECRET','lab-secret'),
                        'viewer': os.getenv('VIEWER_CLIENT_SECRET','viewer-secret'),
                        'order-service': os.getenv('ORDER_CLIENT_SECRET','order-secret')}[client]
    form = {'grant_type': 'client_credentials'}
    if scope: form['scope'] = scope
    basic = base64.b64encode((client+':'+secret).encode()).decode()
    request = urllib.request.Request(os.getenv('AUTH_URL','http://localhost:9000')+'/oauth2/token',
        data=urllib.parse.urlencode(form).encode(), headers={'Authorization':'Basic '+basic,
        'Content-Type':'application/x-www-form-urlencoded'})
    with urllib.request.urlopen(request,timeout=5) as response:
        return json.load(response)['access_token']

if __name__ == '__main__':
    p=argparse.ArgumentParser();p.add_argument('--client',default='lab-client',choices=['lab-client','viewer','order-service'])
    p.add_argument('--scope');p.add_argument('--file');args=p.parse_args()
    value=token(args.client,scope=args.scope)
    if args.file:
        Path(args.file).write_text(value+'\n');print('Token written; renew it after 30 minutes.')
    else: print(value)
