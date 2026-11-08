# R3-STRING-COMPARER-INPUT

## 实际静态交付

找到精确primary root **fresh module+596c848/8B**，来源为global metadata usageRow750/slot265、methodSpec13200→method10288/classInstance4/String。cell属于非file-backed区域，文件没有其当前值；本卡没有采到MethodInfo/default或执行任何原生CPU。固定classInstance整数4及注册GenericInst记录均未当成自然runtime指针。

新完整窗仅1个：GetDefault `15746c870..15746c9ef` 383B/SHA `fb37ad9d367cb55a85501b2ad6d8520a095aa0c40d9feaf37ade46cf8c103ef1`。必要cold helper `159b6e720..159b6eb28` 1032B/SHA `3c587b6461f41cd11b685afd9313ed1235fd2d8d6bb2fbda746511f83a6367d4` 已由旧cold第三完整固定，复核复用；加MethodCache七旧窗，共9准确原范围，没有第二新窗。

primary合同最坏**首遍17读920B、单读最大208B**。R1确认其现有42读2296B也是新增首遍上界；合并为**59读3216B≤64读4096B**，主线已允许R1在新warm collector集成optional String结果。所有精确跨度复读归R1统一final，不在本卡实现另一个collector；R1整体cap5276读96608B不变。单独String图的精确复读上界34读1840B。两个32-probe稀疏query仍只是未采取的独立后备，不计入这59/3216，不会因primary失败自动启动。

静态双fresh各 **12843B/SHA `e94a8da07ef71eac494700493dbf394c0e1c0eb8b781400006cbb2102ef86e98`**，逐byte相同；21个纯host有界正反状态向量、原usage/AOT注册/父VAR0参数源及9范围hash均通过。它们只验证采样选择规则，不是原生控制或自然输入：`originalInstructionHooks/liveReads=0`，`actualDefaultObtained/actualEqualsCodeBound/completeStringComparer/gadgetConfigurationApproved/abilityApproved/d02Complete=false`。

主线已实际执行 `main-third-static.json`，同12843B/SHA。另由纯标准库close逐byte核双fresh/可读第三、17来源pin、9个完整PE范围和21向量/预算/false边界，无producer导入、无原生CPU或live读取；结果在本卡 `determinism.private.json`。

## 实施前计划

承接冻结Gadget config-key的准确停止 `158b48cf6`，本轮只恢复下一次必要自然样本的String comparer输入合同，不重跑七case、资源、外部候选或配置query，不提供DefaultComparer/业务成功模型。旧config-key源码/报告/专题均冻结；仅本页及忽略 `local/migration72/d02-resume-20261109/r3-string-comparer-input/` 可写。无新ready，不启动或附加客户端，不操作REA/Git/构建/DB/现场/共享索引。

固定证据是当前 `tools/game_path.ps1` 解析的五stock、原metadata/签名/AOT、上卡两个String FindEntry/原String hash及typed共享注册，另复用冻结 `tools/probe72_avatar_method_cache.py` 的精确GenericInst/Method hash、比较、稀疏cache读取规则和根。已有enum20060采样仅证明旧enum上下文，不能套给String。

先查准确旧cache。旧 `scene-entry-astra/static2.json` 的大跨度指令流可找到15746c870指令，`avatar/flag-region.json`有caller文本；它们没有该方法独立准确PE extent/hash，不能当已审完整窗口或本轮新增成果。首个新窗只核 `15746c870` 的准确完整PE范围及原GetDefault ABI；若依赖一个尚未审、又为合同必要的有源直接callee，先列理由再取一个，总≤2。其它新caller/泛型图/方法名称遍历均不做；后继未知精确保留。

静态核验顺序：

