# D02-R4：SceneInfo 自然运行上下文来源

仅固定国服7.2离线研究，前一Scene入口子卡/双raw保持冻结；不执行客户端、服务、网络、数据库、Git、构建或生产资源写入。

## 重大问题计划

| 项目 | 本轮约定 |
| --- | --- |
| 目标 | 将21761必经能力名称注册缺口拆为初始化、hash生产者、资源来源和自然状态，继而推进team/44512创建与生命周期首个真实后继，交可接入规格。 |
| 证据 | 五profile固定输入；已审locator与Scene整表；前卡token/80154/raw/retain/复制/hash合同；主线新自然启动与99span快照只读复用。 |
| unknown | C698真实默认、C6D0初始化/逐名称来源、field→能力名/hash对应、SceneScript stage、创建模板与组件/owner、完整handler和通知顺序。 |
| 候选 | 字符串注册可能由静态初始化与资源配置解析共同生产；先找producer和真实literal/blob，不能将“未采槽”统称为只能实机。 |
| 最小实验 | 重提41992 `.cctor/AddAbilityString` 及调用/资源引用，执行实际默认与非零注册→查询；独立hash/重复/错误/边界；现有snapshot只能按其实际reads回答。 |
| 范围 | 仅新runtime_context工具、测试、本专题，原始证据在忽略 `local/migration72/d02-active/scene-runtime-context-astra/`；不重做旧setter/Scene row或并行Avatar工作。 |
| 失败出口 | 真正缺现场输入时记录模块RVA稳定根、字段类型/宽度/长度、producer/consumer窗SHA、null语义、一次最小快照能回答的问题；首未知处停止。 |
| 验收 | 有来源的字段/tag/Java来源/默认省略和顺序；独立正反样本、双新进程、源码/输入/raw SHA及第三命令；不以空对象或业务成功桩批准完整handler/ready。 |

## 本卡新增结论和许可范围

本卡将此前“能力默认名/世界模板只能等自然上下文”的宽缺口拆开。实际执行了能力 hash、默认槽初始化、受控注册及反查；实际定位两份 Gadget 资源、完整原生解析31,682项主索引，并把真实世界模板行接回 native cache/getter/world factory。完整21761 handler、自然注册集合、对象池分配、生命周期、SceneTeam/EntityAppear三层和实机入场仍未闭。`completeSceneInfoHandler/naturalAbilityRegistryObserved/targetProtocolReady=false`，`runtimeStatus=not-tested`。

工具每次通过 `tools/game_path.ps1` 所给 client 路径核验profile五文件及前卡固定输入。前卡源码SHA `5d255f494bf5755b204697789c3a8ef1ae4db88ea3031d772d0a7cf987f7bfb3` 保持。实际资源locator已由主线审核；本卡只读取其冻结index/serialized字节，没有改资源安装目录或运行revision。

## 能力注册：默认、hash和实际错误路径

`41992..cctor/1500D6270` 的真实前缀执行到 `1500D62BC`：literal6的空字符串经`String.Intern/140546AD0`写入 **GLOBAL+C698**；literal10015的`Default`另写 **GLOBAL+C628**。Intern在本卡是明确的托管字符串identity模型。没有执行整个cctor，也没有由C628含`Default`推导它已注册到反查表。

`AddAbilityString/1500D5480` 的hash按 **UTF-16 code unit** 计算，初值0，`h=(131*h+unit) mod 2^32`。本卡12项实际指令与独立算法一致，覆盖0/1/2/3/4/5字符、非零高位、257字符、中文、代理项对及单独高代理项。当前Java `Utils.abilityHash` 的`charAt`及int溢出与该数值规则相同；这只批准数值转换，不批准任何候选能力名称已经由目标资源注册。

