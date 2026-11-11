# E-01d1 / R3-PROUD-EC8-LOCAL-BINDING-SEMANTICS

2026-11-11C，父 D02-R3。已独立确认：**仅从 `141126812` 顺序执行这两条指令时，将地址常量 `1408b9780` 写入 EC8**。现成固定来源未找到该地址的完整带 raw 原窗；固定 ordinary metadata、business 和 signature 索引均无精确命中。因此只交此条件地址的唯一 source 请求，不把它称当前 callback、Ec2b、随机表或实际初始化。

输入为[14B 局部原窗](CLIENT_72_D02_PROUD_EC8_STORE_SLICE_WINDOW_20261111C.md)及其双 fresh 四件。忽略根 `local/migration72/d02-resume-20261111c/proud-ec8-local-semantics/` 保存语义复核、下一窗 request 和获准文案 diff。旧普通边界停止、旧 raw/request/算法只读保留；没有接续 A/B 拒绝动作。

## 局部原证和条件链

`slice.bin` 原字节 `488d05672f79ff488905a8e66104`，14B SHA-256 `c7730f930c4f55575aaa824be335917b027790f67be9b0b5d91fcccc4f62250f`。fresh1/fresh2 的 slice、asm、pre-slice、result 四件一致；result 7949B SHA `d4335ed56286ba101e18be919a793dcd093ce05356b70c416340f23f3e49893f`。本项没有声称第三次集成验收。

| site | 独立 raw 算术与语义 |
| --- | --- |
| `141126812` | `48 8d 05` 为 64 位 RIP LEA，源不是内存内容。signed disp=`-8835225`；`141126819+disp=1408b9780`，写 RAX。 |
| `141126819` | `48 89 05` 为 64 位 RIP store，来源为同一 RAX。signed disp=`73524904`；`141126820+disp=145744ec8`，写该槽的 qword。 |

局部结论 `localBindingProvenUnderFallthrough=true`；`globalBindingProven=false/currentCallbackTarget=null/naturalExecution=false`。41PC 是 `sorted(counts.items())` 命中集合，不是边序列；不能证明自然执行了该 pair、无外部直接进入 store、producer 支配全部路径，或后续没有覆盖 EC8。

与[117B helper](CLIENT_72_D02_PROUD_SCALAR_TABLE_BUILDER_SEMANTICS_20261111C.md)拼接仍需要另一个明确条件：**该次 `14146af23` 间接 CALL 实际选中这个地址**。只有在此条件下，原 131985 caller 和 helper 可给出 `RCX=address(14574d560)`、`EDX=800h`、`R8=address(145872d30)`；R9 未显式设置。本项从现成 120B caller 和 117B helper 原 bytes 另核 seed LEA、RCX→R8 和间接 cell 位移，不读取 seed、buffer、EC8 内容或 target。这个条件参数合同没有证明 target 写 seed、buffer 四 quarter/inverse 或 EAX 状态含义。

## 现成 target 来源核查

按 REA 技能复用已有固定源，定向检索 `1408b9780` 的大小写变体及同名文件：tools/docs、analysis/evidence，以及 local 的现成 JSON/TXT/ASM/MD/PY 索引；不查 PE 字节、排除依赖/参考和 A/B 冻结根。命中仅为新 slice 的 asm/result、独立审计和专题，没有找到 target listing、code raw 或完整 body SHA。Ec2b/seed_crypto 现成材料中的模型和 native-library 允许范围不是这个函数的原窗，不能替代源码或给它命名。此结论只覆盖上述现成来源，不声称本机所有数据均不存在。

固定 `r3-equipment/metadata.private.pkl` 117201003B SHA `df52eb70fe8e70dfecd1059e898d15e288cfa98bcefc78bcaabf753ae150349f` 经拒绝 pickle globals 的 DataOnly 读取：89748 types、741021 ordinary methods，`registeredAddress==1408b9780` 命中 0。`analysis/business-methods.json` 718579 entries 无该键；`evidence/client72-signatures.json` 326354 methods 命中 0。没有 ordinary method index/AOT 槽可据此请求，也没有新读 AOT。不能由这些索引未命中推成无 native/generic 注册。

target 的 current pdata、end 和 code extent **尚未查询/未存**。唯一准确 source 锚点是上述 LEA 常量及局部 store；没有伪造 direct E8 caller，也不把邻地址 `1408b9790`、RET 扫描或旧 native-library allowance 当函数 end。该普通 source 首缺可由本机固定 PE 的单窗后继解除，非外部 blocked；当前尚不足给真正 quarter/seed 投影或实际 codec 测试期望。

## 唯一 Terra entry-prefix source 包

私有 `next-window-request.private.json` 固定条件入口 `1408b9780` / RVA `8b9780`，仅取得**入口形态和第一个控制转移源边**。这是新批准规划的局部 prefix 合同，不是旧 unique-pdata fallback；不要求完整 function、exception/pdata/end/AOT 或 RET 边界，不再机械重查 unwind。最大 **64B / 16 条完整指令**，不是预定读取长度。seed/quarter 算法尚不闭合。

