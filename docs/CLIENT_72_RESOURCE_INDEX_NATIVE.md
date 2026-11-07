# D02-R3：native 字符串、reader 与 ctable 原字节视图

2026-11-07，本卡接续[资源索引入口](CLIENT_72_RESOURCE_INDEX.md)，限定子合同已由主线第三复验通过。**已执行真实字符串构造、reader 构造/open/seek/read及原生签名检查片段；没有解码 ctable、取得`0000006f`内容或完整 native archive handle。** 原始文件视图的失败不能写成真实客户端读取失败。没有客户端/调试器/进程操作、Git、Gradle、数据库或部署；主线继续独占实机。

## 开始前的小计划

| 项目 | 约定 |
| --- | --- |
| 目标 | 越过旧`140970e90/140970f80`停止边界，确定分配、native reader、实际字节读取及逻辑索引的下一依赖。 |
| 已有证据 | 五profile输入；两个ctable同SHA；已证UTF-16→UTF-8及`0x6f→0000006f`；指定metadata/.pdata链。 |
| 未知与候选 | allocator内部/runtime初始化、资源加载分支、文件系统provider/cache、自然选中文件与转换。固定native helper可能通过特定provider读取，不能默认普通磁盘字节就是parser输入。 |
| 实验 | 先以明确内存服务模型运行真实构造/复制；恢复reader读数接口，再以不改字节的固定文件视图验证native成功/短读/EOF/错误及header分支。 |
| 范围 | 新`client72_resource_index_native.py`、对应test、本页；忽略输出`local/migration72/d02-active/resource-index-astra/`。旧Sol工具/专题/产物冻结不改。 |
| 失败出口 | 首个未知runtime选择或接口立即保留；不造成功opener/解码结果，不换头、不试旧密钥、不构造人工资源map。 |
| 验收 | 固定原字节/hash、准确执行范围、独立UTF-8/FNV/读数边界和两新进程重复；模型与真实执行分别记录。未达到完整读取时给出最小自然补充。 |

## 固定输入与执行范围

[工具](../tools/client72_resource_index_native.py)入口按`protocol/cn-7.2/profile.json`核五文件SHA-256；读取两个ctable并要求相同。ctable为28,160,000字节，SHA-256 `03b2478ba340c6fcda27a4988c6501c32665b1323022e927aaeeb1f338e4710b`。未修改原文件或资源目录。

Unicorn 2.1.4执行原字节。报告记录50个执行允许/静态依赖范围，逐范围hash与是否准确`.pdata`，另标记只观察不执行的边界；“允许范围”不代表其中每条指令都执行。33次受限native调用包含准备、seek、reader和片段，不能与7个字符串样本、8个读例和6个单测相加为独立验收数量。

模型仅为：

- `141a2e450`的已核size/alignment/label/flags调用合同，提供有限对齐存储；`141a2e5a0`只释放该模型持有且label一致的分配，null释放单独记录。allocator内部singleton/vtable、线程和OOM机制未执行，不称allocator完整恢复。越过模型预算或未支持alignment/flags直接拒绝。
- 托管UTF-16存储及GS StackLimit离线夹具；UTF-8、native字符串构造、长度/容量选择、复制和FNV均执行客户端原指令。
- 已核`1415ba4a0`read-at接口提供固定不可变字节视图，精确返回实际字节数、EOF或不存在视图的0；它**没有实现自然文件系统provider/cache/转换**。因此不是给未知I/O返回一个有利的解码内容。
- header检查使用准确`1415ab350`继续片段、明确寄存器/栈夹具及已经由native构造的reader；不是执行整个archive opener。真实文件与短视图均不更改任何字节，短视图只限制可读长度。

## 已执行的新事实

### 字符串与内存服务边界

`1414d30b0→140970e90→140970f80`现在完整返回，真实`141fdf310`复制指令也已执行，未替换成Python memcpy。7个独立样本为空、15字节、16字节、中文/emoji、501字符、内嵌NUL和固定真实路径；输出与Python UTF-8独立比较，另核长度、返回对象和末端NUL。

15字节以内使用对象内缓冲`+8`；超过15字节按`length+1`请求alignment16、label`0x46`，容量存`+8`、长度存`+18`。501字符样本额外走UTF-8暂存的堆分支，原指令请求2004字节/alignment1/label1并随后释放，最终字符串申请502字节。内嵌NUL在显式长度字符串中保留，不把它当路径实机成功。

