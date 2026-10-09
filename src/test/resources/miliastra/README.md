# Actual editor fixtures

`editor-sample.gia` and `editor-sample.server.json` are exact copies of the paired public
artifact in `Artifacts/0-7632376/` from
[WebMiliastraNodesEditor](https://github.com/Columbina-Dev/WebMiliastraNodesEditor/tree/f2a5e2055d99b82cb4a6b2197e00f3106f4223d7/Artifacts/0-7632376).
They are distributed under the upstream GNU GPL v3 license. Only the filenames were shortened.

The artifact records the editor's first GIA export milestone on 2025-11-30, not a 7.1 export.
The 381-byte GIA contains graph GUID 1024346482; its JSON contains three nodes and three edges.
Tests establish parsing and lossless archive export, not current-client execution compatibility.