1. 先核 request、24 项固定 pins、五完整 stock 与 decoder，从 saved 14B 独立验证 LEA/store 锚点及局部限制。检查 AMD64/PE32+、preferred base 和唯一可执行 file-backed mapping，先保存 `prefixRead=0` 的 pre-prefix。完整 stock hash 输入 550661784B、header/section metadata 最多 8192B/两次读取分开记账；新 exception、pdata、AOT、caller 均为 0。
2. 从准确 entry 开始，每次只向当前指令的 pending buffer **追加 1B**，用固定 Capstone x86-64、skipdata 关闭、count=1 尝试 decode。指令未完整才取下一字节；完整后必须消费全部 pending bytes、起止连续并重组相符，才开始下一条。禁止预取 64B、16B、15B 或 terminal 后一字节。保存每次实际读取地址/1B、decode 尝试、完整指令及未完尾部。
3. 首次完整 CALL/JMP/条件 branch/RET（包括 indirect）即停，包含该条原 bytes，**即使是 CALL 也不顺读 fallthrough**；其他控制转移/trap 形式交 unsupported-flow stop。仅从原操作数计算 direct target 或 indirect cell 的地址，不读取其内容或任何后继代码。
4. 到 64B、16 条、指令 15B 仍未完整、mapping 结束或 decode 不完整时，保留精确已取 raw/范围/尾部，以 incomplete 返回，不扩窗。全 stock hash 读取与这项逐字节 source 读取分账，不能把先读取满 prefix 隐藏成只解到 terminal。

产物为 pre-prefix、`prefix.bin`、完整指令 asm 与未完 tail、首 transfer/停止原因/真实读取账的 result；fresh1/fresh2 不覆盖，独立 artifact oracle，第三由主线 intake。最小可写文件是新 `tools/migration72/proud_ec8_conditional_target_prefix.py`、对应 `test_proud_ec8_conditional_target_prefix.py`、新 `docs/CLIENT_72_D02_PROUD_EC8_CONDITIONAL_TARGET_PREFIX_20261111C.md`；独立输出仅 `local/migration72/d02-resume-20261111c/proud-ec8-local-semantics/conditional-target-prefix/`。其他文件只读，不实现通用 collector/codec。

独立测试用可控 reader 和字面 NOP/多字节指令、E8/E9/EB/Jcc/RET/FF indirect，断言每次读取地址、完整 transfer 后没有下一 byte request、rel8/rel32/RIP 算术；覆盖未完 opcode、15B/64B/16 条、无 transfer、非执行/歧义 mapping 和身份漂移。saved artifact 审计核 raw=完整指令编码+pending tail、连续 1B 账、精确 terminal end；不能用 collector 分类反造 oracle。未支持的实际字面编码保持未独立核，不猜结果。合成测试不作为 target 原证。

若精确 CALL/data 边进入 A allocator/第二返回/Default 归属或 B loader，记录边并冻住依赖，不能以 callback 名字转交被拒核心。新安全拒绝保留原提示/site，立即停动作及依赖，不换方式。禁止 cell/global、callee、邻代码、xdata 读取或执行注册码、callback、runtime/model。移除 A/B 拒绝动作和产物后，本地条件赋值和此 prefix 请求仍独立成立；任何准确下一跳仅供后续另审，不在本 request 内跟随。

## 文案修正及实际交付

依授权仅修改[旧 table-builder 专题](CLIENT_72_D02_PROUD_SCALAR_TABLE_BUILDER_SEMANTICS_20261111C.md)一处短语：`完整执行 PC` → `按地址排序的 PC 命中集合，非动态边序列`。`wording-only.diff` 保存统一 diff，`wording-before.private.md` 保存修正前文档；字节断言确认只做这一次替换。旧文档由 10759B SHA `f6c6830b07c8e42b86a6f34556b8399b262b7443e192e301985060df2792e6c8` 变为 10799B SHA `6063168aa4ce9c98c3b4ae42f700adbf028baa8f3100f69f24d623880d3a51bc`。未改旧 raw/spec/工具或重跑链，由集成者独立核对并另提交。

实际核了现成双 fresh 四件、独立 raw/RIP 位移、caller/helper 参数流、固定 metadata/index absence 和单处文案 diff；新 PE/global/callee/live/target CPU 读取均为 0。semantics-review 7921B SHA `15d08fd0a75a54a54e9ecb4c5b21ce9c45d5cec932525f3f365e4f2f112bcbac`；request 13430B SHA `0b4db606420fa3935fd7128155a0580456ed0073af0f383e716816fcf2de2d60`。没有实现后继采集器/投影，没有 Git、构建、Java、DB、客户端或部署。actualForward、seed write/epoch、encoded/getter/currentTarget 保持 null，actualRow/resourceReady/D02-R3/D-02 保持 false；实际 reader、QueryWrapper base 寿命和 41105 row 缺口不因局部赋值解除。