沿原`14146c630`完整路径转换继续，准确停在`1415d36d0`前，未模型化它的配置选择返回。后续`1419eccf0`实际经资源加载管理器，不是简单`fopen`。该未知现场保存在`openerFrontier`。

### reader 的open不是OS文件打开

原生`141123490`完整构造reader并写vtable`1421fe258`；`141123530`将路径存入`reader+250`指向的状态对象，调用`1415ba6a0`计算FNV-1a32，最后无条件返回1。该阶段没有创建OS文件句柄。实际对本机不存在的路径也返回1，而随后raw-view read返回0；不能把open=1当成功读取证据。

| 操作 | 原生合同与实测 |
| --- | --- |
| seek `141123810` | origin1设64位绝对位置，origin2加64位相对位置；origin0返回false且保持位置。实测大于32位的`2^33+7`不截断。 |
| sequential read `141123790` | 参数为reader、请求数、buffer、out-count。经实际状态FNV/position进入read-at，按实际count更新`reader+248`，只有count等于请求量才返回true。 |
| read-at `1415ba4a0` | RCX=路径状态，EDX=FNV32，R8=目标buffer，R9=uint64 offset，栈第五参数=count；下游转`1415bc210`。模型核路径及独立FNV后返回原字节数量。 |
| 边界 | 连续12+17字节、尾部2/请求8的短读、EOF、超32位EOF、非法origin、零长、null buffer均按原指令核结果；null buffer不进入文件视图，零长返回true。 |
| 不存在视图 | 原生open=1；read false/count0，与EOF在原生bool层相同，在模型证据中保留missing/EOF区别。 |

### 原字节视图不满足native blob头

由`1419fccb0→1415aabb0/1415ab1b0`恢复archive链。`1415ab350`准确片段先seek，再通过实际reader vtable读12字节；`1415ab3a0`要求u32 `03626C42`，随后要求版本低字节4或5及headerSize至少`0x48`。

固定ctable的原始首u32为`B501F20E`。实际12字节读取后，原生比较跳到`1415aba2b`，对应“Unable to read blob signature”分支。原样4字节短视图与不存在视图分别真实返回4/0，在到达magic比较之前走同一失败入口。完整header/解码段未执行，没有替换magic或提供伪造decoded payload。

这说明**已声明的raw-file-view模型不能使这个native parser通过**。自然运行时是否经过provider转换、cache，是否选中另一文件/offset/分支仍未知；不能直接断言客户端加载失败或必须采用某算法。`141122120/141122e00`需要真实archive/index状态，未伪造该handle、`+188`索引树或`0000006f`entry，保持未执行成功。

## 下一批最小自然只读材料

以下为静态指令已复算的读取规格，供主线在已核目标中审核执行；本代理没有读取目标内存。`B`为本次主EXE实际基址，不沿用旧基址或PID。只读失败保留unknown，不调用vtable或写任何目标状态。

| 次序 | 地址与最大读取 | 来源与解释 |
| --- | --- | --- |
| 1 | `B+5756990`读8字节得R；非零R读`0x90`字节 | `1415b8930`原指令getter；R为文件系统router。 |
| 2 | `A=*(R+20)`、`N=u64(R+30)` | `1415b8059..8076`：A是指向provider指针数组的指针，不是inline；元素8字节，按N-1逆序检查。记录完整N，N=0不读；本批N>64只记录超观察上限。 |
| 3 | `k=min(N,4)`，从`A+8*(N-k)`读`8*k`字节 | 只取查找顺序的前4候选，输出索引按N-1递减。不能把此窗口称全部providers。 |
| 4 | 候选P：`*(R+0)`默认provider、上步最多4个注册provider、另记`*(R+88)`special wrapper | 最多6个P，每个只读`0x10`取得首qword VT；每VT读`0x90`。default及special不证明本次ctable已选择它们。 |
| 5 | 每候选VT的`+08/+10/+60`非零目标各读32字节 | `1415b8076`只证明此接口slot60为路径accept谓词；`1415ba0c0/1415ba2b0`分别证明此文件系统接口slot08=open、slot10=read。这些名称不能泛化到任意vtable。 |
| 6 | `B+5756980`读8得F；非零则只读`F+558`的8字节 | `1415ba4bc`与`1415bc24d`：决定cache/direct-file路径的指针；不扫描cache树或内容。 |
| 7 | `B+5766598`读1，`B+576659C`读4 | `1415d36f7/1415d36ef`分别返回选择字节和检查once guard；静态地址准确，快照不证明捕获了实际选择调用。 |

