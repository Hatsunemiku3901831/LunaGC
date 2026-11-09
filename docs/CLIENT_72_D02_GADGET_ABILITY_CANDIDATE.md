# E-01/R3-ITEM-ENTRY、R3-GADGET-CONFIG/ABILITY：完整候选与局部Java加载出口

本轮复用定向分支检索已经固定的两个新raw正文，不重做旧35文件审计，不下载、不修改运行资源。完整候选足够立即验证**当前Java真实configMap加载、ability名称/hash注册和精确引用读取**，无需等待String完整图。这个局部出口不批准候选内容适用于目标客户端，不批准未知字段省略或空ability，也不完成R3/resourceReady/D-02。

UTC起点为2026-11-09 14:27:37，硬限15分钟，截止14:42:37。实际工具、独立正反和收尾时间见忽略目录`local/migration72/d02-parallel-20261109/r3-gadget-ability-candidate/`。本轮只新增本页、离线[工具](../tools/client72_gadget_ability_candidate_audit.py)和[检查](../tools/test_client72_gadget_ability_candidate_audit.py)；没有Java/JVM、Gradle、数据库、端口、客户端、部署或Git操作，仅文档/离线工具/测试无需重启。实际Java执行由主线串行验收，不把本工具称为Gson模拟器。

## 固定输入与来源

上游为`MeChen618/AstaPS-Resource`，commit `6cfecfb55f889f8e3b7156adb12809608db4f74b`，root tree `3cf83488a94b47a018934e5f91c1bf7e93357aae`。检索时`main`与另一个7.2名分支根树相同，分支名不证明目标版本。固定manifest SHA-256 `70aec1cb722b71280393389216bc5bceaa7c9d59aa04e2345411b042a1c4a80d`；本工具重新核完整正文的长度、SHA-256、Git blob以及非截断tree中的路径/大小/blob，并核commit→tree和许可证blob。没有仅凭manifest文字认定文件匹配。

| 完整输入 | 长度 | Git blob | SHA-256 |
| --- | ---: | --- | --- |
| `BinOutput/Gadget/ConfigGadget_Avatar_Equip.json`，检索raw `requests/Q05/body.bin` | 361168 | `ce2b119da8d74e43eeffc93308bfe91919aeee5b` | `700d2c4cb1ea11d5b3036002159326250b3817c127de087b697515321eda75a8` |
| `BinOutput/Ability/Temp/AvatarAbilities/ConfigAbility_Avatar_Weapon.json`，检索raw `requests/Q06/body.bin` | 3745 | `7b1bc8ca167a40b39539d887661619e1ba005dc3` | `b3f7f417c47ff4f2d726728450ca2092e07bc060458d874f7b919c57c2c564d2` |

上述raw路径均相对`local/migration72/d02-parallel-20261109/upstream-reuse/branch-retrieval2-20261109T133247Z-terra/`。复用旧tree SHA-256 `7871959ac380937c6b691adbd6f77e99885ffabb9dc6baa829cc1e84374f5115`和commit响应`04021f23858a90f76419675e945cabfb63d3543afefddaac7977e04937ef7ea1`，没有重新读取全部旧文件。缓存同commit MIT正文1061B、SHA-256 `5653a2bde6fd785bc82cd78fb96dd28c8ebd335e968e5b1a00584c5e0713dda2`，blob `337856c369eb467b1bbf5810ca61cbb8975c6c1f`；仓库许可证不建立游戏数据完整权利链。主线负责接纳后的外部clone，本轮没有clone。

目标用途直接复用[lookup-key已验合同](CLIENT_72_D02_GADGET_LOOKUP_KEY_JAVA_LOAD.md)：Gadget50011101的40472.field199649/+58值`Weapon_Sword_Metal`，原查询值type25270继承37853，对应entity28174.field138419/+2b0配置域。批准报告SHA-256 `f5601d314c51a6417aeb899d26c80cfe2ecdf490c5ff869de2b11ab09fca7398`和原配置key报告`10085ff137a6d918a43adaa2cf27754e4b5c638c9de12c3119705852e65e5abd`都重新核hash。它们批准查找key职责，不证明这两个JSON就是目标原配置bytes；本轮不改String共同输入和旧报告。