| 输入/场景 | 真实执行结果 |
| --- | --- |
| `Default` | hash1178079449 |
| `Ability_Avatar_Dive_Team` | hash4039962699；保存原32位bit pattern |
| `TeamAbility_MoonPhase`重复两次 | hash2301684124；第二次前向ContainsKey已存在，反向仅一项 |
| `能力_😀` | hash1180612212；按两个UTF-16代理单元，不按Unicode标量值 |
| `A\u0083`与`B\u0000` | 真实碰撞8646；第二名前向表已插入，反向仍留第一名，止`1500D57AA`错误准备，不伪装原子回滚 |
| 注册空串/查询hash0 | hash0查询直接返回C698空串，不走反向字典 |
| 非零未注册hash192727030 | 真实`1500D85A0`到`1500D872C`错误准备；不返回`Default`，也未证明一定断线 |

原型：`AddAbilityString(string*)`接RCX；`Lookup(uint32 hash)`接ECX、返回string*。完整Add入口与Lookup执行原生。前向`C608/IDictionary<string,uint32>`是显式typed ContainsKey/Add storage模型；反向`C6D0/IDictionary<uint32,string>`通过明确接口表调用真实`ContainsKey/Add/get_Item/FindEntry/TryInsert`，具体实现选用受控`Dictionary/instance4742`、64项entry/67 buckets。不是自然DenseDictionary选择，也不是把业务成功写成模型。OS thread-id源显式为23，实际main-thread比较`1412D3860`执行；不冒称自然线程。

`DenseHashTableUtils.useDenseHashTable`的primitive静态地址由metadata root重提为模块RVA`0x5885590`（bool/1B）；cctor仅ret，不能据零填充推断自然值。实际工厂`1548F4E00/1548F4930`按此选择不同generic分支。因此自然字典不能直接套本卡受控layout。

离线已定位的producer还包括 `1500D9980→1504F4BB0→delegate1500D5380→AddAbilityString`，其literal是元素/反应目录。它不是默认Team名称已就绪的证明。Avatar实际表中的能力组/技能名称由Avatar线持有；没有准确native literal、generic carrier和suffix前，不把字符串拼成资源路径。

## 世界模板：从实际载体到首个未闭分配器

静态字段`23977.BJFONLNHAGK`来自 `.cctor14D2CA110`，真实store `14D2CA40D`把 **90000004** 写到模块RVA`0x586F4C0/u32`。本卡实际执行store后执行`15154BF20`，确认该值送到`152E10FC0→152E110C0`。它不再是需要填旧常量的缺口。

owner54132实际literal：`Data/_ExcelBinOutput/GadgetExcelConfigData`；primary是同名加`PrimaryIndex`。普通loader`152E108B0`及primary wrapper后继`157FFC650`均真实调用`14D6E1570`，从而独立闭合两者的`.MiHoYoBinData` carrier。准确native hash→两个真实index查询→native pack/8hex→bundle178→已审serialized1208对象，重新得到唯一条目：

| 实际对象 | native entry/pathID | bytes / SHA-256 |
| --- | --- | --- |
| Gadget | `d65ab32f` /1052 | 2,944,342 / `6355185856a095aea5d523a8465029b2c40c61d5de055e53fc0412a6472dcf2c` |
| GadgetPrimaryIndex | `237dd3a0` /1053 | 163,245 / `1e95ae771d6597160b2438d5a1ead40f04f58a84a26be466c0226fab6923138f` |

固定locator报告SHA `fc2c3aca3f472354f0533c9ac319387102fd6f8a1a7195b0400d06988a420fda`；index报告SHA `e8a02de4a4b24c58be647e8f1cb2638b5e5a06f1b30427a8ae5a627291d54ab9`；serialized SHA `0311cb19ace1cac5475f197043af14b33657f22c9915e93eb9bffa54cae5e8c7`。每次正式工具都重新验证/定位，不只相信本卡中间提取文件。

**完整主索引**执行`157FFC780→152C255D0→1553C0930`，31,682条全部与独立decoder相同；cursor163245=EOF，最后offset2944342=Gadget完整长度。count为首uvarint的zigzag解码；每条key为uvarint，仅首条另读初始offset4，此后offset按每条uvarint length累加。Gadget真实四字节头另由`(LE32+0x5C0A5C94) mod 2^32`得到相同count。没有执行全部31,682条Gadget row，不能称Gadget整表业务parser完成。

