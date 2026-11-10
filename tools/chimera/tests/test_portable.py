import importlib.util
import json
import sys
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))
from verify_portable import inspect_backup
from cryptography.hazmat.primitives.ciphers.aead import AESGCM
import hashlib
import base64


class PortableTests(unittest.TestCase):
    password = 'fixture-password-not-secret'

    def envelope(self, records):
        salt = bytes(range(16))
        nonce = bytes(range(12))
        key = hashlib.pbkdf2_hmac('sha256', self.password.encode(), salt, 310000, dklen=32)
        rows = [{'key': k, 'data': base64.b64encode(v).decode(), 'sha256': hashlib.sha256(v).hexdigest().upper()} for k, v in records]
        plain = json.dumps({'format': 'NFCLAB_PORTABLE_1', 'records': rows}).encode()
        return b'NFCPBK01' + salt + b'NFCLAB01' + nonce + AESGCM(key).encrypt(nonce, plain, b'portable-backup-v1')

    def test_verifies_without_claiming_physical_execution(self):
        report = inspect_backup(self.envelope([('legacy/sessions', b'[]')]), self.password)
        self.assertEqual('VALID', report['backupAuthentication'])
        self.assertEqual('NOT_VERIFIED', report['physicalResults'])

    def test_wrong_password_and_tampering_rejected(self):
        data = self.envelope([('legacy/sessions', b'[]')])
        with self.assertRaises(Exception):
            inspect_backup(data, 'incorrect password')
        with self.assertRaises(Exception):
            inspect_backup(data[:-1] + bytes([data[-1] ^ 1]), self.password)

    def test_unknown_and_duplicate_records_rejected(self):
        for records in [[('../escape', b'')], [('draft/a', b'one'), ('draft/a', b'two')]]:
            with self.assertRaises(ValueError):
                inspect_backup(self.envelope(records), self.password)

    def test_truncated_envelope_rejected(self):
        with self.assertRaises(ValueError):
            inspect_backup(b'NFCPBK01', self.password)
