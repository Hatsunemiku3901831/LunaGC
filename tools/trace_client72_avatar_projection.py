"""Replay the Java getter fixture through existing bounded Avatar/Team consumers."""
import argparse
import hashlib
import json
import subprocess
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

from extract_client72 import HASHES, read_verified, extract
from trace_client72_avatar_core import core_aot
from trace_client72_avatar_outer import avatar_aot
from trace_client72_player_store import type_cells
from client72_avatar_contract import declarations, AvatarContractGraph, AvatarRuntime
from client72_avatar_team import AvatarTeamGraph, TeamRuntime, EVIDENCE_SHA
from client72_emulator import Offline72

ROOT = Path(__file__).resolve().parents[1]
PROFILE_SHA = 'a3b9eda630942ba8479f713fc1692c7eb7b05198ae382dfe878ecc963051fd5d'
SOURCES = {'tools/client72_avatar_team.py': '7556e52f372d82694e117763c3cfe704530904d9f44f9510b468d45efe3b852c', 'tools/client72_avatar_contract.py': '74383f9c724767bb0ff3e1d9f0436b516a328f6858154063a846a03733b68491', 'tools/client72_emulator.py': '8007d2594ef8058a4f2a9f1c24094fb5d2551a202b4e52154876cdfe77a17c3c', 'tools/client72_session_runtime.py': 'c6ff407e33416869a855cfa86e97c3f63a9d24019ac0f6e6dfc74a959bfe1439', 'tools/trace_client72_avatar_outer.py': '57f2c6e6ca1d7990eb7829369f0f13fa7ed969146a1f84798714f977ba0470fd', 'src/test/java/io/grasscutter/Protocol72AvatarProjectionTest.java': 'f368ae7854fbb913ff48105b84e2cb4b83ff3e77d9eb39f3e487e136655e750f', 'src/main/proto/Client72AvatarWire.proto': '2fa9f646b968764a01ebadb227d6f203002c4c7623aeaddf030c826306a492de'}
SOURCES.update({'src/main/java/emu/grasscutter/game/avatar/Avatar.java': '9176d33373ee5e663f1cc8f3a9137b65c3def1908fc13035dc5569e4bd4cc31d', 'src/main/java/emu/grasscutter/game/player/TeamInfo.java': 'd37145eae250f1b04bc9f9825480292bde23e62cffa099e8d7db97adcbe9dc28', 'src/main/java/emu/grasscutter/game/player/TeamManager.java': 'e22bf409e5d9f1d9182f0b4094ed72e02e40751347a152e825c6c3e4f9434dfb', 'src/main/java/emu/grasscutter/game/inventory/GameItem.java': 'e9de522816d9400c1cd3a3ba3ae9ca9ff6691365ceee1ecb8d534c4a03cef132', 'src/main/java/emu/grasscutter/server/packet/send/PacketPlayerStoreNotify.java': '901dda026915caa6df52406be5e0f01adeeb5a701174ebf76e36775398c370e0', 'src/main/proto/PlayerStoreNotify.proto': '2d6b5f82fd96f075ffbf62a0deced0b56daef0d097c3e8974374f4adf5f2eb05', 'src/main/proto/Client72InventoryWire.proto': '2ee7e9ab45073b575e2641cf46c1a02802880e054bfcdb08990674ac68cf8248'})
EXPECTED = {'7': {'name': '队伍 A', 'guids': [9007199254740995, 9007199254740993]}}

def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()

