# Miliastra reference data

`node-catalog.json` is extracted from the literal node definitions and availability lists in
[Columbina-Dev/WebMiliastraNodesEditor](https://github.com/Columbina-Dev/WebMiliastraNodesEditor),
commit `f2a5e2055d99b82cb4a6b2197e00f3106f4223d7`, under its GNU GPL v3 license.
Its upstream author explicitly describes these definitions as inferred from editor screenshots.
They may be incorrect or incomplete. This catalogue is used for preview warnings, not packet
mapping or automatic execution of unverified instructions. Both conflicting `client.math.tan`
definitions are retained and marked ambiguous.

Reproduce with `python tools/extract_miliastra_catalog.py`. The extractor parses literals;
it does not execute upstream TypeScript or install third-party dependencies.
The catalogue includes its source URL, commit and source SHA-256.

The independently implemented container/metadata reader was checked against:

- [script-1024/genshin-miliastra-file-format](https://github.com/script-1024/genshin-miliastra-file-format),
  commit `48ffc9c79ca0b62039a89686c579482b55dff14b`, MIT, copyright (c) 2025 script-1024.
- [luern0313/UGC-File-Generate-Utils](https://github.com/luern0313/UGC-File-Generate-Utils),
  commit `fba7c267a3752d3f0108b30020c46868d56acdd5`, Apache-2.0;
  its `UGC-Gia-Gil-ProtoBuf` submodule `c56862b4fe2cb1d8dac5fdb6fb738ad559641ca2`
  is MIT, copyright (c) 2025 luern0313.

No code from their Python exporters is executed during server startup or an upload.

`block-palette.json` contains the 21 literal block templates from `config/block_config.py` in
UGC-File-Generate-Utils commit `fba7c267a3752d3f0108b30020c46868d56acdd5`, Apache-2.0.
The source does not state an individual copyright holder in its license. The complete license
is included as `LICENSE.block-palette`. Reproduce with `python tools/extract_miliastra_blocks.py`;
the extractor parses the Python AST and literal keyword arguments without importing the source.
The independently implemented entity writer follows the MIT-licensed proto definitions listed
above; the image converter uses HSV color distance and dimension normalization described in
the reference. Java bicubic resizing is used, while the reference uses Pillow Lanczos.
Neither template availability nor generated-file import has yet been verified with the 7.1 client.

`src/main/proto/MiliastraNodes.proto` is derived from proto 1.5.1 in the MIT-licensed
Genshin-Impact-Miliastra-Wonderland-Code-Node-Editor-Pack submodule, commit
`5d85fc474428686c6705be03ebd8b3139f1952f3`, copyright 2025 Wu-Yijun.
Only the protobuf and Java package/outer-class options and an attribution comment were added.
The complete MIT license is included as `LICENSE.node-protocol`.
That source labels its game version 6.2.0. Its graph definitions are used for typed conversion,
including preservation of uninterpreted fields, not as proof of 7.1 runtime compatibility.

`node-protocol.json` retains the literal TypesList, NodesList, EnumList, ClientEnumList and
GraphConstList from `utils/node_data/index.json` in the same MIT submodule and commit.
Reproduce with `python tools/extract_miliastra_protocol.py`; no upstream scripts are executed.
The independent canvas compiler uses those type/concrete-node rules together with the
GPL-3.0 screenshot catalogue's port names. Ambiguous or unsupported mappings are diagnosed;
successful encoding does not establish import or execution in the current 7.1 client.

The four `src/main/proto/MiliastraAsset*.proto` schemas are adapted from the same
MIT UGC-Gia-Gil-ProtoBuf submodule `c56862b4fe2cb1d8dac5fdb6fb738ad559641ca2`,
copyright (c) 2025 luern0313. Package names, imports, Java options and attribution
were changed. `LICENSE.assets-protocol` contains the complete license;
`assets-protocol.json` records each original file's SHA-256. Reproduce with
`python tools/extract_miliastra_assets.py`. GUID copying uses explicitly identified
fields; it does not infer references in uninterpreted byte fields.

The independent reverse canvas projection uses the same pinned node metadata and catalogue.
It diagnoses multiple matching templates and missing pin/constant mappings rather than dropping
them. A source-backed canvas retains the original GIA, restoring its exact bytes when unchanged;
edited graphs retain identified native metadata and uninterpreted protobuf fields. This does not
establish 7.1 client import, execution, publishing or reward compatibility.

`camera-templates.json` retains all five literal templates and editor hints from
`model/camera_model.py` in UGC-File-Generate-Utils commit
`fba7c267a3752d3f0108b30020c46868d56acdd5`, Apache-2.0.
`LICENSE.camera-templates` contains the complete upstream license. Reproduce with
`python tools/extract_miliastra_cameras.py`; the extractor reads AST literals and does
not import the reference module. The independent camera writer uses the pinned MIT
camera and asset schemas, including the float collision field omitted by the Python
assembler. Classic mode 5 is encoded as absent/zero following that reference writer.
Template ranges and editor hints are reference data, not verified 7.1 client behavior.