## 完整选中对象→精确Java字段

Q05是34个聚合entry；必要对象为完整`/Weapon_Sword_Metal`，共255个叶值。工具逐叶生成JSON pointer、原值、Java声明文件/行/类型及Lombok getter；独立遍历核255个pointer无遗漏或重复。其中仅7叶精确对应Java声明，248叶未映射。下表是7叶的全部集合；“有声明”不代表后续所有getter已被实体消费。

| 相对`/Weapon_Sword_Metal`的pointer | Java声明/读取 | 原值与可核对表示 |
| --- | --- | --- |
| `/abilities/0/abilityName` | `ConfigEntityBase.abilities : Collection<ConfigAbilityData>` → `ConfigAbilityData.abilityName : String` / `getAbilityName()` | `Avatar_Weapon_Metal` |
| `/abilities/0/abilityID` | `ConfigAbilityData.abilityID : String` / `getAbilityID()` | `Metal` |
| `/combat/property/HP` | `ConfigCombat.property` → `ConfigCombatProperty.HP : float` / `getHP()` | 9999999，float32可精确表示 |
| `/combat/property/isInvincible` | `ConfigCombatProperty.isInvincible : boolean` / `isInvincible()` | true |
| `/combat/property/isLockHP` | `ConfigCombatProperty.isLockHP : boolean` / `isLockHP()` | true |
| `/combat/property/useCreatorProperty` | `ConfigCombatProperty.useCreatorProperty : boolean` / `isUseCreatorProperty()` | true；本轮没有发现`fillFightProps`消费此字段 |
| `/combat/die/dieForceDisappearTime` | `ConfigCombat.die` → `ConfigCombatDie.dieForceDisappearTime : double` / `getDieForceDisappearTime()` | 0.009999999776482582，binary64 bits `3f847ae140000000` |

重要的实际损失是`/combat/property/defense=999999`：Java字段拼为`defence`，没有该alias，故预测`getDefence()`仍为0；`EntityBaseGadget.fillFightProps`实际读取`getDefence()`写基础/当前防御。`/common`也不是`ConfigEntityBase.configCommon`，没有alias，故后者预测null。其余248叶含正常明文字段和混淆字段，不能一概称客户端无用字段。本轮不添加alias，也不按它们未进入Java对象就批准省略。

Q06是3个Default对象。完整`/0/Default`只有5叶：`/abilityName`对应`AbilityData.abilityName : String`，另一个完整对象`/LKLPDIMKLKI`含`elementDurability=100`、`minus_one=-1`、`IAKPBCLBNKF=-1`、`isLimitedProperties=true`四叶；当前`AbilityData`没有这些目的字段或此对象alias。它们的目标字段表示、语义及必要消费仍未知。当前Java能注册名称；未知对象被忽略不能称为原能力为空、无动作或可省略。

两个文件的明文引用足以做精确连接：Q05全部34个entry的ability名称集合恰为`Avatar_Weapon_Metal`、`Avatar_Weapon_Wood_Normal`、`Avatar_Weapon_Wood_Hard`，Q06各有唯一同名Default；选中Metal能力列表唯一，ID为`Metal`。这只闭合候选名称引用，不证明目标实际注册或完整能力内容。工具还记录每个名称的当前Java base131有符号int hash，不能与目标System.String配置key hash混同。

本机现有同名资源另作精确对照，没有覆盖：旧Q05对应本机完整文件391235B/SHA-256 `3beef16104a81fabd4c0bd14c8339d6f8eb790cdff65d4fbca55019944ef5993`；旧ability文件1518B/SHA-256 `dc0f61c3a52dc8cbc34b76dab152cfe9c9e5b9b35edfe19b844758e450e7032a`。旧Metal Default仅有`$type/abilityName`，新候选明确多出上述完整未知对象；新config有额外可读combat字段。因此取得完整新正文有可执行审计增量，不能据旧空表示断言完整新能力可空。

## 可立即实施的局部真实加载规格

