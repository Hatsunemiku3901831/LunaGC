"""Trace fixed legacy scene3 ScriptLib sites without executing script functions.

Lua lexical call syntax is not runtime reachability or API compatibility.
Java is parsed with the JDK compiler, never loaded or initialized.
"""
import argparse
import base64
from collections import Counter, defaultdict
from dataclasses import dataclass
import difflib
import hashlib
import json
from pathlib import Path
import re
import subprocess
import tempfile


LEGACY_COMMIT = 'dac2a9d43332ef51879522cf39da2831b0ca0543'
MANIFEST_SHA256 = '47b783755a7c8f714a868db551f76a503092e0fd7895cc7a787e6060e651c02d'
SUMMARY_SHA256 = '9ffd79a3b9321485e2d870fe2bfccd89db0eddc0f0f71d62a778020c8e7b97cf'
REPORT_SHA256 = 'b99d5deeb475e2dae3269e7946f574d001230e98d9ae18c212b39ad4eada3a7d'
REVIEWED_SOURCES = {
    'src/main/java/emu/grasscutter/game/player/Player.java': 'f080a4f521ebeb0467bcb09b84e993a68d147140046cea0e00898f5d9eee0b91',
    'src/main/java/emu/grasscutter/game/entity/GameEntity.java': '91f3fe982ea91b6028106866d5e616b3968693f113792c096eaa61398140ba56',
    "src/main/java/emu/grasscutter/scripts/ScriptLib.java": "3b554d175c4028e0d30bc4727035f5f04d44f76a3d88b562b0d03227b4cfcdf2",
    "src/main/java/emu/grasscutter/scripts/ScriptLibUserdata.java": "691ac41d63b2fcca6833c989293d7f4df7ddd98261d21162badd90e3c1b102b5",
    "src/main/java/emu/grasscutter/scripts/GroupLuaCall.java": "ab0d0f59ba44ed4ca76e80a7e09e1dec2e43457ea7cf28d0978e6419c29be1d8",
    "src/main/java/emu/grasscutter/scripts/MonsterTideLua.java": "c2def0a3229ed951c66e7ec06769fe4671e6dff9cd7507069686a54f58ad4ee1",
    "src/main/java/emu/grasscutter/scripts/GroupTimerLua.java": "5fbc7ab94fc36d1c64646ded091f897b4f2a43b04c902d966696dd8df82a9b57",
    "src/main/java/emu/grasscutter/scripts/ScenePlayBattleLua.java": "035b10c07d610d5fcdec9a48e9dddc319f7d75e8ae8e483a80c7cdfba0144b34",
    "src/main/java/emu/grasscutter/scripts/TemplateReminderLua.java": "fba2ad78f57dcecf1d16332e80576cdc9a2843a9cb2f63bfaaa0f7e57a97140d",
    "src/main/java/emu/grasscutter/scripts/ScenePlayRewardLua.java": "a62dcd288cdd0ab7e25c1a9ab5e6a9aa3b547fb25dcc5ed4a96e558996479301",
    "src/main/java/emu/grasscutter/scripts/ScriptLoader.java": "ff7a09590df7038fa1c9ab48f347ebe8f0845bc67360d967844908b715324cc1",
    "src/main/java/emu/grasscutter/scripts/SceneScriptManager.java": "e6c707e1eab5d439e6762a25a0591a2c798125d1e95cb3091a72127929b8b89e",
    "src/main/java/emu/grasscutter/scripts/SceneTimeAxis.java": "16c3f73598a9566e6d96436dd8063ff45c4a864c7f8ad94076badaf980d6d9ba",
    "src/main/java/emu/grasscutter/scripts/data/SceneRegion.java": "d7068474765905a4e1a6cb1c52ee560b49fcd11524315dc69ce2f641e334e8f0",
    "src/main/java/emu/grasscutter/game/world/Scene.java": "100d28f74ea1fe5291458d0ec0a4f898fa10cd7551fade2b95b8e6b6f4adcc35",
    "src/main/java/emu/grasscutter/game/world/World.java": "3fe1b04b3c2681dd96a5206a60a0035952de827cf69a5dd6c7f0ff5e44768147",
    "src/main/java/emu/grasscutter/game/player/PlayerProgressManager.java": "1b8a66ff0b8ab4827ba3b8149d3edd64ad47aa305ac4a02bc51748f587208960",
    "src/main/java/emu/grasscutter/game/dungeons/challenge/WorldChallenge.java": "507f3bb0fdfd140e980084ce50b7da57a4f5dce2ea1e56f13302a45b4dd3528b",
    "src/main/java/emu/grasscutter/game/dungeons/challenge/trigger/KillMonsterTimeIncTrigger.java": "8edfb1a533fa02e243d1c846bc36962a614ecbfe068ecab6779a3978b183f908"
}
SCRIPT_LIB = 'src/main/java/emu/grasscutter/scripts/ScriptLib.java'
USERDATA = 'src/main/java/emu/grasscutter/scripts/ScriptLibUserdata.java'
WRAPPER_GROUP = 'src/main/java/emu/grasscutter/scripts/GroupLuaCall.java'


