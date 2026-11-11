# E-01d1 / R3-OLD-TALENT-ENTRY-NULLS：六差异的当前 Java 调用链

状态：已完成当前 Java 行为的限定验收。主线第三轮包含缺配置键对照，5 项全部通过，720 个非空源键经真实 loader 均安装。本专题没有启动 Mongo、服务或客户端。它不恢复 7.2 目标 parser、字段 alias 或修订号，也不改变资源 ready 和 D-02 状态。

## 固定输入与边界

[旧号引用](CLIENT_72_D02_OLD_TALENT_REFS_20261111.md)已核 121 角色、726 个当前 Depot 唯一查键；[候选源对照](CLIENT_72_D02_OLD_TALENT_SOURCES_20261111.md)指出两表对 621–626 有 null/空串差异，其余 720 个非空值在当前 checkout 的 AvatarTalents 文件各有一个精确源键。这是限定固定旧号、固定候选和 checkout 的来源覆盖，不是目标行语义或自然运行时配置的证明。

| 输入 | 相对路径 | SHA-256 |
| --- | --- | --- |
| 候选 Talent 表 | `local/migration72/d02-r3-candidate/ExcelBinOutput/AvatarTalentExcelConfigData.json` | `f573e89101fc47a8a395def6b840f075dd7dfb52d6df69488b9fefbe2b6ab0d4` |
| checkout Talent 表 | `resources/ExcelBinOutput/AvatarTalentExcelConfigData.json` | `6b10e3bcf21dc0cb5f07e0abd74624af8c4fd853eb75689da9906f560841c886` |
| 726 行来源报告 | `local/migration72/d02-resume-20261111-asc/c-talent-sources/third.private.json` | `19be8f2bde7e36eddb7e2d844c946e8b7d78c877c35f43cda4593353ab8f2cb0` |
| 旧号查键报告 | `local/migration72/d02-resume-20261111-asc/c-talent-refs/main-third.private.json` | `6ba484b3a4c65b3ed773e084fa95d559c91f1385abb0e7454c16606e1f76690a` |

六个候选行均缺 `openConfig`，checkout 则显式为 `""`；候选 `addProps` 为两个空对象，checkout 各为两个显式零属性对象。六差异均属于固定旧号 Avatar 10000062 的当前 Depot 6201；该角色不在固定活动普通队伍，活动 24 键与之无交集，且活动 24 键的候选 OpenConfig 均非空。这是固定 JSON 只读观察；下文 Java 只验证隔离测试构造，不证明该角色自然入场。

## 源码路径和判断

- [ResourceLoader](../src/main/java/emu/grasscutter/data/ResourceLoader.java) 的 `loadAll` 先 `loadTalents`、`loadOpenConfig`，之后 `loadResources(true)`；`loadFromResource` 解码每行、调用 `onLoad()` 后安装，单行异常记拒绝并继续。离线 JSON 命中不能保证成功安装。
- `loadOpenConfig` 先读取编译缓存，再按 Equip、Avatar、Relic、Team 四个直属目录顺序读取；[OpenConfigLoader](../src/main/java/emu/grasscutter/data/OpenConfigLoader.java)对缓存名和源定义键均以 `isBlank()` 拒绝空白，合法同名后项覆盖前项；非法 effect 可以被跳过，完全不可读的非空定义不覆盖。既有 720 源命中只调查 AvatarTalents，不包括实际缓存、其余三个目录与运行时胜出结果。
- [AvatarStorage](../src/main/java/emu/grasscutter/game/avatar/AvatarStorage.java) `loadFromDatabase` 对 AvatarData 和当前 Depot 都存在的角色绑定对象后调用 `recalcConstellations`，`postLoad` 再调用 `recalcStats`。数据、持久状态和控制流有条件，不能据此推断所有角色实际执行完毕。[Avatar](../src/main/java/emu/grasscutter/game/avatar/Avatar.java) `getTalentIdList` 返回 Depot 列表与保存解锁集合交集；`recalcConstellations` 的行查找后过滤 null 和空串再查 OpenConfig；`recalcStats` 过滤 null，`addToExtraAbilityEmbryos` 再过滤 null/空串，非空才查配置。这两条都不把缺键强制加为 ability。
- [HandlerSceneInitFinishReq](../src/main/java/emu/grasscutter/server/packet/recv/HandlerSceneInitFinishReq.java) 创建 `PacketPlayerEnterSceneInfoNotify`，其对活动实体调用 `buildAvatarAbilityVars`；[sender](../src/main/java/emu/grasscutter/server/packet/send/PacketPlayerEnterSceneInfoNotify.java)查 Talent 行后仅过滤行缺失或 null；空串仍查询 `GameData.getOpenConfigEntries()`。若由上述 loader 单独产生该 map，空白键不可从 loader 安装；其他直接写 map 的行为和实际运行配置须另核。
- [AbilityTalentResolver](../src/main/java/emu/grasscutter/game/ability/AbilityTalentResolver.java)把存在的 Talent 行的 null/空字符串均存入候选，再以其为 key 查 `GameData` 的 `HashMap`；当前代码不会因空串自动跳过这一查询。它有冷却、变量、解锁等使用条件，不能一概称为必经入场。`AbilityAutomaticRevival` 等也有独立触发条件，不能用 sender 测试代替。

