# D02-R1 Avatar cold comparer 输入与 RuntimeType 缓存

任务 `F-03d3f4d2-cold-contract`，2026-11-07。独立后继，不修改已冻结并由主线提交 `5cba125` 的[warm构造合同](CLIENT_72_AVATAR_LIVE_CONTRACT.md)。只新增本工具、测试及本文；没有客户端/调试器操作、生产改动、构建、数据库、部署或Git提交。

**主线已审核的限定结果：cold `EqualityComparer<enum20060>.get_Default` 已在准确type输入和预置RuntimeType缓存下，连续运行到 `ComparerHelpers.CreateDefaultEqualityComparer/159b6e720` 入口。** 没有创建comparer或写回defaultComparer，完整cold构造/Avatar handler/正式g2仍未通过。20组记录含23次原生调用路径，10项独立断言通过，双新进程完整/摘要逐字节相同。

## 目标、证据与边界

上卡恢复了enum初始化选项和准确共享ctor，但自然cold输入仍未知。本卡优先解决三个可区分问题：rgctx slot到底是类还是type描述符；RuntimeType缓存按指针还是类型内容比较；enum comparer分派选哪种具名泛型定义。证据为固定EXE/global/startup、实际metadata RGCTX word、注册type descriptor、目标代码和PE import，不按近名或普通int32猜enum行为。

最小实验连续运行cold getter到helper前，独立执行原生cache的hash/比较/查找和TypeCode分派片段。managed storage是明确输入；只有两个已核Windows共享锁入口用单线程模型，不给任何业务或反射函数默认成功。cache miss、泛型构造和自然首次初始化各保留独立停止点。

每次进程重新核三hash，与warm卡相同：EXE `8b014f64b5dc12fbc476e0c362b053abfdd7a1378cd282e4ad81057621dbd6b5`；global `45dc7a44dc9fc187146c52f6c33de57e2d1c1dcfec57f8736431553460f6ef2a`；startup `b314810840ca6f495822c9c44483dde2394b9f09ed3c6b13c2782092f04d5105`。报告保存各源码及原生窗口hash，不读取历史PID或客户端内存。

## cold T 的准确来源

目标HashSet闭合实例5917的参数来自registered type reference **48169 / 静态指针142f1e560**，文件声明为enum20060。不是fixture重新造一个相近type。对 `EqualityComparer<T>/1234` 的class RGCTX表重新解析并绑定该参数：

| 槽 | definition word | kind | 绑定后的实际内容 |
| --- | --- | --- | --- |
| 0，context+10 | `0806d795` | 2 | `EqualityComparer<20060>` class |
| 1，context+18 | `0406d794` | 1 | 20060的 **type descriptor** |
| 2，context+20 | `0806d794` | 2 | 20060的class |

kind1与kind2不可混用。static field `defaultComparer` 位于class static area offset0；getter是10288。helper由另一metadata声明确认：type1328 `ComparerHelpers`、method10494 `CreateDefaultEqualityComparer`、地址159b6e720。其先行`HasIEquatable`是10493/159b6e5b0，后继159b6eb50是RuntimeType的泛型创建路径，未伪造这些返回。

在明确initialized class table中，从157d7fe90开始，defaultComparer为null，真实执行 `140517680 → 1405269f0 → 15a4d6b60` cached-class路径，接 `140538230` RuntimeType缓存查询。得到的RuntimeType对象为显式type284 wrapper fixture，+10指向20060 type；在159b6e720前停止，传参和原cache仍null分别断言。这证明精确输入交付，不证明helper完整执行或自然缓存存在。

## 原生缓存与独立hash断言

完整原生 `1404f0740` 计算type hash，`1404f0820` 比较type，`14053c1c0`执行稀疏cache查找。模型仅准备可追溯布局的storage；没有替换hash、比较或查找函数。

