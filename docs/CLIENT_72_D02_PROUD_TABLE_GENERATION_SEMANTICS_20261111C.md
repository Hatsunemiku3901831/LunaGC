# E-01d1 / R3-PROUD-CALLBACK-TABLE-GENERATION-SEMANTICS

2026-11-11C，父 D02-R3。717B 原窗内已包含四个 forward quarter 的初始化、交换和四个 inverse quarter 的回填，**没有把 quarter 构造委托给 provider**。若每组初始化 256B 是一个排列，且外部调用正常返回并保持所需 ABI/工作区，则任意 AL 序列都保持左逆；无需追 provider 的概率或内部算法。这个条件证明不等于已证 provider 纯、不等于实际 callback/自然 table。

输入为[完整 source](CLIENT_72_D02_PROUD_CALLBACK_TAIL_WINDOW_20261111C.md)及双 fresh 六件。范围 `[1408b94a0,1408b976d)`，717B/131 条，code SHA `9af503bd341f1c7b7ef6332585c06bc7ac6767d5de46b97cdcb9122a0dda9eb8`，result 13398B SHA `15391d3cd010c5291b5bba4dc6aa024fdc6ab60d3fa542bfe5bc139f8e824f69`。主线第三已独立验收，`integration/acceptance14/tail717-independent1.private.json` 1104B SHA `0f005f0976d6af229b264a4c2650561e28902f95a20c2ef8df1f8cf9146452c6`：六件与双 fresh 同，131 条/10 控制边/17 RIP、原 pdata 与条件锚点均核。验收仍只到条件 source。

## 逐路径值流

以下令比较处 EDX=800h、`W=R13`、`p=R14`。固定调用链的条件参数为 W=`address(14574d560)`、p=`address(145872d30)`；仍须当次间接 CALL 确实选择局部绑定的入口。前置 41PC 为排序命中集合，非自然执行边。

| 原 site | 本体可证的值流 |
| --- | --- |
| `94ab..9536` | EAX=1460h 调 `141cc01a0`，按其返回 RAX 调整栈；EBX=FFFFFFFFh，比较 EDX 与 800h。不等跳 `96f0`；相等才保存 R8→R14、RCX→R13。首 helper 保持这些入参并返回合适栈大小是尚未在本包独立核完整 raw 的前置。 |
| `9539..954b` | `1408b9770` 返回的 EAX 先零扩展，再取 `RDX=W+u32(EAX)`；`RCX=rsp+28h` 调 `1408b6940`，其返回值被忽略。offset、该处输入范围和 context 行为未知，没有边界/API 状态检查。 |
| `9553..9650` | 预装十个 vector，另六个在 quarter 循环内读取；每组 16 次 16B store，填 `[W+100h*j,W+100h*(j+1))`。初始化字节 C 的实际值未读，不能假定 identity/ramp。 |
| `9660..968b` | j=0..3，每组 i=0..255 调 `1408b6950`。只取返回 AL，MOVZX 为 u∈[0,255]；交换 `F_j[i]` 和 `F_j[u]`，两次 byte store 位于 `9678/967b`。`(j<<8) OR u` 因位不重叠等于加法，不会跨 quarter。 |
| `968d..96ca` | inverse base 为 400h+100h*j；s=0,2,..254，每轮两次 byte store：`I_j[F_j[s]]=s`、`I_j[F_j[s+1]]=s+1`，site `96a9/96bb`。最大偏移 7ffh，MOVSXD 输入均为正小偏移。 |
| `96cc..96eb` | 四组完成后第 1025 次调 `1408b6950`（此前 1024 次用于交换）。`96eb` 将此次**完整 EAX** 以 **4B DWORD** 写 `[p]`，不是只写 AL；seed=0 也允许。seed 写在全部八 quarter 之后。 |
| `96ee..9700` | 完成路径设 EBX=0；共享 cookie/epilogue CALL 正常返回且保持 EBX 后，`EAX=EBX`。所以长度满足且正常完成时明确返回 uint32 0，与 seed 是否为 0 无关。长度不符则跳过本体 buffer/seed stores，正常返回 uint32 FFFFFFFF；不能由此证明外部 helper 没有其他副作用或失败都以此状态返回。 |

本体没有入站 R9 的直接读写；未审 callees 是否读取 R9/其他易失输入仍未知。它们的正常返回、ABI 非易失 GPR/XMM 保持，以及不额外破坏 W/已填 inverse/所用常量，均为**未独立验证条件**，没有用模型补有利值。首栈 helper、context/offset 合法性、cookie epilogue 也仍属正常路径前置。固定 W 与 p 的地址不重叠，但当前可访问性未观察。

## 八 quarter 与 scalar 条件合同

令 `F_j[b]=U8[W+100h*j+b]`，`I_j[b]=U8[W+400h+100h*j+b]`。每次交换都是一个 transposition，自交换亦然，所以保留初始化 C_j 的多集。若 C_j 含 0..255 各一次，F_j 仍是排列；inverse 循环恰遍历每个输入位置，按其唯一像写回下标，因而对全部 byte：

`I_j[F_j[b]]=b` 且 `F_j[I_j[b]]=b`。

反之，C_j 有重复则交换后仍有重复，inverse 对同一像的最后一次写入不能同时恢复两个位置。因此对所有 byte 的左逆成立，当且仅当 C_j 是排列（上述正常返回/非干扰条件成立时）。这个证明允许任意 AL 返回序列，不要求均匀分布；末次完整 EAX 可为任意 uint32 seed。