1. 分开原CodeRegistration共享AOT与concrete caller contexts。核classInstance4的注册GenericInst/参数记录、String descriptor与10288注册行，判断它是否给runtime GenericClass/owner/MethodInfo指针；serialized整数不能直接叫runtime pointer或自然canonical。
2. 复核原GetDefault对RCX carrier、owner、rgctx、class/static area、初始化flags/default值的实际读取，区分MethodInfo与class，不手设flag1或选择初始化分支。明确返回default后FindEntry如何读实际Equals+110函数/MethodInfo伴随槽，以及后续callee的owner/参数所需字段。
3. 复用原 `1404f0740/1404f0820/14051fb70/1404eea50/1404f0640`，沿STRING kind14的准确hash/equality分支证明data是否消费，独立给count1/String14/byref0 canonical与method10288最小查询公式、null/flags/错误/probe停止。不套kind17/data20060公式，不把descriptor.data改成293。
4. 把下一采样图写成逐节点RVA/地址来源/width/type/context/守卫/停止及预算；只读必要字段，不读整个class/vtable/表。所有后继来自同轮成功读取，实际Equals functionptr保持该轮ASLR值，代码绑定与执行由后继另审。
5. 初始稀疏方案单列String额外两个最多32-probe查询，不预占R1 64读4096B；后续实际找到有源primary +596c848，主线确认改用17读920B直接图，可在首遍合并59/3216且原cap不变。稀疏方案仍未采取，不与primary混算。本轮不实现live adapter或改其文件，只把固定来源充分的小合同交主线审后合并。

原生CPU预算为0；最多2新完整准确窗，静态双fresh audit/source pins与独立负例必须通过。输出不是自然String缓存命中/default已取得或配置职责完成；若当前文件无法给其中某一地址/上下文，准确说明最小还缺节点并停止扩窗。主线持有第三审查、未来ready与现场授权。

## Primary来源与GetDefault真实ABI

固定CodeRegistration的共享AOT getter body为15746c870，String具体methodSpec13200的三元组是 `(10288, classInstance4, methodInstance=-1)`；其pointerIndex916，tableIndex124642。共享Object context另有methodSpec929/classInstance0，不能替代String的具体来源。serialized classInstance4是注册数组索引，不是Class/GenericClass/MethodInfo或自然canonical指针。

MetadataRegistration `1428a53c8/+70` 给注册instanceBase `142360770`。record4为 `1423607b0`（RVA23607b0），16B header count1，argument array `1455be280`（RVA55be280），唯一descriptor `142e624e0`（RVA2e624e0），原bytes `250100000000000000000e0000000000`，kind14/byref0。该记录只给静态参数内容；没有证明它就是当次intern canonical。原固定global usage cache array RVA596c000，加slot265×8，给准确cell RVA596c848；没有可用的concrete EqualityComparer<String> type-usage cell替代此MethodInfo根。

原 `15746c877..87a` 保存RCX并取 `[RCX]`：**RCX是MethodInfo**。随后检查owner+c8 bit0，取owner+30的rgctx、rgctx+10的class槽，再检查此class+c8 bit0，取class+38 static area的首QWORD。default非null时由 `15746c8b5→15746c92b..935` 返回，不执行class-init/lazy RGCTX/helper或写default。

本窄warm合同只接受实际slot0 class等于MI owner，且该owner的GenericClass证明definition1234、当前String canonical、methodInstnull、cachedClass backref。若flag实际0、slot/null/类型不符或default为空，保留partial及准确冷分支；不设置flag1，不调用initializer。GetDefault冷分支的原helper已固定，但本卡不执行它或采RuntimeType/反射输入。

## STRING14原hash/equality语义

复用原 `1404f0740`：kind14不命中mask `20008000` 或 `400e0000`，也不等于kind21，于 `1404f07af` 直接返回 `14*1cfaa2db + byRef`；该分支不读descriptor前8B data。原 `1404f0820` 先比kind/byref，kind14在 `1404f0896/089c` 的无符号分派直接到 `1404f0965` 返回相等，也不读data或attributes。故STRING descriptor.data的固定0x125应原样保留，但匹配语义不要求它为293，不能套kind17/data20060公式。

原GenericInst的count1 seed为1，按 `14051fbb8..fbc7` 组合后得到hash **15×1cfaa2db=7292816085**。canonical必须来自当次内容相同的runtime图；只有未来另启稀疏后备时，才以此hash查实际roots。单独String type+byref1的hash会加1，匹配必须拒绝；低16位attributes和data变化不改本STRING14比较结论。这些是静态分支和纯host公式核验，本卡未运行这些原指令。

## 最小字段图与预算

下表每条为一次有界请求。指针值均保留本轮完整64位，`B`来自同轮已核模块身份；相同已读对象直接复用跨度。自然collector由R1新卡/主线实现，本卡仅提供源和选择规则。