@dataclass(frozen=True)
class Token:
    kind: str
    text: str
    start: int
    end: int
    line: int
    column: int


def tokens(source):
    """A bounded Lua 5.2 lexer: comments/quoted/long strings are indivisible."""
    i, line, column = 0, 1, 1
    result = []
    while i < len(source):
        start, first_line, first_column = i, line, column
        comment = source.startswith('--', i)
        position = i + 2 if comment else i
        long_open = re.match(r'\[(=*)\[', source[position:]) if source[position:position + 1] == '[' else None
        if long_open:
            end = source.find(']' + long_open[1] + ']', position + long_open.end())
            if end < 0:
                raise ValueError(f'Unterminated long bracket at line {line}')
            i = end + len(long_open[1]) + 2
            kind = 'comment' if comment else 'string'
        elif comment:
            end = source.find('\n', i)
            i = len(source) if end < 0 else end
            kind = 'comment'
        elif source[i].isspace():
            i += 1
            kind = 'space'
        elif source[i] in "\"'":
            quote = source[i]
            i += 1
            while i < len(source) and source[i] != quote:
                i += 2 if source[i] == '\\' else 1
            if i >= len(source):
                raise ValueError(f'Unterminated quoted string at line {line}')
            i += 1
            kind = 'string'
        elif source[i].isalpha() or source[i] == '_':
            i += 1
            while i < len(source) and (source[i].isalnum() or source[i] == '_'):
                i += 1
            kind = 'identifier'
        else:
            i += 1
            kind = 'symbol'
        text = source[start:i]
        if kind not in ('space', 'comment'):
            result.append(Token(kind, text, start, i, first_line, first_column))
        if '\n' in text:
            line += text.count('\n')
            column = len(text.rsplit('\n', 1)[1]) + 1
        else:
            column += len(text)
    return result


def literal_string(token):
    if token.kind != 'string':
        return None
    text = token.text
    if text[0] in "\"'":
        return text[1:-1] if '\\' not in text else None
    opening = re.match(r'\[(=*)\[', text)
    value = text[opening.end():-(len(opening[1]) + 2)]
    return value[1:] if value.startswith('\n') else value


def scan_lua(source):
    values = tokens(source)
    sites, requires, boundaries = [], [], []
    lines = source.splitlines()
    for i, token in enumerate(values):
        tail = values[i + 1:i + 5]
        if token.kind != 'identifier':
            continue
        if token.text == 'require':
            arg = tail[1] if tail and tail[0].text == '(' and len(tail) > 1 else tail[0] if tail else None
            name = literal_string(arg) if arg else None
            if name is not None and re.fullmatch(r'[A-Za-z0-9_/.-]+', name):
                requires.append(dict(name=name, line=token.line, column=token.column))
            else:
                boundaries.append(dict(kind='dynamic-require', line=token.line, column=token.column, source=lines[token.line - 1]))
        if token.text != 'ScriptLib':
            continue
        base = dict(line=token.line, column=token.column, source=lines[token.line - 1])
        member, syntax, after = None, None, None
        if len(tail) >= 2 and tail[0].text in ('.', ':') and tail[1].kind == 'identifier':
            member, syntax, after = tail[1].text, 'dot' if tail[0].text == '.' else 'colon', 2
        elif len(tail) >= 3 and tail[0].text == '[' and tail[2].text == ']':
            member = literal_string(tail[1])
            syntax, after = 'constant-bracket', 3
        if member is None:
            boundaries.append(dict(base, kind='dynamic-member' if tail and tail[0].text == '[' else 'ScriptLib-value-or-shadow-boundary'))
            continue
        following = tail[after] if len(tail) > after else None
        call = following is not None and (following.text in ('(', '{') or following.kind == 'string')
        declaration = i > 0 and values[i - 1].text == 'function'
        usage = 'member-declaration' if declaration else 'call-syntax' if call else 'member-value'
        sites.append(dict(base, member=member, accessSyntax=syntax, use=usage,
                          start=token.start, end=tail[after - 1].end))
    return dict(sites=sites, requires=requires, boundaries=boundaries)