独立原文件对照：router ctor `1415b7580`经`140b0a7b0`构造默认provider，写VT=`B+21E8A38`；原文件该表slots08/10/60分别是`B+B09830`、`B+B09B20`、`B+97AEF0`。router+88由`1415c03c0`构造special wrapper并以`141161620`保存底层provider；自然现场可以因注册或初始化不同而不同，不能强制它匹配默认表。

如能取得上述指针与代码窗口，先静态还原实际provider的open/read/accept合同；再决定是否有可离线执行的字节转换。若还需自然I/O，最小材料为实际`1415ba4a0`调用路径状态/FNV/offset/count及返回count、输出首12字节hash和对应本次文件hash。未经明确采样，不将普通进程I/O字节计数称作ctable文件读取，也不因未捕获I/O便猜解码参数。

## 测试、失败与冻结产物

[6项测试](../tools/test_client72_resource_index_native.py)实际通过：独立已知FNV向量、与`io.BytesIO`对照的边界/64位EOF、错误路径/hash拒绝、missing与EOF区分、受限读数拒绝及零长/原字节保持。native样本在CLI中另有真实CPU执行与独立结果断言；没有重跑旧metadata/ctable来源分支充数。

| 冻结项 | SHA-256 / 大小 |
| --- | --- |
| `native-final-{1,2}.json` | `6a24ef69ded74323049144849764ce4e727f99c5090e8e3d508e8f167402915c` / 55,885字节；两个新进程逐字节一致 |
| 新native工具 | `3a0fc703c63429d66775a07c1dbfa8c515678461fa00b5f6e050e98cab16a1a0` |
| 旧Sol工具保持 | `b7e1c58ccc6de5102b8e458c967a223bc2d070260b0b91e9830bf24e68e39273` |

原始输出均相对`local/migration72/d02-active/resource-index-astra/`，证明`determinism.json`，测试`tests.log`。探索`strings-first.json`、逐函数静态文件与router-xrefs保持；xref原始字节匹配只作候选，最终规格依据完整指令语义复核。一个任意中段地址`1415b9ac0`的探索反汇编未有正确起点，不进入证据范围或最终模型。

首个CLI在全部实验后、输出写入前因局部变量覆盖hash函数名导致序列化失败，保留`native-first.log`，没有完整raw报告，不纳入双跑验收；改名后的最终两份才是冻结结果。

按reverse-engineer-anything技能，REA成功open固定EXE并核SHA；Ghidra 12.1.4在330,000ms startup期限返回`provider_timeout`，没有成功函数Evidence、没有确认OOM。原错误为`rea-native-analysis.json`，导出记录数0，已调用close释放该分析会话；没有原样重试或把Capstone/Unicorn结果称REA成功。后续使用同原字节的限定分析继续，不涉及调试器或游戏启动。

```powershell
python -m unittest tools.test_client72_resource_index_native -v
python tools/client72_resource_index_native.py --client $env:GAME_PATH `
  --output local/migration72/d02-active/resource-index-astra/native-review.json
```

命令前由调用者设置经本机配置核实的GAME_PATH；Python不自动读.env。主线第三复验应比较完整报告与冻结hash，审核上述模型界限。仅离线工具/测试/本专题变化，无运行代码/配置/Proto/资源或补丁变化，不需部署重启。本代理没有Git提交权限，Project/Harness/活动记录及本地提交由主线统一维护。下一项优先使用主线自然provider快照恢复真实读取层，D02-R3与D-02均未完成。

## 2026-11-08 主线审核

主线审查真实工具、模型/停止边界及原始证明，以独立新进程第三回放和6项定向检查接收本限定子合同。完整raw `resource-index-native-third.json` SHA-256 `6a24ef69ded74323049144849764ce4e727f99c5090e8e3d508e8f167402915c`，与独立双跑逐字节一致；证明/日志位于`local/migration72/d02-active/main/`的`pending-contracts-main-proof.json`和`resource-index-native-main-tests.log`。字符串/reader/seek/read边界已验；显式raw-file read模型不等于实际provider或ctable解码。 本项仅离线研究工具/测试/脱敏文档，无运行代码、Proto、配置、补丁或资源变更，因此无需服务重启；不改变ready/required/runtime或D-02客户端验收状态。
