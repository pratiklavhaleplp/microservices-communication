#!/usr/bin/env python3
"""Publish invalid JSON/schema to Kafka using the Compose broker's bundled CLI; no pip packages."""
import argparse, datetime, json, subprocess, uuid
from pathlib import Path
ROOT = Path(__file__).resolve().parents[1]

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--malformed', action='store_true', help='Send malformed JSON instead of schema version 99')
    args = parser.parse_args()
    key = str(uuid.uuid4())
    event = {'eventId': str(uuid.uuid4()), 'schemaVersion': 99, 'paymentId': str(uuid.uuid4()),
             'orderId': key, 'sku': 'BOOK-1', 'quantity': 1, 'amount': 500, 'currency': 'INR',
             'correlationId': 'poison-lab', 'occurredAt': datetime.datetime.now(datetime.timezone.utc).isoformat()}
    payload = '{not-json' if args.malformed else json.dumps(event)
    command = ['docker', 'compose', '-f', 'compose.yml', 'exec', '-T', 'kafka',
               '/opt/kafka/bin/kafka-console-producer.sh', '--bootstrap-server', 'kafka:29092',
               '--topic', 'payment.captured.v1', '--property', 'parse.key=true',
               '--property', 'key.separator=|', '--producer-property', 'acks=all']
    subprocess.run(command, cwd=ROOT, input=key+'|'+payload+'\n', text=True, check=True, timeout=45)
    print('Published poison event with key:', key)
    print('Expect the same key/payload in BOTH payment.captured.v1.order-service.DLT and')
    print('payment.captured.v1.inventory-service.DLT. See test.md M01 for read commands.')

if __name__ == '__main__':
    main()