def sha256(path, normalize=False):
    raw = path.read_bytes()
    return hashlib.sha256(raw.replace(b'\r\n', b'\n') if normalize else raw).hexdigest()


def verify_sources(repository):
    for name, expected in REVIEWED_SOURCES.items():
        if sha256(repository / name, True) != expected:
            raise ValueError('Review changed API dependency: ' + name)


def read_inputs(repository, baseline, report_path, summary_path):
    if sha256(report_path) != REPORT_SHA256 or sha256(summary_path, True) != SUMMARY_SHA256:
        raise ValueError('Fixed inline report/summary changed')
    report = json.loads(report_path.read_text('utf8'))
    summary = json.loads(summary_path.read_text('utf8'))
    encoded = json.dumps(report['files'], sort_keys=True, separators=(',', ':')).encode()
    if hashlib.sha256(encoded).hexdigest() != MANIFEST_SHA256 or summary['evaluatedScriptManifest']['sha256'] != MANIFEST_SHA256:
        raise ValueError('Fixed inline script manifest changed')
    if report['legacyResourceCommit'] != LEGACY_COMMIT:
        raise ValueError('Unexpected legacy provenance')
    commit = subprocess.run(['git', '-C', str(baseline), 'rev-parse', 'HEAD'], check=True, capture_output=True, text=True).stdout.strip()
    if commit != LEGACY_COMMIT:
        raise ValueError('Legacy resource commit changed')
    subprocess.run(['git', '-C', str(baseline), 'diff', '--quiet', 'HEAD', '--', 'Scripts/Scene/3', 'Scripts/Common'], check=True)
    for name, metadata in report['files'].items():
        path = (baseline / 'Scripts' / name).resolve()
        if not path.is_relative_to((baseline / 'Scripts').resolve()):
            raise ValueError('Manifest path outside scripts')
        if path.stat().st_size != metadata['bytes'] or sha256(path) != metadata['sha256']:
            raise ValueError('Fixed script bytes changed: ' + name)
    verify_sources(repository)
    if sha256(repository / SCRIPT_LIB, True) != report['scriptLibSourceSHA256']:
        raise ValueError('ScriptLib differs from parent API observation')
    return report, summary


def java_surface(repository, java_home, work, sources=None):
    helper = repository / 'tools/Client72SceneApiSurface.java'
    subprocess.run([str(java_home / 'bin/javac.exe'), '-encoding', 'UTF-8', '-d', str(work), str(helper)], check=True, capture_output=True)
    manifest = work / 'sources.txt'
    sources = sources if sources is not None else [name for name in REVIEWED_SOURCES if name.startswith('src/')]
    manifest.write_text('\n'.join(sources) + '\n', 'utf8')
    run = subprocess.run([str(java_home / 'bin/java.exe'), '-Xmx1g', '-cp', str(work), 'Client72SceneApiSurface',
                          str(repository), str(manifest)], check=True, capture_output=True)
    rows = []
    for line in run.stdout.decode('utf8').splitlines():
        file, owner, name, public, number, signature, body, calls = [base64.b64decode(value).decode('utf8') for value in line.split('\t')]
        rows.append(dict(file=file, owner=owner, name=name, public=public == 'true', line=int(number), signature=signature,
                         methodSHA256=hashlib.sha256(body.replace('\r\n', '\n').encode()).hexdigest(),
                         logsUnimplemented='Call unimplemented ' in body, calls=calls.splitlines(), source=body))
    return rows


def reachable_files(group_file, scans):
    seen, pending, unresolved = set(), [group_file], []
    while pending:
        file = pending.pop()
        if file in seen:
            continue
        seen.add(file)
        for dependency in scans[file]['requires']:
            target = 'Common/' + dependency['name'] + '.lua'
            if target not in scans:
                unresolved.append(dict(file=file, line=dependency['line'], target=target))
            else:
                pending.append(target)
    return seen, unresolved