| 序 | 地址来源 / width | 守卫与停止 |
| --- | --- | --- |
| 1 | `B+596c848` /8B | null/错误/短读即partial；非零仅候选pointer |
| 2 | cell值指向的MethodInfo /56B | +0 owner非空，+18/u32必须10288；不重写函数字段 |
| 3 | MI+0 owner header /208B | 实际+c8 bit0=1，+30/+38/+68非空 |
| 4 | owner+68 GenericClass /32B | U32+0=1234、Q+8为canonical候选、Q+16=0、Q+24=owner |
| 5 | GC+8 GenericInst /16B | U32 count1，Q+8 argument array非空 |
| 6 | 唯一argument array元素 /8B | descriptor pointer非空 |
| 7 | 唯一STRING descriptor /16B | kind14/byref0；data/attributes不作相等条件 |
| 8 | `[owner+30]+10` /8B | 实际class须等于owner；否则partial，不替换 |
| 9 | `[owner+38]+0` default cell /8B | 实际default须非空；为空停冷分支 |
| 10 | default object首klass /8B | 非空；未读取对象其它字段 |
| 11 | 实际derived class header /208B | 实际kind18/21与warm flag；definition据下述有限源确认 |
| 12 | derived+68 GC /32B，条件 | 非空才读；GC的实际definition、canonical、null methodInst、backref必须相符。ordinary1243要求GCnull |
| 13 | `derived+110`函数槽 /8B | 原FindEntry slot8；非零，原ASLR值保留 |
| 14 | `derived+110+u16(derived+be)*8` MI槽 /8B | 原companion槽，vtableCount≥9、MI非空 |
| 15 | companion MethodInfo /56B | +0实际owner，+18必须其非abstract Equals/slot8 |
| 16 | 若companion owner不同于已读owner/derived，header /208B | 有限同String祖先关系，不接受同String sibling |
| 17 | 上项owner+68 GC /32B，条件 | 非空才读；相同canonical、methodInstnull、owner backref。无额外context追踪 |

所有最坏条件计入仍为17请求920B；ordinary1243、重复owner等路径会更少。统一final须覆盖新增跨度和此前旧图，不能在旧collector已结束复读后追加读取并声称旧图末尾一致。任何漂移/相矛盾重叠/退出/身份改变保留partial；重复一致仍不是atomic snapshot、自然lookup或writer排除证明。

完整GetDefault warm所需源字段已在图中；Equals的调用输入为 `RCX=actual default object, RDX=entry key, R8=query key, R9=actual companion MI`。原FindEntry从+110读取函数、从伴随槽读取MI后直接call，**不比较slot函数与MI+8字段是否相等**。采样可记录二者是否相同，但不据此重写指针或预绑定代码；真实Equals实现、可能再需的rgctx/库输入与完整FindEntry执行由采后原生后继审查。

## Derived与companion owner的有限源守卫

GenericClass32的实际definition决定使用哪一份固定metadata父模板，不能把已采enum1239指针借给String。六generic family的父VAR都经原14B参数行及冻结 `generic_position` 验到ordinal0：

| 实际generic definition | 固定parent chain / VAR参数index | 非abstract Equals owner/index |
| --- | --- | --- |
| 1235 | 1235→1234，363/VAR0 | 1235/10296 |
| 1237 | 1237→1234，365/VAR0 | 1237/10310 |
| 1239 | 1239→1234，366/VAR0 | 1239/10324 |
| 1240 | 1240→1239→1234，367/366均VAR0 | actual parent1239/10324 |
| 1241 | 1241→1239→1234，368/366均VAR0 | actual parent1239/10324 |
| 1242 | 1242→1234，369/VAR0 | 1242/10333 |

这些是实际definition识别后的继承模板，不是本卡选定的自然Default类型。generic GC的classInst须为同轮String canonical、methodInstnull、cachedClass回指当前class。未知definition/链/参数形状停止，不枚举其它candidate。

另有有源ordinary definition1243 `InternalStringComparer`：固定metadata parent是 `EqualityComparer<String>/1234,instance4`，没有VAR替换。只有实际header为kind18/data1243、GCnull且parent核验相符才用此分支；其Equals为10339/slot8。旧helper的完整固定代码含String RuntimeType特例来源，但本卡没有执行constructor或声称自然default一定是1243。

