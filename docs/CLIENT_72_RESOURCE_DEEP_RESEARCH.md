# E-01/R3：固定目标资源字段与默认值深度研究

2026-11-07新增[来源审计](CLIENT_72_D02_RESOURCE_SOURCES.md)已主线审核：13固定输入（含两张Ornament）逐Git blob核验、3161安装清单行有界审计，8测试/第三新进程与双跑原字节同。真实ctable persistent/streaming同hash且MD5匹配声明，已定位准确loader/index parser离线后继；不能再笼统称本机没有资源输入。JSON→binary桥梁、ctable解码、实际revision、接受规则和自然loadAll仍未知，原规则/ready不变。

2026-11-07，D-02 纯离线 Astra 范围。本卡不修改运行 Java、Proto、配置、补丁、resources 或原数据；不运行 loadAll、数据库、客户端、调试器或部署。复用已经验收的 [152 类/Chapter 加载](CLIENT_72_D02_CHAPTER_LOAD.md)、[303 恢复](CLIENT_72_D02_QUEST_303_RECOVERY.md)、[303 启动/保存](CLIENT_72_D02_QUEST_303_START.md) 和 [depot 有限消费](CLIENT_72_AVATAR_SKILL_DEPOT.md)，没有把这些旧执行数计入本轮。

首个独立交付是 **目标二进制 parser 的默认、非零、嵌套及错误边界**。15 个完整解析样本、1 个消费边界、5 个拒绝样本与 10 项独立断言通过。候选 JSON 到该二进制格式的转换来源、自然 loadAll、正向 QuestManager 重绑定、候选 revision 对齐及 D-02 实机仍未完成；`resourceReady=false`，没有批准任何字段 alias。

## 问题、证据与停止点

| 问题 | 本轮证据 / 结论 | 禁止越过的边界 |
| --- | --- | --- |
| depot `+68/+6c` 缺省是否为逻辑 0 | 固定目标完整 parser 实际覆盖先前编码 777，实际 codec 读回 0；非零与高位值分别保留 | 省略二进制 flag 不等于候选 JSON 键缺失；不能代替实际客户端资源文件读取 |
| `LHFOOPMJHBN` 是否仅能忽略 | 独立元素类型、具名条件 enum、完整元素 parser 和首参数查询证实它承载条件化数据 | 尚未执行 depot 外层数组 wrapper、恢复全部业务名称，不能把当前 Java 单字段结构认作完整转换 |
| `BGHPAKBEPND` 是否可直接命名 `param_str` | 原始名称精确命中目标 69472 字符串字段；完整 parser 和真实非空字符串消费到 Split 前完成 | 没有执行 LUA_NOTIFY 键匹配；不能由字段类型、同值或另一条件消费者批准该 alias |
| Quest LogicType object/acceptCond | 目标条件/引导/显示类型与当前 Java LogicType 是不同结构；30302 两候选输入均未提供 acceptCond | 不能把对象换成 LOGIC_NONE，不能将缺条件转为空自动接受，不能从旧表补入 |
| 候选 revision | 本轮重新读取内置 revision，数值与固定候选标签不同 | 内置文件与提交标签都不是运行时热更新实际选择记录 |

已证首个 2B 冷读门禁仍是 `ResourceLoader.isLoadedAll()==false`。本卡没有运行 loadAll，故不声称发现该流程第一个运行失败点。30302 Excel 的真实拒绝 1/安装 0、BIN fallback 63、目标 Chapter 436 行与真实启动保存结果继续按原合同解释。

## 固定输入和实际执行

每次全新 Python 进程通过 `tools/game_path.ps1` 取得客户端位置，重新核三份输入，调用现有 `extract_client72` 从原始字节提取结构；正式产物不依赖探索时的旧缓存：EXE `8b014f64…d6b5`、global `45dc7a44…ef2a`、startup `b3148108…5105`，完整 hash 在摘要 `inputSHA256`。不存在进程 attach、客户端启动或 I/O 模型成功替代。