主索引dictionary布局是明确预分配存储：owner+18指向dict，dict+10 entries/+30 buckets/+3C count/+40 freeList；entry stride20，key+8、offset+12、length+16。数组+18 count/+20 data。原生解析、插入及`1553C0640`查询没有模型。只保留真实三字节count前缀的负样本在cursor3、插入0项时到`1404C2650`数组边界，不当成空表成功。

| key | native index切片 | 实际row值 |
| --- | --- | --- |
| 90000004 | 1451356+54 | 父+18 encoded58209=90000004；父+20另encoded字段=9999；+50 `Default_Item`；+58 `MPLevel` |
| 90000003 | 1451287+69 | +18=90000003；+20=0；+58 `NPC_Guide_Paimon`；+40 u64=10510483980407350969 |
| 50011101 | 432396+92 | +18=50011101；+20=0；+50 `Lv1_Weapon`；+58 `Weapon_Sword_Metal`；+40 u64=8431959023108435415 |
| uint32 max | 原生FindEntry返回-1 | 不构造默认模板 |

三行均经真实`14E62B290/type40472`及父type27264消费到精确slice EOF。世界行SHA `3288b3a44679bcb4ff775fc52fcb5274e20818c9b58c3ce9bc5d33892198283a`；武器行SHA `eaf903171c14cfd27367b0bc7012ef7036671d4b7bb0d6c3723d237e6a17b9ad`。世界行去掉末字节在cursor53到真实数组边界。特别注意factory读`row+20`，其逻辑值9999，**不是行ID90000004**。

row runner复用前卡冻结NaturalRuntime源码SHA `a1955fcf4b47dd70cf0dc51495b30307beb8d54a7b5b960dca97503ac1efe48f`；typed class/数组/string转换/metadata初始化与配对scalar storage permutation是明确模型。encode/decode没有自然encoder前半表来源，本卡也不声称该模型是文件cipher。父字段完整64位值保留，未转float或截低32位。

随后把真实90000004对象通过原生`1578B6A20`放入GLOBAL+237D8具体字典，执行`152E110C0`查询命中及max miss，再连续执行 **15154BF20→152E10FC0→152E110C0→FindEntry**。此处显式选择模块RVA`0x587CB61/bool=0`的非streaming分支；不是自然配置观察。

工厂取得真实row后读取 **GLOBAL+5D60/ObjectPoolUtility._cluster**，本卡不提供分配器，真实路径在`15154BFB0`判空后到`1404C2BE0`，尚未调用`15154BFC6`虚槽分配。没有构造假entity继续声称组件或生命周期成功。

该槽的producer已拆出：`ReplaceCluster/146ECD7B0`写入；`Init/146ECD800`非null直接返回，null才转`Recreate/146ECD850`。本卡实际执行了Replace、既有pointer保持及null转Recreate入口，pointer是opaque存储夹具，不是可用allocator。Recreate静态有`ObjectPoolIl2CppCluster/type11008`和`ObjectPoolNonGenericCluster/type11009`两分支；实际选择与实例、构造后代未验。后续factory请求type46314，class cell模块RVA`0x5897B48`，调用cluster class+F0对应`IObjectPoolCluster.Allocate/slot4`。这个具体接口是下一个对象分配缺口，不是“所有资源尚缺”。

`MPLevel`字符串之后由`14AA97730/14AA9C630`查询owner17586：GLOBAL+213E8为`Dictionary<string,uint64>/3744`，GLOBAL+213F0为`Dictionary<uint64,type17591>/11691`；`14AA9AF10`执行两级查询。它们是配置缓存，不能当Ability hash表，也不能把`MPLevel`直接拼成文件路径。注册入口`14AA99DC0`的cell145983438已独立解为`GetAssetPathHash<MiHoYoBinData>`（methodInstance58257/type8026），cell1459833F8为`Get<ExternalResources>`（58337/type11088）。参数直接来自metadata注册argument records，不能假设所有method实例都出现在startup的class实例表。下一离线动作是继续恢复该入口的准确路径/源字典及注册链；本卡没有伪造配置对象跳过它们。

