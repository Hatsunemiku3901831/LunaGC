# F-03d3f4d2：自有 trace 构建与首轮实际证据

**首轮 W1 在 CALL 获准继续之前停止，未通过单步校准。** 输入只改 TF，但同暂停期回读额外出现 bit1 差异；原件和失败状态保留。W2、W3、完整904B helper、第二分配及 Avatar 均未由本轮通过。

## 已完成的源码、构建和材料门

按[冻结接口](CLIENT_72_D02_CLEAR48_TRACE_IMPLEMENTATION_CONTRACT_20261111.md)，Terra 分别交 child、parent、离线 validator 和 root driver；root 唯一维护 header/build/bootstrap。8组 artifact 人工门、4个 validator 测试方法（三个合成 case、24原件和5回执变异）、9组 driver mock 均通过。它们没有执行 native witness，不能作真实结果。

首构建 exit5，唯一告警为 creation FILETIME 的可能未初始化；root 显式初始化，失败 API 仍记录 null，未补称观察值。第二构建 exit0。独立源审随后发现失败 pending 记录不准确：未知退休被写成 false、日志失败可能丢 post 或重复事务；parent 已改为 null/unconfirmed、成功 Get 先保留同 stop、emit 只尝试一次，并补齐已有 Win64 shadow-space 上界。原 main1/main2、源码、日志、PE 和候选审核均保留。

第三新目录构建 exit0，48个 header offset/两个 size 断言实际编译通过。实际 child/parent、map 和源快照逐项核对；129个实际 include、5个链接库、2个 c1/c2 组件和10份构建日志已独立复核。另两库仅确认搜索，不算已链接。编译日志证明组件路径，不声称取得完整 OS 模块装载清单或独立构建中逐文件采样。

root 从实际 PE/map 核25B wrapper、11/23/3B三 leaf及13B XCR、RVA、权限、真实 C caller、唯一 wrapper pdata 和 unwind `0104010004420000`。Capstone与dumpbin的原bytes相同；两个明确文本差异是 CC 的 int3/int 3，以及同一64位 MOV 的 movabs/mov，10B MOV跨行bytes也完整复核，没有改代码或放宽指令长度。

| 第三构建材料 | SHA-256 |
| --- | --- |
| parent源码 | `e5b60ee368d724e947e2aad0d12fa2cbe1fe6201506d3775f441cc02a6f67cea` |
| child EXE | `9616750854a1e9f99360835b598128e22d9b97a5c729e4ac235937ea01de65fb` |
| parent EXE | `84547fc4a8c411d78f693f3b926ceb37063cdb7e70200cbbba0d257510467bb6` |
| bootstrap128B | `c4bc0d86a243437105cde19e8b0f623d5d581ce7e4afdabfc730da0aa5b202dd` |
| root PE/caller/unwind审核 | `d8db0feedb7a7e523ea51e419255a7e0d2e8db51f1b6c8ab8f9ed5bf742e6ea4` |
| root独立依赖审核 | `7d4d17e850a9ec0c489d3608b67e3efd1f0f9cca2693e700593e41f49f59eda7` |
| 失败记录纠正闭环源审 | `38d1194c937ba2f7f016842b602cfeba66662f549f0892f2bb5e1b87eb7eae45` |

静态审核前两次脚本适配失败也保留：首次未加入既有Capstone目录；随后解析器不认识上述dumpbin文本形式。仅复核既有raw输出后完成该审查，没有执行 witness 补证明。

## 唯一一次 W1

原件在 `local/migration72/d02-resume-20261110b/a-clear48-trace-witness/w1-main1-20261111/`；root监督回执记录新 parent 创建身份，parent原件另记录新 child 的创建句柄、PID/TID及creation FILETIME。没有复用旧ready、PID或地址，没有附加其它进程。

原系统断点和自有INT3均已取得 raw ExceptionAddress/Get RIP；自有marker的实际RIP等于Call label，descriptor/source/真实stack预检通过。在同一个Arm stop：

| 观察 | 原 EFlags |
| --- | --- |
| 第2次Get | `00000202` |
| 提交CONTROL-only Set（仅TF输入差） | `00000302` |
| Set返回 | TRUE |
| 同stop第3次Get | `00000300` |

所有15个GPR、SegCs/Ss、RIP/RSP前后相同；回读与提交只有bit1差异。parent按旧严格raw equality停止为 `setReadbackMismatch`。此时tx1没有授权Continue，`false/not_executed`，退休数0；不能把Set成功或TF已置位当CALL/RET成功。

实际账为8事件/8 Wait/3 Get/1 Set/5读303B。parent退出2；parent通过own句柄终止child为90，原EXIT事件、Continue、signal、退出码和两个PI句柄关闭均记录成功。root前后材料hash一致、未超时；随后当前owned parent/child进程盘点为空。root回执的child级字段仍是未观察null，不能用它代替parent原件；两层证据分开保留。

events SHA为 `7377c54ffa294c714214f8179476ffe0a2c9a077f44775623f952cc8dd306932`，Sol max独立失败原件冻结 proof SHA为 `876b892e90bf754b0d8d5b5e4b307197b48066de8b660c88348174e5bdc4abc0`。旧W1持续为失败，后续case没有启动。

## 下一出口与边界

