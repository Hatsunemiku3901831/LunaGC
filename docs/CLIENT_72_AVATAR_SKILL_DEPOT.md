# 7.2 AvatarInfo depot 与当前队伍资源消费有限合同

任务 `F-03d3f4c / D02-R1`。目标是独立解释 `AvatarInfo/48178 tag11 BLBHOJMCOJN/+0xc4`，延续 f3 当前队伍成员消费者此前在 `151a9ed62` 的停止点。这里只提出 **AvatarSkillDepotExcelConfigData 资源 ID** 的有限语义供主线审核；不修改 Proto、sender、opcode、mappings、资源加载 alias、ready 或公共 runtime。`runtimeStatus=not-tested`、`targetProtocolReady=false`，完整 Avatar/Team handler 与入场必要性未验。

## 目标、来源和最小实验

候选解释包括技能 depot、其它资源 key 或普通计数；同值501/701、旧Proto名称和getter存储均不足以区分。本卡重提目标声明、资源具名literal、具体缓存类型和消费机器码，并执行非零字段→编码模型→完整GUID选择→实际资源查询→派生列表写入。缺少资源反序列化和生产时序证据时，不批准默认资源选择或入场必要集合。

正式进程均经当前 `tools/game_path.ps1` 解析路径，重新读取 `extract_client72.HASHES` 固定的三份输入：

- EXE：`8b014f64b5dc12fbc476e0c362b053abfdd7a1378cd282e4ad81057621dbd6b5`
- global：`45dc7a44dc9fc187146c52f6c33de57e2d1c1dcfec57f8736431553460f6ef2a`
- startup：`b314810840ca6f495822c9c44483dde2394b9f09ed3c6b13c2782092f04d5105`

复用 e8、f1、f2a/f2b、f3、f4a、f4b 的已审摘要，完整名称/hash列于工具 `EVIDENCE_SHA` 和摘要 `inputContracts`。g1独立raw adapter保持原样，本卡没有执行Java producer或生产发送。输入资源候选另固定 commit `d54f6f0d423ded9d055bb740a3e5f3aa8f2667f1`、root tree `5b334e711319726c504da95dbfe6ebb26baaa7d3`，校验 commit→root→Excel树→四份文件Git blob/大小/SHA256。

## 字段和资源类型证据链

实际 `15184e18e` 读取 raw `+0xc4`，调用编码int32入口 `14b1046f0`，在 `15184e1ad` 写入模型18163的 `+0x2b8`。真实 `14b1049f0` 解码保留uint32全部bit，包括 `0x80000001`、`0xffffffff`。每个样本先向模型编码旧值777，再执行初始化前缀，独立确认省略字段也把旧值覆盖为0。

当前队伍函数 `151a9ec40` 的正常分支完整执行到返回：

1. 清空 manager39488 `+0xb8` 派生列表；在受控空override/fallback条件下用真实 `151a9bd20` 取得当前Team成员。
2. 按每个完整uint64 GUID经 `14e9b2c60 / 156f6f510` 取得Avatar模型，核对18163类，再于 `151a9ed62` 读取编码 `+0x2b8`；0跳过。
3. 在 `GLOBAL+0x212b8` 的 `Dictionary<uint32,22284 ALFCDOBDDFP>` 查询该值。缺缓存、缺行或null行均跳过。
4. 在 `151a9ede5/151a9edfe` 读取 depot 行 `HEGEDDGIFLL/+0x6c`，真实58209解码入口 `14aef9db0` 取得另一资源ID；0跳过。
5. 在 `GLOBAL+0x16c70` 的 `Dictionary<uint32,88602 HIKGFKFMHGP>` 查询该ID，实际 `151a9ee62` 读取行 `BONJLBPAEIP/+0x54`。
6. 该字段和 Team `+0xb8` 列表成员均声明为目标enum74813 `ICOOCCCNDCH`。实际 `151a9ecf5` 用4字节写入非零值；不同GUID的相同元素不去重。

两个缓存的资源身份由独立literal、path getter/loader和parser绑定，而非根据字段形状推断：

