# F-03d3f4d2：完整原始 head span 只读输入 collector

本批新增第三个固定 profile，捕获原 record 声明的单个完整 head span；**只有纯 reader/adapter 人工实现，没有真实 live、Windows API、native CPU、replay 或写操作**。原34项测试、两个旧 profile 和旧证明保留。读取到 block 不能证明分配返回、GC所有权、自然 Avatar 线程或完整构造；全部执行/写权限仍为 false。

主线独立重跑52项人工测试通过，并另构造L87/L208第三图：实际按L请求一次head首读/一次统一final，L208达到56/1244；native U16=32与高位legacy展示分开、tag0按源计算、非零dirty原span及N=U64max原样保留不跟随，内存字节未改。原primary人工全门禁及四tool来源核验通过，2TBI/96B等API账不变。主线proof `local/migration72/d02-resume-20261110/a-tls-preparation/avatar-block-main-review/main-review.private.json` SHA `29afb5cb175458ee28e55b0338af42b17abbb53c9d59e4898fb089f495f472fc`。这些全是人工callback，0真实Windows API/native/live；没有实采block或副本写许可。

## 冻结输入与新源码 epoch

[分配捕获规格](CLIENT_72_D02_AVATAR_CAPTURE_ALLOCATION_SPEC_20261110.md) SHA-256 `04b6cf78d9df9bc8d7c02362deac342fd3566f87896b4b6e4b1adf2f59a46bc7` 是唯一合同。`a-avatar-capture-contract/implementation-spec.private.json`、delivery 与两份23139B审计报告固定 pin；两报告 SHA 均为 `c522c0fc42d7eb79aa122a5bed980dd3f1a345287c1371f8c89a402731f31e14`。

- 新 [head reader](../tools/client72_d02_avatar_block_inputs.py)，复用原 TLS/R1 helpers、原 Pool 条件 helper 和同一个 ledger。
- 新 [人工测试](../tools/test_client72_d02_avatar_block_inputs.py)，自造脏 byte spans/API callbacks；没有使用历史现场 heap。
- [core](../tools/client72_d02_tls_inputs.py) 只增加 `avatar-r1-pool-head` 固定 profile。
- [adapter](../tools/observe_client72_tls_inputs.py) 只增加 `include_allocator_inputs=False/True` 和对应已知 reader/source gate/第四源码 pin；不复制 primary 或 ledger 逻辑。

新 source gate 先核原12个 source pin，再核新五 pin，并按冻结双报告核27个原分配来源。该报告包含新扩展前的core/adapter SHA；两个旧源码明确从本卡 `source-before/` 精确快照核对，**不会把旧pin自动绑定到修改后的源码**。未改的Pool tool和其他原件仍核原路径。当前四tool由新epoch `implementationPins` 另行绑定；旧source审批不能批准本新实现。

## 第三个固定读图

顺序为原 TLS4项与R1九项 → 下列十项 → 原 Pool 最多5项 → 唯一共同 final。原输入/有限分支失败即停止首遍扩展，不拼旧报告、不另选线程/record/head。

| 新增顺序 | 实际地址/宽度 | 保留内容 |
| --- | --- | --- |
| 1 | `M+19cc/1` | 原state byte，0合法，不要求初始1 |
| 2 | `C+48/8 → R` | 指针cell；R不是内嵌record |
| 3 | `R+18/8 → L` | 完整unsigned U64；先核32≤L≤208 |
| 4 | `R+20/8 → H` | 非零head，0保持partial/原slow边 |
| 5 | `R+10/2` | 原U16计数，0与ffff均保留，不执行inc |
| 6 | `C+8/8` | 原U64 counter，0合法，不加猜测32 |
| 7 | `B+5699680/1` | 原header OR byte，不重建header |
| 8 | `M+19e0/8 → E` | 非零依赖；E=0不等于notify=0 |
| 9 | `E+a0/1` | 完整原notify byte，0合法 |
| 10 | **`read(H,L)`** | 一次首读整个原block，N与oldFlags从同一原span解码 |

全部新增span有实际地址/宽度/raw/error和来源site；state/counter/lock原值使用write-origin/read-modify-write-origin标签，不冒称原代码在写前另读它们。采集顺序是规格约定的只读输入顺序，不是执行原指令的时间线。

L实际为32就首读32B，为208就首读208B。L=0/31/209/高64位超界等原QWORD均保留并partial；208是采集政策阈值，不是native尺寸证明。不会截断、分块、补字节、换head、重试或扩大cap。完整span替代head+0/8与head+8/1两次冗余读，没有这两个额外请求。

N取block[0:8]，oldFlags取原byte8。N=0合法，非零也不跟随；其余bytes全部保存原hex和SHA，没有注入对象header、清零或改写payload。记录 `rawBlockHasNonzeroBytes` 只是所给span的观察；人工非零fixture不称自然dirty覆盖。`naturalHeapOwnershipProven=false`。

## U16请求、原tag与有限分支

R1仍一次读取原208B class头，新profile从B4按**U16**解码 `requestU16`/`sizeB4NativeU16`；原helper的U32展示值在新profile明确改名 `sizeB4U32LegacyDisplay`，不用它选择请求或bin。两个旧profile字段保持原样。