候选固定 commit `d54f6f0d423ded9d055bb740a3e5f3aa8f2667f1`、tree `5b334e711319726c504da95dbfe6ebb26baaa7d3`；QuestExcel SHA-256 `c8561299f543456573b0fa16404e210bcd2ecf84ce5006eb63dd4b2845137a87`、303 BIN `aaf6901da4c30528047c1210bc636f810b368d5fb2f19aad6369bbb956dd3cca`。工具校验这两份原字节及既有 depot 合同 `a8a7b4ce…e1a95`；不重审全表和 849 条引用。

新入口 [client72_resource_deep.py](../tools/client72_resource_deep.py) 只在独立内存运行目标 AMD64 parser/codec。使用已有 `Offline72` 和 `fragment`，未修改公共 runtime。内存中的数组/字符串、空数组/空字符串 singleton、byte permutation 和 XOR key 是明示夹具；native 加减/XOR、分支、字段写入和 codec 执行。数组分配限定三个已审call-site，encoded uint32/int32元素宽4、char元素宽2；这是托管存储夹具，类型来源为调用者已核字段/写入，不是已加载真实类型对象。报告保留每次分配site/元素类型/宽度/数量及字符串转换明文。memcpy/memset、UTF-8 转托管字符串/reader cursor 是明示模型；字符串 helper 的前后边界来自 `14f0de820`，不是 LUA 或任务逻辑替身。错误样本在目标 throw helper 入口前停止，不执行异常运行库。

## depot 两个 scalar：默认、编码与网络值严格分离

目标类型 22284 `ALFCDOBDDFP` 与实际 depot 缓存/路径的身份继续由 f4c 独立 literal/loader/parser 绑定。字段 `OADEJHBGKHO/+0x68` 和 `HEGEDDGIFLL/+0x6c` 均为 58209 编码 uint32；ctor `148328b50` 仅 `ret`，实际调用后原始内存完全不变，不能说 ctor 把编码字段初始化为逻辑 0。

完整 parser `148328b60` 的实际数据合同为：

| 分支 | 输入及实际原生行为 |
| --- | --- |
| 表头 | `mask = (read_u32_le + 0x51fee905) mod 2^32` |
| `mask & 0x40` | 读取 payload，XOR `0x2a999229`，经实际 `14aefa090` 编码，`14832937e` 写 `+68` |
| `mask & 0x10` | 读取 payload，XOR `0x3361bcab` 后加 `0x05bd78ff`，经同 codec 编码，`148329d2b` 写 `+6c` |
| 任一 flag 缺失 | 该值走逻辑 0，再由实际 encoder 编码并覆盖旧值；并非保留已有 777 或把 raw 内存清零 |

五个完整样本分别是缺失/缺失、显式 0/0、123/缺失、缺失/456、`0xffffffff/0x80000001`；实际读回 `[0,0] [0,0] [123,0] [0,456] [4294967295,2147483649]`，消耗字节 4/12/8/8/12。关键 parser/codec 没有模型调用。二进制缺失与显式 0 得到相同逻辑值，但不同字节数仍保留。

f4c 已证 `+6c` 用于当前队伍元素资源查询；本轮与 Avatar 代理协调发现 `151847f70` 初始化尾段另外读取 `+68`，关联 AvatarInfo tag13/raw `+c0`。这是两种字段，不能相互代替。本卡不覆盖该 Avatar 消费者实际执行，不命名为 `energySkill/attackModeSkill`；以 Avatar 新合同为后继。候选 501/701 JSON 的 scalar 缺失，仍没有独立 JSON→flag→row 桥梁，不能用本实验批准其实际能量缺省。

Java 后继尤其不能忽略保存：`Avatar.java` 的 `captureVitals/completeLoadingVitals` 在 depot 缺失或非零 energySkill 缺资源时保留已保存 scalar。用猜测 0 将未知资源当作已就绪，会绕过真实资源前置并改变能量恢复结果；g2 生产接入必须先解释所选 depot/skill 的实际来源。

