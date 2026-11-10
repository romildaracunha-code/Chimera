import importlib.util
import json
import tempfile
import unittest
import zipfile
from pathlib import Path

spec=importlib.util.spec_from_file_location('verify',Path(__file__).parents[1]/'verify_bundle.py')
v=importlib.util.module_from_spec(spec);spec.loader.exec_module(v)

class BundleTests(unittest.TestCase):
    def fixture(self):
        s=dict(schemaVersion=3,cases=[],analyses=[],runs=[],artifacts=[],journal=[])
        e=dict(sequence=1,timestamp=1,action='SYNTHETIC_SOFTWARE_FIXTURE_NOT_PHYSICAL_EVIDENCE',entityId='fixture',previousHash='GENESIS',stateDigest=v.digest(v.canonical({k:s[k] for k in v.COLLECTIONS}).encode()))
        e['hash']=v.digest(v.canonical(e).encode());s['journal'].append(e)
        return s
    def test_hash_consistency_never_means_physical_authenticity(self):
        r=v.verify_manifest(self.fixture());self.assertEqual('CONSISTENT',r['integrity']);self.assertFalse(r['automaticVulnerabilityOrFixClaim']);self.assertEqual('UNVERIFIED_BY_OFFLINE_TOOL',r['physicalAuthenticity'])
    def test_state_and_journal_tampering(self):
        s=self.fixture();s['analyses'].append({'fake':'data'});self.assertEqual('INVALID_OR_INCOMPLETE',v.verify_manifest(s)['integrity'])
        s=self.fixture();s['journal'][0]['action']='CHANGED';self.assertEqual('INVALID_OR_INCOMPLETE',v.verify_manifest(s)['integrity'])
    def test_portable_unicode_canonicalization(self):
        self.assertEqual('{"\\u00e9":"\\ud83d\\udd2c\\n"}',v.canonical({'é':'🔬\n'}))
    def test_zip_reads_without_extracting_and_rejects_changed_artifact(self):
        with tempfile.TemporaryDirectory() as p:
            s=self.fixture();aid='00000000-0000-0000-0000-000000000000';raw=b'SOFTWARE FIXTURE NOT HARDWARE'
            s['artifacts']=[dict(id=aid,runId='fixture',size=len(raw),sha256=v.digest(raw))]
            e=s['journal'][0];e.pop('hash');e['stateDigest']=v.digest(v.canonical({k:s[k] for k in v.COLLECTIONS}).encode());e['hash']=v.digest(v.canonical(e).encode())
            path=Path(p)/'fixture.zip'
            with zipfile.ZipFile(path,'w') as z:z.writestr('manifest.json',json.dumps(s));z.writestr('artifacts/'+aid+'.bin',raw)
            self.assertEqual('CONSISTENT',v.load_and_verify(path)[1]['integrity'])
            with zipfile.ZipFile(path,'w') as z:z.writestr('manifest.json',json.dumps(s));z.writestr('artifacts/'+aid+'.bin',b'BAD')
            self.assertEqual('INVALID_OR_INCOMPLETE',v.load_and_verify(path)[1]['integrity'])
    def test_duplicate_zip_member_rejected(self):
        with tempfile.TemporaryDirectory() as p:
            path=Path(p)/'fixture.zip'
            with zipfile.ZipFile(path,'w') as z:z.writestr('manifest.json',json.dumps(self.fixture()));z.writestr('manifest.json','{}')
            self.assertRaises(ValueError,v.load_and_verify,path)

if __name__=='__main__':unittest.main()
