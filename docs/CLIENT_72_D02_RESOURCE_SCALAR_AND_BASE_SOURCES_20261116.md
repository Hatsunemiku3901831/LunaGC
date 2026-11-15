# Skill 与 Reliquary 最小语义及两个后继来源

`R3-OLD121-SKILL-PARSER-MINIMUM-SEMANTICS` 和 `R3-ACTIVE-RELIQUARY-MINIMUM-SEMANTICS` 已完成限定静态语义；`R3-OLD121-SKILL-FLOAT-SETTER-SOURCE-WINDOW` 与 `R3-ACTIVE-ITEM-BASE-PARSER-SOURCE` 已完成完整静态来源验收。均不证明自然初始化、typed row、新资源值、完整 Player 恢复或 ready；直接冻结引用为零只覆盖各窗直接索引，不证明传递路径 A-free。

## Skill parser 两字段

正常 parser 路径中 `440582/+68` 的 id 在 `149b581f8` 覆盖：原 mask bit 41 有 flag 时读取 u32 并 XOR `d5f9b2ff`，无 flag 时局部逻辑值为 0，先经已验 157B UInt32 setter `14aefa090` 编码再覆盖。`440577/+54` 的 `costElemType` 在 `149b58b17` 覆盖：原 mask bit 25 有 flag 时按 `(word + 5a02b85a) mod 2^32 XOR dd69ea57`，无 flag 时局部逻辑值为 0。两字段覆盖路径不读其旧值，但不推 host zero 或自然初始化；这些是条件来源，不是新行值。R13 在数组循环中临时作 reader，随后恢复；该临时读取不能误记为旧 row 字段读取。

已验 166B Float getter `14f905140` 与 157B UInt32 setter直接复用，不重采。parser 的必经 `149b573e3 → 14f905250` 是**不同的 Float setter**，其完整静态来源见下。`costElemVal`、`specialEnergyMin`、`specialEnergyMax` 的 target 职责仍 unknown，包括 `10068` 在内 121 个 energy 行均未闭，不能按旧片段省略补零。固定私有 `local/migration72/d02-resume-20261116/old121-skill-parser-minimum-semantics/spec.private.json` 为 49611B，SHA-256 `2fce0bc57668ed61d1253588feb954103d719a06a2c9c3d28a18f880db22c399`。

## Reliquary 条件入口与槽位

`1529b1959 → 150b6a9b0` 位于首个条件分支前，CALL 时 RCX/RDX 保持原 receiver/input 寄存器值；这是局部转交事实，不是 ABI 或返回验收。该 raw 窗未直接向 receiver 的 `+28/+38` store。已知槽位布局 `+7c` 在 `1529b1cd4` 条件 store：`flags & 8000` 时读 4B 并 XOR `c0689c0e`，否则局部逻辑值为 0。旧槽 1/5 fixture 不推 183 个候选 ID 的实际值，rank/set 的目标职责仍 unknown。固定私有 `local/migration72/d02-resume-20261116/active-item-reliquary-minimum-semantics/spec.private.json` SHA-256 `dafa0603f5959f8dba06cee0689fcf66a4523efb69da08ad3b990c766add7b17`。

## 新 Float setter 原窗

`54724.m454807 / 14f905250` 的参数 `373185` 是 type reference `651` 的 float、`byRef=false`。fresh AOT 和当前完整 exception 的唯一 exact pdata 确定 `[14f905250,14f9052f3)`，一次 body 读取 163B，SHA-256 `bac1a635359180adac5ac415d084cd45d36d9f7a01c59eda1ea9b1d1eb79833c`；strict 48 指令、9 引用（2 CALL / 5 branch / 2 RIP）、直接冻结命中 0。证据 191B = 16 + 12 + 163；五 stock hash 550661784B，header 两读 4616B / distinct 4096B、exception 14344080B 另账，PE hash 与计量来源读为 465275875B。预检与底层 loader / OS calls 未计量，不称全部物理 IO 已覆盖。

私有根 `local/migration72/d02-resume-20261116/old121-skill-float-setter-source-window/`：`capture/result.private.json` SHA-256 `a68b3af65c6dbfbe1d74e9542f6e353816ca3e77e63b8595b638e1b5cdd4384a`，`manifest.private.json` SHA-256 `095861d1b0ab2a28917bedb5d1f820e215792157c3cdf6ec273c722100ce7935`，`capture/body.asm.private.json` SHA-256 `907d65bbf6c9c4a6856eff08e3c39c30f0e5d81383666097299636ae28fc2421`。准备 generator 的换行替换错误发生在 runner 写入和实际 source 之前、客户端读取 0；`capture/preparation.private.json` SHA-256 `2e7cd68a57f4138789c758ecb00ccb6d50d7094a2da324b7c9b9a26c3c060b65` 保留，不算 source 重试。后继[Float/Int32条件合同与raw flag盘点](CLIENT_72_D02_SCALAR_CONTRACTS_AND_SKILL_FLAGS_20261116.md)已限定验收，不证明实际M或新行值。

## 共同父 parser 原窗

`88968.m735168 / 150b6a9b0` 一显参为 typeRef `86325 → class 42005`。fresh AOT 与当前唯一 pdata 确定 `[150b6a9b0,150b6b192)`，一次读取 body 2018B，SHA-256 `64d41cef20efe8497be861797e4a9d224e96fc0cee2f71259145fd60b1e3485e`，与旧 control 一致；raw pdata `b0a9b61092b1b61040957704`、RVA `1ab948f0`。strict 488 指令、130 引用（41 CALL / 77 branch / 12 RIP）、直接冻结命中 0。证据 2046B = 16 + 12 + 2018；header 两读 4616B、exception 14344080B、五 stock hash 550661784B 及 metadata/helper 预检另账，pdata 从目录复制，不重复计物理读。

私有根 `local/migration72/d02-resume-20261116/active-item-base-parser-window/`：`capture/result.private.json` SHA-256 `3aa27b4cfb568b59fb2d2e2da1d0f36c1764d6784591d358952057f051aee5e5`，`manifest.private.json` SHA-256 `4dfa1f8bc96475277655988de4ef85c94717e12a329af1d45a67735d7847b173`。actual pins、controls 和 helpers 已 hash；历史 ctor `entryResultPins` 明确不作为该 source 的输入。`R3-ACTIVE-ITEM-BASE-MINIMUM-SEMANTICS` 正由 Sol max 统一审定共同父 id/itemType，尚无结论，不重采该 source。

本批无目标/native/ctor/parser/getter 执行、host row/value、JVM、DB、客户端或新测试。A/B、preview 和准确 tail 冻结不变；A 原明文恢复误验已撤回，D-02 / F-05 / D02-R6 仍为 0/6。
