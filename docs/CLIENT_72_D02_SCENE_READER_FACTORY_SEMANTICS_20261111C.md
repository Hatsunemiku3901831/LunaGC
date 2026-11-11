# F-03d3s2 / Scene reader 工厂值流与初始化首缺（2026-11-11C）

任务：`F-03d3s2/SCENE-READER-FACTORY-SEMANTICS`，父项 `D02-R1`。本页消费已验238B工厂原窗和既存资源加载原件，交回准确值流及一个最小后继，不重提parser、字典、真实782517B payload或707组/World21名称。

## 结论与验收范围

**普通路径返回的是虚调用取得的同一个对象，且在返回前明确写其`+1c=0`；这只关闭该存储槽的静态清零事实，尚未证明自然reader的有效起点。** 工厂另有一条ILFix wrapper尾调用路径，其结果及自然选择均未知。完整返回声明类型、对象来源/重置/归还、有效backend及refill合同仍未成立，不能标自然reader、Scene、resourceReady或D-02通过。

普通路径先调用`146eb90f0`取得U64值；非零时把它写到reader`+20`，把`141123260`返回的低32位写到`+28`。只有该值为零才调用`146eb3550`，其非零结果写到byte[]字段`+10`。工厂不直接写`+18`，也不在fallback分支直接清理`+20/+28`。这不是可以补零实现的完整构造规格。

旧审计已保存`146eb90f0 → 147065360 → 14713f3c0`三个完整带hex的原窗；本轮重建并独立解码后直接复用，**不再申请重取它们**。资源主路的首个缺机器码窗为`14713f4a0..14713f64e`的430B。旧索引报告只记其范围/hash，另有无hex listing，不能重建原code。因此唯一新窗规格选这个ResourceIdentifier重载，不并发展开factory的fallback、length helper、tail或整个provider。

## 固定来源与本轮实际核验

本轮只读原件，未读取当前PE、GAME_PATH、全局内容或现场；下面的输入一致表示所存报告的同源关系，不冒称重新核过当前客户端文件。

| 固定来源 | 实际大小 / SHA-256 | 用途 |
| --- | --- | --- |
| `local/migration72/d02-resume-20261111b/scene-reader-factory-window/{fresh1-20261111b,fresh2-20261111b,main-third-20261111b}/` | 每份9原件逐byte一致；body238B / `f3c65163277d38595ff0b48bac94e1a6a047c097f71b28f518c545b028a9c905` | 60257.m500134完整68条；hex恰拼回body并独立连续解码 |
| `local/migration72/d02-active/resources/index/native-final-{1,2}.json` | 每份312462B / `a57966dabbc34b177d6a03bd76dfc91e02ba6bd9fe606e9974e66c41bb9dba13` | 三个完整wrapper的声明、extent、hash和每条原hex；输入三hash与工厂相同 |
| `local/migration72/d02-active/resource-payload-index-astra/index-final-1.json` | 92384B / `e8a02de4a4b24c58be647e8f1cb2638b5e5a06f1b30427a8ae5a627291d54ab9` | 五输入与工厂相同；430B重载仅有范围、exactPdata标记和code hash，没有raw/instructions |
| 同目录`index-consumer-functions.txt` | 27765B / `6a023e794c15728d15551d9693153f237a28404f61585b6b8ef383e84a25a147` | 430B重载的历史listing，无hex；只作缺原件的范围证据，不据其升级机器码或运行结论 |
| `local/migration72/d02-resume-20261108/r3-equipment/metadata.private.pkl` | 117201003B / `df52eb70fe8e70dfecd1059e898d15e288cfa98bcefc78bcaabf753ae150349f` | 完整pin后以禁止globals的unpickler解析类型、字段和准确方法声明 |
| `local/migration72/evidence/client72-signatures.json` | 78649549B / `28377e7a8d3c891cd61be97ddb74f8502a22f2f2f039d6a91dba3b092d7822fe` | 参数类型和原参数名；返回类型不在该提取器覆盖内 |

三wrapper的完整原字节本轮由旧hex恢复；连续地址、extent、完整SHA与重新解码全部相符：

