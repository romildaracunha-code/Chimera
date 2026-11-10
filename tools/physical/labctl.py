#!/usr/bin/env python3
"""Physical NFC Lab collection and evidence indexing. No EMV generation, mutation, replay or authorization."""
import argparse
import csv
import hashlib
import json
import math
import shutil
import statistics
import subprocess
import sys
import time
import uuid
from datetime import datetime, timezone
from pathlib import Path

SELECT = bytes.fromhex('00A4040008F04E46434C41420100')
GET_DATA = bytes.fromhex('00CA000000')


def now():
    return datetime.now(timezone.utc).isoformat()


def write_json(path, data):
    path = Path(path)
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open('x', encoding='utf-8') as stream:
        json.dump(data, stream, ensure_ascii=False, indent=2)
        stream.write('\n')


def sha256(path):
    h = hashlib.sha256()
    with Path(path).open('rb') as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b''):
            h.update(block)
    return h.hexdigest()


def under(root, relative):
    root = Path(root).resolve()
    candidate = (root / relative).resolve()
    try:
        candidate.relative_to(root)
    except ValueError as exc:
        raise ValueError('Evidence path escapes the experiment directory') from exc
    return candidate


def inventory(args):
    root = Path(args.out)
    root.mkdir(parents=True, exist_ok=True)
    rows = []
    for i in range(1, args.slots + 1):
        tid = f'T{i:03d}'
        row = dict(internalId=tid, inventoryStatus='UNIDENTIFIED_SLOT_NOT_CONFIRMED_DEVICE', manufacturer='UNKNOWN',
                   model='UNKNOWN', firmware='UNKNOWN', paymentAppVersion='UNKNOWN', contactlessKernelVersion='UNKNOWN',
                   acquirer='UNKNOWN', nfcDeclared='UNKNOWN', sdk='UNKNOWN', homologation='NOT_CONFIRMED',
                   officialToolVersion='UNKNOWN', officialCardKit='UNKNOWN', evidenceReference='', accessTicket='')
        rows.append(row)
        report = root / 'terminals' / (tid + '.md')
        report.parent.mkdir(exist_ok=True)
        with report.open('x', encoding='utf-8') as f:
            f.write(f'# {tid} — ficha presencial\n\n**NÃO EXECUTADO. Slot de inventário, não dispositivo identificado.**\n\n')
            f.write('\n'.join(f'- {k}: {v or "PENDENTE"}' for k, v in row.items()))
            f.write('\n\nExperimentos: nenhum. NFC, EMV, autorização e liquidação: desconhecidos.\n')
    with (root / 'inventory.csv').open('x', newline='', encoding='utf-8') as f:
        writer = csv.DictWriter(f, fieldnames=rows[0].keys())
        writer.writeheader()
        writer.writerows(rows)
    write_json(root / 'inventory.json', {'schemaVersion': 1, 'createdAt': now(), 'slotsNotDiscoveredHardware': rows})
    print(f'{len(rows)} slots não identificados; zero experimentos físicos executados')


def doctor(args):
    def run(*command):
        p = subprocess.run([args.adb, *command], capture_output=True, text=True, timeout=25)
        return {'exitCode': p.returncode, 'stdout': p.stdout.strip(), 'stderr': p.stderr.strip()}
    devices = run('devices', '-l')
    report = {'schemaVersion': 1, 'capturedAt': now(), 'adbDevices': devices,
              'attestation': 'ANDROID_REPORTED_ONLY_NOT_RF_VERIFICATION', 'physicalHceConfirmed': False}
    code = 0
    if args.serial:
        def device(*command):
            return run('-s', args.serial, 'shell', *command)
        features = device('pm', 'list', 'features')
        relevant = [s for s in features['stdout'].splitlines() if 'android.hardware.nfc' in s]
        report.update({'deviceReference': args.reference, 'featuresCommandExitCode': features['exitCode'],
                       'nfcFeatures': relevant, 'manufacturer': device('getprop', 'ro.product.manufacturer'),
                       'model': device('getprop', 'ro.product.model'), 'android': device('getprop', 'ro.build.version.release'),
                       'emulatorSerial': args.serial.startswith('emulator-')})
        report['hceFeatureReported'] = ('feature:android.hardware.nfc.hce' in relevant) if features['exitCode'] == 0 else None
        report['nfcFeatureReported'] = ('feature:android.hardware.nfc' in relevant) if features['exitCode'] == 0 else None
        report['status'] = 'FEATURES_REPORTED_NOT_RF_VERIFIED' if features['exitCode'] == 0 else 'DEVICE_QUERY_FAILED'
        code = 0 if features['exitCode'] == 0 else 2
    else:
        report['status'] = 'NO_DEVICE_SELECTED_OR_CONFIRMED'
        code = 2
    write_json(args.out, report)
    print(report.get('status', 'Feature report saved; execute controlled physical HCE test'))
    return code


