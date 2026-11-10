"""Offline source-bound classifier for the audited managed final request URL."""
import argparse
import ast
import hashlib
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
STOCK = 'local/migration72/d02-resume-20261110/b-managed-route-stock/'
SOURCE = 'tools/client72_sdk_managed_route_inputs.py'
SPEC = STOCK + 'implementation-spec.private.json'
SUFFIX = 'local/migration72/d02-resume-20261110/b-combo-suffix-literal/literal.private.json'
CODE = 'local/migration72/d02-resume-20261110/b-combo-request-window/code.bin'
PINS = {
    SOURCE: '7d4253504c04f0c767a64b671f4c936a706686cb283762439e0d911dbed10409',
    SPEC: '3807982afbdab44f79b24b9a206e8bdbf216c7f8856192adeede7196b1b3b382',
    STOCK + 'frozen-inputs.private.json': 'cb5e66330892e6a081f94545df82ed5697f2966c06d69bb7a0bf4d9f83808b5c',
    STOCK + 'audit-first.private.json': '5cc9a55d521174813f21b9c8e21655d9b36e36ee3a0c917f323af4bf47f7c532',
    STOCK + 'audit-repeat.private.json': '5cc9a55d521174813f21b9c8e21655d9b36e36ee3a0c917f323af4bf47f7c532',
    SUFFIX: '03a7c24111df037a76af8eacf3d5561d1e448981c2f8f32cb2af6b1bdc2d22ad',
    CODE: '2fdf917e9fa54a1bf1db172fa32fec4c8c212a404ee57d79e5d8023884bc05ac',
}


class SourceValidationError(ValueError):
    """Opaque failure: no source content or caller input is exposed."""


class Classifier:
    def __init__(self, table):
        self._table = table

    def classify(self, raw):
        """Return a fixed category for exact bytes; otherwise return only unknown."""
        if type(raw) is not bytes:
            return 'unknown'
        return self._table.get(raw, 'unknown')


def load_classifier(root=ROOT):
    """Validate all fixed source bytes before constructing a pure classifier."""
    try:
        sources = {name: (root / name).read_bytes() for name in PINS}
        if any(hashlib.sha256(sources[name]).hexdigest() != pin for name, pin in PINS.items()):
            raise SourceValidationError('source-validation-failed')
        return _from_verified_sources(sources)
    except (OSError, ValueError, KeyError, TypeError, IndexError, AttributeError, SyntaxError) as error:
        raise SourceValidationError('source-validation-failed') from None


def _from_verified_sources(sources):
    tree = ast.parse(sources[SOURCE])
    values = {node.targets[0].id: node.value for node in tree.body
              if isinstance(node, ast.Assign) and len(node.targets) == 1
              and isinstance(node.targets[0], ast.Name)}
    envs = ast.literal_eval(values['ENVS'])
    hosts = ast.literal_eval(values['HOSTS'])
    expression = values['BASES'].key.func.value
    scheme = ast.literal_eval(expression.left.left)
    path = ast.literal_eval(expression.right)
    spec = json.loads(sources[SPEC])['classification']
    suffix = json.loads(sources[SUFFIX])
    if (list(envs) != spec['cnEnvNumbers'] or list(hosts) != spec['hosts']
            or scheme != spec['scheme'] or path != spec['path']
            or suffix['value'] != '?' or suffix['passed'] is not True
            or suffix['codeSHA256'] != PINS[CODE] or len(envs) != 8
            or len(set(envs)) != 8 or len(set(hosts)) != 8):
        raise SourceValidationError('source-validation-failed')
    final = {(scheme + host + path + suffix['value']).encode('utf-16-le'):
             'managed-final-request-cn-env-' + str(env) for host, env in zip(hosts, envs)}
    if len(final) != 8 or {len(key) // 2 for key in final} != set(range(65, 71)):
        raise SourceValidationError('source-validation-failed')
    return Classifier(final)


def main():
    parser = argparse.ArgumentParser(description='Validate fixed sources for the offline final URL classifier.')
    parser.add_argument('--verify-sources', action='store_true', required=True)
    parser.parse_args()
    try:
        load_classifier()
    except SourceValidationError:
        print('{"sourceValidation":"failed"}')
        return 1
    print('{"sourceValidation":"passed","scope":"offline-source-bound-classifier"}')
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