def build_report(baseline, parent, methods):
    scans, raw_lines, replaced = {}, {}, 0
    for name in parent['files']:
        raw = (baseline / 'Scripts' / name).read_bytes()
        try:
            text = raw.decode('utf8')
        except UnicodeDecodeError:
            text = raw.decode('utf8', errors='replace')
            replaced += 1
        scans[name] = scan_lua(text)
        raw_lines[name] = [line.removesuffix(b'\r') for line in raw.split(b'\n')]
    groups = [row for row in parent['groupDeclarations'] if not row['dynamicLoad'] and row['evaluated']]
    by_file = defaultdict(set)
    unresolved, reconciliations = [], []
    member_groups = defaultdict(set)
    for group in groups:
        file = f'Scene/3/scene3_group{group["id"]}.lua'
        reachable, missing = reachable_files(file, scans)
        unresolved.extend(dict(row, group=group['id']) for row in missing)
        lexical = {site['member'] for name in reachable for site in scans[name]['sites']}
        compiled = set(group['directScriptLibMembers'])
        if compiled - lexical or lexical - compiled:
            reconciliations.append(dict(group=group['id'], compiledWithoutLexical=sorted(compiled - lexical), lexicalWithoutCompiled=sorted(lexical - compiled)))
        for name in reachable:
            by_file[name].add(group['id'])
        for member in compiled:
            member_groups[member].add(group['id'])
    public = [row for row in methods if row['file'] == SCRIPT_LIB and row['owner'] == 'ScriptLib' and row['public'] and row['name'] != '<init>']
    names = sorted({row['name'] for row in public})
    registered = registered_surface(methods)
    missing_public = set(parent['directScriptLibMemberReferences']) - set(names)
    historical_missing = set(parent['missingDirectScriptLibMembers'])
    public_reconciliation = dict(sourceMissingWithoutParent=sorted(missing_public - historical_missing),
                                 parentMissingNowPublic=sorted(historical_missing - missing_public))
    missing_members = parent['missingDirectScriptLibMembers']
    entries = []
    for member in missing_members:
        sites = []
        for file, scan in scans.items():
            for site in scan['sites']:
                if site['member'] != member:
                    continue
                matched = sorted(by_file[file] & member_groups[member])
                original = raw_lines[file][site['line'] - 1]
                sites.append(dict(site, file=file, staticGroupsWithCompiledMember=matched,
                                  rawLineBase64=base64.b64encode(original).decode('ascii'), rawLineSHA256=hashlib.sha256(original).hexdigest(),
                                  provenance='static require reachability intersected with parent compiled member set; not runtime execution'))
        near = difflib.get_close_matches(member, names, n=3, cutoff=0.6)
        registration = registered.get(member, [])
        entries.append(dict(member=member, parentMissingPublicMethod=True, staticGroups=sorted(member_groups[member]),
                            sites=sorted(sites, key=lambda row: (row['file'], row['line'], row['column'])),
                            publicExact=[method_evidence(row) for row in public if row['name'] == member],
                            registeredUserdata=registration,
                            publicNearNames=[dict(method_evidence(row), semanticEquivalence='unknown; spelling similarity only')
                                             for name in near for row in public if row['name'] == name],
                            implementationStatus='registered-wrapper-present-contract-unverified' if registration else 'not-found-in-reviewed-public-and-registered-surface',
                            reviewedBusinessCandidates=business_candidates(member, methods),
                            callerContractEvidence=caller_evidence(member, baseline),
                            semantics72='unknown', runtimeRequiredForEntry='unknown'))
    boundaries = [dict(row, file=file, staticallyReachableGroups=sorted(by_file[file]))
                  for file, scan in scans.items() for row in scan['boundaries']]
    all_lexical = sorted({site['member'] for scan in scans.values() for site in scan['sites']})
    return dict(schemaVersion=1, scope='fixed legacy scene3 lexical API sites, parent compiled-name reconciliation and reviewed Java declarations only',
                legacySourceIsNative72=False, resourceReady=False, runtimeStatus='not-executed',
                counts=dict(files=len(scans), staticGroups=len(groups), parentDirectMembers=len(parent['directScriptLibMemberReferences']),
                            lexicalMembers=len(all_lexical), parentMissingMembers=len(entries),
                            groupsWithParentMissingMembers=len(set().union(*(member_groups[name] for name in missing_members))),
                            registeredWrappersAmongParentMissing=sum(bool(row['registeredUserdata']) for row in entries),
                            missingWithoutReviewedRegistration=sum(not row['registeredUserdata'] for row in entries),
                            groupsWithoutReviewedMissingRegistration=len(set().union(*(set(row['staticGroups']) for row in entries if not row['registeredUserdata']))),
                            missingMemberSites=sum(len(row['sites']) for row in entries),
                            sourcePublicScriptLibMethods=len(public), filesWithUtf8Replacement=replaced),
                lexicalMembers=all_lexical, entries=entries, registeredSurface=registered,
                publicNameReconciliation=public_reconciliation, lexicalCompiledReconciliation=reconciliations,
                unresolvedStaticRequires=unresolved, dynamicAndValueBoundaries=boundaries,
                sourceTextEncoding='UTF-8, replacing invalid sequences as parent Java reader; exact original line bytes separately base64 encoded',
                limitations=['No Lua function, trigger/action or ScriptLib is executed',
                             'Call syntax is not execution or runtime reachability; no alias/local shadow resolution',
                             'No indirect _G/rawget/metatable/eval resolution; zero observed direct boundaries does not close these paths',
                             'Static require graph is a provenance candidate; conditional/function require reachability remains open',
                             'Parent direct bytecode scan sees adjacent global references only, not all dynamic or aliased calls',
                             'Group/site provenance intersects compiled member names, not per-site bytecode locations',
                             'Near names alone are not implementation candidates; existing wrappers are not CN72 contract evidence',
                             'Only listed reviewed business sources; absence there is not proof of absence in all plugins/services'])