def latency(values):
    if not values:
        return {'samples': 0, 'minMs': None, 'medianMs': None, 'p95Ms': None, 'maxMs': None}
    ordered = sorted(values)
    return dict(samples=len(values), minMs=ordered[0], medianMs=statistics.median(ordered),
                p95Ms=ordered[math.ceil(.95 * len(ordered)) - 1], maxMs=ordered[-1],
                p95Method='nearest rank; descriptive only, no certification threshold')


def pcsc(args):
    try:
        from smartcard.System import readers
    except ImportError:
        raise ValueError('pyscard ausente. Instale requirements-pcsc.txt e PC/SC/CCID do leitor autorizado.')
    available = readers()
    if args.list:
        print('\n'.join(str(r) for r in available) or 'NO_PCSC_READER')
        return 0 if available else 2
    if not args.reader or not args.out:
        raise ValueError('Escolha explicitamente --reader e --out; somente protocolo proprietário NFC Lab')
    match = [r for r in available if str(r) == args.reader]
    if len(match) != 1:
        raise ValueError('Leitor exato não encontrado; use pcsc --list')
    record = {'schemaVersion': 1, 'id': str(uuid.uuid4()), 'startedAt': now(), 'mode': 'PHYSICAL_PCSC_LAB',
              'reader': args.reader, 'credential': 'NFC_LAB_FICTITIOUS', 'environment': 'LAB_PROTOCOL',
              'aid': 'F04E46434C414201', 'emv': 'NOT_APPLICABLE', 'authorization': 'NOT_REQUESTED',
              'settlement': 'NOT_OBSERVED', 'capturePoint': 'PCSC_API_RESPONSE_NOT_ANALOG_RF_MEASUREMENT',
              'exchanges': [], 'errors': []}
    connection = match[0].createConnection()
    start_times = []
    try:
        connection.connect()
        record['atr'] = bytes(connection.getATR()).hex().upper()
        for attempt in range(args.attempts):
            for name, command in [('SELECT_LAB_AID', SELECT), ('GET_LAB_DATA', GET_DATA)]:
                timestamp, start = now(), time.perf_counter_ns()
                try:
                    data, sw1, sw2 = connection.transmit(list(command))
                    duration = time.perf_counter_ns() - start
                    response = bytes(data) + bytes([sw1, sw2])
                    start_times.append(duration / 1_000_000)
                    event = dict(attempt=attempt + 1, timestamp=timestamp, direction='PCSC_READER_TO_LAB_PHONE',
                                 command=command.hex().upper(), decoded=name, response=response.hex().upper(),
                                 status=f'{sw1:02X}{sw2:02X}', durationNanos=duration,
                                 durationMs=duration / 1_000_000, capturePoint='PCSC_RESPONSE_RETURNED')
                    record['exchanges'].append(event)
                    if (sw1, sw2) != (0x90, 0x00):
                        record['result'] = 'LAB_PROTOCOL_STATUS_ERROR_NOT_EMV_AUTHENTICATION'
                        return 2
                    if name == 'GET_LAB_DATA' and not bytes(data).startswith(b'NFC LAB | FICTITIOUS | '):
                        record['result'] = 'UNEXPECTED_LAB_PAYLOAD'
                        return 2
                except Exception as exc:
                    duration = time.perf_counter_ns() - start
                    record['exchanges'].append(dict(attempt=attempt + 1, timestamp=timestamp,
                        command=command.hex().upper(), response='', durationNanos=duration,
                        capturePoint='SEND_ATTEMPT_NO_RESPONSE', error=type(exc).__name__ + ': ' + str(exc)))
                    raise
        record['result'] = 'LAB_API_EXCHANGES_OBSERVED_REQUIRES_PHYSICAL_CONTEXT_EVIDENCE'
        return 0
    except Exception as exc:
        record['errors'].append(type(exc).__name__ + ': ' + str(exc))
        record['result'] = 'TRANSPORT_OR_SETUP_ERROR_NOT_EMV_AUTHENTICATION'
        return 2
    finally:
        try:
            connection.disconnect()
        except Exception:
            pass
        record['finishedAt'] = now()
        record['latency'] = latency(start_times)
        record['latencyByCommand'] = {name: latency([e['durationMs'] for e in record['exchanges'] if e.get('decoded') == name and 'durationMs' in e]) for name in ('SELECT_LAB_AID','GET_LAB_DATA')}
        record['attemptedExchanges'] = len(record['exchanges'])
        record['transportFailures'] = sum(bool(e.get('error')) for e in record['exchanges'])
        write_json(args.out, record)