| 目标资源literal | getter / loader | parser、全局缓存写入 | 实际行类型 |
| --- | --- | --- | --- |
| `Data/_ExcelBinOutput/AvatarSkillDepotExcelConfigData` | `14d6d5490 / 14d6d53a0` | `14d6d54a0 / 14d6d5535` | 22284 |
| `Data/_ExcelBinOutput/AvatarSkillExcelConfigData` | `14eb02d90 / 14eb02c40` | `14eb02da0 / 14eb02e35` | 88602 |

实际path getter返回literal指针，loader把同一指针交给 `14d6e1570` 前停止。parser类型cell和缓存写入机器码仅作静态来源，没有声称执行文件I/O或完整parser。各缓存query入口 `14d6d70f0 / 14eb02d30` 的参数独立重提为uint32，并实际执行非零/缺项/null行查询。

## 独立具名消费者与默认资源的区别

目标12832 `Miscs.GetAvatarElementType / 153217960` 有具名 `avatarConfigId:uint32` 参数。完整正常函数读取 AvatarExcel行58272 `+0x168` 的默认depot，随后走同一 depot `+0x6c`、skill `+0x54` 和enum74813。它与当前Team的运行时字段来源不同：重复wire tag11按501→701应用时，受控Avatar资源仍默认501，具名方法返回Fire1，Team则为Water2。

目标enum重提得到 None0、Fire1、Water2、Wind7等原始符号；未知777仍按raw值写入，不因存在enum而批准合法业务范围。具名方法不提前过滤零skill引用，受控skill0存在时可返回其元素；Team函数在引用0处跳过。两种实际路径分别断言，未归并成同一模型。

depot `HEGEDDGIFLL/+0x6c` 在本合同中仅称为“此消费者使用的skill引用”。尚无足够独立证据将其恢复为英文 `energySkill`。`LHFOOPMJHBN/+0x10` 实际为元素40302的数组，与该scalar不同；完整所测Team/具名路径均未读它。其业务意义unknown，**未批准 `specialProudSkillOpens` alias**，不改变R3已证的旧加载红测。

## 实际501/701候选行的有限投影

固定候选 Avatar10000005/10000007 的 `skillDepotId` 为501/701。这两份完整depot行已保留在脱敏摘要并核canonical hash；除id外没有任何顶层number/bool字段，存在skills、subSkills、talents、数组/字符串以及 `LHFOOPMJHBN=[{},{}]`。

本卡只把这两行的已固定ID和**未出现的scalar逻辑0假设**投影到受控缓存，仍通过真实58209编码构造值。两个有效非零wire字段和完整GUID均被实际读取，Team查询501/701后在零skill引用处结束，派生列表为空；具名方法继续查询缺失skill0后返回None0。

这不是目标资源反序列化或构造默认值的验收。`targetResourceDeserializationVerified=false`、`targetConstructorDefaultsVerified=false`、`englishEnergySkillAliasVerified=false`。数组/字符串、客户端资源版本一致性、完整skills初始化均不在投影内。504/704另有纯合成非零样本，只验证ID传递，不把它们当实际起始depot或用候选非零 `energySkill` 值反推目标字段。501/701是否适合实际生产角色仍待后续业务/生命周期合同审核。

## 原生、模型及错误边界

原生执行包括reader、字段初始化前缀、int和资源ID编解码、当前Team选择、完整GUID查找、资源query主体、具名方法及完整正常Team派生函数。managed资源字典 `FindEntry=1578b66f0` 的索引/存储是明确模型；返回row之后的指针检查、实际读取、零值分支及派生列表写入均由机器码执行。没有将关键函数stub为成功。

沿用f1模型分配/batch边界及f3已审Team存储。派生列表具备已有64-slot backing，所测最多两个有效成员；不由此宣称客户端上限。完整Avatar handler只执行f1身份前缀，root处理停止于既有Rename边界；f3已审currentTeam赋值块独立运行。事件只到已审派发边界，没有执行订阅者或场景效果。

22组wire/环境覆盖两Team切换、同低32位而不同高位GUID、重复/缺失/零GUID、重复tag最后值、相同字段重放、省略覆盖777、错误wire跳过、资源高位key、缺缓存/缺行/null行、零引用/零enum及未知enum。109次实际Team消费、42次字段编码/读回。

