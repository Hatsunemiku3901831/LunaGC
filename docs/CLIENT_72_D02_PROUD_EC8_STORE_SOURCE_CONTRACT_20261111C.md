# E-01d1 / R3-PROUD-EC8-STORE-LEAF-SOURCE-CONTRACT

2026-11-11C，父 D02-R3。下一项仅规划 **`[141126812,141126820)` 14B** 的局部静态 source，核一个候选 producer/store 对；本包未读取新 PE。旧 unique-pdata request 已正常停止，原诊断保留。本项是新的局部源合同，不采用旧 fallback，也不声称完整函数、支配关系、自然执行或当前 callback target。

关键勘误：旧 `fragment` 输出的是 `sorted(counts.items())`，故 **41PC 是按地址排序的命中计数集合，不是动态边序列**。前专题的“完整执行 PC”只能解释为集合覆盖，不能据此升级为 `141126812→141126819` 已自然顺序执行、没有分支或每条大小已知。

## 已验前置与同源限度

按 REA 技能复用[117B 条件语义](CLIENT_72_D02_PROUD_SCALAR_TABLE_BUILDER_SEMANTICS_20261111C.md)和[注册入口实际边界停止](CLIENT_72_D02_PROUD_SCALAR_CALLBACK_REGISTRATION_WINDOW_20261111C.md)。没有新检索项目、目标执行或 runtime/model 调用。

- callback 原 site 为 `14146af23 -> U64[145744ec8]`，已验 117B body SHA `b82fe715bc81becf0bc3359f9f798eaa7993aea3b5727893ec441bf8e5fa6abf`。静态 target 仍未知；本项只服务该绑定首缺。
- 当前注册入口两个 fresh 各三件一致：boundary 6454B SHA `f54b63498c9c1426288fd44631620adc25b6cd70a612192c32d0cd4f775778b9`；pre-body/result 各 6620B SHA `c983b11aa174b12b73744d606410cd1cdbf5e4b2de3eae247656ad8078b3f8b1`。`exact=[]/containing=[]`、exit 2、bodyRead=0；`overlapping=[]` 表示没有 exact extent 可检查，不能解读为某个猜测跨度无交叠。无安全拒绝，非外部 blocked。本项未声称第三次集成验收。
- `analysis/disasm-1411267b0.txt` 740B SHA `cb07d1f3cb2116fd4a37db1bf84833cd3655ca3c0ea4673546a9083d1543e6d9`，最后一条是 `141126804` 的 LEA；此前能证同一槽族的若干 LEA/store，包括 EB8。它没有 `812/819` opcode，更没有 EC8 store 或 assigned value。
- `evidence/ec2b-trace{,-repeat}.json` 各 542378B、同 SHA `b7d8076d6496222bb72e23c7f1921db7fe475f3542e4c4a056962a6631c85209`。16 fixtures 的 registration native PC histogram 相同，41 个地址各命中一次、registration models 为空；另有人工 stop 地址计数。没有指令 bytes、寄存器值或边列表。两份早期 init exploration 的 PC 集合只作旁证，其报告没有独立输入身份字段。
- 旧正式 trace 保存三输入 SHA：EXE `8b014f64b5dc12fbc476e0c362b053abfdd7a1378cd282e4ad81057621dbd6b5`、global metadata `45dc7a44dc9fc187146c52f6c33de57e2d1c1dcfec57f8736431553460f6ef2a`、startup metadata `b314810840ca6f495822c9c44483dde2394b9f09ed3c6b13c2782092f04d5105`，均与当前诊断 stock 相同。旧 trace 未保存这些输入的大小或生成器 SHA；当前冻结大小分别为 450927000/83644188/4116148B，不能倒写成旧 run 的实测大小。现存生成器有 read_verified 门，但其当前源码 hash 不证明旧 run 使用完全同版生成器。
- 旧 Offline72 源码从 PE preferred base 映射代码，当前诊断工具显式要求 `base=140000000`。历史真实观察的 base `7ff636390000`、entry runtime `7ff6374b67b0` 相减为 RVA `11267b0`；其入口 32B 对照不覆盖本候选。新 slice 使用 preferred VA `141126812..820` / RVA `1126812..820`，必须重新核当前固定 EXE 的 base，不复用历史 runtime VA/PID。

RET `1411268c1` 的历史 pause 记录只作上下文，不参与本 slice 的边界门。无 unwind 是普通元数据现象，不妨碍另审局部原指令，也不自动授权取 274B。

## 唯一 14B 请求及允许结论

固定 PC 集合中的局部地址为 `...804,80b,812,819,820,827...`；候选前 PC 为 `80b`，两个待核 PC 为 `812/819`，后 PC 为 `820`。两个差值均为 7，只提供预定读取端点与待检验长度，不证明实际两条各 7B。`819` 是 EC8 writer 的**待核假设**；选择该 pair 不赋予相邻槽或 target 任何值。