companion MethodInfo的owner必须是实际derived本身或其固定父链祖先，并继续满足同String context；1240/1241的继承Equals不能误认属于child。基础1234的方法10289 flags5c6是abstract，不能作成功companion。普通1243与generic1235虽都可有String上下文，彼此是sibling，不能配对。MI、owner、函数仍全部来自本轮实际读取，函数代码与AOT映射另审。

## 冻结合同、静态负例与第三命令

两个合同原件分开冻结，后者明确祖先约束与函数/MI字段规则，不覆盖前者：

- `sampling-contract.private.json` 42870B/SHA `7722ea05b8fabc04527fafec20585b31f6a3d985ab683037c05e90b15e9df20a`，17节点、source pins、GetDefault ABI、STRING14语义、父链14B源及未启后备。
- `owner-pair-contract.private.json` 2502B/SHA `bf03a9d29054fba2f467c2a54220ee966ad964b4a937ba91d2035ab243716291`，actual-derived→companion owner/index、原FindEntry四site与ASLR规则；不加读/字节/窗口。
- `getdefault-and-cache-windows.private.json` 483705B/SHA `fd1ea590071f564817a28c2a5017858fd8b1b46428c716f4b69dd2ddd43ba64f`，新383B及七复用窗/注册来源。

21个纯host向量覆盖data/attributes不敏感、byref/kind/短descriptor、null根/default、flag0、slot0错误、ordinary unknown/借enum类型、真实generic context选择、错canonical、inherited companion/child混淆、abstract方法、同String sibling、空函数及原ASLR字段保留。它们没有交给emulator，没有用手设flag或Default输入求原生成功。

两次静态提取假设失败原件保留：旧type-equality窗口尾部含跳表数据，改为显式标记skipdata而不把数据当指令；BSS usage cell无文件-backed字节，改为记录currentValueUnknown而不补0。这些均为静态失败，0原生CPU/0live。本卡未修改任何旧源/文档/raw或R1文件；optional String结果由R1单列，主warm结果不借String失败改写。

root第三使用新输出，脚本自行通过当前 `tools/game_path.ps1` 解析五stock并核源，不带live backend：

```powershell
python local/migration72/d02-resume-20261109/r3-string-comparer-input/audit.private.py --output local/migration72/d02-resume-20261109/r3-string-comparer-input/main-third-static.json
```

新第三应与双fresh12843B/SHA相同。脚本不覆盖已有输出；后续另跑用新文件名。主线审核及未来ready/RPM/退出恢复在独立R1/live卡处理，本卡没有collector、现场或部署许可。

主线已实际执行该第三，12,843B/SHA `e94a8da07ef71eac494700493dbf394c0e1c0eb8b781400006cbb2102ef86e98`与双fresh逐byte相同。脚本实际核17pin、九准确原范围、注册13200/usage750/slot265、八VAR0原参数及21向量；另主线10项独立审核通过，证明`main-review.private.json` SHA `a5e695279d4f2b6d1b5f53a22c72588edd020d7de5a08a70b47aff296f45b5b5`。只验收来源与有界静态合同，不声称已取得自然Default、已绑定Equals代码或已加载配置。

## 未启稀疏后备

只有primary无法给出输入、主线另审后，才可单列两个32-probe方案。GenericInst三根为B+5bc8c18/+5bc8c38/+5bc8c40，Method三根为B+5bc7290/+5bc72b0/+5bc72b8；按已审cache的mask/packed bitmap/rank/probe语义，查询String count1 canonical，再以 `(10288,actual canonical,0)` 查method。occupied collision不能在probeLimit处当miss；probe cap是partial，不触miss materialization。

若只取String匹配实际消费的descriptor+8/4B，最坏首遍公式为 `12根读/96B +32*(5读/60B) +32*(6读/92B) +1MI/56B =365读5016B`，精确复读上界730读10032B，明显不在primary追加预算内。当前不实现、不采样、不启动、不挤入R1 cap。primary当前值和Default/Equals函数未取得，本卡最终状态仍为静态合同，不标String comparer或Gadget配置完成。
