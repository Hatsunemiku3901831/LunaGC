#!/usr/bin/env python3
"""Extract literal node data from the pinned reference, without executing TypeScript.

The upstream catalogue is inferred from editor screenshots. It is preview metadata,
not a verified mapping of 7.1 packet fields or executable server instructions.
"""
import argparse
import ast
import hashlib
import json
import re
import subprocess
from pathlib import Path


class Literals:
    def __init__(self, source, constants=None):
        self.source, self.pos, self.constants = source, 0, constants or {}

    def space(self):
        while True:
            match = re.match(r"(?:\s+|//[^\n]*|/\*[\s\S]*?\*/)", self.source[self.pos:])
            if not match:
                return
            self.pos += len(match[0])

    def value(self):
        self.space()
        char = self.source[self.pos]
        if char in "[{":
            self.pos += 1
            result = [] if char == "[" else {}
            end = "]" if char == "[" else "}"
            while True:
                self.space()
                if self.source[self.pos] == end:
                    self.pos += 1
                    return result
                if char == "[":
                    result.append(self.value())
                else:
                    key = self.value() if self.source[self.pos] in "\"'" else self.word()
                    self.space()
                    if self.source[self.pos] != ":":
                        raise ValueError("expected literal object key")
                    self.pos += 1
                    result[key] = self.value()
                self.space()
                if self.source[self.pos] == ",":
                    self.pos += 1
                elif self.source[self.pos] != end:
                    raise ValueError("only literal data is supported")
        if char in "\"'":
            match = re.match(r"(?:\"(?:[^\"\\]|\\.)*\"|'(?:[^'\\]|\\.)*')", self.source[self.pos:])
            if not match:
                raise ValueError("unterminated string")
            self.pos += len(match[0])
            return ast.literal_eval(match[0])
        match = re.match(r"-?\d+(?:\.\d+)?(?:[eE][+-]?\d+)?", self.source[self.pos:])
        if match:
            self.pos += len(match[0])
            return json.loads(match[0])
        word = self.word()
        if word in {"true", "false", "null"}:
            return {"true": True, "false": False, "null": None}[word]
        if word in self.constants:
            return self.constants[word]
        raise ValueError("nonliteral expression: " + word)

    def word(self):
        self.space()
        match = re.match(r"[A-Za-z_$][\w$]*", self.source[self.pos:])
        if not match:
            raise ValueError("expected identifier at " + str(self.pos))
        self.pos += len(match[0])
        return match[0]


def exported(path, name, constants=None):
    source = path.read_text(encoding="utf-8")
    match = re.search(r"export const " + name + r"\b[^=]*=\s*", source)
    if not match:
        raise ValueError("missing export: " + name)
    return Literals(source[match.end():], constants).value()


def main():
    root = Path(__file__).resolve().parents[1]
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--reference", type=Path, default=root / "debug/WebMiliastraNodesEditor")
    parser.add_argument("--output", type=Path, default=root / "src/main/resources/miliastra/node-catalog.json")
    args = parser.parse_args()
    directory = args.reference / "web/src/data"
    definitions = directory / "nodeDefinitions.ts"
    source = definitions.read_text(encoding="utf-8")
    constants = {match[1]: Literals(match[2]).value() for match in
                 re.finditer(r"^const (\w+) = (.*);$", source, re.MULTILINE)}
    nodes = exported(definitions, "nodeDefinitions", constants)
    server = set(exported(directory / "nodeDefinitions.server.ts", "serverNodeList"))
    client = exported(directory / "nodeDefinitions.client.ts", "clientNodeAvailability")
    counts = {node["id"]: sum(other["id"] == node["id"] for other in nodes) for node in nodes}
    rows = []
    for node in nodes:
        row = {key: node[key] for key in ("id", "displayName", "displayNameEN", "officialID", "category", "kind", "ports")}
        row["environments"] = (["server"] if row["id"] in server else []) + [
            "client:" + name for name, available in client.items() if row["id"] in available]
        row["ambiguous"] = counts[row["id"]] > 1
        rows.append(row)
    commit = subprocess.check_output(["git", "-C", str(args.reference), "rev-parse", "HEAD"], text=True).strip()
    result = {"source": "https://github.com/Columbina-Dev/WebMiliastraNodesEditor",
              "commit": commit, "sourceSha256": hashlib.sha256(definitions.read_bytes()).hexdigest(),
              "license": "GPL-3.0", "verifiedClientVersion": None,
              "warning": "Definitions inferred from screenshots; preview only, not verified 7.1 execution semantics.",
              "ambiguousIds": sorted(key for key, count in counts.items() if count > 1),
              "nodes": rows}
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(result, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps({"nodes": len(rows), "commit": commit, "output": str(args.output)}))


if __name__ == "__main__":
    main()