## LHFO 元素：条件类型和首参数已有独立证据

目标 depot 的 `LHFOOPMJHBN/+10` 是 40302 `CGKGCLDHAHE[]`。元素含 `HGNPCIHMABD/+10` 编码 uint32 数组、`FINHFCNILNN/+18` enum85159、`ELMCBLCOEEK/+1c` 编码 uint32。固定元数据 default table 独立恢复到符号：`SPECIAL_PROUD_SKILL_OPEN_CONDITION_TYPE_NONE=0`、`SPECIAL_PROUD_SKILL_OPEN_CONDITION_TYPE_QUEST_FINISH=1`。

完整元素 parser `15113cbb0` 实际验过缺省、显式空数组/0、`group=901, params=[30302,0x80000001], kind=1`、高位组/参数与未知 enum777。这里 `group` 只是实验输出短键，**没有将 `ELMCBLCOEEK` 的业务名恢复为 proudSkillGroupId**。目标函数 `15113cf70` 完整运行，仅在 kind1 且参数数组非空时解码首元素，否则返回0；五个结果为 0/0/30302/`0xffffffff`/0。未知 enum 值原样保留，不默认合法。

这比旧“只知道数组形状”前进，但尚不能批准 Java `specialProudSkillOpens` alias：当前 `AvatarSkillDepotData.SpecialProudSkillOpens` 只有 `proudSkillGroupId`，会丢掉目标条件类型/参数。现有两个空对象也不能证明整个数组都能忽略。外层 wrapper `15113ce30` 已静态定位，后继可在离线继续执行实际嵌套分配和类型检查；不需要启动客户端。

## Quest：BGH 实际读取与不可从现输入恢复的接受规则

精确 raw 名称 `BGHPAKBEPND` 在目标类型69472 `HOFCNOMNAME/+18`，同类型另有 int32[]/+10、uint32/+20 和 enum36721/+24。目标 default table 独立证实 `QUEST_CONTENT_GAME_TIME_TICK=18`、`QUEST_CONTENT_LUA_NOTIFY=20`。父类型87244 有两组该条件数组；具名引导 enum75148、显示 enum56833和单独对象结构同 LogicType 不同。

完整 `14ef42dd0` 已执行5组独立字节输入：省略/空、`2101001` 和 signed 参数边界、`1,24`、含中文/NUL的字符串；字符串解密到 UTF-8 buffer 是 native，托管字符串分配是模型。非空值/参数/count 分别读回，`count=0xffffffff` 保留。实际消费者 `149987a80` 从同一生成对象 `+18` 取 `1,24`，建立逗号分隔符并到真实 `System.String.Split/159aacf80` 前停止。没有执行 Split 后面的数值解释、完整 GameTime 条件，更没有执行 LUA_NOTIFY 对玩家字符串进度的查询。

因此目前可确定“目标条件对象保留并消费此字符串”，不能确定“BGH 就是当前 Java `param_str` 的所有业务契约”。尤其 30302 的 BGH 为空，旧/新2101001同值只作相关证据，不能批准 LuaNotify alias。保留原始字符串是将来入口转换的必要条件；未知字符串不应被现 Java 静默忽略后视作恢复成功。

候选30302 Excel 依旧是 `acceptCondComb:object`、`finishCondComb:object`、`failCondComb:string`，没有 acceptCond；对应 BIN 也没有 acceptCond。其 guide/guideHint/showType 与这三个错误标签的对应只是候选关系，**尚无 JSON exporter 字段表或原始资源与 parser 字段的桥梁**。本轮精确搜索解码 global literal usage group 中未命中 `Data/_ExcelBinOutput/QuestExcelConfigData`；有 MainQuestIndex literal。此有界负例不证明客户端没有动态形成该路径，也不证明服务器接受规则能从客户端恢复。