| 原窗 | 大小 / 指令数 | 完整原code SHA-256 |
| --- | --- | --- |
| `146eb90f0..146eb9334`，11307.m80101 `CommonMiscs.LoadBinDataZipFile` | 580B / 144 | `87fb95844fc0906bc9e1c4d8fd3ad2fa489a386c784e67edc6ca781b3329bd8d` |
| `147065360..14706548f`，11089.m78058 `Runtime.LoadBinDataZipFile` | 303B / 79 | `0ef1755a678c21027d9db6444fbfbb27fdd078fd2a84d858d01e37142c5ebd27` |
| `14713f3c0..14713f499`，11132.m78585 `AssetBundleExternalResourceProvider.LoadBinDataZipFile` | 217B / 65 | `096552e63e0f7ce6362fbdc2b8ed9e663bcfd82985452f42555c140cddd04501` |

输入五stock与profile的完整大小/hash沿用[已验工厂原窗](CLIENT_72_D02_SCENE_READER_FACTORY_WINDOW_20261111B.md)并逐项写入新request。没有新网络检索或clone；复用[既有GitHub增量成果](CLIENT_72_D02_SCENE_GITHUB_INCREMENT_20261111.md)与仓外来源，不重复已验提取或字典审计。

## 工厂参数、分支与各返回

设三个显式参数为`u:uint64`、`a:int32`、`b:int32`。入口`9218/921b/921d`分别保存b到EBX、a到EBP、u到RSI。上游同步caller给的是PathHash第二QWORD/bin及两个字面0；没有把`a/b`改名为offset/count/mode。

| 原site / 条件 | 观察：原指令和值流 | 推导与未知 |
| --- | --- | --- |
| `9220/9227/922e` | 比较byte`1458308f6`；从QWORD cell`14586b600`取root。flag零走本地路径，非零跳`92c5` | 未读flag/root内容，不知自然选中哪一路；这是两个实际分支，不可只保留本地路 |
| `9234..9251` | RCX=`[root+5d60]`，null跳`92ee`；RDX=`[14589ff40]`，R8D=0，RAX=`[RCX]`，调用`[RAX+f0]` | `+f0`是所取klass的实际调用槽；槽内容、返回来源及任何内部重置/构造未知，不能据此追A allocator |
| `9257..926b` | 该返回非零才进入本地正常流程；RDI保存同返回。写QWORD`[RDI+10]=0`、DWORD`[RDI+1c]=0` | 清零事实成立；没有直接调用reader `.ctor`，也不能证明间接callee不含构造/复用逻辑 |
| `9272..9285` | 栈第五实参DWORD=`100000h`，RCX=u、EDX=a、R8D=b、R9D=0，原E8调用`146eb90f0` | u完整64位保留，两个int32原位值保留；本地没有以a/b计算reader偏移 |
| `928a..929d`，上项返回v非零 | RBX=v；RCX=v调用`141123260`；其EAX写reader`+28`，原v写QWORD`+20` | `+20`声明U64，不因非零就证明有效native指针/handle；`+28`只证低32位结果，helper完整返回/长度/容量/可读范围仍未知 |
| `92a3..92b5`，v为零 | RCX=u、EDX=0、R8D=0，调用`146eb3550`；结果非零才写QWORD`[RDI+10]` | 这一路没有转发a/b；结果被byte[]字段接收，但callee返回声明、实际bytes/extent未闭。结果零也不让本地factory正常出口返null |
| `92b9..92c4` | RAX=RDI，恢复栈与保存寄存器后唯一`RET` | 三种本地结果（v非零；v零且fallback非零；两者都零）均返回同RDI。最后一种只清了上述两个槽，不等于有效reader |
| `92c5..92e9`，入口flag非零 | 从`root+232a0`再取`+3d0d50`为RCX，null跳`92f8`；RDX=u、R8D=a、R9D=b，恢复栈后尾跳`14d9048c0` | 固定metadata唯一绑定33459.m265146 `ILFixDynamicMethodWrapper.__Gen_Wrap_481`，显式U64/I32/I32；此窗没有其返回/字段写入/生命周期合同 |
| `92ee/92f3/92f8` | 三个null检查分别进入同地址helper`1404c2be0`的三处原CALL；函数末尾有`INT3` | 只能称失败后继；未取helper体，不把它当成功或断言其必抛出/不返回。三个入口后没有本窗显式通往正常RET的边 |