14B 是一次自足 producer/store 候选核验的最小合同：只取 store 不能独立确定源寄存器；完整注册函数对这个条件命题并非必要。匹配条件固定为：

1. 所有 14B 恰好解为 `141126812`、`141126819` 两条各 7B 指令，最终 end 正好 `141126820`，原 bytes 重组一致。
2. 前条为 `LEA r64,[RIP+disp32]`，不读取内存内容、不依赖入口寄存器；后条为 `MOV qword ptr[RIP+disp32],同一 r64`，独立计算的目的地址必须恰为 `145744ec8`。没有 segment/address-size prefix、CALL、branch、RET 或其他指令。
3. 由独立字面 oracle 核 REX `48/4c`、opcode `8d`/`89`、`ModRM & c7 == 05`、含 REX.R 的寄存器相同，以及两个 signed LE32 displacement。producer 值为 `(141126819+disp1) mod 2^64`，store 目的为 `(141126820+disp2) mod 2^64`；与 decoder 操作数独立比较。没有预填 producer target 或当前原 bytes。

若匹配，只能说：**从 `812` 开始按解出的局部直线顺序执行时，该 pair 将计算出的地址常量写入 EC8。** `localBindingProvenUnderFallthrough=true`，但 `globalBindingProven=false/currentCallbackTarget=null/naturalExecution=false`。外部是否直接跳入 `819`、producer 是否支配全部到达路径、注册函数是否被自然调用、后续 writer 是否覆盖 EC8 均未知；本 slice 不要求或声称全函数 CFG closure。

任一 opcode、大小、寄存器、宽度、目的槽或完整 decode 不符，保存原 14B 并交本候选 not-found/inconclusive；不能据此称整个注册函数没有 EC8 writer，也不补邻 slice、producer 前驱或 274B。不同但可能有效的编码同样超出这一个明确候选谓词，不在 collector 内扩成通用符号分析。

## Terra 最小机械包

固定 request 位于 `local/migration72/d02-resume-20261111c/proud-ec8-store-contract/source-request.private.json`。26 项 source/decoder pins 和五项 stock size+SHA 全量列于该文件；`planning-review.private.json` 记录本轮 artifact 核对和同源缺项。现有原件及旧停止诊断均只读。

- **先于 slice 读取：** 核冻结 request/source/decoder、五 stock，检查 AMD64/PE32+、preferred base，并仅用 PE header/section metadata 证明 14B 在唯一可执行 file-backed mapping 内。先保存 `sliceRead=0` 的 pre-slice；不再要求 pdata、函数 end、RET 或 AOT。
- **预算：** 新 code 恰 14B、一次读取、两条指令；header/section metadata 最多 8192B/两次读取，记录实际区间、总读取与去重字节。完整 stock hash 输入 550661784B 单列。新 exception index、pdata、caller、RET、邻窗、global/cell、assigned target、callee、live 和 target CPU 均为 0。没有理由重读 14344080B exception 目录。
- **可写文件：** 新 `tools/migration72/proud_ec8_store_slice_window.py`、`tools/migration72/test_proud_ec8_store_slice_window.py`、`docs/CLIENT_72_D02_PROUD_EC8_STORE_SLICE_WINDOW_20261111C.md`；独立输出仅 `local/migration72/d02-resume-20261111c/proud-ec8-store-contract/source-window/`，fresh1/fresh2 不覆盖，主线另做 main-third。
- **产物与独立验收：** pre-slice 身份/映射/预算、`slice.bin` 14B、两条 asm、raw 算术及局部判定 result；双 fresh 一致、主线第三 intake、只读 saved artifacts 的独立 oracle。合成测试覆盖匹配对、错误 opcode/内存 load/宽度/寄存器/槽、分支/短读/不完整 decode，以及身份/base/非可执行或歧义 mapping 在 sliceRead=0 停止。合成输出不是实际 EC8 证据。

若 assigned address 命中准确 A allocator/第二返回/Default 归属或 B loader 拒绝依赖，只记录边并冻结后继，不读取它；新拒绝保留原文/site，立即停动作及依赖。不得执行注册码、callback、runtime/model，不借 leaf 重试被拒核心。本合同只依赖独立固定 PC/原 listing/普通边界诊断，删除 A/B 被拒动作和产物后仍成立。

本包实际只核旧 JSON/source：双 fresh 三诊断一致、16 fixture PC histogram、sorted 记录器语义、三 stock SHA 对应、历史 base/RVA 算术及 request JSON；新 PE/global/callee/live 读取均为 0。planning-review 8959B SHA `220406485981f36bd56f368238702c5ae6e893151a508e119176b0903def788e`；request 12106B SHA `f96b51ce2592315c77ce56daf0daf35991ee73f913b132d079f0bfd664a25c3b`。未实施后继 collector/test，没有 Git、构建、JVM、客户端或部署。actualForward/seed epoch/encoded/getter 保持 null，actualRow/resourceReady/D02-R3/D-02 保持 false；共享索引由主线维护。
