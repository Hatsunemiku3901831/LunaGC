# Skill scalar 条件合同与原始 flag 清查

`R3-OLD121-SKILL-FLOAT-SETTER-CONDITIONAL-CONTRACT`、`R3-OLD121-SKILL-INT32-SETTER-SOURCE-WINDOW`、`R3-OLD121-SKILL-INT32-SETTER-CONDITIONAL-CONTRACT` 和 `R3-OLD121-SKILL-RAW-FLAG-INVENTORY` 已完成限定验收。条件合同、静态原窗及原始 flag 分别成立；它们不证明整条 parser、资源逻辑值、typed 初态、自然初始化或 ready。

## Float setter 正常条件

`14f905250` 的 48 条指令中，正常路径无 CALL、间接转移或旧目的 DWORD 读取，也不查询 M/cache/cctor/GC/allocator/IFix。`MOVD` 取 XMM1 低 32 bits，经 `14586b600 → B+e970` 的表 A 按四个 byte 分区查表，与 `145872d30` seed 异或后一次 store 4B；可读表/seed、可写目的及正常栈是条件。`L≥1024` 是全 bit 域充分条件，不是实际长度。null/bounds 的两个 CALL 只登记错误边，不解释返回。

已验 166B Float getter 使用同 cell 的四个逆区；同一 W、匹配 seed、所访问位置可读且 `Gq[Fq[b]]=b` 时可按 bits 逆回，getter 全域充分条件 `L≥2048` 亦非实际长度。无浮点数值转换，`-0`、Inf 和 NaN payload 仍属位级关系。Proud 的 M/41105 不是此 setter 入参；实际 M、key、cache、encoded、current 和 epoch 仍 unknown，旧 inverse/seed fixture 不是实际 Float M。本次不创建 cache/Default、主动 getter 补输入，也不重跑逆表或旧测试。私有 `local/migration72/d02-resume-20261116/old121-skill-float-setter-conditional-contract/spec.private.json` 为 26067B，SHA-256 `0cd5c5db2ad0e230dbad65c1f7cdefc89c5efd292ae8273ee1cddce0139fc039`。

## Int32 setter 来源与正常条件

parser 必经 `149b57721 → 14ef4b0c0`。准确 `44333.m369202`、parent 405，参数 `318365` 的 type reference 70 为 int32；fresh AOT 与独立完整 pdata 确定 `[14ef4b0c0,14ef4b15d)`，一次读取 157B，body SHA-256 `37ba57e88a12ec7681b14ffc3c5bf58140661181ae89b08f52da214891ec6f1f`。strict 45 指令、9 引用（2 CALL / 5 branch / 2 RIP），准确冻结直接命中 0 仅限直接索引。证据 185B = 16 + 12 + 157；五 stock hash 550661784B、header 4616B / distinct 4096B、exception 14344080B 另账，PE hash + 计量来源读为 465275869B，应用预检与 OS loader 未计部分分列。Talent、121 Depot、phase6 固定索引的补核仅为范围/hash，不重验旧成果。

正常路径以 EDX 低 32 bits 做逻辑 SHR/字节抽取，不符号扩展且忽略 RDX 高位；同根表四分区查表后与 seed 异或，一次覆盖目的 DWORD。正常路径无 CALL、旧目的读取、M/cache/cctor/allocator；不新增 Int32 getter，也不推负值业务或完整 parser。私有 `old121-skill-int32-setter-conditional-contract/spec.private.json` 为 28020B，SHA-256 `1cd9d4ecc7f360ec0c2c766f9cb7657780659ee9693e94a71c4fdefbfe391bf0`。原窗根 `local/migration72/d02-resume-20261116/old121-skill-int32-setter-source-window/` 的 `capture/result.private.json` SHA-256 `f16fd4c8f6cbc7a594cbc24c08a93e8e380eea5dcde4d31d657d6091cbeddd64`，`manifest.private.json` SHA-256 `c202d6a863c92ea7128b9454bf1eaf3a353dbf6f898227b35ac58051a40a8275`。

## 固定 raw Skill 首 8B

121 Depot 的 484 个 slot 含 248 个普通正引用和 236 个零，另有 121 个正 energy 引用，合成 369 个目标 Skill；旧 4 个 control 与其交 2，故盘点 union 371 行，其中 367 为新 cohort。1176 个 row spans 连续覆盖 `[4,141702)`；固定 raw 文件 141702B 一次 disk/hash，371×8=2968B 为 header 语义读取，选中 span 的 43596B 仅 hash，不读取第 9 字节及其后 payload 语义。

| 范围 | 行数 | id flag | element flag | 五个中性 gate 的计数 |
| --- | ---: | ---: | ---: | --- |
| 全部 union | 371 | 371 | 121 | 371 / 0 / 149 / 0 / 371 |
| 新 cohort | 367 | 367 | 120 | 367 / 0 / 145 / 0 / 367 |
| energy | 121 | 121 | 121 | 121 / 0 / 8 / 0 / 121 |

`rawMask` 与 `rawMask XOR b0c7b439` 的 effective 值分层，gate 4 使用反向 raw bit 28；64 位不降精度。26 anchors、8 literal byte oracle 和每行 byte-index 断言通过，但不是新测试 suite 或 native value oracle。总输出 240897B 小于 512 KiB；schema 预检 Depot 3 次、mapping 2 次实读另账，OS loader 未知。仅 raw flags：不证明 String 长度/非空、TLS、实际调用、字段逻辑值或实际 row，未置 flag 也不自动给 `None`；payload 语义 0、`nativeParserExecuted=false`、新逻辑值 0、ready=false。

私有 `local/migration72/d02-resume-20261116/old121-skill-raw-flag-inventory/` 的 `inventory.private.json` 为 224822B，SHA-256 `11e23311eb75a31ebeaa4ab24024a3b873a308a14eddba5c7352493166479f4b`；`ledger.private.json` SHA-256 `180d987f05b8151509e953761285d834c28fe49586549c6d428f32a4410f2a94`；`manifest.private.json` SHA-256 `0c8bdd4be2add1aebbd49829327d490ab0c553f8309e65fbd6b06a902c006ae7`。`R3-OLD121-SKILL-RAW-FLAG-TRIAGE` 进行中，尚无后继结论。

`costElemVal`、`specialEnergyMin`、`specialEnergyMax` 的 target 职责仍 unknown，121 个 energy 行均未闭。原窗仅静态采集；本批未执行目标/native、ctor/parser/getter，也无新 M 采样、JVM、DB、客户端或 Git 操作。A/B、common return、preview、准确 tail 和 A opaque 纠错保持，D-02 / F-05 / D02-R6 仍 0/6。