当前输入不能无损恢复自动接受行为：删除两个 object 或置 LOGIC_NONE只能让解析形式通过，不能创造缺失的条件。目标二进制 parser 的字段结构也不能代替一个权威的 server acceptance 数据源。应继续保持 `automaticAcceptance=false` 和显式303路径边界，拒绝自动完成/关任务/替换旧表/清档/手置loadedAll。

## revision、必要加载与可执行后继规格

本轮重读内置 `res_revision=data_revision=49008628`、`silence_revision=48722240`；候选标签仍为 R49086080/S49210135/D49230962。实际热更新选择、资源容器及运行时读取字节未观察；不能断言完全不同版本，也不能按7.2.0文字认定一致。解除需要选定安装实际读取的必要资源 blob+revision/manifest 对照，或可信 exporter 输入 manifest；只读取文件名和旧摘要不足。

| 后继 | 文件/来源与最小验收 | 当前分类 |
| --- | --- | --- |
| R3-n4b 外层 LHFO 路径 | 本工具新增独立卡；实际运行 depot bit0x20→`15113ce30`→元素 parser→`15113cf70`，数组0/2项、非零条件和类型拒绝，保留分配/类型库模型边界 | 可继续离线，未执行；不把元素单测扩大成外层已验 |
| BGH 目标业务身份 | 追 `149987a80` 调用者与条件18、LUA_NOTIFY20的独立消费；若目标客户端无该判断，需权威 server条件来源。只在证据闭合后触及 `QuestCondition/QuestBinFallback/ContentLuaNotify` | 尚未批准实现；不能加alias求绿 |
| Quest 入口转换 | 只有取得独立 raw→JSON 映射后，单独边界适配 QuestData；保留未知字段与原输入hash、冲突拒绝、自动接受不可恢复的显式状态。验非零/字符串/列表/错误和303真加载 | 当前缺原始资源或可信 exporter 映射；不能给无来源转换 |
| depot 初始化/保存 | `AvatarSkillDepotData.onLoad`、`Avatar.setSkillDepotData/recalcStats/captureVitals/completeLoadingVitals`；分别验default depot/wire depot/真实skill引用/保存能量恢复，不把0或None当未知的替代 | Avatar新消费可离线推进，候选资源桥梁未完成 |
| 正向2B | 复用303/Chapter夹具；先自然loadAll及必要map/索引/引用/ERROR有效，再新Player→真实QuestManager.loadFromDatabase，比状态、进度、owner及BSON，并保留loadedAll=false负例 | 前置未满足，本卡未运行DB或正向重绑 |
| D-02实机 | 新旧号登录/选人、完整初始化/入场/心跳/退出冷读；对上述实际资源来源及客户端日志逐项核验 | 全部未执行，不在本轮授权内 |

最小资源加载继续按已有索引使用：152反射类/优先级不是P0全集；Chapter只有特定索引合同；303 fallback仅支持所验字段和显式启动。depot `energySkillData` 依赖较高优先级 skill map，ability group依赖前置 `loadAbilityEmbryos/GameDepot.playerAbilities`；既有28真实引用合同只验其范围，不能由最终loadedAll标志推成无日志错误、全部引用成立。

## 验证、复现与冻结产物

两次新进程重新核客户端三hash、原资源输入、提取类型/enum/literal并执行所有native样本。完整报告和摘要分别逐字节一致：

| 产物 | SHA-256 | 字节 |
| --- | --- | --- |
| `local/migration72/astra-resources/native-{3,4}.json` | `05c6d8ee419a069f6aed64a89bad88e9be4acded2805c1364ef01c965d413692` | 185728 |
| `protocol/cn-7.2/resource-deep-observations.json` / 本机summary-4 | `5d0f7487de6bc4c1a5b5d43460d6e4c70152d6d8613ba16c764a2359246c69d7` | 83220 |
| 工具源码 | `95787d9e4cf3b941bb17045986ffa6234352818bb89e0d02ece9094b1c78e265` | 19125 |