接到[已验 setter/getter](CLIENT_72_D02_PROUD_SCALAR_SETTER_SEMANTICS_20261111C.md)后，正常同表/同 seed/同 epoch 的存储读回可由该不变量推出。逻辑 quarter 顺序为 big-endian 输入 byte：`b_j=(x>>(24-8j))&ff`，setter 的四段映射打包后 XOR seed，getter XOR 同 seed 后各段取 inverse；内存 DWORD 的 little-endian 存储另计。既有正常物理内存、长度、存储值未被改写条件仍必需。

这里进一步消除了“必须先恢复 provider 算法才能证明 scalar 左逆”的依赖；**没有消除 provider 实际非干扰/正常返回、context 访问、运行常量未改写、实际绑定/epoch、下游对象容量/复制、reader/QueryWrapper 寿命和实际 row 的依赖**。固定 EDX=800h 的正常 callback 返回 0，才会使 117B helper 走 800h 分支；请求长度仍不是已观测对象长度。

## 最小下一源：两段静态数据合计 256B

本轮定向查全部 16 个 RIP 地址，现成固定索引仅命中本 717B 的 asm/result，没有已存内容窗；未读取 global。初始化排列谓词尚未证。精确缺口只需文件映像数据：

| 静态范围 | 大小 | quarter 初始化顺序 |
| --- | ---: | --- |
| `[1422502f0,142250300)` | 16B | offset 00h；load `95b7` → store `95be` |
| `[1439725a0,143972690)` | 240B | 15 个连续 16B 块，依序填 offset 10h..f0h |

所有中间块均有原 RIP load，合并没有额外邻数据。完整 16 项 load/register/store 对应及原 hex 保存在 `constant-data-request.private.json`。下一 Terra 只核 saved 717B 原 RIP/16B 宽度/store 顺序、五 stock、PE 映射及 flags，先保存 staticDataRead=0，再恰两读 16+240B。新 code/pdata/exception/AOT/provider/W/seed/cookie/live 读取均为 0；header/section 最多 8192B、全 stock hash 550661784B 单列。

按 store 顺序拼 C=256B，独立核 `sorted(C)==bytes(range(256))`，保存 histogram/重复/缺失与 raw SHA；identity 只可另记，不是要求。失败不补字节、不修成 identity、不转读 provider。通过仅证明**固定文件映像**的初始化数据是排列；自然运行是否读取同一未改写常量仍未知，provider purity 仍未证。

后继唯一可写新 `tools/migration72/proud_initial_quarter_constants.py`、对应 `test_proud_initial_quarter_constants.py`、新 `docs/CLIENT_72_D02_PROUD_INITIAL_QUARTER_CONSTANTS_20261111C.md`，独立输出 `local/migration72/d02-resume-20261111c/proud-table-generation-semantics/constant-data/`。测试覆盖原引用/order/size 漂移在数据前停止、恰 256B 两读、permutation 与重复/缺失反例、文件/自然标记分离；双 fresh 和独立 artifact oracle，主线第三验收。不需要新 generator body 或 entropy 库 source。

## 有限两字段投影规格与交付

`projection-spec.private.json` 限 key22101 的 203376（native30h，输入221）与 203373（native38h，输入1），保留固定桥 token/span/presence。先接受并冻结上述 C 的 raw SHA/permutation；缺失或失败就不生成。只读复用现有 conditional projection 的 `validate_bridge/setter/getter` 数学函数，不调用其旧 `load_inputs/project` 或沿用历史 inverse 重建 forward 的 provenance。

provider 值只用四组明确 synthetic fixture，每组 1024 个 swap U32 加第 1025 个 seed，不冒充实际返回：self-index→F=C/seed0；固定 AL0→C 右循环移一位/seedFFFFFFFF；固定 AL255（不同高24位）→同一闭式 oracle/seed80000001；每组仅首次交换 0 与 j→各 quarter 的明确单次置换/seed12345678。独立 inverse oracle 从这些闭式 F 枚举 value→position，与原 paired-write 算法比较全部1024项；再核两个字段及 0、01020304、80000000、FFFFFFFF、seed/forward 反例、调用计数和偏移边界。所有 fixture 编码/getter 标签与 actual 分离，不增加任意 row/provider CLI 或通用 codec 工具。

该投影若另获执行授权，仅新建 `tools/migration72/proud_generated_scalar_projection.py`、对应测试、新 `docs/CLIENT_72_D02_PROUD_GENERATED_SCALAR_PROJECTION_20261111C.md`，输出到本忽略根 `generated-projection/`；已有工具/结果只读。当前只交规格，未实现/运行投影、callback、模型或 target CPU。

实际完成双 fresh 六件、717B/131条重组、17 RIP 独立位移、关键 opcode/循环边界和原证值流复核。review 12572B SHA `6bff9b794b70765433cac0e80bfbd46d3dc13abbfcbb30542c30b7e8dd240e0a`；常量 request 11946B SHA `87e71a4105dce31f2ab9d5ac91c5b290c948e81186443d42f86ec548335ea996`；投影规格 6331B SHA `34921b872031d2509bdb10166884987e39ee3ae4da0b871263509b0a023933c8`。新 PE/global/callee/live 均为 0，无 Git/build/Java/DB/client/部署。131984→TLS→A 与 B 保持冻结；准确拒绝依赖只留边停枝，新拒绝原文保留即停，不改路重试。

actualEncoded/getter/currentCallback/natural epoch/实际41105 row、hot map O0/当前采用仍未知；resourceReady、D02-R3、D-02 未晋级。不补空 OpenConfig/addProps/paramList，不生成 Java-ready 资源。共享索引由主线维护。
