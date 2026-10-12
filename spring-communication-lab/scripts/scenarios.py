#!/usr/bin/env python3
"""Executable scenarios against the running lab. See test.md for expected observations."""
import argparse, concurrent.futures, json, os, subprocess, time, urllib.request, urllib.error, uuid
from pathlib import Path
from get_token import token
ROOT=Path(__file__).resolve().parents[1]
ORDER=os.getenv('ORDER_URL','http://localhost:8081')
PAYMENT=os.getenv('PAYMENT_URL','http://localhost:8082')
INVENTORY=os.getenv('INVENTORY_URL','http://localhost:8083')
AUTH=os.getenv('AUTH_URL','http://localhost:9000')
ZIPKIN=os.getenv('ZIPKIN_URL','http://localhost:9411')
passed=[]

def http(base,path,method='GET',body=None,bearer=None,headers=None,expected=200):
    h={'Accept':'application/json',**(headers or {})}
    if bearer: h['Authorization']='Bearer '+bearer
    data=None
    if body is not None: h['Content-Type']='application/json';data=json.dumps(body).encode()
    req=urllib.request.Request(base+path,data=data,headers=h,method=method)
    try: res=urllib.request.urlopen(req,timeout=15)
    except urllib.error.HTTPError as error: res=error
    with res:
        raw=res.read(); status=res.status; hs=dict(res.headers)
    try: value=json.loads(raw) if raw else None
    except json.JSONDecodeError: value=raw.decode()
    allowed={expected} if isinstance(expected,int) else set(expected)
    assert status in allowed, f'{method} {path}: expected {allowed}, got {status}: {value}'
    return value,hs

def check(name): passed.append(name);print('PASS',name,flush=True)
def wait_until(fn,seconds=40):
    end=time.monotonic()+seconds;last=None
    while time.monotonic()<end:
        try:
            result=fn()
            if result:return result
        except (AssertionError,OSError) as e:last=e
        time.sleep(.4)
    raise AssertionError(f'Timed out after {seconds}s: {last}')

