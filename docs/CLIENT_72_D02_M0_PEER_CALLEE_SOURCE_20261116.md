# D02-M0-PEER-CALLEE-SOURCE：唯一真分支调用的来源与消费

## 中文有界计划

本项只处理已证 `15A4DCCB7 PUSH 0x56FE983E` → `15A4DCCBC CALL15A46C1EF`。复用既有 Peer source、四字段 wire 和任务绑定；不重新进行全项目或上游研究，不改其它专题。固定 EXE、global、startup 三个输入 SHA-256，路径从当前 `GAME_PATH` 解析。

1. 先对已知 Peer、Scene 证据精确检索目标地址，若已有同一 source 就只读复用；再用固定 metadata/AOT、PE 异常目录与可执行节映射核 `15A46C1EF` 的所有普通别名、唯一 containing pdata 和 unwind 来源。
2. 只有唯一完整普通边界且累计 source ≤8192B，才一次保存完整 body、SHA，进行 strict 连续解码、EOF 和逐字节回拼；CFG 只从本 CALL 的真实入口开始。若 split pdata 仅是 prologue，只能沿原指令的真实 fallthrough/直接目标及唯一 pdata/unwind 关系连接独立可达片段，不把片段集合冒称完整函数。
3. 只分析本次压栈立即数、已知 `R14=root19176`、`RDI=task75453` 及原 live-in 的字段消费与返回关系，特别是 field12/root `+20`。未知 CALL、保护 dispatcher、间接边或准确冻结依赖立即停止；不执行目标 CPU、不加模型/hook/预算、不追 call 树、不泛扫。
4. 按实际结果给出可复用合同或精确最小缺输入。未读 field12 不能证明可省；授权范围用尽本身不记为外部阻塞，也不把普通未知 CALL 写成安全拒绝。

唯一 tracked 产物为本文；原件仅写入 ignored `local/migration72/d02-m0-peer-callee-source/`。无 Git、Java/Proto/生成、构建、DB/JVM、实例/端口或客户端操作。完整 Peer、Ready 与 M0 入场状态由主线另行验收。

## 当前状态

**本有界 source 子项完成：已证明压栈立即数确实成为只读数据读取及间接代码目标的来源；field12 和本调用返回关系仍未知。** 取得六个独立 256B 窗，共1536B代码，以及另行批准的唯一只读4B。没有取得1,071,719B完整范围、完整 callee 或完整共享实现。

最终语义停止点为 `15A480C64: MOVZX EBX,byte[R10]`，此时 `R10=146458F68`。该新1B未读取，不预称它必须来自动态环境或无法静态取得。八窗原额度尚余两窗；本项停止依据为主线要求在新数据来源处冻结并返回，不是预算耗尽、用户暂停或外部阻塞。D02主线继续。

主线依次另行批准：原超界门后的独立256B入口诊断；至多再七窗/代码总量2048B的真实直接CFG诊断；唯一 `146458F64/4B` 的只读来源门及纯静态目标计算；最后使用原额度剩余三窗，遇新数据来源即停。本项实际只使用其中一窗。原超界门、首间接停止及线性失败均独立保留，没有追认成完整通过。

## 固定输入和已有 source 复用

路径按当前 `GAME_PATH` 解析。实际核对三输入、profile和既有metadata索引，不使用历史进程或客户端绝对路径：

| 输入 | 字节数 | SHA-256 |
| --- | ---: | --- |
| EXE | 450927000 | `8b014f64b5dc12fbc476e0c362b053abfdd7a1378cd282e4ad81057621dbd6b5` |
| global metadata | 83644188 | `45dc7a44dc9fc187146c52f6c33de57e2d1c1dcfec57f8736431553460f6ef2a` |
| startup metadata | 4116148 | `b314810840ca6f495822c9c44483dde2394b9f09ed3c6b13c2782092f04d5105` |
| metadata私有索引 | 117201003 | `df52eb70fe8e70dfecd1059e898d15e288cfa98bcefc78bcaabf753ae150349f` |

profile1453B/SHA `a3b9eda630942ba8479f713fc1692c7eb7b05198ae382dfe878ecc963051fd5d`。复用既有 `extract_client72` 的PE/地址映射、`scene_mplevel_actual_xref` 的唯一pdata门、`Literals72` 的ordinary usage解码和固定Capstone5.0.7；没有打开新的深度分析会话或运行目标代码。

先在 `d02-m0-peer-true-branch`、`d02-m0-peer-producer`、`d02-m0-peer-min-semantics`、`d02-active/main`、`astra-scene` 五组既知忽略证据中精确检索地址。初始目标只命中原CALL记录，没有目标正文；后续准确直接目标同样先查这组来源。此负结果仅覆盖列明资料，不声称全盘穷尽。已保存的新窗和4B均只读复用，未重复获取。