普通返回值的reader角色来自已验caller把同RAX交显式`reader42005`的parser参数。**这不恢复factory的声明返回类型，也不观察自然返回对象。** 该区分与[上游来源合同](CLIENT_72_D02_SCENE_MPLEVEL_GROUP_SOURCE_20261111.md)一致。

## 五个实例字段：store事实与有效状态分开

固定42005字段仍为：byte[] `207760/+10`；int32 `207761/+18`、`207762/+1c`；U64 `207763/+20`；int32 `207764/+28`。

| 字段 | 工厂普通路直接作用 | 尚不能得到的结论 |
| --- | --- | --- |
| `+10` | 先null；仅zip结果零、fallback结果非零时保存fallback返回 | 不能把成功恢复的782517B文件自动绑定成该自然数组 |
| `+18` | 无直接写入 | 原值/生产者/初始化语义未知；不称cursor，不补0 |
| `+1c` | 明确写int32零，两种后端分支都在此后 | 既有primitive-reader模型将该槽作cursor；静态清零不证明对应有效backend起点或之后refill状态 |
| `+20` | 仅zip结果非零时写同U64 | fallback路未直接清此槽；不能假设来自全新零内存，也不能由U64类型证明所有权 |
| `+28` | 仅zip结果非零时写`141123260`的EAX | fallback路未直接清此槽；不能用低32位赋值证明完整64位长度、容量或有效数据数 |

既有[行reader合同](CLIENT_72_D02_PROUD_ROW_READER_20261111.md)对`+1c`/byte[]容量/有效字节的分界只读复用。本工厂不含读取循环或直接refill实现；何时切换后端、短读与EOF、+18用途，以及从+20取得下一段数据的调用均未由本窗证明。真实文件offset0的语法闭合继续只证明文件framing。

## 已存wrapper怎样消费参数

三个原窗均已重核完整机器码；本节仍只报告静态条件关系，没有执行其资源查询、Runtime、provider或任何helper。

1. `146eb90f0`保存u/a/b/第4参0及第5参`100000h`。正常路径在`146eb91cd`把同u/a/b交`147065360`，第4/5显式参数作为instance调用的栈第5/6位置。`146eb91d2`保留原返回，条件delegate/cleanup路径之后可将同值返回；另有wrapper分支和错误/清理代码。它没有reader参数，也没有直接初始化reader五字段。
2. `147065360`正常路径取receiver`+10`，有虚槽状态检查与条件初始化，再于`1470653c8`尾跳`14713f3c0`；u/a/b保留，栈中的另两参数继续留给同签名instance调用。它还有独立wrapper分支，不能把普通tail静态存在当自然运行已到达。
3. `14713f3c0`正常路径从provider`+30`取得索引。原u交`147234540`；其AL为零时`14713f435`清EAX并正常返0。非零路径还调用`1472377c0`、`147237b80`，后者RAX在`14713f41f`移至RDX；receiver为同provider、a/b仍在R8D/R9D，`14713f430`尾跳`14713f4a0`。新目标的准确第一个显式参数声明为`ResourceIdentifier11130`，所以此处不再把RDX称原hash。旁路wrapper和null失败入口仍保留。

固定签名为三个wrapper记录了`hash/offset/length/compressionType/pageSize`这些**原参数名**。本页允许把a/b描述为“转交其名为offset/length的参数位置”，不据名字断言零表示文件offset0/全长度，也不将factory改名参数；这种行为须由实际callee消费及有效输出支持。numeric0的CompressionType枚举成员含义、本次自然flag/receiver/index选择均未观察。

## 唯一成熟后继：430B ResourceIdentifier 重载原窗

机械任务：`F-03d3s2/SCENE-READER-BACKEND-IDENTIFIER-ONE-WINDOW`。独立request：

`local/migration72/d02-resume-20261111c/scene-reader-semantics/backend-identifier-one-window-request.private.json`，**12248B / SHA-256 `4d64dbb5a6189096ef413f0ea7f8380a45b09fd451d705ea4517cd65a68f6f7f`**。