6个错误/拒绝：截断varint和超过uint64的varint为明确输入模型拒绝；负Avatar嵌套长度进入native reader错误helper；人为null entries在实际Team中到 `1404c2be0`；人为返回超出64-slot fixture位置到 `1404c2650`，不是资源ID上限；IFix开关实际到 `151a9ef29`，在未知patch对象访问前停止。

首次独立测试把合成返回哨兵常量误写，只有该断言失败；已改为实际固定 `0x620000100`，原始 `logs/avatar-skill-depot-tests-probe.log` 保留。没有为通过测试改公共runtime、跳过native错误或修改生产行为。

## 验收与复现

新增12项独立断言，连同 f4b/f4a/f3/f2a/f1/e8 共70项通过。两次全新进程各自重新核三hash、重提类型/参数/enum/literal与机器码；完整trace和脱敏摘要逐字节一致。

| 产物 | SHA-256 | 字节数 |
| --- | --- | --- |
| `local/migration72/evidence/avatar-skill-depot-native{,2}.json` | `0d1868c9af49d42a5833a849fa09a6027273cef3fde6904f94077cf94e2b9f4d` | 5,311,200 |
| `local/migration72/evidence/avatar-skill-depot-summary{1,2}.json` | `a8a7b4ce71869c4af88e3bf8164413ed109c7d6e051478d1e682b19e5d0e1a95` | 718,102 |

比对记录 `local/migration72/evidence/avatar-skill-depot-determinism.json`，正式日志 `logs/avatar-skill-depot-native{1,2}.log`、关联测试 `logs/avatar-skill-depot-tests.log`。共享 `protocol/cn-7.2/avatar-skill-depot-observations.json` 使用同一摘要字节。所有原始metadata/反汇编/完整trace留在忽略目录 `local/migration72/{analysis,evidence,logs}/avatar-skill-depot-*`，具名函数完整反汇编亦在 `analysis/avatar-team-native-0x153217960-*.txt`。

主线已审核四文件、资源来源/投影和所有限定模型/语义边界，独立70项通过；第三个全新进程完整/摘要与上表双跑逐字节相同，七source pin保持。证明 `local/migration72/evidence/avatar-skill-depot-main-review.json`，独立输出 `evidence/avatar-skill-depot-main-{native,summary}.json`，日志 `logs/avatar-skill-depot-main-{tests,native}.log`。仅接受tag11有限资源ID关系，不批准投影为目标实际加载、英文别名、入场必要性或生产发送。

```powershell
$depotGamePath = & powershell.exe -NoProfile -ExecutionPolicy Bypass -File tools/game_path.ps1
python tools/client72_avatar_skill_depot.py --client $depotGamePath --output local/migration72/evidence/avatar-skill-depot-native.json --observations protocol/cn-7.2/avatar-skill-depot-observations.json
python tools/client72_avatar_skill_depot.py --client $depotGamePath --output local/migration72/evidence/avatar-skill-depot-native2.json --observations local/migration72/evidence/avatar-skill-depot-summary2.json
python -m unittest tools.test_client72_avatar_skill_depot tools.test_client72_avatar_fight_props tools.test_client72_avatar_state tools.test_client72_avatar_team tools.test_client72_avatar_control tools.test_client72_avatar_contract tools.test_client72_avatar_complete_raw
```

## 最小producer的剩余条件

本卡支持待审的tag11资源ID关系，并独立区分默认AvatarExcel depot、wire当前depot与当前Team成员选择；不能证明省略该字段对实际入场无害。控制角色是否使用501/701、完整技能初始化会读取哪些数组/技能资源，以及生产值来自保存还是新建流程仍需单独合并审核。

下一步应以真实完整 `15184e100` 初始化handler为边界，先补实际模型构造与未覆盖的必经调用，再对受控初始角色/默认装备验证省略路径。`151849670` 是其中生命状态子调用，不是完整初始化handler。FightProp之后 `152f7ec20` 在scene缺失、entity缺失或相应组件/回调条件不满足时返回；实体存在分支可能到组件ID `0x100d000` 和未知delegate，当前不批准其效果或真实入场时序。具体最小实验清单另交主线拆分，避免无条件恢复整个战斗图。
