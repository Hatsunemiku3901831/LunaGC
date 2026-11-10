# E-01 / R3-PROUD-NATIVE-17208-ARRAY-CONTRACT（2026-11-11）

## 结论与唯一下一项

**准确329B数组体已审：同key22101在明确普通文件条件下从318读4B到322，得到count=4，必进首元素 `17208.m130222 / 147e917c0`。完整nested返回cursor和最终row终点仍未知，当前不具备完整单key文件reference。** 现有旧Promote摘要能绑定这个元素体的声明、旧边界与hash，但没有可重解码原bytes；唯一下一source已收敛为该元素体，不扩helper树。

本卡接续[derived-file合同](CLIENT_72_D02_PROUD_DERIVED_FILE_CONTRACT_20261111.md)和[准确数组原窗](CLIENT_72_D02_PROUD_NATIVE_17208_ARRAY_WINDOW_20261111.md)。已先读AGENTS、README、Project、Harness、迁移和部署手册；私有中文计划2642B / SHA-256 `efa4f2932beac1420fd4bcf5f5781947c21c94cb82a908fa8ca8d8b7c98b94f3`。这是同一必经链静态小续项，直接复用固定GitHub成果，没有新检索或招募。

## 冻结输入与审核范围

唯一新审体为17208/m130225，完整范围`[147e91d10,147e91e59)`，329B/90条，SHA-256 `9a3e1976af758bf71514b1f2af1addc3b712d69ac6456f75330addc7751ab3bf`。只读`c-proud-native-17208-array-window/fresh1`和fresh2的既存八原件，双份逐byte相同；root第三与原audit各17885B，SHA-256 `5e046a7b8876ce779617908d937d39be267beffde9adbf68d28a22025552cb04`。原窗的五门禁由root验收，本卡没有冒称重新运行采集器或那五项测试。

独立host脚本重新解码全部90条并逐byte拼回，核stored cell/slot/pdata算术、65个准确锚点，列出12个call、14个branch、22个显式memory operand、4个RIP地址和27条栈相关指令。这里只算地址，不读取RIP所指literal或全局对象；LEA的memory operand也不冒称实际内存读取。

上游derived第三和两fresh各576784B，同SHA-256 `4849718ddb2679ad26d821d2c74b867968e31ed11b5593d4abeb903f03eb355f`，其正常文件合同到318及caller尾部关系直接复用，没有重执行CPU、locator或全表解析。完整Proud payload2132778B / SHA-256 `82a8017c6c2868e41e3ce8fc02f8bc04437cb641ec653ed3383c5cc109e25835`；同key22101的A=4/B=330、候选`[4,334)`仍只是候选。物理end始终2132778，不拿334当reader长度或停止条件。

metadata cache117201003B / SHA-256 `df52eb70fe8e70dfecd1059e898d15e288cfa98bcefc78bcaabf753ae150349f`及signature78649549B / SHA-256 `28377e7a8d3c891cd61be97ddb74f8502a22f2f2f039d6a91dba3b092d7822fe`均先核完整hash再反序列化。必要源固定41份/1296336663B，另存8份说明性上下文；共享Project/Harness后续变化不放宽输入pin。

## reader、header与准确count

本卡沿上游分别声明derived+40=null、base+20=null、此前选中reader检查走inline及相应原调用正常返回。本体还须在`147e91d56`的reader+20检查走null分支，并且reader和reader+10有效、起始cursor通过原检查；allocator/分配/构造/元素/存储调用亦须正常返回。这些是普通文件条件，不由传入INativeAllocator=0推导，不是自然mode/backend或typed对象成功观察。文件抽象不为没有reader实参的opaque存储调用补额外消费。

入口`d23`把RCX保存到RDI。reader消费阶段没有重新赋值RDI，元素setup明确复用它。reader+20非空时`d65`会先以RCX=同reader、EDX=4调`14f614760`，然后汇回共同header路径；该helper可能怎样改变reader仍未知，不能将它猜成“已读4B”。普通inline条件跳过此边，本项不申请展开它。

