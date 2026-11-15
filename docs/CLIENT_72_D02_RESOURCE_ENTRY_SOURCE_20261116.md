# Skill、Material、Reliquary 构造器入口来源

两项原完整 ctor source 任务仍**阻塞**：Material `23400.m180605 / 14a88c9f0` 与 Reliquary `46431.m385147 / 1529b26c0` 的各一次采集均在唯一 pdata 边界门停止。完整 exception 目录中 `containing=[]`，`beginMultiplicity=[]` 是其派生结果，不是独立负证据；各次 body 0B、body read 0、AOT 16B、header 4616B。完整 exception 目录为 14344080B，SHA-256 `5895dbeef8fe13458720889e318ba64f3fc9c5d3ca03bf01b0adc70eee16c08d`。两项均缺可追溯完整边界，不能猜 16B、以 metadata 下一地址当终点或扫描至 RET；Skill 原完整窗同类失败及其解除条件见[既有专题](CLIENT_72_D02_OLD121_SKILL_SOURCE_BLOCKER_20261116.md)。

原 Material 与 Reliquary 的 `capture/failure.private.json` 分别位于 `local/migration72/d02-resume-20261116/active-item-material-ctor-window/` 和 `active-item-reliquary-ctor-window/`，SHA-256 分别为 `ec754fcc2a3c76eb99fb80e9cf00b2a1a2f0287851b1bc030adcb3afe3806ab7`、`918f76a173e90c0c8dfa927f064428db837d6f90fa76c7b4862d3b8637d7b4ee`。metadata cache 沿用旧 accepted pin 加 size，未重新大文件 hash；structures/signatures 的 exact identity gate 经主线明确授权，旧 planner 的 `thisScopeRead=false` 不构成本次执行禁令。

三个**新、仅入口首指令**任务已完成有限 source 验收：

| 任务 | 准确入口 |
| --- | --- |
| `R3-OLD121-SKILL-RESOURCE-ENTRY-1B-SOURCE` | `88602.m732415 / 149b59940` |
| `R3-ACTIVE-MATERIAL-RESOURCE-ENTRY-1B-SOURCE` | `23400.m180605 / 14a88c9f0` |
| `R3-ACTIVE-RELIQUARY-RESOURCE-ENTRY-1B-SOURCE` | `46431.m385147 / 1529b26c0` |

三项 exact ordinary identity、alias 和 fresh AOT 相符；每项入口各 1B `c3`，SHA-256 `ae3f4619b0413d70d3004b9131c3752153074e45725be13b9a148978895e359e`，strict Capstone x86-64、skipdata false 解为单条 size 1、无 operand 的 `ret`。仅条件正常入口到 return transfer 的指令区间可知无对象数据 store 或 CALL；目标 destination、ABI、formal return 和完整物理 end 未知。三者零参 signature 缓存均缺记录。本结论不追认原完整 body 失败，也不证明资源初始化、host zero、自然 GC、资源 row 值或 ready。

本轮分账：每项 header 两次读取共 4616B、distinct 4096B；每项 fresh AOT 8+8B 加入口 1B 为 17B 来源证据，三项 51B。共享五 stock hash-only 550661784B，其中 PE 450927000B；PE hash、header 和来源证据合计 450940899B。17B 不是总读取量；exception 的 semantic/index 读取为 0 不代表物理字节未经过，callee/global/xdata 语义读取及进程读取、目标 CPU 均为 0。metadata 完整 hash/parse 仅为 identity，另行记账。

新规格 `local/migration72/d02-resume-20261116/resource-entry-only-contract-scope/source-only-spec.zh.md` SHA-256 `b6d0d47b80650b538b4b3e801a9b907f93c6877af54816851383a5bfd2c95fb9`，同目录 `review.private.json` SHA-256 `f6e5ac65d5720fb2f969e254b92dcdaf6991fa81b92780f14525f673aeae96c9`；`proposed-source/shared/manifest.private.json` SHA-256 `23010b497401182bb728327875c3404215181639f87f87670db8339b59c37388`，`batch-summary.private.json` SHA-256 `e0ee603ab31222c151c19f13ef6dd28c14f08d62f80d7c25664258f8dc5e243f`。三个子目录保留各自 identity、AOT、入口、解码与结果。

`R3-ACTIVE-ITEM-NEXT-INPUTS-TRIAGE` 已完成限定规格；固定三 blob 候选包进行中，新 row 尚未验。`R3-OLD121-SKILL-ENTRY-TO-PROJECTION-SCOPE` 正审定 typed 条件输入，不自动批准执行。此独立入口问题不依赖此前已撤回的 A 请求明文误验；A/B、preview 及准确依赖冻结保持，D-02 / F-05 / D02-R6 仍 0/6。无目标执行、native、JVM、DB、客户端、测试或 tracked 代码修改。