| 项目 | 可执行规格 |
| --- | --- |
| 唯一入口 | owner11132、method78586、普通实例方法、flags86、slotFFFF，`14713f4a0`；显式参数：ResourceIdentifier11130 / I32 / I32 / CompressionType8024 / I32。metadata/signature完整pin及逐记录索引均写入request |
| 已验caller | 本轮从固定旧report恢复`14713f3c0..14713f499`全部217B，SHA见上表；独立解码65条。新PE仅核`14713f430`的5B原tail `e96b000000`，signed rel32目标必须同入口；不重读完整caller或三个wrapper |
| AOT | cell`1422f9950`应为原QWORD`1428a54d0`；P+78586*8=`14293eca0`应指同entry。须新核两QWORD共16B，不把本页的计算当已采原pointer |
| pdata与body | 重新核固定exception目录14344080B/SHA；exact与containing各唯一且同记录，先保存真实12B行和pre-body。预期完整范围`[14713f4a0,14713f64e)`、430B；code预期SHA `a0da494e959a2705129d4e6c772f9d0c3c8fb58de17842556e6117dbd667d2d1`。非可执行、非file-backed、范围不同、非正或超430先留诊断并停，不能取邻窗补齐 |
| 解码与输出 | 全430B连续覆盖，最多256条；原body/asm/SHA、calls/branches/RIP/return/tail、来源门禁、边界/pre-body/caller-tail/AOT/pdata全部保存。两fresh、独立纯原件audit、主线第三逐byte比较 |
| 本窗能区分 | ResourceIdentifier及a/b/两个额外参数的真正消费；零/非零和wrapper返回；是否直接生成+20所存backend、可读范围或refill机制；准确首个后继。必须等原窗验过再解释旧无hex listing |
| 本窗不能代供 | `+f0`对象来源、`+18`状态、`141123260`返回、fallback`146eb3550`、factory wrapper`14d9048c0`、自然flag/receiver/返回/生命周期。不得为了补这些缺口自动开第二窗 |

门禁测试包括完整来源/profile/五stock漂移、错误声明/参数、原tail与正负rel32和完整64位目标、错误AOT、缺失/重复/不一致pdata、非正/超430/不同预期范围在body前失败、非可执行/非file-backed/短读、code SHA/连续解码及256条限制。纯audit只读保存原件，不接触GAME_PATH/当前PE/目标CPU。

尚不能交运行代码、自然reader投影或有利的backend/refill模型。非零/64位、嵌套/列表、错误/边界的业务期望必须来自真实后端合同；新窗先保留原64位request和signed int32操作，不能把原文件解析测试挪作这些未知行为的oracle。

## 本轮复现、独立性与返回

私有审计：`local/migration72/d02-resume-20261111c/scene-reader-semantics/audit_scene_reader_semantics.private.py`。两个新Python进程实际检查全部source pin、禁止globals的metadata、准确signature/五字段、工厂三套9原件一致，重建并独立解码4个已存body共1338B/356条，验证provider原E9。两份审计报告各8610B，SHA-256 **`38ab32d772b1d03ed367e5dc69889e244d4b325bb4069312efc6d01ad26def69`**，四份对应恢复raw也各自相同。

主线第三命令（选新的未存在输出目录）：

```powershell
python -B local/migration72/d02-resume-20261111c/scene-reader-semantics/audit_scene_reader_semantics.private.py --output local/migration72/d02-resume-20261111c/scene-reader-semantics/main-third
```

这些是固定来源/原字节审核，不是native路径执行或客户端测试。本轮新PE/code采集、target CPU、live、构建、JVM、客户端、DB、端口及部署均为0；无运行代码/Proto/patch/config/resources变更，无需重启。本子任务不操作Git/index，提交和Project/Harness/HANDOFF维护归主线。

已审四窗未出现准确直接CALL到A已拒第二allocator；不能由此断言所有未审后继独立。A-SECOND-RETURN-CONTRACT、热Default/allocator/第二返回归属及B组合loader保持原拒绝冻结，不读或运行草稿，不借Scene/Pool续接。若唯一新窗出现准确拒绝CALL/data边，只保存原边、停止该边及依赖；新安全拒绝保留原提示，不换工具/模型/代理或间接重试。

本页关闭工厂原窗的参数/分支/store/返回值流分析及后端新raw首缺定位。下一项只执行上述430B单窗；获得机器码后再决定必要语义后继。没有完整Scene/reader/World/slot114验收，也没有新增ready或父项完成标记。