def new(args):
    root = Path(args.out)
    root.mkdir(parents=True, exist_ok=True)
    if args.environment == 'LAB_PROTOCOL' and args.credential != 'NFC_LAB_FICTITIOUS':
        raise ValueError('LAB_PROTOCOL aceita apenas NFC_LAB_FICTITIOUS')
    if args.environment == 'OFFICIAL_HOMOLOGATION' and not args.credential.startswith('OFFICIAL_'):
        raise ValueError('Homologação exige credencial oficial de teste')
    if args.credential.startswith('OFFICIAL_') and (args.environment != 'OFFICIAL_HOMOLOGATION' or not args.case):
        raise ValueError('Caso oficial exige ambiente de homologação e --case do provedor')
    if args.credential == 'NFC_LAB_FICTITIOUS' and args.environment != 'LAB_PROTOCOL':
        raise ValueError('NFC Lab não é credencial EMV')
    terminal = dict(manufacturer='UNKNOWN',model='UNKNOWN',firmware='UNKNOWN',paymentAppVersion='UNKNOWN',contactlessKernelVersion='UNKNOWN',acquirer='UNKNOWN')
    if args.inventory:
        with Path(args.inventory).open(newline='',encoding='utf-8-sig') as f:
            matches = [r for r in csv.DictReader(f) if r['internalId'] == args.terminal]
        if len(matches) != 1:
            raise ValueError('ID deve corresponder a uma única linha do inventário')
        terminal.update({k:matches[0].get(k,'UNKNOWN') for k in terminal})
    write_json(root / 'experiment.json', dict(schemaVersion=1, id=str(uuid.uuid4()), createdAt=now(),
        terminalId=args.terminal, terminalSnapshot=terminal, phoneReference=args.phone, environment=args.environment, environmentReference=args.environment_ref,
        credential=args.credential, officialCaseId=args.case or '', operator=args.operator,
        nfc='UNKNOWN', emv='NOT_APPLICABLE' if args.environment == 'LAB_PROTOCOL' else 'UNKNOWN',
        authorization='NOT_REQUESTED' if args.environment == 'LAB_PROTOCOL' else 'UNKNOWN',
        settlement='NOT_OBSERVED', rejection='UNKNOWN', physicalExecution='NOT_EXECUTED', evidence=[], notes=''))
    print('Plano criado, sem resultados físicos. Atualize somente a partir de observações e evidências.')