对于已核kind17、typeDefinition20060、byRef位0的descriptor，独立整数公式是：`((17 * 0x1cfaa2db) * 0x1cfaa2db + 20060) mod 2^64 = 4018434798416866117`。实际原生值相同。改变低16位attributes保持hash和相等性；改index为20124使hash加64，在64槽fixture中产生真实碰撞；byRef位1使hash增加`0x1cfaa2db`，原生比较也将其视为不同type。

六查找样本分别是直接hit、同type/不同指针和attributes的hit、collision后第二项hit、collision miss、byRef miss、empty miss。所有hit保留完整64位值`fedcba9876543210`；miss返回false且输出仍为旧`123456789`。collision hit实际执行两次key比较。hash断言另用整数公式，避免用同一hash函数安置/查询产生循环自证。

`140538230`的cache-hit wrapper只调用两个模型：固定IAT `143bac0f8 = AcquireSRWLockShared`、`143bac708 = ReleaseSRWLockShared`。单线程模型仅核对同一lock指针及acquire/release平衡；真实OS锁未运行。其余type解析、key比较/查找及返回均执行原始机器码。反射cache为空的独立样本在 `1405382a9` 分配前停止，锁已释放，defaultComparer仍null；没有执行RuntimeType新分配、cache insert或其并发语义。

cache layout只按已执行reader解释：table+8为mask、+28为32字节block数组、+60为value数组；block+0为16字节packed entry数组、+8为occupied bitmap、+10为tombstone bitmap；entry含type pointer和value index。table+10/+18另参与边界，+30参与探测界限，不为未证字段发明额外业务名称。64槽是本实验输入，不是客户端上限。

## 精确enum分派片段

显式live-in `eax=TypeCode`，从159b6ea12执行真实跳表到 `159b6e9a2` 或`159b6ea6a`后继call前。descriptor来自固定MetadataRegistration type数组，不是按代码形状命名。

| 输入TypeCode | 目标definition / 名称 | descriptor offset |
| --- | --- | --- |
| 5 | 1240 SByteEnumEqualityComparer | `691320` |
| 6、8、9、10 | 1239 EnumEqualityComparer | `691330` |
| 7 | 1241 ShortEnumEqualityComparer | `691310` |
| 11、12 | 1242 LongEnumEqualityComparer | `691340` |
| 0、13、ffffffff | 1237 ObjectEqualityComparer | `691350` |

这里只验11个给定输入的分派。没有以“20060 underlying是int32”替代运行时TypeCode产生，也没有声称目标已走到code9。完整cold仍需实际`HasIEquatable`、parent/underlying RuntimeType、virtual TypeCode、MakeGenericType和comparer构造/写回；本表不得用作直接替这些函数返成功的许可。

## 无断点自然采样的最小规格

供主线单次原始启动成功后使用ReadProcessMemory；不调用native、不注入、不设点。每批记录当次PID/启动时间、实际模块基址B、EXE hash、UTC时刻，每次读取的地址/长度/raw bytes SHA及错误。静态基址140000000，以下均以RVA加B；null、不可读或仍为metadata token必须原样记录，不沿链硬读或当作已初始化。

1. `B+5bc8ef0`读8，再读目标8；固定producer值应对应`B+22f9988`和`0101000007000000`。`B+5bc9638`读8，再读default class+10的16字节、+c8的1字节。
2. `B+5a30968`读8取得HashSet MethodInfo，读取其前16；其class+30读8、+c8读1。rgctx+10/+18各读8；slot18所指ctor MethodInfo前16的callee与`B+16fbc0b0`比较。
3. slot10所指EqualityComparer getter MethodInfo前16→class+30读8、+c8读1。该context+10读8为comparer class，+18读8为T descriptor；T读16，独立检查kind和20060。comparer class+c8读1、+38读8→staticArea+0读8为defaultComparer；非null只读对象头8及其class+10的16字节。未知泛型参数仍不命名。
4. `B+5bc9068`读8取得RuntimeType table，非null只读头0x68。仅当T实际kind17/index20060时，用上述公式（byRef位独立计入）和实际mask定位一个bucket。读取blockArray+(bucket>>6)*32共32字节。若occupied目标位为1，index为该位以下occupied的popcount；读取packedEntries+index*16的16字节，再读key type16字节、valueArray+valueIndex*8的8字节和非null RuntimeType对象0x20字节。第一候选不匹配时只记录collision未闭，不扫整表。

