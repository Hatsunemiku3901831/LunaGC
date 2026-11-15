# 旧号 Skill 首个中性字符串长度限定清查

`R3-OLD121-SKILL-RAW-FLAG-TRIAGE` 和 `R3-OLD121-SKILL-STRING1-LENGTH-INVENTORY` 已完成限定验收。既验 371 行的首个中性 String1 长度 N 均在 14..23，全部非零且 ≤64；N=0 与 ≥65 均为 0 行。这仅是固定 raw 的长度头分布，不证明文本、非空语义、TLS、reader/callee 实际调用、parser 成功或资源值。

原 parser 的首 8B mask 与十个固定宽度条件确定长度头位置；本项只在 371 个位置各读 2B，新增语义读取 742B，`N = LE16 XOR f38e`。固定 raw 141702B 一次 hash；32 source anchors、4 prefix literal、5 u16 literal 及边界断言通过，ledger `passed=true`、`stage=complete`，native model / 目标 CPU / RPM 均 0。未读字符串 payload，也未执行目标 parser；已验的 String2/4 absent 路径不等于可填空字符串或跳过整条 parser。

私有 `local/migration72/d02-resume-20261116/old121-skill-raw-flag-triage/spec.private.json` SHA-256 `4930e591761efcdd36c5c1cf1a336a1fedf9ba8c41a8b61622626d43ac68e16a`；`local/migration72/d02-resume-20261116/old121-skill-string1-length-inventory/inventory.private.json` SHA-256 `2de5d9e8c1fe9bb0f083e820038a6429b3a93bc1795a8944c4ac3d7c514b760d`、`ledger.private.json` SHA-256 `ca1267cb6efc6b7671b666da3736bbec49d59972ce2886be7f1db54a3f1bf009`、`manifest.private.json` SHA-256 `dc24799137dfca93a67c55eeb20df6eb2053a07bd27631cb044b71babcc56767`。同目录 request、`run_inventory.private.py` 和 summary 保留固定范围和复现入口；不重跑既有盘点。

合法 typed 输入、独立值 oracle、reader/TLS 条件及 `costElemVal/specialEnergyMin/specialEnergyMax` 的 121 个 energy 行仍未闭。用户要求当前收尾后暂停，本批不启动 small path 后继；A/B、preview、准确 tail 和 opaque 纠错保持，D-02 / F-05 / D02-R6 仍为 0/6。