`tools/test_client72_resource_deep.py` 10项通过；未重跑全面研究或Java回归。native-first是修正夹具singleton地址前的探索成功报告；native-1/2是在分配器逐site宽度与模型日志补齐前的成功报告，均保留但不作最终证据。初次bounded literal探索无QuestExcel精确字符串，不当作工具失败或广域不存在证明；旧缓存只用于找入口，最终两轮都重新提取。没有生产改动，因此无需构建、部署或重启。

```powershell
$resourceClient = & powershell.exe -NoProfile -ExecutionPolicy Bypass -File tools/game_path.ps1
python tools/client72_resource_deep.py --client $resourceClient --output local/migration72/astra-resources/native-review.json --summary local/migration72/astra-resources/summary-review.json
python -m unittest tools.test_client72_resource_deep -v
```

主线第三复验应比较完整和摘要字节、检查sourceSHA及每个stop/models，不只看退出码。本卡不自行Git提交，公共Project/Harness/交接由主线协调。以上冻结项之后还有表中明确可离线工作的n4b和BGH调用关联，不能以这一小项完成称资源研究整体穷尽或D-02完成。

主线逐项审核分配call-site/宽度、实际parser与模型、错误停止点，并独立执行10项断言及第三新进程；完整与摘要均同上述最终双跑。证明`local/migration72/astra-main/resource-review.json`，日志`resource-{tests,native}.log`。首次比较器误用不存在的summary-3文件名，改为实际summary-4后核同；不影响原生执行。无JSON alias或资源就绪晋级。

## n4b：外层 LHFO 数组链已继续实际执行

后继独立入口 [client72_resource_deep_nested.py](../tools/client72_resource_deep_nested.py) 固定首卡工具和摘要hash，没有修改它们或公共runtime。两个新进程重新核三客户端输入、提metadata及usage type cells，再完整执行 `148328b60 → 15113ce30 → slot4/15113cbb0`，并逐元素执行 `15113cf70`。上文“未执行外层wrapper”为首卡冻结时的历史边界；现仅本段指定样本已完成，候选 JSON 桥梁仍未成立。

三个独立 allocation cell 分别是 `1458a5698 → class40302[]`、`1458b97e8 → class40302`、`1458973e8 → encoded58209[]`；实际元素parser声明virtualSlot4，fixture vtable `+f0` 绑定该真实入口。native wrapper读取长度、分配后循环、间接调用真实parser、检查类型库结果并写入引用数组。

五个完整外层样本分别为省略数组、显式空、两个默认元素、两条非零/高位条件、未知enum777和空条件；前3组字节消耗4/8/12，native wrapper调用0/1/1次、元素parser调用0/0/2次。非零样本得到两个独立元素 `[group901, params30302/0x80000001, kind1]` 与 `[group0xffffffff, params0xffffffff, kind1]`，真实首参数查询各返回30302和`0xffffffff`。`group`仍是中性实验标签，未命名业务。

托管数组/对象分配、class/vtable及类型registry存储是明确模型；`14051b7b0`类型兼容内部没有执行，模型严格比较已声明的40302 fixture class，并有错误class反例，不能视作真实IL2CPP类型系统验收。四个反例分别到原生 array-type helper `1404c2c70`、null object/null array `1404c2be0`、截断子项 `1404c2650`；没有把类型检查恒定设true或跳过错误。首轮曾漏掉registry的二次解引用，在类型模型中明确拒绝；仅补模型需要的slot，记录 `nested-initial-failure.txt`。原始首成功nested-1和正式2/3均保留。

新增5项独立断言与原10项合计15项通过，两个新进程完整/摘要逐字节一致：

| n4b产物 | SHA-256 | 字节 |
| --- | --- | --- |
| `local/migration72/astra-resources/nested-{2,3}.json` | `430b63e55ebe0a3091bffdb39ace3b583758c151329e79965fbbc83b43641575` | 105975 |
| `protocol/cn-7.2/resource-deep-nested-observations.json` / 本机nested-summary-3 | `628b58e0484c0cd92581256937263325e7522279cd0c1a8cf222e9f976ef4e44` | 16584 |
| 新工具源码 | `fbb62a933cef882cee7d04437f6a9631c37ca5ce4ebfb2661432f58720251e2f` | 9912 |