## 给生产者的字段、值和处理顺序

本卡没有增加CmdID或reader tag；L1/L2/L3沿[SceneInfo raw](CLIENT_72_SCENE_INFO_RAW.md)、[身份合同](CLIENT_72_SCENE_INFO_CONTRACT.md)、[深消费](CLIENT_72_SCENE_DEEP_RESEARCH.md)及[前卡](CLIENT_72_SCENE_ENTRY_CLOSURE.md)。仅以下数值/前置说明新增：

| 输入 | Java来源/转换 | 默认、省略和接入边界 |
| --- | --- | --- |
| 49723/tag1→89710/tag1[]→83320：tag2/+20、tag3/+18 | 两值均fixed32；UTF-16 base131可使用当前`Utils.abilityHash`保留int原bit。候选来源`TeamManager.getBaseAbilityControlBlock`的default/team资源名、widget扩展 | hash0仅查询C698空串；不能把两个非零hash换成0绕过注册。两个field与旧name/override的正式对应仍依独立语义；本卡不直接采用旧AbilityEmbryo标签 |
| 83320/tag1 | uint32；旧Java递增embryoId只是候选 | 原tuple第三项保留完整u32；不能为空条目默认批准 |
| 34208/tag7、tag12；18295四GUID/entity字段 | 保持前卡token、本地实体及Avatar/Weapon同owner合同 | 要求先存在可查Avatar；GUID保持64位，武器不借用其它owner或填0 |
| root/tag14→44512：tag5/+20、tag1/+24、tag2/+18→34045 | 两u32和能力对象实际送`14828BA00`。旧Java`world.getLevelEntityId/getHostPeerId/getAbilityRoots`为候选；factory第二u32本卡只记`secondU32Argument` | 实际模板90000004不是发送的实体ID；row+20=9999也不能写进wire；完整字段业务名、owner/组件/teardown仍未闭 |
| 49723/tag3[]→80154 | 保持前卡u32/tag1与float32/tag2、按原bits/重复覆盖 | 业务来源与非空必要性仍unknown；本卡不新增默认记录，也不批准空省略 |

能力非零反查发生在21761的80154、Avatar列表和world factory之前；有完整名字注册不等于其它后继成功。当前Java Init先调用SceneInfo，后调用ScenePlayerInfo/SceneTeam/SyncTeam/SyncScenePlayTeam/InitRsp，具体条件与中间通知见[D02依赖](CLIENT_72_D02_ENTRY_DEPENDENCIES.md)。Done先置LOADED再spawn，不能把服务器状态当客户端落地。当前factory还要pool allocate→IAutoAllocRecycle→MPLevel配置查询→设置模板与两个入参→虚调用→组件创建→scene更新；本卡只到pool前。已有entity分支的teardown也未执行。

允许后续Sol在独立适配器/测试里复用已证数值公式、真实数据行与停止断言。**不批准**正式SceneInfo sender、SceneTeam/EntityAppear迁移、空能力默认、资源安装/JSON生成、修改required/verified/ready、跳过校验或完整handler成功。current Java三文件按固定SHA核验；只读审计，没有Java/Gradle执行。

## 剩余输入：先离线来源，再一次有界自然观察

对完整21761最先仍未闭的是**实际发送的两个非零83320 hash对应的自然注册集合与资源来源**，而不是C698默认值或hash算法。当前捕获 `plain/run-elevated3.json` SHA `303db4bc6daa85d3fda6d73f52cbe339021ba84cfaa45152bb4bf92a1746c58a` 与 `main/cold-arrays-tool/live-elevated1.json` SHA `63d3aaf80a37ca1f4f724a73ba2ee84354da94846acde16f27deccd116eef8c0`已固定读取；后者99span无错误，只涉及cold类/SDK，并未请求下面这些Scene根。因此结论是 **not-sampled**，不是null，不要为同一个冷槽重复启动客户端。