def method_evidence(row):
    return {key: row[key] for key in ('file', 'owner', 'name', 'line', 'signature', 'methodSHA256', 'logsUnimplemented')}


def registered_surface(methods):
    """Source-reviewed fixed registry forms, not a generic Java expression interpreter."""
    result = defaultdict(list)
    get = next(row for row in methods if row['file'] == USERDATA and row['name'] == 'get')
    for name in re.findall(r'case\s+"([A-Za-z_][A-Za-z0-9_]*)"\s*:', get['source']):
        result[name].append(dict(method_evidence(get), registration='explicit userdata get switch', runtimeStatus='not-executed'))
    for file, field in ((WRAPPER_GROUP, 'groupFunctions'),
                        ('src/main/java/emu/grasscutter/scripts/GroupTimerLua.java', 'timerFunctions'),
                        ('src/main/java/emu/grasscutter/scripts/MonsterTideLua.java', 'tideFunctions'),
                        ('src/main/java/emu/grasscutter/scripts/ScenePlayBattleLua.java', 'sceneBattleFunctions')):
        row = next(row for row in methods if row['file'] == file and row['name'] == 'functions')
        if field + '.get(' not in get['source']:
            raise ValueError('Reviewed userdata registry path disappeared: ' + field)
        literal_keys = re.search(r'new String\[\]\s*\{(.*?)\}', row['source'], re.S)
        if literal_keys is None:
            literal_keys = re.search(r'return Map\.of\((.*?)\);', row['source'], re.S)
        if literal_keys is None:
            raise ValueError('Reviewed registry literal form disappeared: ' + file)
        names = set(re.findall(r'"([A-Z][A-Za-z0-9_]*)"', literal_keys[1]))
        for name in sorted(names):
            result[name].append(dict(method_evidence(row), registration='reviewed literal registry -> userdata ' + field,
                                     lookupEvidence=method_evidence(get), runtimeStatus='not-executed'))
    return dict(sorted(result.items()))


