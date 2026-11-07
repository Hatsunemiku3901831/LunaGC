# D02-R1 cold 接口、Type[]与比较器后继

2026-11-08。接续冻结的[cold completion](CLIENT_72_AVATAR_COLD_COMPLETION.md)，只新增本工具、测试和本文；限定交付已由主线审核及第三复验。研究过程没有现场进程/调试器操作、资源/生产改动、构建或部署；旧卡保持冻结。

主线已审真实代码、typed storage/锁/停止边界和原始证明，第三新进程raw SHA`5539fe3b6fa617375dc136b78419db4b1c7441ab517008a231b8ca0edc65d69a`与双跑逐字节相同，14项独立断言通过。证明`main/followups-main-proof.json`，日志`cold-interfaces-main-tests.log`。完整GetInterfaces/HasIEquatable仅在声明模型下通过；Array117/Type314自然初始化和完整comparer/default写回仍待，不批准g2或D-02。仅研究工具/测试/文档，无需服务部署。

## 计划与结果

目标是将上一卡之后的接口声明、class producer和predicate研究接成完整GetInterfaces→HasIEquatable，并继续Type[]工厂及泛型comparer必要后继。证据为五个固定客户端文件、provider4自然样本、其执行前源码、metadata及原始机器码。起初未知为Type[] class factory、三个接口的RuntimeType缓存、class层级/初始化和泛型构造。候选优先固定metadata可推导状态；最小实验先准确准备typed storage，再原生运行查询和集合代码，只有明确布局分配/释放与单线程锁可以建模。

失败出口为原生异常、未满足的实际函数/内存输入或缺缓存后的第一条必要指令；不补未采业务字段，不用返回成功模型跨越反射/泛型函数。验收要求完整调用顺序、独立非零/高位/继承/错误对照、双跑相同和唯一首阻塞。

**在明确prepared cache/storage模型下，完整GetInterfaces返回三个实际声明接口，完整HasIEquatable原生返回false。** 这次不再仅从GetInterfaces返回后开始循环。14组原生记录、14项独立断言通过。原生hierarchy producer还生成RuntimeType的五层链，generic comparer factory送入准确类型后停在Type314初始化前。完整cold comparer/defaultComparer写回、自然接口缓存及正式g2仍未完成。

## 固定输入与原始范围

每次真实重读并验以下五个文件，路径由当前GAME_PATH解析；原始文件不提交。

| 输入 | SHA-256 |
| --- | --- |
| YuanShen.exe | `8b014f64b5dc12fbc476e0c362b053abfdd7a1378cd282e4ad81057621dbd6b5` |
| global-metadata.dat | `45dc7a44dc9fc187146c52f6c33de57e2d1c1dcfec57f8736431553460f6ef2a` |
| startup-metadata.dat | `b314810840ca6f495822c9c44483dde2394b9f09ed3c6b13c2782092f04d5105` |
| AccountPlatNative.dll | `adecdf0bd5442e321b1b66b0ca9e98bef76d5067326aa4add5b75266055a6c25` |
| Astrolabe.dll | `87e0a005ac46515156636af2120d3f824bcd28d69253e7d7235b959b54a0b442` |

provider4原始 `plain/initialization-provider-elevated4.json` SHA `5a233bd0048f38f449e890268510e00d74870c770f58a416a82ceffe9faf0b9b`；执行前 `plain/source-snapshots/sample_client72_initialization-v3.py` SHA `2dfc86a6bd9f99ae72cd380c1315ba6d5f6d3968ff892a5eb7c089f4c1fc65ab`，本工具实际核对文件。复用前卡审核函数验证57项raw长度/hash/地址链。自然enum20060和RuntimeType284的type/c8以及enum+ca字节进入本次模型；其他父链/接口/数组cache是明确静态输入，不冒充自然读取。自然HashSet getter/ctor RGCTX仍0，defaultComparer字段未读。

上一卡之后的原始研究在`avatar/cold-completion/post-freeze-evidence.json`固定五份raw hash；本工具独立从固定客户端重提取，不依赖执行那些忽略脚本。新原始目录为`local/migration72/d02-active/avatar/cold-interfaces/`。

## 固定metadata给出的接口与producer

原生setup14051f370在14051f495..499取type record+43 xor25作为接口数，14051f505..52f从record+36加6f6c取接口起点，header+c8 xor1d590619定位接口索引表。独立恢复：

| type | 父类 | 直接接口数 | 实际接口 |
| --- | --- | --- | --- |
| 20060 FBOAFNIBKED | 204 Enum | 0 | 无直接声明 |
| 204 System.Enum | 405 ValueType | 3 | refs60193/444914/444915 → 241 IComparable、248 IFormattable、243 IConvertible |
| 405 System.ValueType | 371 Object | 0 | 无 |
| 371 System.Object | 无 | 0 | 无 |

这不是依据通常.NET enum行为猜测。每个70字节record保存原始hash和明确offset，工具重提取父类型。

