import importlib.util
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
from types import SimpleNamespace
import json

spec=importlib.util.spec_from_file_location('labctl',Path(__file__).parents[1]/'labctl.py')
lab=importlib.util.module_from_spec(spec);spec.loader.exec_module(lab)

class EvidenceTests(unittest.TestCase):
    def record(self):
        return dict(terminalId='TEST FIXTURE',operator='UNIT TEST',phoneReference='FIXTURE',environmentReference='AUTOMATED TEST',terminalSnapshot={'manufacturer':'TEST FIXTURE','model':'NO REAL HARDWARE'},environment='LAB_PROTOCOL',credential='NFC_LAB_FICTITIOUS',physicalExecution='NOT_EXECUTED',
                    nfc='UNKNOWN',emv='NOT_APPLICABLE',authorization='NOT_REQUESTED',settlement='NOT_OBSERVED',rejection='UNKNOWN',evidence=[])
    def test_empty_plan_never_proves_physical_execution(self):
        with tempfile.TemporaryDirectory() as p:
            result=lab.assess(p,self.record())
            self.assertFalse(result['physicallyVerified']);self.assertEqual('INCOMPLETE_OR_NOT_EXECUTED',result['status'])
    def test_hash_validation_and_tampering(self):
        with tempfile.TemporaryDirectory() as p:
            f=Path(p)/'fixture.txt';f.write_text('SYNTHETIC UNIT FIXTURE NOT PHYSICAL EVIDENCE')
            r=self.record();r['physicalExecution']='OPERATOR_REPORTED_PHYSICAL'
            r['evidence']=[dict(path='fixture.txt',sha256=lab.sha256(f),kind='PHYSICAL_CONTEXT')]
            self.assertEqual('ARTIFACTS_PRESENT_MANUAL_REVIEW_REQUIRED',lab.assess(p,r)['status'])
            self.assertFalse(lab.assess(p,r)['physicallyVerified'])
            f.write_text('changed');self.assertTrue(lab.assess(p,r)['issues'])
    def test_path_escape_rejected(self):
        with tempfile.TemporaryDirectory() as p:
            self.assertRaises(ValueError,lab.under,p,'../outside')
    def test_lab_cannot_claim_emv_or_authorization(self):
        with tempfile.TemporaryDirectory() as p:
            r=self.record();r['authorization']='APPROVED';r['emv']='RECOGNIZED';r['settlement']='DOCUMENTED'
            issues=lab.assess(p,r)['issues']
            self.assertTrue(any('Lab protocol' in i for i in issues));self.assertTrue(any('host' in i for i in issues));self.assertTrue(any('Settlement' in i for i in issues))
    def test_official_negative_must_be_homologation(self):
        with tempfile.TemporaryDirectory() as p:
            r=self.record();r['environment']='PRODUCTION_OBSERVATION';r['credential']='OFFICIAL_NEGATIVE'
            self.assertTrue(any('Official case' in i for i in lab.assess(p,r)['issues']))
    def test_unknown_model_and_invalid_status_remain_incomplete(self):
        with tempfile.TemporaryDirectory() as p:
            r=self.record();r['terminalSnapshot']['model']='UNKNOWN';r['authorization']='UNSUPPORTED_VALUE'
            issues=lab.assess(p,r)['issues']
            self.assertTrue(any('model' in i for i in issues));self.assertTrue(any('authorization' in i for i in issues))
    def test_failed_device_query_is_unknown_not_absent_hce(self):
        with tempfile.TemporaryDirectory() as p:
            args=SimpleNamespace(adb='adb',serial='MISSING-UNIT-FIXTURE',reference='TEST',out=str(Path(p)/'report.json'))
            with patch.object(lab.subprocess,'run',return_value=SimpleNamespace(returncode=1,stdout='',stderr='device not found')):
                self.assertEqual(2,lab.doctor(args))
            result=json.loads(Path(args.out).read_text())
            self.assertIsNone(result['hceFeatureReported']);self.assertIsNone(result['nfcFeatureReported'])
            self.assertFalse(result['physicalHceConfirmed'])
    def test_latency_empty_and_nearest_rank(self):
        self.assertIsNone(lab.latency([])['p95Ms']);self.assertEqual(20,lab.latency(list(range(1,21)))['maxMs']);self.assertEqual(19,lab.latency(list(range(1,21)))['p95Ms'])

if __name__=='__main__':unittest.main()