以下仅供主线将来一次受控采样，**不是本卡启动/附加授权**。全部从当次五SHA匹配模块base重新计算；禁止复用历史PID/VA。

| 稳定根/consumer | 一次最少字段与可回答的问题 | null/停止含义 |
| --- | --- | --- |
| module+`0x586B600`指向GLOBAL；+C608/+C6D0/+C698/+C628各8B | 先取四槽和实际klass/header；string为+10/i32长度、+14/UTF-16，最多按已验证非负长度读取；实际dictionary按klass判定，取count/version、bucket/entries及仅目标两hash和Default的key/string。另取module+`0x5885590` bool | C698 null表示该时点未建立已证默认；C6D0 null表示未建立反查实例。不能填空字典继续，也不能把未查到entry当未调用handler。初步只问实际使用哪些实现/名字，不要求全注册表无界dump |
| module+`0x586F4C0` u32；+`0x587CB61` bool；GLOBAL+237D0/+237D8/+237E0 | 区分实际streaming分支；先前90000004来源已离线闭，无须再靠猜常量。主索引与row可由本卡重建 | 真空cache可能是未加载/streaming选择，不是资源不存在；不得替换未知分支为本卡controlled false |
| GLOBAL+5D60 8B，pool[0]klass 8B，klass+F0 8B，type46314 cell module+`0x5897B48` 8B | 回答实际allocator实现及分配目标，先保留class前0xD0及所用slot；producer `146ECD7B0/146ECD800/146ECD850`。确认选定实现后才决定最小pool字段跨度 | null是原生factory的真实错误前置；非null只证明有pool，不证明Allocate/ctor/AutoAllocRecycle成功。遇未采对象引用首失败停止 |
| GLOBAL+213E8/+213F0两8B槽 | 先按明确generic 3744/11691的实际header取MPLevel键→u64→type17591，最少保留value+28状态/+2C引用count/+40源key及class。producer缓存构造`14AA97070`、注册`14AA99DC0`，reader`14AA9AF10/14AA9C630` | 缺项仍先沿已定位注册/资源上下文离线解；不能用空MPLevel配置。现有Gadget行里的`MPLevel`不证明此缓存已加载 |

完整handler还需要实际SceneScriptData `45183+70/stage enum66233`及loading task；Scene3表enum80117 `SCENE_WORLD=1`不能写成stage World0。再后的实体、owner、能力Clone/组件及SceneTeam/EntityAppear仍按真实首失败输入追踪；本卡不把这一输入队列压成“一个采样能证明全部入场”。

主线随后准备的**仅登录页根存在性**问题比上表未来完整输入更窄：本卡另交`scene-runtime-context-astra/minimal-root-contract.json`，SHA `149c5ebcacf726009dbd028becf2d75f6caa1b2178ccf38e5566c3de26b4a45d`，包含4字段metadata和8个最小producer/consumer字节窗。仅读module+586B600的8B pointer、S+5D60/C608/C6D0/C698四个8B槽；非null对象最多读首8B klass及klass前0xD0B，不追接口列表、buckets/entries、string长度或字符。0xD0不包含class+F0 Allocate槽，不能由类头可读推断分配器调用有效；只回答对应时点根和类头是否建立。实际采样器/进程选择由主线持有，本卡没有现场动作。

关键producer/consumer完整PE unwind代码窗SHA（正式报告同时保留起止地址/指令/类型usage）：