真实class定义producer片段15a4d6f4e..6ff1从全ff旧存储清零前d0字节，包含+68。15a4d7291..72e2比较metadata+3e是否8709并设置+c9 bit20。三接口和enum的实际encoded值均8709，故该producer状态为+68=0、bit20清除；不是未知补零。完整class初始化与自然cache状态仍不由这两个片段代替。

RuntimeType284 method2746/slot82/159b6ec60检查这两个字段；方法名已从metadata移除，报告保留准确index/slot/实现。虚表+360按d0+82*8绑定为明确typed runtime输入。前卡已独立验证bit20与+68非零反事实，当前继续在完整循环里用泛型bit反事实证明会转入其他必要provider。

## 连续GetInterfaces与HasIEquatable

从1404dd170入口开始：真实type解析/cached class lookup、四层父链遍历、C++有序集合插入/平衡/去重、Type[] class cache查找、三个RuntimeType cache读取、数组元素填入和集合销毁全部按原机器码执行。随后从159b6e5b0完整入口调用同一provider，遍历三个RuntimeType并实际执行三次159b6ec60，返回false。

GetInterfaces 2025条原生观察指令，HasIEquatable 2409条（各含执行前return sentinel观察）；每条路径只有四次40字节set节点分配及四次释放，原生wrapper对三个interface各查两次RuntimeType cache。完整函数调用顺序、rcx/rdx/r8、native执行计数和路径hash全部保留。非零接口身份241/248/243、完整64位指针和数组尾部`fedcba9876543210`哨兵独立断言，不能因恰好返回false而忽略provider。

prepared输入包括固定声明对应的class table、+88接口指针数组、+b0父类compact offset、Type[] class cache和interface RuntimeType cache。它们是metadata和reader布局明确的storage模型，**不声称provider4已经采到它们**。GetInterfaces的分配型输出自身在本次原生路径内真实构造；不是给GetInterfaces或HasIEquatable挂成功返回。

| 模型入口 | 精确职责与限制 |
| --- | --- |
| 141a2e150 / 141cbfe48 | 仅1404dd170及其真实销毁函数的40字节节点alloc/free；核caller、size与一次释放 |
| 140514ee0 | 仅准确Type[] class、最多8项、GetInterfaces和generic comparer两个实际caller；写managed header/length并提供storage |
| 1405091d0 | 仅hierarchy producer请求的8/16/24/32/40字节storage |
| 141fdf9c0 / 141fdf310 | 仅已登记hierarchy内存清零/父链复制，核边界 |
| IAT143bac0f8 / 143bac708 | 固定PE import独立验证AcquireSRWLockShared/ReleaseSRWLockShared；两把已声明锁的单线程成对所有权，不执行OS锁 |

业务/反射/hash/compare/lookup/HasIEquatable没有返回成功模型。数组分配仍是typed managed模型，不把它称为GC/TLS分配器本体已执行。

## Type[]来源与精确cold分歧

`GetInterfaces/1404dd58a`读取default cell145bc97a8，其producer140542d34..d4e以固定ASCII `System`/`Type`调用14051f560后写回，绑定type314。另一独立metadata usageRow698707/source444046/cell145886658为System.Type314[]，也是generic factory159b6eb50的数组来源。

1404e9e60参数为Type314 class、rank1、特殊数组标志0。rank1 cache的三个独立global分别为145bc7160 mask、145bc7180 block array、145bc7188探测界限。type hash/compare直接执行1404f0740/1404f0820；32字节block含packed pointer、occupied bitmap和tombstone bitmap；16字节entry直接包含key class pointer和value array class pointer，**不使用RuntimeType cache的value-index布局**。hash来自class+10 descriptor，命中后返回完整array-class指针。

缺缓存独立对照执行到 **1404ea15f前**，共享锁已释放。下一条读default cell145bc9868；独立producer140542c0b..c25以`System`/`Array`创建type117并存此cell。后续先读Array class+ca bit20，必要时进入14051c9d0初始化；不能因固定metadata有Array就代填class状态。

**本卡接口provider路径的唯一首个未闭cold后继是这个Type[] class factory miss分支及其System.Array初始化输入。** 它不是声称整个自然Avatar构造已经运行到这里。若先声明Type[] cache ready，第二个独立miss在interface241的RuntimeType wrapper1405382a9分配前，锁也已释放；此分支没有被成功hit对照掩盖。

## 原生class层级与generic comparer后继

新恢复的14051f280是完整hierarchy producer：读取+b0父类，递归建立祖先的+c4/+48，分配8×depth storage、复制父链并添加自身。按固定RuntimeType284→TypeInfo531→Type314→MemberInfo513→Object371的父声明执行，实际生成depth5及 `[371,513,314,531,284]`，不是手填depth或ancestor数组。

原生普通类层级检查14051c02c..bdf5分别证明Type314接收RuntimeType284为true、RuntimeType284接收Type314为false、Type314接收Object371为false。这是完整assignability函数中的明确片段，不冒充其class初始化/泛型/数组分支已经完成。

