# E-01d1 / R3-PROUD-CALLBACK-TAIL-SOURCE-SEMANTICS

2026-11-11C，父 D02-R3。已核唯一直接 tail 边 **`1408b9780→1408b94a0`**。现成固定来源没有找到 94a0 的完整 raw/listing；普通 metadata/AOT 槽也未命中。下一项只请求该条件 target 的完整原窗，优先 current unique pdata；真实 extent/预计 codeBytes 保持未知，不展开 crypto 库或初始化树。

输入为[条件入口 prefix](CLIENT_72_D02_PROUD_EC8_CONDITIONAL_TARGET_PREFIX_20261111C.md)和 `local/migration72/d02-resume-20261111c/proud-ec8-local-semantics/conditional-target-prefix/{fresh1,fresh2}/`。本项只消费旧原件/固定索引；忽略输出根 `local/migration72/d02-resume-20261111c/proud-callback-tail-semantics/`。

## E9 原证和条件参数

5B 原 bytes `e91bfdffff`，SHA-256 `f43d94be74a12dacdfa8746d56e13b9f52fdca42bfd1a632d51407e8f8662cda`。`1bfdffff` 的 signed rel32 为 `-741`，`1408b9785-741=1408b94a0`。这是 JMP，不是新 CALL：执行此指令只转移 RIP，不新增返回地址，也不改显式参数、GPR/flags 或原栈。helper 原 CALL 留下的返回地址仍在，但没有证据说明 94a0 最终如何返回。

两 fresh 的四件逐 byte 相同；result 10603B SHA `d931c1113fcc52c7606a33bcb2d8b8d94b2002cae62b811f3961a2a1d896b97d`。另从原件独立核五次 1B 地址恰为 `9780..9784`、一条完整指令、pending tail 空、首 transfer 即停，没有 `9785` 或 target 读取。本项未声称第三次 intake 通过。

若 helper 那次间接 CALL 实际选择了前置局部 store 的 9780 地址，则到 94a0 的条件参数仍是 `RCX=address(14574d560)`、`EDX=800h`、`R8=address(145872d30)`，R9 未显式准备。前置 14B 仅证明从 `141126812` 顺序落到 store 时的赋值；41PC 是排序命中集合，不是动态边。**global/current/natural callback 均未知**。此 5B 不写 seed、不建立 quarter，不补实际 seed/table 或成功状态语义。

## 只查新 94a0 的来源结果

按 REA 技能复用既存固定源，未重复 9780 旧 target 检索。对 `1408b94a0` 定向查同名文件及 tools/docs、analysis/evidence、local 现成 JSON/TXT/ASM/MD/PY 索引，排除依赖/参考及 A/B 冻结根。命中仅新 prefix asm/result、独立审计与专题；没有完整 target code raw/hash/listing。这是已查范围内的结论，不是声称所有本机数据均无来源。

`tools/client72_seed_crypto.py` 的 native allowance `[140001000,1421c2096)` 确实包含该地址；它只是旧离线 harness 的许可范围，不是函数体、pdata 或语义证据。Ec2b/seed_crypto 模型、相邻地址和前一份 prefix 都不能代替 94a0 原码；本项未导入/执行模型。

固定 metadata 117201003B SHA `df52eb70fe8e70dfecd1059e898d15e288cfa98bcefc78bcaabf753ae150349f` 经拒绝 pickle globals 的 DataOnly 查询：89748 types、741021 ordinary methods，精确 registeredAddress 94a0 命中 0。business 索引 718579 entries 无该键；signature 索引 326354 methods 命中 0。因此没有 ordinary method index/AOT 槽，也没有普通 AOT 新读取；不外推为不存在 native/generic 注册。

## 单一最小完整 source 请求

私有 `next-window-request.private.json` 固定唯一 entry `1408b94a0` / RVA `8b94a0`，锚点只用已存 E9 和前置条件，不伪造 E8 caller。真实 pdata/end/extent 未存，预计 codeBytes=null。**拟议硬 cap 1024B/512 条仅是供主线审定的单入口研究预算，不是原证长度**；不从到 9780 的距离推 736B，也不从参数 800h 推长度。实际只读取 current unique pdata 指定的完整范围。

- 先核 request 的 28 项来源/decoder pins、saved E9 位移/五次读取账和原 14B 条件标记，再核五完整 stock、AMD64/PE32+ 和 preferred base。新 anchor/caller/AOT 字节为 0。
- 查询 current exception index 的唯一 exact/containing/overlapping pdata，begin 必须为 94a0；先保存原 12B 和 bodyRead=0 的 pre-body。范围必须完整可执行 file-backed、无分歧/交叠且不超主线批准 byte cap。无 unique pdata、非执行或超 byte cap 均停在 bodyRead=0；若 leaf 无 unwind，只交真实诊断让主线另审 local contract，不自动 fallback。
- 按拟议 cap，body+原 pdata 最多 1036B；全 stock hash 输入 550661784B、exception index 14344080B、header/section metadata 最多 8192B 分列。指令数只能在 admitted raw 读取后核；若超 512 条，真实保留已读字节和失败状态，不能写成 bodyRead=0。
- 只交这一个完整 code.bin、连续 asm/pdata 和原 CALL/RIP 引用。双 fresh 一致及独立 artifact 审计，主线另做第三 intake；不跟任何 successor、cell/global、间接目标、xdata、邻窗，也不执行目标。

建议 Terra 唯一可写新 `tools/migration72/proud_callback_tail_window.py`、对应 `test_proud_callback_tail_window.py`、新 `docs/CLIENT_72_D02_PROUD_CALLBACK_TAIL_WINDOW_20261111C.md`，独立输出仅 `local/migration72/d02-resume-20261111c/proud-callback-tail-semantics/tail-window/`。其余文件只读。测试核原 E9 与漂移、身份/decoder/边界/byte cap 在 body 前停止、完整重组/短读及指令 cap 的真实读取状态；独立 oracle 不抄 collector 分类。

后续语义只问：原 R8 seed 指针何时/以何宽度写，RCX buffer 实际生成什么，八 quarter/逆关系及 EAX 路径是否在窗内闭合；若委托，仅交首个准确必要 source 边，不扩全树。当前算法源未闭，不能给真实 codec 两字段投影期望。

准确 A allocator/第二返回/Default 归属或 B loader CALL/data 边只保留边并停止该依赖，不以 callback 名字重包被拒核心；新拒绝保留原提示/site，立即停动作及依赖，不换方式。删除被拒动作和产物后，本项只靠独立固定 E9、旧 source/index 和身份报告仍成立。

实际完成旧原件/读取账、27 source pins 和三个精确声明索引的复核；新 PE/body/global/cell/后继/live/CPU 读取为 0，未实现 collector。review 8578B SHA `6782e68346a63597adc7279a4b5a271c19b2d5a881a3eadf270594c608ac7c4b`；request 12096B SHA `32d9d0d53e30d88728b5b149e5103b863ef1b0ca431ffa09bb45a4dc35ea1898`。actual seed 写/epoch、forward、encoded/getter/currentTarget 保持 null，actualRow/resourceReady/D02-R3/D-02 保持 false；reader、QueryWrapper base 寿命、实际 41105 row 仍单列。无 Git、构建、Java、DB、客户端或部署；共享索引交主线维护。