## 主线执行的独立 Java 测试

新增 `src/test/java/emu/grasscutter/migration/OldTalentEntryNullBehaviorTest.java`，第三轮执行前 SHA-256 `a94dcdfef5f06b8857421aa72ca42e7259a2b6261df76608c6bd1914105eebc1`。测试会校验输入原字节哈希，复制六行进临时文件，真实走 `ResourceLoader.loadFromResource`/`onLoad`；每行分别走角色交集、`recalcConstellations`、`recalcStats`、场景 builder 和 resolver。合成非空配置验证正常 ability 和 scene param；另有合成 Talent 缺行、Talent 行存在但非空配置名缺键两个独立对照，用来验证当前 fallback；null/空串 map 哨兵仅证明对应代码是否进行查询，它们故意绕过 loader，不能作为实际资源合法性或放行依据。

第五项验证报告内 129 个直属 JSON 文件逐字节 SHA 及目录文件集合，再只传入此目录和不存在的缓存给真实 `loadOpenConfig`；对报告的 720 个非空查键逐一读取加载后 map，在 stdout 写 `OLD_TALENT_SOURCE_REPORT` 和未安装行清单。此项可以发现解析失败，但仅代表给定目录的隔离加载；不包括生产实际缓存、其他三目录、完整装载、目标身份和实际客户端。不得把只有部分安装写成全部 720 安装成功。

主线以便携 JDK17、离线单 worker、隔离 config/data/resources/temp/log 运行。forked test worker 显式传以下四个 JVM system property（路径转换成当前 checkout 内绝对路径）：

```text
d02.talent.candidate = 上表候选 Talent 表
d02.talent.checkout  = 上表 checkout Talent 表
d02.talent.sources   = 上表 726 行来源报告
d02.talent.binroot   = resources/BinOutput/Talent/AvatarTalents
```

使用已有隔离 init 脚本向 fork 的 `Test` task 传入以上属性并启用 `testLogging.showStandardStreams = true`，执行：

```powershell
./gradlew.bat --no-daemon --offline --max-workers=1 --console=plain -PskipHandbook=1 --init-script '<隔离 init 脚本绝对路径>' test --tests emu.grasscutter.migration.OldTalentEntryNullBehaviorTest
```

保留实际 Gradle 命令、init、日志/XML、输入输出与测试源哈希，核 `OLD_TALENT_SOURCE_REPORT` 和 class-init、保护文件；若测试不通过，记录首个真实失败与原因，不将准备视为通过。仅测试和文档无需部署。该夹具反射创建测试 Avatar/Depot，未走真实持久化角色登录、完整 sender、生产 `loadAll` 或客户端；六差异的目标值仍需要独立目标证据，四目录和缓存的自然加载/覆盖、真实保存与活动角色入场仍待后继核验。

本子任务实际运行 `python -X utf8 -B -m unittest tools.test_client72_d02_old_talent_sources -v`，7 项通过；它复核固定源、六差异和源键。

主线首轮 java1 因隔离资源根缺少启动检查所需目录，在 `startupCheck` 退出，测试未执行；修正隔离根后 java2 的测试源 SHA-256 `f2fe3b897d906e21b7746cb5df68840656d9e57c648cf99304e`，五项通过（13 秒），隔离真实 `loadOpenConfig` 对报告的 720 非空查键 `unresolved=0`。此结果只表示固定 AvatarTalents 目录和不存在的缓存组成的测试输入均被当前 loader 安装，不表示其他目录、真实缓存、有效 effect 语义、目标身份或完整资源加载。该实体采用 `EntityAvatar(scene=null)` 的测试构造，构造过程会记录空 scene；成功执行的是静态 `buildAvatarAbilityVars`，没有真实场景实体生命周期或网络发送。

主线指出旧版只覆盖 Talent 行缺失，未单独覆盖 Talent 行存在但其非空 OpenConfig 缺键；最终源码已加入该对照。第三轮 java3 以该冻结源码实际编译运行，24 秒 exit 0，XML 为5项、failure/error/skipped均0，720查键仍 `unresolved=0`。`recalcStats` 的正配置及缺配置对照证明实际消费者执行，不能将测试的空 proud 集合外推为旧号完整恢复。

原件留私有 `local/migration72/d02-resume-20261111b/talent/java3/`：隔离 init、`gradle.private.log`、XML、`work/class-init.log`。主线核查 class-init 没有 DatabaseManager、GameServer、HTTP/DispatchServer 或 PluginManager 初始化；未启动数据库或服务。java1 失败原件、java2 边界均保留。后继只调查本次720个必要键在生产实际缓存/四目录的覆盖和目标同源语义，不默认将全资源库列为P0。