前置 [TRUE-BRANCH](CLIENT_72_D02_M0_PEER_TRUE_BRANCH_20261116.md) 的完整45B SHA `5cd763a5fde151a60e09b85b2e49fcdd10eb119240c85d0409425279efa4bb90`、`B7 PUSH56FE983E` 和 `BC CALL15A46C1EF` 逐字节核同。原 typed receiver1464B 的 source SHA `023e0d7b71c4bdef29128fff083bd4e185794400da6294bd41b107b25e5eb9b3` 只读复用。

原live-in继续为R14=root19176、RDI=75453任务、RAX=此前Session、ESI=该Session旧`+1E4`、EBX=任务`+2C`、ECX=0；其它寄存器和真实对象内容未补值。首任务`+40`真路径仍保留，不能用默认false消除这次调用。

## 原始来源门：唯一大范围，完整正文未获准

| 项 | 实际结果 |
| --- | --- |
| target/begin普通AOT别名 | 均0；range内其它普通AOT entry也为0。此结果只针对固定ordinary索引，不冒称所有可能generic/native别名均已穷尽 |
| 唯一containing pdata | `[15A3D0F18,15A4D697F)` /1071719B，beginMultiplicity=1；target偏移`9B2D7` |
| pdata记录 | RVA `1B2943C4`，raw `180f3d1a7f694d1a48f03c1a`，unwindRVA `1A3CF048` |
| 异常目录 | RVA `1A4E6A40`、14344080B，SHA `5895dbeef8fe13458720889e318ba64f3fc9c5d3ca03bf01b0adc70eee16c08d` |
| 执行映射 | 唯一file-backed executable `.upx0`，flags `68000060`，完整大范围可映射；失败原因是大小超过8192B，映射本身通过 |
| 精确unwind | `1A3CF048` /20B，raw `0900060005011a00045003600270013080694d1a`，SHA `d20822bd8d505f44f27fac08d61b834e53cde5e47b15ea35b6676a7890f90ef3` |
| unwind关系 | version1、flags1/EHANDLER、6 slots，handlerRVA `1A4D6980`；无CHAININFO。只记录这个20B编码范围，没有跟handler或语言数据 |

这不是只含prologue的独立小记录；已检查的pdata/unwind没有给出本入口更小的完整普通边界。原driver的合并门错误文字是 `Unique executable mapping / complete range byte gate failed`；原件保留，后继明确归因为大小门。未提取1MB正文，不据section名、大小或普通别名缺失把它叫A、完整VM或安全冻结。

## 六窗严格解码和入口路径

每次新窗均重新核固定EXE、唯一file-backed executable映射及唯一containing pdata；六窗都落在上表同一大记录。它们是有界诊断窗，不能拼称完整函数。

| 窗 | 完整保存范围 /256B | source SHA-256 | 入口CFG指令/字节 | 该轮停止 |
| --- | --- | --- | ---: | --- |
| 1 | `[15A46C1EF,15A46C2EF)` | `3d7fd69f66ccfc4f2e0e0f661604a24b155c3ff210b195a20db72b0544f5a90b` | 5/14 | `1F8 JMP15A4534EA`，窗外 |
| 2 | `[15A4534EA,15A4535EA)` | `978803210266c638276706ed71200d29b36dbefbeeedbbe25906b00167409a36` | 3/9 | `34EE JMP15A468AAD`，窗外 |
| 3 | `[15A468AAD,15A468BAD)` | `729e7fc7e293525879b79b46d7abe0931b952729748a3f80510cb6cb5bc2b62d` | 16/43 | `8AD3 JMP15A4BACE3`，窗外 |
| 4 | `[15A4BACE3,15A4BADE3)` | `caa9d9bbc0f665c2c92ad5e0b46497d8833c64abbb22856d5cd1117ea4eac456` | 25/91 | `BAD39 JMP15A4BBE9F`，窗外 |
| 5 | `[15A4BBE9F,15A4BBF9F)` | `b5356b7a4c6491008e1b53af5d49615c9e423ae8845f8dd0db7b1f518b9559c8` | 58/211 | 原始停止 `BBF6F JMP R8`，未跟间接边 |
| 6 | `[15A480C4C,15A480D4C)` | `7bdcc5e9f137203c97a0848be0eb712dbde9fc45fd163f459ee7d7d0e7e58df4` | 6/28 | `80C64 MOVZX EBX,byte[R10]`，新1B来源 |

合计113条/396B的限定入口listing和控制边；其余已保存字节没有升级为该路径业务语义。第6窗入口来自下面独立4B来源及静态计算，不把第5窗原间接停止改为“当时已通过”。