```powershell
$resourceClient = & powershell.exe -NoProfile -ExecutionPolicy Bypass -File tools/game_path.ps1
python tools/client72_resource_deep_nested.py --client $resourceClient --output local/migration72/astra-resources/nested-review.json --summary local/migration72/astra-resources/nested-summary-review.json
python -m unittest tools.test_client72_resource_deep_nested tools.test_client72_resource_deep -v
```

此后不再把“未执行LHFO外层wrapper”列为当前阻塞。仍不能将候选 `[{},{}]` 转成上述真实装载结果：缺的是候选JSON和客户端读取字节的来源对应，不是现在已经跑通的parser分支。将同一候选JSON重新序列化成自造字节不会形成独立验证。生产alias和资源门禁保持不变。

## n4c：BGH 已知消费路径限定为 GameTime 条件

后继独立入口 [client72_resource_deep_quest.py](../tools/client72_resource_deep_quest.py) 重新提取两个方法参数，确认selector `1487cbb80` 的输出引用以及 `149987a80` 的入参都确为69472。固定原生caller `1499882f0` 在 `149988628` 调selector，检查成功且指针非空，再检查 `1487ca860` 为false，才在 `14998865c` 把同一指针交给字符串consumer；两条call机器码重新核对。该caller只作静态条件调用链，`1487ca860` 和完整caller没有执行。

selector实际扫描父对象`+48`数组、跳过非18的条件、返回首个enum18；18已独立恢复为 `QUEST_CONTENT_GAME_TIME_TICK`，不是20/LUA_NOTIFY。8个实际样本分别为 `[20]`拒绝、`[18]`接受、`[20,18]`选第二项、`[18,18]`首项优先、空列表、空父、空数组、null元素。前6项完整返回，最后两项到原生null helper；输出在搜索前实际清零。每个非null条件先通过首卡真实parser生成，未直接伪造BGH字段；父/数组存储是显式夹具。

这个结果关闭“已知非空字符串消费者是否足以解释LuaNotify”的歧义：**它不能**。这不是全二进制不存在其他LUA_NOTIFY消费者的证明；有界声明参数/直接call发现没有提供所需的Lua键匹配证据，不能把这条GAME_TIME_TICK链换名使用。

新增4项独立断言；与n4a/b合计19项通过，未增加Java/全面回归。两次新进程完整和摘要逐字节相同：

| n4c产物 | SHA-256 | 字节 |
| --- | --- | --- |
| `local/migration72/astra-resources/quest-{1,2}.json` | `c6d815b0b0094186e80f3cdba514508cd73c381aaf4125596e46027ecceafa16` | 15609 |
| `protocol/cn-7.2/resource-deep-quest-observations.json` / 本机quest-summary-2 | `0451e15f8e74040b5051ff5fafdfca6168e7ba0f30811f8f516a9fbbb93104f2` | 8823 |
| 新工具源码 | `e06e537e8c31e9aabfb0e33bbb38a87f4348ad7ca69bbea7c403429f23c5b6d0` | 7379 |

```powershell
$resourceClient = & powershell.exe -NoProfile -ExecutionPolicy Bypass -File tools/game_path.ps1
python tools/client72_resource_deep_quest.py --client $resourceClient --output local/migration72/astra-resources/quest-review.json --summary local/migration72/astra-resources/quest-summary-review.json
python -m unittest tools.test_client72_resource_deep_quest tools.test_client72_resource_deep_nested tools.test_client72_resource_deep -v
```

## 本轮结束时的准确后继，不把未知改成默认成功

