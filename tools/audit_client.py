"""Read-only client migration inventory; never installs a patch or claims wire compatibility.

Example: python tools/audit_client.py --client C:/path/to/client --output local/client-audit.json
A successful exit means the inventory completed, not that the client is supported.
"""

import argparse
import configparser
import hashlib
import json
import mmap
from pathlib import Path
import re
import struct
import subprocess


FILES = {
    "binary": "YuanShen.exe",
    "metadata": "YuanShen_Data/Managed/Metadata/global-metadata.dat",
    "startup": "YuanShen_Data/Managed/Metadata/startup-metadata.dat",
    "sdk": "YuanShen_Data/Plugins/AccountPlatNative.dll",
    "astrolabe": "YuanShen_Data/Plugins/Astrolabe.dll",
}
CRITICAL_PACKETS = (
    "GetPlayerTokenReq", "GetPlayerTokenRsp", "PlayerLoginReq", "PlayerLoginRsp",
    "PingReq", "PingRsp", "PlayerEnterSceneNotify", "EnterSceneReadyReq",
    "EnterSceneReadyRsp", "SceneInitFinishReq", "SceneInitFinishRsp",
    "EnterSceneDoneReq", "EnterSceneDoneRsp", "PostEnterSceneReq", "PostEnterSceneRsp",
)


def digest(path):
    with path.open("rb") as stream:
        return hashlib.file_digest(stream, "sha256").hexdigest()


def git_head(path):
    result = subprocess.run(["git", "-C", str(path), "rev-parse", "HEAD"],
                            capture_output=True, text=True, timeout=15)
    return result.stdout.strip() if result.returncode == 0 else None


def pattern_regex(pattern):
    tokens = pattern.split()
    if not tokens or all(token in ("?", "??") for token in tokens):
        raise ValueError("A signature must contain at least one literal byte")
    return re.compile(b"".join(b"." if token in ("?", "??") else
                               re.escape(bytes([int(token, 16)])) for token in tokens), re.DOTALL)


def pe_ranges(binary):
    """Return file-backed ranges with RVAs, not loaded pointers."""
    pe = struct.unpack_from("<I", binary, 0x3C)[0]
    if binary[:2] != b"MZ" or binary[pe:pe + 4] != b"PE\0\0":
        raise ValueError("Expected a PE executable")
    count = struct.unpack_from("<H", binary, pe + 6)[0]
    optional_size = struct.unpack_from("<H", binary, pe + 20)[0]
    size_of_code = struct.unpack_from("<I", binary, pe + 28)[0]
    base_of_code = struct.unpack_from("<I", binary, pe + 44)[0]
    ranges = {"il2cpp": [], "code": []}
    for i in range(count):
        section = pe + 24 + optional_size + 40 * i
        name = binary[section:section + 8].rstrip(b"\0")
        rva, size, offset = struct.unpack_from("<III", binary, section + 12)
        if offset + size > len(binary):
            raise ValueError("PE section extends past end of file")
        if name == b"il2cpp":
            ranges["il2cpp"].append((offset, offset + size, rva))
        low, high = max(rva, base_of_code), min(rva + size, base_of_code + size_of_code)
        if low < high:
            ranges["code"].append((offset + low - rva, offset + high - rva, low))
    return ranges


def signatures(repo, client):
    report = []
    for key, modules in (("binary", ("http", "security", "free_recharge")), ("sdk", ("native_sdk",))):
        with (client / FILES[key]).open("rb") as stream, mmap.mmap(stream.fileno(), 0, access=mmap.ACCESS_READ) as binary:
            ranges = pe_ranges(binary)
            for module in modules:
                source = (repo / f"patch/src/modules/{module}.rs").read_text("utf-8")
                for name, pattern in re.findall(r'const (\w+): &str = "([0-9A-Fa-f? ]+)";', source):
                    section = "code" if module == "native_sdk" or name in (
                        "MHYRSA_PERFORM_CRYPTO_ACTION", "KEY_SIGN_CHECK") else "il2cpp"
                    regex = pattern_regex(pattern)
                    hits = []
                    for start, end, rva in ranges[section]:
                        pos = start
                        while len(hits) < 3:
                            match = regex.search(binary, pos, end)
                            if not match:
                                break
                            hits.append(hex(rva + match.start() - start))
                            pos = match.start() + 1
                    report.append({"module": module, "signature": name, "section": section,
                                   "matchCountCappedAt3": len(hits), "rvas": hits})
    return report