只新增独立Java合同测试，主线串行使用新JVM和独立目录。R包含Q05/Q06原字节，分别置于`R/BinOutput/Gadget/ConfigGadget_Avatar_Equip.json`、`R/BinOutput/Ability/Temp/AvatarAbilities/ConfigAbility_Avatar_Weapon.json`；S为空、不初始化ScriptLoader；D为空、不复用cache或数据库。输入hash必须在加载前断言，完整raw保留在忽略目录，不把原文提交Git。无需整包resources，也无需`loadAll`、完整Gadget行、Scene或持久化夹具。

1. 按现有`ResourceLoaderIsolationTest`顺序先初始化日志/配置。备份并清空`GameData.getGadgetConfigData()`、`getAbilityDataMap()`和`getAbilityHashes()`；读取并记录`ResourceLoader.loadedAll`，不得手设。使用finally恢复三map，断言loadedAll前后不变。
2. 反射调用当前私有`ResourceLoader.loadConfigMapFile(Map,Path,Class)`，传**实际GameData Gadget config map**、Q05和`ConfigEntityGadget.class`。真实`JsonUtils.loadToClass(JsonElement.class) → JsonUtils.decode(entry value, configClass) → targetMap.put(entry key, config)`，不要host造ConfigEntityGadget。断言map含34个entry、精确key取回非null、唯一abilities的ID/name及表中combat getter值。
3. 反射调用当前私有`ResourceLoader.loadAbilityModifiers(Path)`，传Q06。真实`JsonUtils.loadToList(AbilityConfigData.class)`、Default动态标志合并、`loadAbilityData(Default)`写入实际ability map和hash反查。断言3个名称和hash反查、Metal `GameData.getAbilityData(configAbility.getAbilityName())`与注册map值为同一对象。不能以直接`map.put`替代loader。
4. 同时保留原JSON `LKLPDIMKLKI`四叶及两项拼写损失的显式断言；`getDefence()==0`、`getConfigCommon()==null`标为当前Java损失。缺引用/改key对照只验证Java lookup结果；不得把缺项后null或不报错解释为目标省略许可。未知对象即使不影响名称注册，完整内容仍未验。

真实后续消费链已核源码：`EntityWeapon`构造器用`gadgetData.getJsonName()`查询config map，再`fillFightProps(configGadget)`及`initAbilities()`；后者逐`getAbilityName()`经`GameData.getAbilityData()`进入`AbilityManager.addAbilityToEntity`，创建`Ability`后`data.initialize()`并触发onAdded。工具报告提供13个准确调用位置和原源码hash。本最小出口停在config/ability的真实加载、注册、读取，不构造EntityWeapon、不运行AbilityManager。不能用Java构造不异常替代目标语义或能力执行验收。

## 实际检查与复现

在仓库根目录执行，输出均在独立忽略目录，不依赖GAME_PATH、进程、数据库或现场输入epoch：

```powershell
python tools/client72_gadget_ability_candidate_audit.py
python -m unittest tools.test_client72_gadget_ability_candidate_audit -v
python tools/client72_gadget_ability_candidate_audit.py --output local/migration72/d02-parallel-20261109/r3-gadget-ability-candidate/audit-second.private.json
```

审计已实际执行，27项完整输入pin通过，固定完整输入/树/blob与完整selected对象全部覆盖；12项独立检查包含：完整raw长度/blob、255/5叶无遗漏、7精确类型/非零值、未知对象保留、defense/common拒绝alias、引用改名缺失、空引用拒绝、Metal定义缺失/重复拒绝、额外未知嵌套/列表和超过2^63整数保留、输入字节漂移、Java声明漂移以及全部越界验收标志保持false。它们是离线工具回归，不计Java/DB/客户端测试。

开发首轮为11过1失败：手写dieForceDisappearTime的binary64期望误写，真实完整JSON十进制和解析值未变；修正为little-endian `00000040e17a843f`后复跑。另有一次仅用于打印树信息的临时只读命令误把commit响应内的tree对象当列表，TypeError后修正响应分类，未影响输入或审计；未发任何网络请求。完整最终执行/报告SHA记录在本轮私有目录，主线验收后再记录Java实际XML及产物。