BUSINESS_REVIEW = {
    'GetGadgetConfigId': [('src/main/java/emu/grasscutter/game/world/Scene.java', 'getEntityById',
                         'Lua sites use gadget_eid; scene entity-ID lookup exists and GameEntity stores a distinct configId field',
                         'Gadget type, invalid/cross-group IDs and return/error contract need validation; not GetGadgetIdByEntityId alias')],
    'GetSceneOwnerUid': [('src/main/java/emu/grasscutter/game/world/Scene.java', 'getHost',
                         'Scene.getHost delegates to World host; concrete owner-player lookup exists',
                         'Lua/context/multiplayer authority and return UID contract require validation')],
    'UnlockScenePoint': [('src/main/java/emu/grasscutter/game/player/PlayerProgressManager.java', 'unlockTransPoint',
                         'Existing point-unlock path persists discovery and sends unlock notification',
                         'Scene/point/target-player arguments, quest locks and CN72 notification remain unverified')],
    'IsInRegion': [('src/main/java/emu/grasscutter/scripts/data/SceneRegion.java', 'contains',
                    'Existing sphere/cubic/polygon/cylinder coordinate membership mechanism',
                    'Which entity/player/region and Lua return/error semantics are unknown')],
    'AddChallengeDuration': [('src/main/java/emu/grasscutter/game/dungeons/challenge/trigger/KillMonsterTimeIncTrigger.java', 'onMonsterDeath',
                              'Existing challenge path updates timeLimit and emits challenge-data notification',
                              'This is a death-event bounded increment, not proof of the missing Lua API contract')],
    'SetChallengeDuration': [('src/main/java/emu/grasscutter/game/dungeons/challenge/WorldChallenge.java', 'onCheckTimeOut',
                              'Existing timeout consumer uses active challenge timeLimit',
                              'Duration base, active challenge selection and notification contract unknown')],
    'PrintGroupWarning': [(SCRIPT_LIB, 'PrintContextLog', 'Existing contextual logging entry exists',
                           'Group warning formatting/return behavior unknown; not proven alias')],
    'ShowReminderByUid': [(SCRIPT_LIB, 'ShowReminder', 'Existing scene-broadcast reminder packet constructor exists',
                          'UID-targeted delivery differs from scene broadcast; return and CN72 packet unknown')],
    'CreateGadgetByConfigIdByPos': [('src/main/java/emu/grasscutter/scripts/SceneScriptManager.java', 'createGadget',
                                   'Existing gadget construction mechanism',
                                   'Position/rotation/ownership/config copy and persistence contract unknown')],
    'CreateGadgetByParamTable': [(SCRIPT_LIB, 'CreateGadget', 'Existing Lua table-driven gadget creation path',
                                'Missing API parameter table fields and error semantics not proven equivalent')],
}
for _member, _method in (('AddExtraFlowSuite', 'AddExtraGroupSuite'), ('RemoveExtraFlowSuite', 'RemoveExtraGroupSuite'),
                          ('GoToFlowSuite', 'GoToGroupSuite'), ('SetFlowSuite', 'GoToGroupSuite')):
    BUSINESS_REVIEW[_member] = [(SCRIPT_LIB, _method, 'Existing group-suite mechanism with concrete scene/group side effects',
                                'Flow-suite identity/order/persistence differs or is unknown; name similarity is not alias evidence')]
for _member, _method in (('ChangeGroupTempValue', 'ChangeGroupVariableValue'), ('CreateGroupVariable', 'SetGroupVariableValue')):
    BUSINESS_REVIEW[_member] = [(SCRIPT_LIB, _method, 'Existing group variable storage and mutation path',
                                'Temporary/create-vs-update lifetime, save and notification semantics unknown')]


def business_candidates(member, methods):
    result = []
    for file, name, reason, unknown in BUSINESS_REVIEW.get(member, []):
        matches = [row for row in methods if row['file'] == file and row['name'] == name]
        if not matches:
            raise ValueError('Reviewed candidate method disappeared: ' + file + '#' + name)
        result.append(dict(reason=reason, remainingContract=unknown, status='source-backed-mechanism-only-not-an-alias',
                           evidence=[method_evidence(row) for row in matches]))
    if member == 'GetGadgetConfigId' and result:
        result[0]['fieldEvidence'] = dict(file='src/main/java/emu/grasscutter/game/entity/GameEntity.java', line=56,
                                          declaration='@Getter @Setter private int configId;',
                                          basis='reviewed source field plus Lombok getter annotation; not runtime inspection')
    if member == 'GetSceneOwnerUid' and result:
        result[0]['evidence'].extend(method_evidence(row) for row in methods
                                     if row['file'] == 'src/main/java/emu/grasscutter/game/player/Player.java' and row['name'] == 'getUid')
        result[0]['fieldEvidence'] = dict(file='src/main/java/emu/grasscutter/game/world/World.java', line=58,
                                          declaration='@Getter private volatile Player host;', basis='reviewed source field and Lombok getter annotation')
    return result


