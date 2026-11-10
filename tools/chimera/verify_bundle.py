#!/usr/bin/env python3
"""Offline verification of a Chimera manifest/ZIP. Hash integrity is not physical authenticity."""
import argparse
import hashlib
import json
import re
import sys
import zipfile
from pathlib import Path

COLLECTIONS = ('cases', 'analyses', 'runs', 'artifacts')
LIMIT = 16 * 1024 * 1024


def canonical(value):
    return json.dumps(value, sort_keys=True, separators=(',', ':'), ensure_ascii=True, allow_nan=False)


def digest(value):
    return hashlib.sha256(value).hexdigest().upper()


def verify_manifest(state, read_artifact=None):
    errors = []
    try:
        if state.get('schemaVersion') != 3:
            raise ValueError('Unsupported schema')
        journal = state['journal']
        if not journal or len(journal) > 4096:
            raise ValueError('Invalid journal size')
        previous = 'GENESIS'
        for index, event in enumerate(journal):
            content = dict(event)
            claimed = content.pop('hash')
            if content['sequence'] != index + 1 or content['previousHash'] != previous or digest(canonical(content).encode()) != claimed:
                raise ValueError('Journal mismatch at sequence ' + str(index + 1))
            previous = claimed
        data = {name: state[name] for name in COLLECTIONS}
        if journal[-1]['stateDigest'] != digest(canonical(data).encode()):
            raise ValueError('Current state digest mismatch')
        artifacts = {a['id']: a for a in state['artifacts']}
        if len(artifacts) != len(state['artifacts']):
            raise ValueError('Duplicate artifact IDs')
        total = 0
        for aid, artifact in artifacts.items():
            if not re.fullmatch(r'[a-f0-9-]{36}', aid) or not 0 < artifact['size'] <= 8 * 1024 * 1024:
                raise ValueError('Invalid artifact identity or size')
            total += artifact['size']
            if total > 64 * 1024 * 1024:
                raise ValueError('Artifact quota exceeded')
            if read_artifact is None:
                errors.append('Artifact bytes unavailable: ' + aid)
            else:
                data = read_artifact(aid, artifact['size'])
                if len(data) != artifact['size'] or digest(data) != artifact['sha256']:
                    errors.append('Missing/changed artifact bytes: ' + aid)
        for run in state['runs']:
            for aid in run['artifacts']:
                if aid not in artifacts or artifacts[aid]['runId'] != run['id']:
                    errors.append('Unbound artifact: ' + str(aid))
    except (KeyError, TypeError, ValueError, RecursionError, OSError, zipfile.BadZipFile) as exc:
        errors.append(str(exc))
    return {
        'integrity': 'CONSISTENT' if not errors else 'INVALID_OR_INCOMPLETE',
        'physicalAuthenticity': 'UNVERIFIED_BY_OFFLINE_TOOL',
        'cryptography': 'NOT_VERIFIED',
        'automaticVulnerabilityOrFixClaim': False,
        'runCount': len(state.get('runs', [])),
        'analysisCount': len(state.get('analyses', [])),
        'errors': errors,
        'scope': 'Journal/state/bytes only. Stored reviews/results remain operator declarations, not independent hardware proof.'
    }


def load_and_verify(path):
    path = Path(path)
    if zipfile.is_zipfile(path):
        with zipfile.ZipFile(path) as archive:
            if len(archive.infolist()) > 2048 or archive.getinfo('manifest.json').file_size > LIMIT:
                raise ValueError('Archive limits exceeded')
            if len(archive.namelist()) != len(set(archive.namelist())):
                raise ValueError('Duplicate ZIP members')
            state = json.loads(archive.read('manifest.json'))
            def read(aid, expected):
                name = 'artifacts/' + aid + '.bin'
                if name not in archive.namelist() or archive.getinfo(name).file_size != expected:
                    return b''
                return archive.read(name)
            return state, verify_manifest(state, read)
    if path.stat().st_size > LIMIT:
        raise ValueError('Manifest exceeds size limit')
    state = json.loads(path.read_text(encoding='utf-8'))
    return state, verify_manifest(state)


def markdown(state, result):
    def cell(value):
        return str(value).replace('|', '/').replace('\n', ' ')
    lines = ['# Operação Chimera — verificação offline', '',
             'Integridade: ' + result['integrity'],
             'Autenticidade física: UNVERIFIED_BY_OFFLINE_TOOL. Não é aprovação EMV ou financeira.', '',
             '| Run | Terminal | Firmware | Caso | Equipe | Etapa declarada | Resultado declarado |',
             '| --- | --- | --- | --- | --- | --- | --- |']
    for run in state.get('runs', []):
        device, case = run.get('terminalSnapshot', {}), run.get('caseSnapshot', {})
        fields = [run.get('id'), device.get('model'), device.get('firmware'), case.get('id'),
                  run.get('team'), run.get('claimedStage'), run.get('outcome')]
        lines.append('| ' + ' | '.join(map(cell, fields)) + ' |')
    lines += ['', '## Limitações', '', result['scope']] + ['- ' + e for e in result['errors']]
    return '\n'.join(lines) + '\n'


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('input')
    parser.add_argument('--report')
    args = parser.parse_args()
    try:
        state, result = load_and_verify(args.input)
        if args.report:
            Path(args.report).write_text(markdown(state, result), encoding='utf-8')
        print(json.dumps(result, ensure_ascii=False, indent=2))
        return 0 if result['integrity'] == 'CONSISTENT' else 2
    except (ValueError, KeyError, OSError, zipfile.BadZipFile) as exc:
        print('Blocked: ' + str(exc), file=sys.stderr)
        return 2


if __name__ == '__main__':
    sys.exit(main())