最终实际12项通过、失败/错误/跳过均0；两次独立进程报告均57355B、逐byte相同，SHA-256 `2be87d7f34bd389624fabcd914bb26d6b2c649f83a42140249c56bc3ca72ab32`。`execution.private.json` SHA-256 `4c0d2310719454130cc6d3624fd9414cabb37d1b3a6d0d0b49391ed17f938537`记录三条实际命令、日志hash和首轮失败说明。冻结工具22063B/SHA-256 `ab5afe3d098898380152b30162be844016839240b7afe34e1e8fcb40d3668149`，test8115B/SHA-256 `b73fa811b496a330ff70bf60b69f0b8672b099147f2e205d1c7dfd0ae4623528`。这些12项自身不执行Java；主线后续实际运行见下节。

## 主线第三审核、外部克隆及实际Java加载

独立审阅实际重跑12项及第三fresh，27pin/完整raw/commit→tree→blob与source均通过，57355B与上述报告完全一致。审核记录SHA `2f04d4b75cc22128a478d1f9977607fe01dfe2498b1608fd0b417afe5fd09704`。已采纳为局部测试输入后，主线按AGENTS将固定AstaPS-Resource main克隆到仓库外独立目录，depth1/filter blob:none/no-checkout；HEAD与树精确匹配固定来源，pack1803463B低于64MiB，工作树仅`.git`。未checkout、执行上游代码、LFS/子模块或装入生产资源；外部克隆证明SHA `00765c78ff00dc2d5b2c063dd18ba4ca8887e8b16a700e5a503da95e5494a29b`。此前分支检索保留partial及超时，不能由本次选定克隆宣称全部C1/C2检索、runtime license/ABI核验已完成。

主线新增并实际执行`D02GadgetAbilityCandidateLoadTest`三项：真实配置/ability loader→实际三张map及名称/hash连接；原文未映射字段与Java损失；仅临时副本删除key/name的负向查询。原始输入hash在测试入口固定，34配置/3能力、Metal ID/name、HP float及die时间binary64和非零bool均通过；每项恢复三map并断言loadedAll保持，未手设标志。Java测试源码SHA `b43c2a525de3d2fa1d77d0fa2a9b4cd0aeab35544e0a1dfcfd1ee17c3cba4e15`。

便携JDK17、独立Test worker CWD/version14 config及六个显式R/S/D等目录，初次类初始化前即建立bootstrap资源结构；两个raw绝对路径通过外部Gradle init显式供给forked JVM。实际命令为`gradlew.bat test --tests emu.grasscutter.data.D02GadgetAbilityCandidateLoadTest --offline --no-daemon --max-workers=1 -PskipHandbook=1 --console=plain -I <本轮私有init>`，未执行check/serverSmokeTest或loadAll。Gradle27秒exit0，XML tests3/failure0/error0/skip0；3572个实际class-init记录中无数据库/服务/ScriptLoader初始化，结束相关进程和固定监听均0。13个原保护文件及`.env.local`缺席、原JAR保持，独立work仅新增class-init日志。工作CWD实际记录，六目录为显式配置/环境约束；未另外在Java中反射导出六路径，不能把配置约束写成直接路径观察。

原始manifest/init/XML/完整日志与主线13项审核位于`local/migration72/d02-parallel-20261109/r3-gadget-ability-java/run-20261109T150749Z-0ae2eccd/`，审核SHA `951571d66ebb91ff0c89d6d54bf7cf06ea1f251fad6f6df0a46ed20635f1c252`。原始候选完整保留在忽略目录。只有工具/测试/文档变化，无GS重启；完整能力/EntityWeapon/目标注册/持久化/客户端及R3父项未通过。

## 保留的精确缺口与下一项

局部真实Java加载已按上述范围通过，实际目标配置的首缺仍是：同一key的原asset u64、原bundle/配置bytes、type17591真实producer/状态和末级type25270真实值，以及候选JSON字段到目标配置内容的可追溯对应；String共同后继由A维护，不能由本轮候选名称闭包替代。

Metal的`LKLPDIMKLKI`需要同源类型/字段或真实解析/消费者证据来定义Java目的表示；`defense/common`也需独立来源职责后才能讨论alias或实现。完整Gadget必需字段、实际EntityWeapon/ability执行、自然revision/loadAll、场景/持久保存和客户端入场保持未验；本页不许可空ability、不装入生产resources、不标记R3 ready。下一步由Sol max沿固定目标类型/解析/注册原窗收敛映射；解除完整内容缺口后交最小资源转换或生产实现。