def run(args):
    for base,path in [(AUTH,'/actuator/health'),(ORDER,'/actuator/health/readiness'),(PAYMENT,'/actuator/health/readiness'),(INVENTORY,'/actuator/health/readiness')]:
        wait_until(lambda: http(base,path)[0].get('status')=='UP',180)
    jwt=token();viewer=token('viewer');check('S01 startup and genuine OAuth client credentials')
    def get_order(oid):return http(ORDER,'/api/v1/orders/'+oid,bearer=jwt)[0]
    def new_order(note='scenario'):
        return http(ORDER,'/api/v1/orders','POST',{'sku':'BOOK-1','quantity':1,'note':note},jwt,expected=201)[0]
    def checkout(oid,expected=(200,202),headers=None):
        return http(ORDER,f'/api/v1/orders/{oid}/checkout','POST',bearer=jwt,headers=headers,expected=expected)
    def paid(oid):return wait_until(lambda:(o if (o:=get_order(oid))['status']=='PAID' else None),60)
    def fault(mode='none',remaining=0,delay=2000):
        return http(PAYMENT,'/api/lab/faults','POST',{'mode':mode,'remaining':remaining,'delayMs':delay},jwt)[0]
    def calls():return http(PAYMENT,'/api/lab/faults',bearer=jwt)[0]['calls']
    def reset():http(ORDER,'/api/lab/circuit/reset','POST',bearer=jwt)
    fault();reset()
    try:
        http(ORDER,'/api/v1/orders',expected=401)
        http(ORDER,'/api/v1/orders',bearer='invalid.jwt.value',expected=401)
        http(ORDER,'/api/v1/orders',bearer=viewer)
        http(ORDER,'/api/v1/orders','POST',{'sku':'BOOK-1','quantity':1,'note':''},viewer,expected=403)
        check('S02 missing/invalid token 401 and insufficient scope 403')
        http(ORDER,'/api/v1/orders','POST',{'sku':'BOOK-1','quantity':0,'note':''},jwt,expected=400)
        http(ORDER,'/api/v1/orders/not-a-uuid',bearer=jwt,expected=400)
        http(ORDER,'/api/v1/orders/'+str(uuid.uuid4()),bearer=jwt,expected=404)
        http(ORDER,'/api/v1/orders?size=0',bearer=jwt,expected=400)
        http(ORDER,'/api/v1/orders?sort=unsafe',bearer=jwt,expected=400)
        check('S03 validation, malformed paths, missing resources and query allowlist')
        o=new_order();oid=o['id'];assert o['amount']==500
        changed,_=http(ORDER,'/api/v1/orders/'+oid,'PUT',{'sku':'BOOK-1','quantity':2,'note':'replace'},jwt);assert changed['amount']==1000
        changed,_=http(ORDER,'/api/v1/orders/'+oid,'PATCH',{'note':'patched'},jwt);assert changed['note']=='patched'
        rows,_=http(ORDER,'/api/v1/orders?status=DRAFT&page=0&size=2&sort=amount',bearer=jwt);assert len(rows['items'])<=2
        http(ORDER,'/api/v1/orders/'+oid,'DELETE',bearer=jwt,expected=204)
        http(ORDER,'/api/v1/orders/'+oid,bearer=jwt,expected=404);check('S04 POST GET PUT PATCH DELETE, pricing and pagination')
        for kind in ['restclient','webclient','feign','resttemplate']:
            body,_=http(ORDER,'/api/lab/clients/'+kind,bearer=jwt,headers={'X-Correlation-ID':'client-'+kind})
            assert body['correlationId']=='client-'+kind
        http(ORDER,'/api/lab/forward-token',bearer=jwt);check('S05 four clients, custom headers and token forwarding')
        baseline=http(INVENTORY,'/api/v1/inventory/BOOK-1/sales',bearer=jwt)[0]['soldUnits']
        previous={e['id'] for e in http(PAYMENT,'/api/lab/outbox',bearer=jwt)[0]}
        o=new_order();oid=o['id'];_,trace_headers=checkout(oid,headers={'X-Correlation-ID':'trace-'+oid});paid(oid)
        wait_until(lambda:http(INVENTORY,'/api/v1/inventory/BOOK-1/sales',bearer=jwt)[0]['soldUnits']==baseline+1)
        http(ORDER,'/api/v1/orders/'+oid,'PATCH',{'note':'too late'},jwt,expected=409)
        http(ORDER,'/api/v1/orders/'+oid,'DELETE',bearer=jwt,expected=409)
        checkout(oid,200);check('S06 HTTP payment then asynchronous PAID and sales receipt')
        req={'orderId':oid,'sku':o['sku'],'quantity':o['quantity'],'amount':o['amount'],'currency':o['currency']}
        p,headers=http(PAYMENT,'/api/v1/payments','POST',req,jwt,{'Idempotency-Key':'order-'+oid},200)
        assert headers.get('Idempotency-Replayed')=='true'
        assert p['id']==get_order(oid)['paymentId']
        http(PAYMENT,'/api/v1/payments','POST',{**req,'amount':501},jwt,{'Idempotency-Key':'order-'+oid},409)
        http(PAYMENT,'/api/v1/payments','POST',req,jwt,{'Idempotency-Key':'different-'+oid},409)
        check('S07 same key replays, changed body conflicts, second key cannot re-charge order')
        events=http(PAYMENT,'/api/lab/outbox',bearer=jwt)[0]
        event=next(e for e in events if e['id'] not in previous)
        http(PAYMENT,'/api/lab/outbox/'+event['id']+'/replay','POST',bearer=jwt)
        wait_until(lambda:any(e['id']==event['id'] and e['sent'] and e['attempts']>=2 for e in http(PAYMENT,'/api/lab/outbox',bearer=jwt)[0]))
        time.sleep(1)
        assert http(INVENTORY,'/api/v1/inventory/BOOK-1/sales',bearer=jwt)[0]['soldUnits']==baseline+1
        check('S08 duplicate event does not duplicate sales or alter payment')
        baseline+=1
        concurrent_order=new_order()['id']
        with concurrent.futures.ThreadPoolExecutor(max_workers=6) as pool:
            list(pool.map(lambda _:checkout(concurrent_order),range(6)))
        paid(concurrent_order)
        wait_until(lambda:http(INVENTORY,'/api/v1/inventory/BOOK-1/sales',bearer=jwt)[0]['soldUnits']==baseline+1)
        check('S09 six concurrent checkouts produce one sales receipt')
        reset();fault('503',2);o=new_order();checkout(o['id']);paid(o['id']);assert calls()==3
        check('S10 retry two transient 503 responses with exponential backoff')
        for mode,expected in [('400',502),('500',502),('429',503)]:
            reset();fault(mode,-1);o=new_order();_,h=checkout(o['id'],expected);assert calls()==1
            if mode=='429':assert h.get('Retry-After')=='2'
            fault();checkout(o['id']);paid(o['id'])
        check('S11 400/500/429 are not blindly retried; Retry-After is propagated')
        reset();fault('slow',-1,2000);o=new_order();started=time.monotonic();checkout(o['id'],503)
        assert time.monotonic()-started<8;assert calls()==3
        # These deliberately slow requests may still finish and commit after our HTTP client gave up.
        fault();checkout(o['id']);paid(o['id'])
        check('S12 finite response timeout and recovery with the same order/key')
        reset();fault('after-commit',1,2000);o=new_order();checkout(o['id']);paid(o['id']);assert calls()==2
        check('S13 payment commits before timeout; retry returns the original payment')
        reset();fault('503',-1)
        for _ in range(4):checkout(new_order()['id'],503)
        assert http(ORDER,'/api/lab/circuit',bearer=jwt)[0]['state']=='OPEN'
        before=calls();probe=new_order()['id'];body,_=checkout(probe,503);assert body['code']=='CIRCUIT_OPEN' and calls()==before
        fault();time.sleep(5.2);checkout(probe);paid(probe)
        assert http(ORDER,'/api/lab/circuit',bearer=jwt)[0]['state']=='CLOSED'
        check('S14 CLOSED -> OPEN -> HALF_OPEN probe -> CLOSED')
        v2,_=http(ORDER,'/api/v2/orders/'+oid,bearer=jwt)
        assert v2['total']=={'value':500,'currency':'INR'} and 'amount' not in v2
        metrics,_=http(ORDER,'/actuator/metrics/http.server.requests',bearer=jwt)
        assert metrics['name']=='http.server.requests'
        http(ORDER,'/actuator/metrics/http.client.requests',bearer=jwt)
        http(ORDER,'/v3/api-docs');http(PAYMENT,'/v3/api-docs');http(ORDER,'/actuator/metrics/lab.payment.timeouts',bearer=jwt);check('S15 v1/v2, HTTP/timeout metrics and generated OpenAPI')
        if args.traces:
            trace_id=next((v for k,v in trace_headers.items() if k.lower()=='x-trace-id'),None)
            assert trace_id,'No X-Trace-ID returned: check observation filter and tracing config'
            def connected_trace():
                spans=http(ZIPKIN,'/api/v2/trace/'+trace_id)[0]
                names={s.get('localEndpoint',{}).get('serviceName') for s in spans}
                return {'order-service','payment-service','inventory-service'}<=names
            wait_until(connected_trace,45);check('S16 same trace spans HTTP, outbox and Kafka consumers')
        if args.lb:
            instances={http(ORDER,'/api/lab/clients/feign',bearer=jwt)[0]['instance'] for _ in range(12)}
            assert {'payment-1','payment-2','payment-3'}<=instances,instances;check('S17 Feign distributes calls across three payment instances')
        if args.disrupt:
            def compose(*command):subprocess.run(['docker','compose','-f','compose.yml',*command],cwd=ROOT,check=True)
            try:
                compose('stop','kafka');time.sleep(2);o=new_order();checkout(o['id']);assert get_order(o['id'])['status']=='PENDING'
                assert any(not e['sent'] for e in http(PAYMENT,'/api/lab/outbox',bearer=jwt)[0])
            finally:compose('start','kafka')
            paid(o['id']);check('S18 broker outage retains outbox; recovery delivers event')
    finally:
        fault();reset()
    print(f'\n{len(passed)} scenario groups passed. Optional groups run only when their flags are supplied.')

if __name__=='__main__':
    p=argparse.ArgumentParser();p.add_argument('--traces',action='store_true');p.add_argument('--lb',action='store_true');p.add_argument('--disrupt',action='store_true',help='Temporarily stop and restart this lab Kafka container')
    run(p.parse_args())