全窗线性strict与入口CFG分别记录。Capstone `skipdata=false`；仅第2窗79条和第6窗82条完整连续到EOF并全回拼。第1窗在`15A46C2ED`停，尾`81C3`/2B；第3窗在`15A468BAC`停，尾`48`/1B；第4窗在`15A4BADE2`停，尾`41`/1B；第5窗在`15A4BBF9E`停，尾`66`/1B。所有256B和未解尾字节保持原样，未裁尾或把入口CFG替代整窗线性成功。前置45B在B1的原失败也不变。

## PUSH立即数的真实消费和只读4B

令S为CALL刚进入callee时的RSP，则`[S]=15A4DCCC1`、`[S+8]=56FE983E`。前四窗在第一次栈读之前有且仅有17次8B PUSH/PUSHFQ，没有其它RSP改写。因此 `15A4BAD0E MOV R10,[RSP+90]` 的地址为 `S-136+144=S+8`，精确读取这次PUSH立即数。

其中R14/root指针保存于`S-24`，原RDI/task指针保存于`S-128`；`BAD03 MOVABS RDI,0` 后的最后一次PUSH把0放于`S-136`。这是保存对象指针和上下文，未解引用业务字段。部分指令使用对象地址的低位参与寄存器运算，也不能当root或Task字段read。

| 已证指令/阶段 | 仅就相关寄存器的静态结果 |
| --- | --- |
| `BAD0E MOV R10,[RSP+90]` | `R10=56FE983E` |
| `BAD21 NEG R10D` | `A90167C2` |
| `BAD28 ADD R10D,10B908D8` | `B9BA709A` |
| `BAD33 NOT R10D`、`BBE9F DEC R10D` | `46458F65` → `46458F64` |
| `BBEA2 ADD R10,RDI` | RDI已置0，结果不变 |
| `BBEB0 R9=100000000`、`BBEC4 LEA R10,[R10+R9]` | `R10=146458F64` |
| `BBED3 MOV RBP,RSP` | `RBP=S-136`；之后RSP另减180并16B对齐，不改变该RBP关系 |
| `BBEF2 MOV RSI,R10`、R9置0及`BBF05 SUB RSI,R9` | 此阶段`ESI=46458F64` |
| `BBF1C MOV ECX,dword[R10]` | 首次非stack读取，来源为下面独立来源门 |

主线只批准该准确4B。它位于file-backed `.pdata`，范围`[145F66000,146D13A00)`，file offset `5C05964`，flags `40000040`：可读、非WRITE、非EXEC。固定13813个primitive static字段无重叠；ordinary usage最终788696行，已包括kind1/5/6确切cache及全部12个registration qword基址的8B重叠过近似，均无重叠，不排除BSS基址。

**第一次usage检查把基址限于file-backed范围，错误排除了virtual/BSS缓存，产生空基址集合；该负结果已撤回。** 原报告保留，`read4-usage-audit` 用全部registration word更正覆盖，复用已保存4B，没有再次提取。primitive字段检查和只读section映射保持各自证据范围；不把ordinary负结果扩为全程序无写引用。

唯一4B为 `D5D42FE8` / LE `E82FD4D5`，SHA：

`f6f7e4b5a4d45d828bec73b596a22b2fde501320f5152a5bc2bcef60c43a218e`

其来源是固定只读文件数据，不是可变初始化cell或当前global采样。仅在已保存listing上做整数计算：

`E82FD4D5 XOR 46458F64 = AE6A5BB1` → NEG32 `5195A44F` → ADD32 `1A853E32` 得 `6C1AE281` → NOT32 `93E51D7E` → XOR32 `6C195045` 得 `FFFC4D3B`。

`BBF11 LEA R8,[RIP-7]` 给基址`15A4BBF11`；将上述结果按32位有符号扩展为`-242373`，静态计算 `BBF6F JMP R8` 目标为 **`15A480C4C`**。未执行这个跳转；其target/begin普通AOT别名仍为0，唯一pdata仍为同一1071719B记录。

## 第6窗更正和最终准确停止

`15A480C4C 4C8B4C2500` 被Capstone写为 `MOV R9,[RBP+RIZ]`。初版门把RIZ当真实未知index，原停点保留。实际REX.X=0、SIB index=4，是无index编码；只用已保存256B更正为`[RBP]`，没有重新取窗或补现场寄存器。

该读访问`S-136`，即已证最后保存的0，所以R9取0；`15A480C5B ADD RBP,8` 使RBP指向下一已保存槽。随后准确到达：

`15A480C64 410FB61A MOVZX EBX,byte[R10]`