def attach(args):
    root = Path(args.experiment)
    path = root / 'experiment.json'
    record = json.loads(path.read_text(encoding='utf-8'))
    source = Path(args.file)
    if not source.is_file() or source.stat().st_size == 0:
        raise ValueError('Evidência ausente ou vazia')
    collected = datetime.fromisoformat(args.collected_at.replace('Z', '+00:00'))
    if collected.tzinfo is None or collected.utcoffset() is None:
        raise ValueError('collected-at exige data/hora ISO8601 com fuso explícito')
    relative = Path('evidence') / (uuid.uuid4().hex + '_' + source.name)
    destination = under(root, relative)
    destination.parent.mkdir(parents=True, exist_ok=True)
    shutil.copyfile(source, destination)
    record['evidence'].append(dict(path=relative.as_posix(), sha256=sha256(destination), kind=args.kind,
        source=args.source, collectedAt=args.collected_at, registeredAt=now(), operator=args.operator,
        interpretation='UNREVIEWED_ARTIFACT_HASH_DOES_NOT_PROVE_PHYSICAL_AUTHENTICITY'))
    temp = root / 'experiment.tmp'
    temp.write_text(json.dumps(record, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    temp.replace(path)
    print(relative)


def assess(root, record):
    issues, artifacts = [], []
    allowed = {'environment': {'LAB_PROTOCOL','OFFICIAL_HOMOLOGATION','PRODUCTION_OBSERVATION'},
        'credential': {'NFC_LAB_FICTITIOUS','OFFICIAL_POSITIVE','OFFICIAL_NEGATIVE','LEGITIMATE'},
        'nfc': {'UNKNOWN','OBSERVED','NOT_OBSERVED','NOT_APPLICABLE'},
        'emv': {'UNKNOWN','RECOGNIZED','REJECTED','NOT_APPLICABLE'},
        'authorization': {'UNKNOWN','APPROVED','DECLINED','NOT_REQUESTED'},
        'settlement': {'UNKNOWN','NOT_OBSERVED','DOCUMENTED'},
        'rejection': {'UNKNOWN','TERMINAL_DOCUMENTED','PROCESSOR_DOCUMENTED','PROTOCOL_INCOMPATIBILITY_DOCUMENTED','NOT_APPLICABLE'}}
    for field, values in allowed.items():
        if record.get(field) not in values:
            issues.append('Invalid or missing field: ' + field)
    for field in ('terminalId','operator','environmentReference','phoneReference'):
        if not str(record.get(field,'')).strip():
            issues.append('Missing identity/context: ' + field)
    snapshot=record.get('terminalSnapshot',{})
    if any(snapshot.get(k,'UNKNOWN').strip().upper() in ('','UNKNOWN','DESCONHECIDO','DESCONHECIDA') for k in ('manufacturer','model')):
        issues.append('Terminal manufacturer/model not confirmed in experiment snapshot')
    for e in record.get('evidence', []):
        try:
            f = under(root, e['path'])
            if not f.is_file() or f.stat().st_size == 0 or sha256(f) != e.get('sha256'):
                issues.append('Missing/empty/changed evidence: ' + e['path'])
            else:
                artifacts.append(e['kind'])
        except (ValueError, KeyError) as exc:
            issues.append(str(exc))
    if record.get('physicalExecution') == 'NOT_EXECUTED':
        issues.append('Physical execution not performed')
    if record.get('physicalExecution') not in ('NOT_EXECUTED','OPERATOR_REPORTED_PHYSICAL'):
        issues.append('Unknown physicalExecution value; no automatic physical verification exists')
    if record.get('environment') == 'LAB_PROTOCOL':
        if record.get('credential') != 'NFC_LAB_FICTITIOUS' or record.get('emv') != 'NOT_APPLICABLE' or record.get('authorization') != 'NOT_REQUESTED' or record.get('settlement') != 'NOT_OBSERVED':
            issues.append('Lab protocol cannot establish EMV, authorization or settlement')
    if record.get('environment') == 'OFFICIAL_HOMOLOGATION' and not record.get('credential', '').startswith('OFFICIAL_'):
        issues.append('Homologation requires official test credentials')
    if record.get('credential', '').startswith('OFFICIAL_'):
        if record.get('environment') != 'OFFICIAL_HOMOLOGATION' or not record.get('officialCaseId'):
            issues.append('Official case requires documented homologation environment and case ID')
    if 'PHYSICAL_CONTEXT' not in artifacts:
        issues.append('Missing physical context artifact (identified terminal/phone and run reference)')
    if record.get('nfc') == 'OBSERVED' and not set(artifacts).intersection(('LAB_APDU_TRACE','OFFICIAL_L2_TRACE','OFFICIAL_L3_TRACE')):
        issues.append('No trace supporting NFC observation; screen alone is insufficient')
    if record.get('emv') in ('RECOGNIZED','REJECTED') and not set(artifacts).intersection(('OFFICIAL_L2_TRACE','OFFICIAL_L3_TRACE')):
        issues.append('Missing official EMV processing trace')
    if record.get('authorization') in ('APPROVED','DECLINED') and 'HOST_TRACE' not in artifacts:
        issues.append('Missing correlated host evidence for authorization claim')
    if record.get('settlement') == 'DOCUMENTED' and record.get('environment') != 'PRODUCTION_OBSERVATION':
        issues.append('Homologation does not prove real financial settlement')
    if record.get('settlement') == 'DOCUMENTED' and ('RECONCILIATION' not in artifacts or record.get('authorization') != 'APPROVED'):
        issues.append('Settlement requires independent reconciliation and recorded authorization')
    if record.get('rejection') == 'PROCESSOR_DOCUMENTED' and 'HOST_TRACE' not in artifacts:
        issues.append('Processor rejection needs correlated processor trace')
    if record.get('rejection') in ('TERMINAL_DOCUMENTED','PROTOCOL_INCOMPATIBILITY_DOCUMENTED') and not set(artifacts).intersection(('OFFICIAL_L2_TRACE','OFFICIAL_L3_TRACE','LAB_APDU_TRACE')):
        issues.append('Rejection attribution requires documented trace; no message/SW inference')
    return dict(status='INCOMPLETE_OR_NOT_EXECUTED' if issues else 'ARTIFACTS_PRESENT_MANUAL_REVIEW_REQUIRED',
                physicallyVerified=False, issues=issues, verifiedHashes=len(artifacts))


def report(args):
    root = Path(args.experiment)
    record = json.loads((root / 'experiment.json').read_text(encoding='utf-8'))
    assessment = assess(root, record)
    lines = [f'# Experimento {record.get("id")}', '', f'Terminal: {record.get("terminalId")} / {record.get("terminalSnapshot",{})}', f'Celular: {record.get("phoneReference","UNKNOWN")}',
             f'Ambiente: {record.get("environment")} / {record.get("environmentReference")}',
             f'Credencial: {record.get("credential")} / caso {record.get("officialCaseId")}',
             f'Execução: {record.get("physicalExecution")}', '',
             '| Camada | Observação declarada |', '| --- | --- |']
    lines += [f'| {k} | {record.get(k,"UNKNOWN")} |' for k in ('nfc','emv','authorization','settlement','rejection')]
    lines += ['', '**' + assessment['status'] + '**', 'Nenhuma confirmação física automática. Hash não comprova autenticidade nem interpretação.', '']
    lines += ['- ' + issue for issue in assessment['issues']]
    lines += ['', '## Artefatos', ''] + [f'- {e["path"]} — {e["kind"]} — SHA256 {e["sha256"]}' for e in record.get('evidence', [])]
    Path(args.out).write_text('\n'.join(lines) + '\n', encoding='utf-8')
    print(json.dumps(assessment, ensure_ascii=False, indent=2))
    return 0 if not assessment['issues'] else 2


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    sub = parser.add_subparsers(dest='action', required=True)
    p = sub.add_parser('inventory-init'); p.add_argument('--out', required=True); p.add_argument('--slots',type=int,default=40); p.set_defaults(fn=inventory)
    p = sub.add_parser('doctor'); p.add_argument('--adb', default='adb'); p.add_argument('--serial'); p.add_argument('--reference', default='PHONE-01'); p.add_argument('--out',required=True); p.set_defaults(fn=doctor)
    p = sub.add_parser('pcsc'); p.add_argument('--list', action='store_true'); p.add_argument('--reader'); p.add_argument('--out'); p.add_argument('--attempts',type=int,choices=range(1,101),default=5); p.set_defaults(fn=pcsc)
    p = sub.add_parser('new'); p.add_argument('--out',required=True); p.add_argument('--terminal',required=True); p.add_argument('--operator',required=True); p.add_argument('--inventory'); p.add_argument('--phone',default='PHONE-UNKNOWN')
    p.add_argument('--environment',choices=['LAB_PROTOCOL','OFFICIAL_HOMOLOGATION','PRODUCTION_OBSERVATION'],required=True)
    p.add_argument('--environment-ref',required=True); p.add_argument('--credential',choices=['NFC_LAB_FICTITIOUS','OFFICIAL_POSITIVE','OFFICIAL_NEGATIVE','LEGITIMATE'],required=True); p.add_argument('--case'); p.set_defaults(fn=new)
    p = sub.add_parser('attach'); p.add_argument('--experiment',required=True); p.add_argument('--file',required=True); p.add_argument('--source',required=True); p.add_argument('--operator',required=True); p.add_argument('--collected-at',required=True)
    p.add_argument('--kind',choices=['PHYSICAL_CONTEXT','LAB_APDU_TRACE','OFFICIAL_L2_TRACE','OFFICIAL_L3_TRACE','HOST_TRACE','RECONCILIATION','CAPABILITY_REPORT','OTHER'],required=True); p.set_defaults(fn=attach)
    p = sub.add_parser('report'); p.add_argument('--experiment',required=True); p.add_argument('--out',required=True); p.set_defaults(fn=report)
    args=parser.parse_args(argv)
    if args.action=='inventory-init' and not 1 <= args.slots <= 200:
        parser.error('slots must be 1..200')
    try:
        return args.fn(args) or 0
    except (ValueError,OSError,KeyError,subprocess.SubprocessError) as exc:
        print(type(exc).__name__ + ': ' + str(exc), file=sys.stderr)
        return 2


if __name__ == '__main__':
    sys.exit(main())