CALLER_REVIEW = {
    'GetGadgetConfigId': [('Common/V3_0/HookLook.lua', 148, 151, 'Caller rejects absent gadgets[configId]'),
                         ('Common/V3_0/Dreamland.lua', 352, 355, 'Caller indexes gadgets[configId] without a preceding nil guard'),
                         ('Scene/3/scene3_group133220055.lua', 369, 371, 'Caller compares returned number to script config ID 55023')],
    'GetSceneOwnerUid': [('Common/V3_0/HookLook.lua', 143, 145, 'Returned value is consumed as GetAvatarEntityIdByUid input')],
}


def caller_evidence(member, baseline):
    result = []
    for file, start, end, reason in CALLER_REVIEW.get(member, []):
        raw = (baseline / 'Scripts' / file).read_bytes().split(b'\n')
        span = b'\n'.join(raw[start - 1:end])
        result.append(dict(file=file, line=start, endLine=end, reason=reason, source=span.decode('utf8', errors='replace'),
                           rawSpanBase64=base64.b64encode(span).decode('ascii'), rawSpanSHA256=hashlib.sha256(span).hexdigest(),
                           invalidInputReturnContract='unknown; caller does not identify a universal 0/-1/nil contract'))
    return result


def summarize(report):
    summary = {key: value for key, value in report.items() if key not in ('entries', 'dynamicAndValueBoundaries', 'lexicalCompiledReconciliation', 'unresolvedStaticRequires')}
    summary['missingMembers'] = [dict(member=row['member'], staticGroups=len(row['staticGroups']), sites=len(row['sites']),
                                     callSyntaxSites=sum(site['use'] == 'call-syntax' for site in row['sites']),
                                     implementationStatus=row['implementationStatus'],
                                     businessCandidateCount=len(row['reviewedBusinessCandidates'])) for row in report['entries']]
    summary['boundaryCounts'] = dict(sorted(Counter(row['kind'] for row in report['dynamicAndValueBoundaries']).items()))
    summary['reconciliationCounts'] = dict(groups=len(report['lexicalCompiledReconciliation']), unresolvedRequires=len(report['unresolvedStaticRequires']))
    for key in ('entries', 'dynamicAndValueBoundaries', 'lexicalCompiledReconciliation'):
        summary[key + 'SHA256'] = hashlib.sha256(json.dumps(report[key], sort_keys=True, separators=(',', ':')).encode()).hexdigest()
    return summary


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    for name in ('baseline', 'java-home', 'output', 'summary-output'):
        parser.add_argument('--' + name, type=Path, required=True)
    parser.add_argument('--repository', type=Path, default=Path(__file__).resolve().parents[1])
    parser.add_argument('--parent-report', type=Path, default=Path('local/migration72/evidence/scene-grid-inline-audit.json'))
    parser.add_argument('--parent-summary', type=Path, default=Path('protocol/cn-7.2/scene-grid-inline-observations.json'))
    args = parser.parse_args()
    repository, baseline = args.repository.resolve(), args.baseline.resolve()
    for output in (args.output, args.summary_output):
        if output.resolve().is_relative_to(baseline):
            parser.error('Output must be outside legacy input')
    parent, summary = read_inputs(repository, baseline, args.parent_report, args.parent_summary)
    with tempfile.TemporaryDirectory(prefix='scene-script-api-', dir=repository / 'local/migration72/analysis') as folder:
        methods = java_surface(repository, args.java_home.resolve(), Path(folder))
    report = build_report(baseline, parent, methods)
    report.update(legacyResourceCommit=LEGACY_COMMIT, parentReportSHA256=REPORT_SHA256, parentSummarySHA256=SUMMARY_SHA256,
                  evaluatedScriptManifest=summary['evaluatedScriptManifest'], reviewedSources=REVIEWED_SOURCES,
                  toolSourceSHA256={name: sha256(repository / name, True) for name in (
                      'tools/audit_scene_script_api72.py', 'tools/Client72SceneApiSurface.java')})
    for output, document in ((args.output, report), (args.summary_output, summarize(report))):
        output.parent.mkdir(parents=True, exist_ok=True)
        output.write_text(json.dumps(document, indent=2, sort_keys=True) + '\n', 'utf8')
    print(json.dumps(report['counts'], sort_keys=True))


if __name__ == '__main__':
    main()