EqualityComparer cache和RuntimeType cache是两套状态，不能互相替代。UI未入场时不要求存在Avatar/model/装备/技能；即使采到warm缓存，也只证明自然构造**前**初始化来源，不能称自然Avatar constructor已执行。

## 实际验证、复现与剩余工作

全部原始产物在`local/migration72/d02-active/avatar/`：

| 产物 | SHA-256 / bytes |
| --- | --- |
| `cold-final-first.json` / `cold-final-repeat.json` | `e0ffa6518897a27589c5ef1de2831ec0b245fdc7e97e98f9a267e148b098590b` / 167040 |
| `cold-final-first-summary.json` / `cold-final-repeat-summary.json` | `0fa8b31eb95db8e3915ae3d532b4fe3be7ee4d291d2ae96e56fb7595e48d05f2` / 23025 |
| 工具源 | `3d62c0d09e00d4c6233c03bb8d6bc8346419965929fd4c45c79ce68271e8baaa` |

`cold-determinism.json`逐字节核同，`cold-tests-final.log`10项通过。第一轮`cold-probe1.failure.json/.log`保留：独立runtime未像旧Graph初始化那样映射return sentinel页，原生ret后未映射fetch；新工具显式映射只用于执行前停止观察的sentinel页，不改目标代码或公共runtime。准备脚本误把RuntimeType default cell当metadata usage cell的KeyError保留在`cold-types-probe1.log`；实际该cell不在usage表，本报告没有替它捏造类型来源。

```powershell
$avatarColdClient = & powershell.exe -NoProfile -ExecutionPolicy Bypass -File tools/game_path.ps1
python tools/client72_avatar_cold_contract.py --client $avatarColdClient --output local/migration72/d02-active/avatar/cold-final-first.json --observations local/migration72/d02-active/avatar/cold-final-first-summary.json
python tools/client72_avatar_cold_contract.py --client $avatarColdClient --output local/migration72/d02-active/avatar/cold-final-repeat.json --observations local/migration72/d02-active/avatar/cold-final-repeat-summary.json
python -m unittest tools.test_client72_avatar_cold_contract -v
```

主线第三回放用独立输出，并设置`CLIENT72_AVATAR_COLD_REPORT`选择该raw报告。没有实际报告不skip求绿。warm与cold两套断言分开记录，不把构造准备计入客户端验收。

Java端准确getter和正式g2实现边界仍采用[warm卡规格](CLIENT_72_AVATAR_LIVE_CONTRACT.md#java来源与实现规格)：当前只已有身份/三属性/Team/装备GUID有限投影。冷运行时研究没有新增可发送业务字段；技能、Ornament、真实Store/item资源、SavedVitals最终恢复、池/scene生命周期及完整必经后缀仍要同源证据。下一步先接收自然只读初始化样本；可独立继续的深度项是RuntimeType接口/underlying/TypeCode与精确泛型实例创建，或同源Ornament parser/cache-builder，不重复本卡cache-hit oracle。

## 2026-11-08 主线审核

主线审查真实工具、模型/停止边界及原始证明，以独立新进程第三回放和10项定向检查接收本限定子合同。完整raw `avatar-cold-third.json` SHA-256 `e0ffa6518897a27589c5ef1de2831ec0b245fdc7e97e98f9a267e148b098590b`，与独立双跑逐字节一致；证明/日志位于`local/migration72/d02-active/main/`的`pending-contracts-main-proof.json`和`avatar-cold-main-tests.log`。精确type/RGCTX、RuntimeType cache和TypeCode选择已验；完整cold helper/defaultComparer写回未闭。 本项仅离线研究工具/测试/脱敏文档，无运行代码、Proto、配置、补丁或资源变更，因此无需服务重启；不改变ready/required/runtime或D-02客户端验收状态。