Sol max核同epoch源码、SDK/ISA和Get/Set公开表示，Terra max按中文有界计划补官方/固定GitHub来源；尚不改raw记录或blind mask，不以新合同追认旧W1。只有准确语义和必要实现/纯门明确后，root另定新epoch与单case步骤。实际安全门禁/Daybreak问题只记录停该包；没有证据时不将普通ABI疑难自动归入该队列。

本轮没有客户端、DLL安装、生产Java/Proto/配置/资源或数据库变更；不需GS重启或portable部署。C资源分支继续独立推进，D-02/F-05/D02-R6及全部六项客户端验收保持未完成。


## main4 / V2与第二次W1（仍失败）

主线接受[限定EFlags合同](CLIENT_72_D02_CLEAR48_TRACE_EFLAGS_20261111.md)后，两个Terra分别交[parent V2](CLIENT_72_D02_CLEAR48_TRACE_EFLAGS_PARENT_V2_20261111.md)与[独立validator](CLIENT_72_D02_CLEAR48_TRACE_EFLAGS_VALIDATOR_V2_20261111.md)，root26纯门通过。新main4构建exit0；五实际源及epoch、129include、5链接库、2compiler DLL和10日志已第三审核；当前内容hash不替代编译时独立采样，完整系统模块清单仍未知。

| main4材料 | SHA-256 |
| --- | --- |
| parent源 | `781e5ee0c22f2d7d1583f6b59d692db827af03a84e343081cea92a2640771c7e` |
| child EXE / map | `9412200c5c820a885a925b627b14ef92b09192ea747d335c2fc91fadea28ffc4` / `372959fb6bc970026d2ae860cbd72e017face8d69f6ea609ac7f94b097324867` |
| parent EXE / map | `151e6c1bae8fbfe350ddf93b97bfa4f90c943098d808ea600a3e58f58cf1f07e` / `7cf5b8c9e361912e8a5e7c23affef1b4f2ae8dec5baf29b080d433b585dd11c4` |
| bootstrap128B | `d11f5af63a1cf40661291d0bc5a9232edd96d5e3be0d3dc26752fb424e0cba81` |
| root PE/caller/unwind审核 | `6d66cd1a7c31225a8903d155ce4c50e30dea78d3cf91e1c004d238c8375097c8` |
| root依赖第三审39876B | `bb38f101ae5105fe37b5119ca9a3d228e1037c3d70b6f2b719bb18324a22412b` |

新独立输出`a-clear48-trace-witness/w1-main2-20261111/`，未复用旧身份。root runner实际exit1、parent2，未超时、全部材料before=after。events SHA `e382f2b7c9589a6b71f17e66eea733da16d0670efd8d584eb0198aa1357d3c5f`；失败freeze SHA `e869cfffe4513e785443e0a3e5829c0fc225bb86169f7dcc881a4a952de304af`。旧main3的W1保持失败/零退休。

本轮每次Set原始206→306→304，恢复206→206→204，差均2、仅bit1从1到0；新V2审计字段与15GPR/其余控制检查吻合。三条实际事务分别为CALL、MOV RAX imm64、RET；原事件与Get/Read关联确认CALL真实栈写、RET同槽读取、RSP恢复和RAX=`f123456789abcdef`，但**完整W1仍未通过**。预算12event/12wait/9Get/4Set/9read330B/3retired。

准确失败为WAIT_EXIT阶段的event69：同owned PID/TID在退出前收到LOAD_DLL_DEBUG_EVENT，实际模块为kernel.appcore.dll，事件原件的file identity/SizeOfImage/关闭门均通过；原分支只接受EXIT_PROCESS，故`unexpectedExitEvent`。child-result虽已写`continuedToC=true/exit=0`，真实OS退出是随后parent主动终止的90，不能混淆。owned终止、EXIT继续、signal、句柄关闭和fresh OS查询无残留均已核。W2/W3没有执行。

root继续纯文件诊断发现另一准确接口缺口：12个成功wait原件均有指向后续event的stopEventSeq，但header PID/TID/type为null，独立validator正确在`wait identity`拒绝。原因是wait_event已取得实际身份却在line_begin时log.stopSeq仍0；人工fixture已有真实身份，因此原纯测试未覆盖该源记录错误。此次诊断没有产生通过报告或改写原件；随后root仅按原Get/事务/内存逐项独立核局部三退休，部分报告SHA `db09fa6dae89f4bb2debea05ec06e7651c39c086ae5ad4fefaab46006ce8d691`，明确fullW1Accepted=false。该接口冲突交Sol max冻结最小修复，不放宽validator身份门。

[WAIT_EXIT源码续项](CLIENT_72_D02_CLEAR48_TRACE_WAIT_EXIT_PARENT_20261111.md)和[尾事件序列回归](CLIENT_72_D02_CLEAR48_TRACE_WAIT_EXIT_VALIDATOR_20261111.md)为已明确小修：仅W1/W2完整退休并恢复TF后至退出间允许原LOAD_DLL；BOOT/PENDING、W3及其他事件/身份/预算不变。有效file句柄继续受原identity/path/size/close门约束，不将原条件检查描述为无条件要求。新的wait身份合同与纯回归闭合、新source/build/PE/依赖epoch后才可另一次W1；不在相同源码重复。没有安全或Daybreak拒绝、没有客户端/生产部署变化。