generic comparer入口159b6eb50收到准确definition1239 `EnumEqualityComparer`的RuntimeType wrapper及enum20060 argument wrapper。它按145886658请求Type[]长度1，读取数组class+ac的element class，调用14051b7b0检查Type314←RuntimeType284。当前从真实producer得到的Type314状态尚需class初始化，路径在 **14051b7f0前**停止，即将读初始化lock；尚未执行159b6ebb5的数组元素赋值、1404e0c70的MakeGenericType、159b86640后继或159bd3660构造。更没有defaultComparer写回。

因此不得把“层级检查能成功”用来跳过完整class init，或把generic definition1239当已构造的`EnumEqualityComparer<20060>`实例。上述条件后继用于精确定位，不改变首个cold接口阻塞。

## 独立对照与边界

14组记录依次覆盖：完整GetInterfaces、完整HasIEquatable；祖先重复接口仍只有3项；空接口父链仍创建长度0 Type[]；null RuntimeType原生拒绝；泛型接口反事实停definition provider前；Type[] factory miss；interface RuntimeType cache miss；完整hierarchy producer；三个正反类匹配；同bucket的Type[] cache碰撞经两次原生keycompare才命中；generic factory准确输入及未写元素停止点。

反事实明确标名，不能作为实际metadata或自然状态。祖先重复测试在ValueType层额外声明一个IComparable，验证真实set去重；最高位哨兵和>32位对象指针分别验证不同的宽度要求。原生错误/未闭路径不会被测试解释为false业务结论。

自然只读若未来已有合法批次，可按当次B读B+5bc97a8→Type314 class+10的16字节；按实际hash定位上述rank1 cache首block，只有occupied才读一条packed entry/key/value class身份，并与独立B+5886658 cell对照。不遍历后继猜miss。B+5bc9868→Array117 class+10/ca分别16/1字节可解释首个cold初始化前置；nonnull不等于ready。此为可选精确取证规格，本工具不发起任何现场操作，也不重复自然资源的七个空槽采样。

## Java及实现许可

本卡没有新业务字段、sender或协议许可。准确Java来源仍为`Avatar.getAvatarId()/getGuid()`、`getExp()/getPromoteLevel()/getLevel()`、`getEquips()`内同一`GameItem.getGuid()`和`getSkillDepotId()`。生命/战斗值须在`Inventory.loadFromDatabase → AvatarStorage.postLoad → recalcStats → completeLoadingVitals → restoreLoadingVitals(true)`完成后读取；技能集合getter和Ornament字段仍需独立语义闭合，`getCostume()`不能替代Ornament。

本卡仅允许后继离线研究复用：已核接口metadata、class清零/泛型标志producer、准确Type[]缓存reader、完整prepared-model GetInterfaces/HasIEquatable、hierarchy producer和已记录的失败出口。正式实现仍需同源业务资源/Store及全部必要后缀、完整cold状态/构造与cache写回、Java独立wire和实际新旧号验证；不得将空、0或省略用于求通。参见[warm字段规格](CLIENT_72_AVATAR_LIVE_CONTRACT.md#java来源与实现规格)。

## 复现与实际状态

```powershell
$coldInterfacesClient = & powershell.exe -NoProfile -ExecutionPolicy Bypass -File tools/game_path.ps1
python tools/client72_avatar_cold_interfaces.py --client $coldInterfacesClient --output local/migration72/d02-active/avatar/cold-interfaces/first.json
python tools/client72_avatar_cold_interfaces.py --client $coldInterfacesClient --output local/migration72/d02-active/avatar/cold-interfaces/repeat.json
python -m unittest tools.test_client72_avatar_cold_interfaces -v
```

测试默认读取first.json，主线第三验设置`CLIENT72_COLD_INTERFACES_REPORT`到独立输出。无报告不skip求绿。probe1/2/3保留阶段记录；最初忽略目录脚本命名inspect.py遮蔽Python标准inspect导致Capstone circular import，重命名native_frontier.py后解决，未改目标或公共库。正式工具首轮成功，14项断言全部通过；没有使用编译/静态命中代替本卡实际原生回放。

本地提交与Project/Harness更新由主线审核后处理。下一项可执行动作是以上Array117/Type[]冷分支的最小typed输入闭合，或对取得的新自然cache导出做静态复核；不重跑同一缺输入状态。

最终双新进程`first.json`/`repeat.json`逐字节一致，SHA `5539fe3b6fa617375dc136b78419db4b1c7441ab517008a231b8ca0edc65d69a`，686626字节。工具SHA `c2dc58ba89d2eaa1b777393c6f081fc4512eda51037e39f30e67afd7700479c0`；测试SHA `5ee97f58a0f5af37cdb32ac822c51671ab1c570e2f9addc140a00df7e38454b7`。`tests-first.log`记录14项通过，`determinism.json`记录两份完整raw相同。此工具、测试和双跑产物冻结供主线第三验。