| PC / 原操作 | 固定样本结果与边界 |
| --- | --- |
| `d6a/d71`取得reader+10并检查null；`d77`将DWORD cursor符号扩展到RCX | 使用同完整payload、起点318。 |
| `d7b/d7e`以ECX与byte-array+18作unsigned起始下标比较 | 318<2132778；原体没有统一`cursor+4<=length`检查。host另核完整4B物理可读。 |
| `d84/d89`先令ESI=`2bedd918`，再加`[byteArray+RCX+20]`的DWORD | `[318,322)`原hex=`002c0708`，LE raw=`08072c00`；32位加法得到`33f50518`。 |
| `d8d/d90`令EAX=低32位(cursor+4)，写回reader+1c | 条件cursor 318→322。推进在异或与count分支之前发生。 |
| `d93`执行ESI xor`33f5051c` | 得到count=4。必须保留“先add mod2^32、再xor”的顺序。 |
| `d99`仅MOV元数据指针；`da0` JE到`e07` | MOV保留d93的零标志，因此JE确实测试解码count是否0；本样本不跳，进入非零路径。 |

以上PC前缀均为`147e91`。count=4来自准确四字节和完整flags链，未由末尾重复字节、字段大小或候选剩余长度倒推。独立host算术保留0、1、7fffffff、80000000、ffffffff和当前raw的顺序/溢出见证；另有合成count0 raw=`08072c04`，它只是host见证，不是第二份客户端样本。

## allocator、缺省与非零循环

上游原caller令R8D=0。入口把R8保存到R14；`d26/d29`因此选缺省allocator来源，必要时`e41`调`140525e00`做class初始化，再从原global指针+7510取QWORD到R14。所指对象和实际值未读取，不能说allocator或分配已成功；incoming allocator非空时会直接保留并传入后续调用。

非零分支`da2`调`1405336c0`，返回须非空才继续；R12保存该返回。`db8/dba/dbd/dc0`向`15846eec0`传RCX=该对象、EDX=count、R8=选中的allocator、R9D=0；正常返回后`dc5`写输出槽。这里只记录分配/构造相关调用的准确实参及正常返回条件，没有展开其实现或宣称实际对象成功。

`db3`清EBX，`dc8`零扩展ESI到RSI，`dca`将R14改为`&rsp+20`的临时QWORD槽。每轮流程为：

1. `dd0`清临时QWORD；`dd9`令RCX=该槽，`ddc`令RDX=同RDI reader；`ddf e8dcf9ffff`必调`147e917c0`。首轮的条件输入cursor是322。
2. 元素正常返回后`de4/dea`取得并检查当前输出数组；`dec/df1/df3/df6`向`15846f530`传RCX=当前数组、EDX=EBX索引、R8=临时QWORD、R9D=0。实际58209 encoded内容与存储实现仍未知。
3. `dfb`以32位INC更新EBX，`dfd`将其符号扩展到RAX，`e00/e03`作signed64比较`RAX<RSI`后JL回`dd0`。count4且各原调用正常返回时，索引0/1/2/3各一轮，比较结果为true/true/true/false。

本体没有count范围保护；不能将它普遍简化成任意大U32都正常终止的host循环。当前count4无此边界歧义，但第2/3/4轮输入cursor及所有元素返回cursor都未知。临时QWORD清0、两个58209存储字段或setter传8B均不决定元素wire宽度，不能仿照52802。

count0的另一条原路径在`e07/e0e`检查Empty来源的class初始化，必要时`e4b`调同初始化helper，再从class+38所指块的首QWORD写输出，随后共同epilogue/RET。它在header之后没有额外inline文件读取、不调用元素；实际Empty对象仍unknown，本样本不选此路径。

原体保留五个错误边：null reader→e2d、null byte-array→e32、unsigned起始下标越界→e37、分配返回null→e3c、元素返回后当前数组null→e28。四个null类call目标为`1404c2be0`，越界为`1404c2650`。本卡只审原分支和call，不执行这些错误callee、不统一成EOF；host全宽拒绝也不冒充原throw。六个host边界见证分别保留原start check、物理全宽可读及候选334越界三个判断。

## 唯一首缺与既存复用结果

准确原边为`147e91ddf e8dcf9ffff`→`147e917c0`，正常返回PC=`147e91de4`；同reader和栈上receiver的setup已在完整329B内闭合。唯一metadata为owner17208/BPDMGCDGJBK、method130222/CNLIBMNOALB、flags0x83、virtualSlot65535、registeredAddress147e917c0。它是实例方法，**只有1个声明参数**：parameter121521、typeIndex86325、class42005/OFMKHIMCGKE；机器RCX是隐式receiver，RDX才是该reader。return type unknown，不能与静态三参数m130225混淆。

字段81537/+10与81538/+14都声明58209；它们只约束元素存储形状。file input bits、实际stored58209 encoded、管理field203373/203376映射及group/level业务alias继续分开，当前不猜81127门槛或Java公式，也不把全部未映射字段自动升为P0。