def run(args):
    profile = args.directory / 'profile.json'
    if sha(profile) != PROFILE_SHA: raise ValueError('profile changed')
    for name, digest in SOURCES.items():
        if sha(ROOT / name) != digest: raise ValueError('source changed: ' + name)
    for name, digest in EVIDENCE_SHA.items():
        if sha(args.directory / name) != digest: raise ValueError('evidence changed: ' + name)
    data = json.loads(profile.read_text(encoding='utf8'))
    for record in data['files'].values(): read_verified(args.client / record['path'], record['sha256'])
    suite = ET.parse(args.java_results).getroot()
    if suite.get('name') != 'io.grasscutter.Protocol72AvatarProjectionTest' or suite.get('tests') != '1' or any(suite.get(k) != '0' for k in ('failures', 'errors', 'skipped')):
        raise ValueError('projection suite did not pass')
    prefix = 'CN72_AVATAR_PROJECTION '
    wires = [line[len(prefix):] for line in suite.findtext('system-out', '').splitlines() if line.startswith(prefix)]
    if len(wires) != 1: raise ValueError('expected one projection')
    binary, metadata, startup = [read_verified(args.client / name, digest) for name, digest in HASHES.items()]
    records, _ = extract(binary, metadata, startup=startup, structures=True)
    declarations(binary, metadata, startup, records, args.directory)
    cells = type_cells(binary, metadata, startup, records)
    bindings = core_aot(binary, metadata, startup, records)
    outer_bindings = avatar_aot(binary, metadata, startup, records, dict(types=[]))
    outer = json.loads((args.directory / 'avatar-outer-observations.json').read_text('utf8'))
    engine = Offline72(binary, args.python_libs, records, trace_instructions=False)
    graph = AvatarTeamGraph(engine, args.directory, cells, bindings, outer_bindings, outer)
    runtime = None
    try:
        reader = graph.run_graph(30509, bytes.fromhex(wires[0]))
        runtime = TeamRuntime(engine, graph, 65536)
        dispatch = runtime.notification()
        actual = runtime.snapshot_teams()
        if dispatch['stop'] != '0x1507cf4a1' or actual != EXPECTED: raise ValueError('team consumer mismatch')
        selection = runtime.selection_field()
        if selection['stop'] != '0x1507cf627' or engine.read(runtime.team_manager + 0x124, 'I') != 7: raise ValueError('current team mismatch')
        if [x['guid'] for x in runtime.member_stores] != EXPECTED['7']['guids']: raise ValueError('member order mismatch')
        identities = [runtime.identity(model, source) for model, source in runtime.delivery]
        if [x['guid'] for x in identities] != [9007199254740993, 9007199254740995]: raise ValueError('avatar identities mismatch')
        member_stores = runtime.member_stores
    finally:
        if runtime: runtime.close()
        graph.close(); engine.close()
    # TeamRuntime changes the shared enumerator interface. Recreate the original f1
    # runtime exactly as verify() does before executing its property consumer.
    engine = Offline72(binary, args.python_libs, records, trace_instructions=False)
    # The combined Java message includes a Unicode Team name. Reuse f3's
    # already-verified UTF-8 reader while retaining f1's independent runtime.
    graph = AvatarTeamGraph(engine, args.directory, cells, bindings, outer_bindings, outer)
    try:
        graph.run_graph(30509, bytes.fromhex(wires[0]))
        runtime = AvatarRuntime(engine, graph)
        avatar_dispatch = runtime.root()
        if avatar_dispatch['stop'] != '0x1507cf07a' or len(runtime.delivery) != 2:
            raise ValueError('native Avatar delivery differs')
        avatar_rows = []
        for model, source in runtime.delivery:
            identity = runtime.identity(model, source)
            prop = runtime.prop(model, source)
            equip = runtime.equipment(model, source)
            avatar_rows.append(dict(identity=identity, properties=prop, equipment=equip))
        if [x['identity']['guid'] for x in avatar_rows] != [9007199254740993, 9007199254740995]:
            raise ValueError('native Avatar identity differs')
        if [x['properties']['values'] for x in avatar_rows] != [{1001: 275., 1002: 3., 4001: 42.}, {1001: 0., 1002: 0., 4001: 0.}]:
            raise ValueError('native property values differ')
        expected_equipment = [dict(slot=x['slot'], guid=x['guid'], id=x['id']) for x in runtime.equip_rows if x['guid'] == 4294967297]
        if not expected_equipment or avatar_rows[0]['equipment']['delivered'] != expected_equipment or avatar_rows[1]['equipment']['delivered']:
            raise ValueError('native equipment lookup differs')
        report = dict(toolSHA256=sha(Path(__file__)), avatarRows=avatar_rows, avatarDispatch=avatar_dispatch,
            task='F-03d3g2-preparation', profileSHA256=PROFILE_SHA, clientSHA256={k:v['sha256'] for k,v in data['files'].items()},
            sourceSHA256=SOURCES, evidenceSHA256=EVIDENCE_SHA, javaResultsSHA256=sha(args.java_results), wire=wires[0],
            reader=reader, dispatch=dispatch, selection=selection, teams=actual, identities=identities,
            memberStores=member_stores, productionIntegrated=False, targetProtocolReady=False,
            limits=['Separate existing f1 and f3 managed fixtures; bounded native prefixes and current-team assignment only',
                    'Store fixture item ID is zero; native inventory row ID is 10001: only GUID is correlated, not item ID or full inventory',
                    'No full construction, f4d2 suffix, live client, database or production Avatar sender'])
    finally:
        graph.close(); engine.close()
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(report, indent=2, ensure_ascii=False) + '\n', encoding='utf8')
    print('Java projection native prefix and Team assignment matched')

def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--client', type=Path, required=True); p.add_argument('--java-results', type=Path, required=True)
    p.add_argument('--output', type=Path, required=True); p.add_argument('--directory', type=Path, default=Path('protocol/cn-7.2'))
    p.add_argument('--python-libs', type=Path, default=Path('local/migration72/python')); p.add_argument('--worker', action='store_true', help=argparse.SUPPRESS)
    args = p.parse_args()
    outdir = (ROOT / 'local/migration72/offline-sol-terra/avatar-projection').resolve()
    if not args.output.resolve().is_relative_to(outdir): p.error('output must be in avatar-projection directory')
    if not args.worker:
        try: result = subprocess.run([sys.executable, *sys.argv, '--worker'], timeout=180)
        except subprocess.TimeoutExpired: raise SystemExit('projection timed out after 180 seconds')
        raise SystemExit(result.returncode)
    run(args)

if __name__ == '__main__': main()