此前 `15A4BBF25 ADD R10,4` 把已证cursor改为`146458F68`，至该read没有其它R10写入。这里的1B是本轮新的准确来源，**尚未取得**；没有沿已保存的其后listing继续业务分析，没有再取1B、第7/8窗或后继body。不能由邻接4B、section名、线性解码或本source预算给该字节造值或推定其最终用途。

## 专业判断与field12的最小可行途径

当前静态前沿已在**共享的数据驱动分派层**。依据是：该callee没有ordinary具名边界，进入相同大pdata内的多个片段；保存寄存器/flags并建立独立frame；PUSH值经确定变换成为数据cursor；cursor上的4B经异或/算术和相对基址选择代码地址；选中片段继续读取cursor下一字节。这些是实际指令和数据关系，支持“需要恢复本调用所用分派操作语义”的判断。仅凭这些前缀，尚不足以给整片区域命名某种VM、ISA或完整解释器。

**静态继续仍可行；现阶段没有证明必须新增live状态或准确A/B动作。** 新1B的来源门可以独立继续，但仅补该字节不会自动回答field12。要闭合本路径，预计需要几类工作：对这条固定PUSH数据序列逐项核只读来源；恢复所选handler的有限寄存器、frame和cursor操作；把保存的root/Task指针重新接到真实对象字段或业务段结束/返回；遇到实际间接对象、未知call或可变内存时再给准确新增状态规格。剩余序列和handler数量未知，不能以1536B或单字节推算整体工作量，也无须先恢复1MB全部实现。

解决field12有两条可行入口：

1. 沿**这一次**`56FE983E`调用恢复有限数据序列及所选操作，直到出现root19176 `+20`的真实消费，或取得覆盖真实首任务真分支业务段的省略证据；不默认Task `+40=false`。当前最小静态前沿就是`146458F68/1B`及其消费，随后规模须按真实边决定。
2. 找到独立的7.2 producer、ordinary consumer或同token首任务路径的真实样本，证明tag12的业务值或合法省略。已有四u32 wire、普通receiver不读`+20`和本前缀只保存root指针均不足以替代这种证据。未来自然客户端差异样本可用于验收，但本项未证明它是继续研究的技术必需输入。

已证前缀没有新的unknown CALL或A/B/preview准确依赖，也没有新的安全拒绝；这些限制继续分别保留，不能预判后续永远不触达。本任务源码范围只是调度边界，不构成用户停止D02的条件。field4同域合同、既验token/local key与raw wire继续可独立使用；field12的值、省略必要性、完整Peer/Ready及entry-ready均未放行。

## 原件、实际验收与交付边界

原件相对忽略根 `local/migration72/d02-m0-peer-callee-source/`。六窗raw SHA见上表；完整逐文件清单为 `evidence-index.private.json`，13921B/SHA `df62c4b099f98467596c25984243709458456802b77203f50ea81ace5fffd782`。

| 关键原件 | 字节数 | SHA-256 |
| --- | ---: | --- |
| `run1/failure.private.json` | 3386 | `ffed6b3e4b69105ab5da9de09f09024de52df368655764b11312038cec0ea6d7` |
| `run1/result.private.json` | 2398 | `888b2bc8635aa9aa191ded72c0e946459f17c7dc2d8d9dc93b72713e7cd294a7` |
| `read4-run1/result.private.json` | 10038 | `bd345d500e9b0f10d6e4c3f535c6f7532e0619d399d2350538bbdb44a7405a3d` |
| `read4-usage-audit/result.private.json` | 2862 | `b7bc02f279d2e6b7b4ab3114068373734040105e480b700aad8320aab2861218` |
| `direct6-run1/result.private.json` | 31087 | `e1260407ce61c44882fa1541b7a56fb5691b97b32251d761c7efe8890d111a87` |
| `direct6-normalized/result.private.json` | 7390 | `2ea89eb1727c05008aee5e11b2462cd1ec5f9da0f6e50561966733ce5f33cb7e` |

实际完成固定输入/ordinary来源门、唯一pdata/unwind、六窗原件及严格解码记录、入口边字节核验、PUSH栈偏移与确定算术、只读4B/ordinary usage来源更正、目标唯一metadata/pdata及第6窗no-index更正。最终从保存原件核对六个SHA与256B大小、窗不重叠、所有线性已解前缀和未解剩余回拼、113条入口listing字节与只读4B SHA；这些是来源检查，不是服务端测试或客户端验收。

无目标CPU、native进程、live/global现场值、新模型/hook、Java/Proto/生成、Gradle/JVM/DB、实例/端口或客户端操作。无新的运行变更，无需本项构建或部署。本项未使用Git；唯一tracked产物为本文，由唯一集成者处理本地提交与Project/Harness状态，不修改其它专题或交接文件。