准确文本检索发现旧`r3-promote/first.json`、second和main-third各3297024B，同SHA-256 `546b95ce69f5ace87761d2fe83c287da6a91ad9e9d19cbcb146147caed70fef1`；`nativeSources[147e917c0]`只有end=`147e918fd`、空references和code SHA-256 `42112e160290bdf777efc9f7d05f8ae3afe9ed3746c4f9356a6ca614950b96e7`。probe02亦同摘要，未存可重解码body bytes；历史CPU覆盖不移作当前Proud样本验收。五份大声明/index JSON只保存有界准确地址片段；现有86份命名`code.bin`没有317B候选。这是选定库存的有界负结果，不声称所有未审二进制绝无原体。

唯一私有请求`next-native-17208-element-source-request.zh.md`为5058B / SHA-256 `378546a83fda3a1338baa9e81419ec7393d998871a1e939c92e292aa5134f729`。须root另批：先核当前五固定输入、完整329B caller/setup/E8与准确实例声明；ordinaryAOT仅新取cell1422f9950的8B，用本次QWORD P计算`P+130222*8`再取唯一8B slot，必须指向147e917c0。旧P下slot1429a3a40、旧范围317B与旧hash仅作提示。exact与containing pdata各唯一且同一记录，先存边界，再取一个完整body，建议≤1024B/连续≤256条；两fresh八原件与root第三审核。不顺取后继、xdata、literal或helper，管理child14f6f13c0仍未批准。该请求本身不授权新读取。

原derived正常返回后还必读4B，再走末数组Empty/清理/RET，故仍只证明`finalCursor=nestedNormalReturnCursor+4`。当前没有nested返回330的证据，不能补nested消费12B、元素2B/8B或最终334，也不交完整Proud reference。

## 实际复核与交付

唯一私有根为`local/migration72/d02-resume-20261110b/c-proud-native-17208-array-contract/`。最终冻结v3清单11551B / SHA-256 `9cbd9a382d90368d6ea5adc9943daf4adc32605ced47f43980075fcdbc72c7cf`；独立脚本27627B / SHA-256 `265e2b11feee13ad82d2bd995f283dfb6b6337fa1f2bf7fa22d400147edcab8e`。两个最终新host Python进程实际退出0，报告各96575B、逐byte相同，SHA-256 `867406d433dfd169bb64cf8d1b1feccadc8229cf5231e31a27a268329b6fef26`：

```powershell
python -B local/migration72/d02-resume-20261110b/c-proud-native-17208-array-contract/verify-contract.private.py audit fresh1.private.json
python -B local/migration72/d02-resume-20261110b/c-proud-native-17208-array-contract/verify-contract.private.py audit fresh2.private.json
```

复现须用此前不存在的新basename，输出排他创建；root第三待主线独立运行。准备时有两项host断言错误：两进程的operand前缀扫描误把TEST RDI,RDI当写目的寄存器；后一次误把旧机械audit的整数instructions=90当指令数组。均在只读阶段、报告创建前退出，已保留两版脚本/notes与原冻结v1/v2；最终按MOV/LEA赋值和准确schema修正。过量只读显示、一次宽glob与不存在旧文件名也记于read-notes。原件未改，没有安全/Daybreak事件或目标重试。

状态仍为`targetCPU=0`、`nativeParserExecuted=false`、`actualCursorObserved=false`、`naturalBackendProven=false`、`naturalModeObserved=false`、`actualIndexFilled=false`、`actualRowSpanApproved=false`、`typedObjectSuccessProven=false`、`completeProudReference=false`、`resourceReady=false`、`completeAvatar=false`、`d02Complete=false`，客户端新增验收0。无新PE/code/AOT/pdata/xdata/literal采集、CPU/native/emulator/live/REA、进程、网络、Git、构建、DB或部署。仅本文和指定私有目录写入，无需重启；本max交回双fresh、唯一source规格和全部写权，root统一第三审核、Project/Harness、本地提交与交接，不等待A或现场。

## 主线验收增量

root在子任务交回后，用新的host Python进程实际生成第三`main-third.private.json`，退出0；96575B / SHA-256 `867406d433dfd169bb64cf8d1b1feccadc8229cf5231e31a27a268329b6fef26`与最终两fresh逐byte相同。上文“第三待主线”是原交付时状态，现此限定静态出口已验收。root已另行采纳唯一元素原窗请求（≤1024B/256条），不批准顺取其他body或执行CPU。