def inventory(repo, client, scan_signatures=True):
    ini = configparser.ConfigParser(interpolation=None)
    ini.read(client / "config.ini", encoding="utf-8-sig")
    version = ini.get("General", "game_version")
    source = (repo / "build.gradle").read_text("utf-8")
    server = re.search(r"(?m)^version\s*=\s*['\"]([^'\"]+)['\"]", source).group(1)
    files = {}
    for name, relative in FILES.items():
        path = client / relative
        files[name] = {"path": relative, "exists": path.is_file()}
        if path.is_file():
            files[name].update(size=path.stat().st_size, sha256=digest(path))
    extractor = (repo / "tools/extract_client_type_names.py").read_text("utf-8")
    profile = {name: files[name].get("sha256") == re.search(
        rf'^{name.upper()}_SHA256 = "([a-f0-9]+)"', extractor, re.M).group(1)
        for name in ("binary", "metadata", "startup")}
    opcode_text = (repo / "src/main/java/emu/grasscutter/net/packet/PacketOpcodes.java").read_text("utf-8")
    opcodes = {name: int(value) for name, value in re.findall(
        r"public static final int (\w+) = (-?\d+);", opcode_text)}
    report = {
        "clientPath": str(client), "clientVersion": version, "serverSourceVersion": server,
        "sameMajorMinor": version.split(".")[:2] == server.split(".")[:2],
        "sourceCommits": {name: git_head(repo / name) for name in (".", "patch", "resources")},
        "files": files, "existingExtractorProfileMatches": profile,
        "protoSourceCount": len(list((repo / "src/main/proto").glob("*.proto"))),
        "serverCriticalOpcodes": {name: opcodes.get(name) for name in CRITICAL_PACKETS},
        "serverUnresolvedOpcodeCount": sum(value < 0 for value in opcodes.values()),
        "pendingDownloadFiles": sorted(p.name for p in client.glob("*.zip_tmp")),
        "limitations": [
            "SHA-256 identifies files; it does not authenticate their origin or verify the whole installation.",
            "Server opcodes are the current source baseline, not values extracted from the target client.",
            "Static signature hits do not verify callback ABI, object layout, loaded code, or runtime behavior.",
            "No login, world entry, save, or gameplay test is performed by this inventory.",
        ],
    }
    if scan_signatures and files["binary"]["exists"] and files["sdk"]["exists"]:
        report["patchStaticSignatures"] = signatures(repo, client)
    return report


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--client", required=True, type=Path)
    parser.add_argument("--repo", type=Path, default=Path(__file__).resolve().parents[1])
    parser.add_argument("--output", type=Path)
    parser.add_argument("--no-signatures", action="store_true")
    args = parser.parse_args()
    client = args.client.resolve()
    if args.output and args.output.resolve().is_relative_to(client):
        parser.error("Write the report outside the read-only client directory.")
    report = inventory(args.repo.resolve(), client, not args.no_signatures)
    encoded = json.dumps(report, ensure_ascii=False, indent=2) + "\n"
    if args.output:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(encoded, encoding="utf-8")
        print(f"Inventory written: {args.output.resolve()}")
        print(f"Client {report['clientVersion']}; server source {report['serverSourceVersion']}; "
              f"extractor profile matches: {all(report['existingExtractorProfileMatches'].values())}")
    else:
        print(encoded, end="")


if __name__ == "__main__":
    main()
