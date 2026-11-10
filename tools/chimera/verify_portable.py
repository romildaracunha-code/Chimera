#!/usr/bin/env python3
"""Independently authenticate a portable backup without extracting any credential/data files.
Requires cryptography (Apache-2.0/BSD); password requested interactively, never on argv.
"""
import argparse
import base64
import getpass
import hashlib
import json
import re
from pathlib import Path
from cryptography.hazmat.primitives.ciphers.aead import AESGCM
from verify_bundle import verify_manifest


def inspect_backup(data, password):
    if not 60 <= len(data) <= 100 * 1024 * 1024 or data[:8] != b'NFCPBK01' or data[24:32] != b'NFCLAB01':
        raise ValueError('Invalid envelope/size')
    key = hashlib.pbkdf2_hmac('sha256', password.encode('utf-8'), data[8:24], 310000, dklen=32)
    plain = AESGCM(key).decrypt(data[32:44], data[44:], b'portable-backup-v1')
    root = json.loads(plain)
    if root['format'] != 'NFCLAB_PORTABLE_1' or not 0 < len(root['records']) <= 10000:
        raise ValueError('Invalid portable schema')
    records = {}
    allowed = {'legacy/sessions', 'legacy/devices', 'legacy/experiments', 'settings/lab', 'settings/retention', 'chimera/workspace-v3.json'}
    total = 0
    for row in root['records']:
        key = row['key']
        if key in records or not (key in allowed or re.fullmatch(r'chimera/[a-f0-9-]{36}\.bin|draft/[a-zA-Z0-9_-]{1,64}|session/[0-9]{20}/[A-F0-9]{64}|session-id/[A-F0-9]{64}', key)):
            raise ValueError('Duplicate/unknown record')
        value = base64.b64decode(row['data'], validate=True)
        total += len(value)
        if len(value) > 20 * 1024 * 1024 or total > 70 * 1024 * 1024 or hashlib.sha256(value).hexdigest().upper() != row['sha256']:
            raise ValueError('Record size/hash mismatch')
        records[key] = value
    if 'settings/retention' in records:
        policy = json.loads(records['settings/retention'])
        if type(policy.get('enabled')) is not bool or type(policy.get('days')) is not int or not 1 <= policy['days'] <= 3650:
            raise ValueError('Invalid retention policy')
    for name, value in records.items():
        if name.startswith('session/'):
            session = json.loads(value)
            identity = hashlib.sha256(session['id'].encode()).hexdigest().upper()
            expected = 'session/' + str(session['date']).zfill(20) + '/' + identity
            if name != expected or records.get('session-id/' + identity) != name.encode():
                raise ValueError('Invalid session/index binding')
        elif name.startswith('session-id/'):
            target = value.decode()
            if not target.startswith('session/') or target not in records:
                raise ValueError('Missing session for index')
            identity = hashlib.sha256(json.loads(records[target])['id'].encode()).hexdigest().upper()
            if name != 'session-id/' + identity:
                raise ValueError('Index identity mismatch')
    if 'chimera/workspace-v3.json' in records:
        report = verify_manifest(json.loads(records['chimera/workspace-v3.json']), lambda aid, size: records['chimera/' + aid + '.bin'])
        if report['integrity'] != 'CONSISTENT':
            raise ValueError('Ledger/artifact verification failed')
    return {'backupAuthentication': 'VALID', 'recordCount': len(records), 'plaintextBytes': total,
            'physicalResults': 'NOT_VERIFIED', 'scope': 'Password authentication, schema, record hashes and journal; no extraction performed'}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('backup', type=Path)
    parser.add_argument('--software-fixture', action='store_true', help='Use the published test-fixture password only')
    args = parser.parse_args()
    try:
        if args.backup.stat().st_size > 100 * 1024 * 1024:
            raise ValueError('Backup too large')
        password = 'SOFTWARE-FIXTURE-NOT-A-SECRET' if args.software_fixture else getpass.getpass('Backup password: ')
        print(json.dumps(inspect_backup(args.backup.read_bytes(), password), indent=2))
    except Exception as exc:
        print(json.dumps({'backupAuthentication': 'FAILED', 'errorType': type(exc).__name__}))
        return 1
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