已实际推进到独立native parser可回答的默认/非零/边界、LHFO完整嵌套和BGH现有消费链；此前列出的n4b以及已知BGH typed调用关联均已执行，不能再将它们原样交接为未开始。仍未穷尽客户端全部玩法或资源图，也没有新证据允许生产转换。当前可直接执行的Sol/Terra工作仅限复验这些已固定的工具和边界、整理受控输入，不能实现尚无来源的alias。

| 仍缺证据 | 已查范围与最小补充 | 所阻塞后继 / 何时可以继续 |
| --- | --- | --- |
| 候选 JSON 对应目标 parser 字段/flag | 当前研究目录只找到固定候选Quest/depot JSON和旧producer；没有可用原始资源blob、该候选exporter映射或接受规则数据。固定metadata现有literal/类型/parser已查；不以同shape或同值作转换来源。 | 需提供同一固定客户端的解密资源输入blob及路径/revision来源，或可信且固定hash的exporter字段映射。取得后可纯离线把原始字节送本工具独立解析，与明确ID候选记录比对；无需先部署。 |
| 接受规则 | 30302候选Excel/BIN均无acceptCond，现有client条件类/引导对象不能创造这些server条件。 | 需独立的server acceptance源，或另一个确有该信息且来源清楚的数据。否则保留显式303和自动接受不可恢复，不能提供“转换即可完整loadAll”的规格。 |
| BGH用于LuaNotify的业务关系 | 非空parser已验；现有typed consumer属于enum18，enum20被该selector拒绝。 | 最小目标观察为一个非空LUA_NOTIFY20资源字段及其真实进度匹配/事件键来源，或有版本依据的server消费者资料。空303与旧2101001同值不够；取得后才审 `QuestData.QuestCondition`、`QuestBinFallback` 入口alias/冲突拒绝及 `ContentLuaNotify` 回归。 |
| 候选revision与实际选中资源 | 三固定二进制、内置revision、候选commit标签均已固定；尚无运行时热更新选择证据。 | 在未来单独授权的最小观察中记录depot loader `14d6d53a0→14d6e1570` 实际取回的必要blob、revision及输入hash，成功/失败都保留；不能修改版本文件或用健康检查解除。 |
| 正向QuestManager/能量保存 | 只复用既有303冷读拒绝；本轮没有执行loadAll/DB。 | Java门禁 `QuestManager.java:705`、`ResourceLoader.java:85/131` 需自然满足并验必要map/引用/ERROR。Avatar `1564/1579/1605/1612` 的加载能量与保存保护要求真实depot/skill来源；未知不得填0。 |

另外两条协作线已提供可复用的定向静态来源，实际语义与执行由对应代理独立核验：Scene的`GLOBAL+C6D0`在owner41992，getter `1500d85a0` 关联具名ABILITY HASH日志，`1500d5480` 是同表的AddAbilityString入口；Avatar的owner28604和39027分别关联 `AvatarOrnamentGroupExcelConfigData` 与 `AvatarOrnamentExcelConfigData` literal/getter/loader。探索摘要只在本机 `scene-string-source.json` 和 `avatar-tag38-resources.json`，不冒充本卡已批准的协议或资源字段映射。

这里的实机缺口是明确的外部证据边界，不是声称任何资源难点都必须实机：如果能取得来源完整的原始blob/exporter输入，JSON映射、parser和引用可继续离线。没有该输入时重复生成同一个合成样本、重扫全部表、完整loadAll碰错或扩大模型默认值，都不能解除当前缺口。生产内容未改，未构建/部署/重启，无新增客户端验收。

n4b主线核对精确type cells、slot4和类型拒绝模型，15项关联及第三新进程报告/摘要同双跑；证明`local/migration72/astra-main/resource-nested-review.json`，日志同前缀。仅闭合指定二进制外层数组样本，不批准候选JSON资源装载。

n4c主线核对typed参数和caller条件边界，19项关联及第三原生报告/摘要均同双跑；证明`local/migration72/astra-main/resource-quest-review.json`。仅GAME_TIME selector有限闭合，未执行LuaNotify或批准alias。