支持实际请求32、原CA20已选择已初始化前缀、普通1255槽不等于原comparison cell（不经过未知class替换）、CA2选出原tag路径的有限List分支。其他值原样保存并partial，分别列出未采初始化读 `1405336eb`、class替换call `140533784`、未选tag分支 `14053373d` 或请求政策边界 `1405337d8`。

class+8读取原DWORD低3位，按原 `setne(low3 != 1)`、OR class 推导tag；不强设tag1。C7、CA其余bit、C8和完整头原bytes保留。C7=0、CA bit0置位、oldFlags bit08置位或notify bit0置位均不会被改成fast条件。

## 预算与共同复读

| profile/实际L | 首遍 | 首遍及统一final总上界 | 单读上限 |
| --- | --- | --- | --- |
| `avatar-r1` 默认 | 13/340 | 26/680 | 208B |
| `avatar-r1-pool` | 18/369 | 36/738 | 208B |
| `avatar-r1-pool-head`，L=32 | **28/446** | **56/892** | 208B |
| 同上，L=208 | **28/622** | **56/1244** | 208B |

新profile一般式为首遍最多28读/(414+L)B、含final最多56读/(828+2L)B；Pool条件可能少读。`M+1990` 仍是Pool字段，不代替Avatar `M+19d0`。每个成功unique exact span统一复读原地址/原宽度，包含整个 `H,L`、R/H/L cell及class头；错误、漂移和重叠矛盾保留原始返回并partial，不重新追指针。两遍相同仍不是原子快照或无writer/无ABA证明。

## 准确边界与未闭项

非空C的原只读执行首写前沿仍是 **`140563d2d` 写M+19cc前**；本collector没有执行该指令。报告只条件列出潜在后继边，不执行或赋予副本写权限：

- oldFlags bit08：`140563d85 → 141fdf9c0`，clear body未知。
- 否则notify bit0：`140563e01 → 14056c050`，notify body未知。
- 否则CA bit0：`1405337f3 → 14055ecf0`，registration body未知。
- 否则C7=0：`140525e2a` 的首未采全局 `B+5bc90f0` 读前。
- 其余有限条件：只描述后续 `15183fe92` 的未采 `B+5b74628/8`；未运行409B post-pop窗或outer return。

H=0列原slow尾跳 `140563df5 → 140563e10` 并停止捕获扩展；C=0在原TLS入口partial，不伪造 `140533858` 初始化成功。所有条件都保留原观测，不注入锁、TLS、record、对象或第二block。

`approvedWriteSpans=[]`、`capturedCopyWriteApproved=false`、`capturedWriteExecuted=false`、`nativeCPUExecuted=false`、`externalCallExecuted=false`、`objectsAllocated=false`、`canReplayPop=false`。本工具不授予六个28B副本写域，也不计算/施加七个写事件。

单span最多提供单次pop的潜在输入。完整ctor的20个直接call site/12个class usage roots继续未闭；紧接的空数组usage `B+5b74628`、H+10与Avatar this+70写，及第二对象 `B+5897cf0 → call15183fea8` 均未捕获/批准。不能拿N或首块充第二对象，不能叫完整Avatar或allocator成功。

## adapter与外部门禁

仅显式 `sample_primary(..., include_pool=True, include_allocator_inputs=True)` 选择第三profile；allocator=True且pool=False、非bool或缺少第四源码pin在入口拒绝并finally处理原primary handle。epoch `implementationPins` 必须恰有原core、adapter、Pool tool及新head tool的已审SHA。

API仍为原hThread的两次ThreadBasic/96B独立输出、两次alive、GetThreadId/GetProcessIdOfThread/GetThreadTimes各1和CloseHandle1。没有新增API、线程选择、OpenProcess/OpenThread或live CLI。`naturalAvatarAllocatorThreadObserved`、`naturalPoolAllocatorThreadObserved` 和 `gsBaseRegisterReadDirectly` 均false。

root须另审新source epoch、独立第三人工图/原始ledger/失败路径、实际stock/配置/保护/carrier、实时ready、整轮预算、B临时DLL互斥和同次正常launch原handle的完整image/creation/module身份helper；原handle丢失不得拼报告。真实捕获、dirty证据、CPU及任何副本写权限均由root后续单独决定。

## 本批实际验证

纯Python命令：

```powershell
python -m unittest tools.test_client72_d02_tls_inputs tools.test_observe_client72_tls_inputs tools.test_client72_d02_tls_pool_inputs tools.test_client72_d02_avatar_block_inputs -v
```

**52项通过，0失败/错误/跳过**：原34项测试源码/断言原字节不变，新增18项涵盖177个合法L、非法长度、native U16与全部低3位tag、有限分支、合法零、R指针拓扑、dirty原span/N0/N非零不跟随、10个新span短读/API/OS error、5个Pool尾段错误、28个final位置逐项漂移及失败、重叠错误、源合同/审计/历史源码漂移和四pin/API预算/身份失败。

另在两个before profile的完整、首读错误、final错误场景上，以冻结前core/Pool源码作为只读Python参考，**完整报告和请求序列均相同**。人工bytes不代验自然heap；没有执行任何真实Windows API/native/JVM/DB、GAME_PATH/进程端口查询、客户端、网络、Git或子派。

私有简plan、六源before、after/source hash、实际diff、原始测试argv/exit/stdout/stderr、来源门禁和交付摘要留在 `a-tls-preparation/avatar-block-implementation/`。本批只交纯reader实现，root审核后再决定下一项；没有live、replay或write许可。