| 方法 | SHA-256 |
| --- | --- |
| Add `1500D5480` | `826eb0ad8698fe9e7546a5465ac4a4161be33648d0e59214d9df8896483ea342` |
| defaults cctor `1500D6270` | `6e595fe1cc478f9c88708346ee6775647469741890d383d037e941efb66388e4` |
| lookup `1500D85A0` | `acb72640f0880164658250567a920af117df59349a2da94792d0166507837824` |
| primary parser `157FFC780` | `8bfd0f4e32334c985f1fb63cbc9535d1d2a675a9b997fafb890030ebbf696b56` |
| row parser `14E62B290` | `e850f32c86f9d6795aece3ab644955c2a802eb5158d6e66965b5dc17f333c8bd` |
| world factory `15154BF20` | `1d46e69c9304becd4b7efef4671bf31541fb433e6e55e4a58655f5653df08504` |
| pool replace `146ECD7B0` | `d2c8d276639b14fc0fcc4c9fa6076e0712c6dfb2c1d2055aa5689a300f9a74c1` |
| pool recreate `146ECD850` | `76774a662e43845da5b04a05126434e58e3a45b5380deb763c29210dcec3dba0` |
| Gadget配置缓存cctor `14AA97070` | `e1763f1b831027e3d09eb2264e7b12daffc98544da4e89ce95d43802830c0bb8` |
| Gadget配置注册 `14AA99DC0` | `a86b9049a9a2b1443a23efbfc84c7d22cd20252cf0966499e4816f8e054f625d` |

## 复现、失败与冻结

早期raw `gadget-row1.log`是测试脚本把NaturalRuntime的`objects`误写到emulator，尚未进入row parser；修正owner后真实三行通过。补齐配置producer审计时，58337不在startup class实例缓存引发KeyError；`method-instance-lookup-failure.log`保留失败，随后从真实注册instance record解析method参数，未给缺项空列表。全回归第一次缺前卡要求的`CLIENT72_SCENE_ENTRY_REPORT`，保留`tests-missing-preceding-report.log`；明确指定既有冻结报告后通过。更早能力collision/default及静态producer探针保存在本卡raw目录。不是把异常忽略或把原生失败改成成功。正式工具不会读取这些探索结果作为证明输入。

```powershell
$game = & tools/game_path.ps1
python tools/client72_scene_runtime_context.py --client $game --output local/migration72/d02-active/main/scene-runtime-context-third.json
$env:CLIENT72_SCENE_RUNTIME_REPORT='local/migration72/d02-active/main/scene-runtime-context-third.json'
$env:CLIENT72_SCENE_ENTRY_REPORT='local/migration72/d02-active/scene-entry-astra/final-a.json'
python -m unittest tools.test_client72_scene_runtime_context tools.test_client72_scene_entry_closure tools.test_client72_scene_deep_followup tools.test_client72_scene_deep tools.test_client72_scene_info_contract tools.test_client72_scene_info_raw tools.test_client72_scene_entry_identity -v
```

第三命令重新读取五profile、已审index/serialized及冻结NaturalRuntime，独立native回放后再检查断言。仅离线工具/测试/本专题，无运行代码变更，所以本卡无需构建或部署。主线负责第三审核、Git提交及共享索引；本卡不提交或编辑共享台账。

正式双新进程 `scene-runtime-context-astra/final-{a,b}.json` 均 **3,870,401B**，逐字节一致，SHA **`bf9efd6225cd2836286b3a301bb2ea2f33dab6c6ddfb103d53aeed8f10db81d4`**。证明`determinism.json`；`tests-final.log`为13新+61旧=**74通过，0失败/错误/跳过**。工具SHA `b5af21df00710e711b551f88507445e987eae998ba12e049e67a54204e19802e`；测试SHA `8deeccf43b19bf8550028a2891b174ae14d0ed2d36afede239bb5332c43daaf8`。前卡三文件及双raw没有被本卡修改。

主线已审真实代码、原始证明和typed存储/同步模型，第三`main/scene-runtime-context-third.json`为同一3,870,401字节/SHA，三报告逐字节相同，13新+61旧=74检查通过；证明`main/final-context-response-review.json`、日志`main/scene-runtime-context-main-tests.log`。完整handler/自然注册/stage/实体生命周期仍未闭，不批生产sender或入场。用户随后要求当前批次收口后暂停，覆盖此前继续推进指令；本卡验收后不执行新观察或MPLevel后继。

恢复后的首项是按已证producer恢复自然能力名称来源及MPLevel配置注册；准备好的有限登录页根观察只补实际实现/根存在性，再由准确class/分支推进Allocate与原生构造，不能把根非空当世界初始化。
