# D02-R3 最小资源：进行中

本记录是受控入场资源的分项证据，不是完整资源闭包或部署许可。固定候选 commit `d54f6f0d423ded9d055bb740a3e5f3aa8f2667f1`、根 tree `5b334e711319726c504da95dbfe6ebb26baaa7d3`，来源及版本差异见[资源候选](CLIENT_72_RESOURCE_CANDIDATE.md)。上游候选没有 Scripts；scene Lua 仍为本项目旧实现及适配，不冒称 7.2 原始脚本。`resourceReady=false`。

2026-11-06 本轮重新按 `tools/game_path.ps1` 解析 GAME_PATH 并直接读取三输入 SHA-256：YuanShen.exe `8b014f64b5dc12fbc476e0c362b053abfdd7a1378cd282e4ad81057621dbd6b5`、global-metadata.dat `45dc7a44dc9fc187146c52f6c33de57e2d1c1dcfec57f8736431553460f6ef2a`、startup-metadata.dat `b314810840ca6f495822c9c44483dde2394b9f09ed3c6b13c2782092f04d5105`。资源子模块提交 `dac2a9d43332ef51879522cf39da2831b0ca0543`；原有 `ScriptSceneData/flat.luas.scenes.full_globals.lua.json` 的 SHA-256 `db3e6a2e87436fefb57d43e8f2b52528017d2a68d530e8cf56b85a53242e5bfe` 未动。候选目录是已按 Git tree/blob 固定的提取集，并非 Git checkout。

## 子合同 A：出生角色和初始武器、技能引用

`HandlerSetPlayerBornDataReq.java:56-89` 限定选择 `10000005`/`10000007`，任务开启时选基础 depot `501`/`701`，关闭时选 `504`/`704`；成功创建、绑定队伍并确认保存才进入登录。`Avatar.java:124-152` 和 `Player.java:1317`、`AvatarStorage.java:75-88` 将角色的 `initialWeapon` 用于新装备，只有 itemData 存在才添加并装备。具体生产依赖不能由目标资源存在推断已运行。

| 角色 | initialWeapon | questing 启用 depot | questing 禁用 depot |
| --- | ---: | ---: | ---: |
| 10000005 | 11101 | 501 | 504 |
| 10000007 | 11101 | 701 | 704 |

`tools/audit_d02_resources72.py` 验证 commit→根 tree→Excel tree→四个文件 blob/大小，再核以上两行及四个 depot 的 `skills`、`subSkills`、`energySkill`、`attackModeSkill` 正 ID 全部存在于候选 AvatarSkill 表；0 保持缺省哨兵。各 depot 对应的正 ID：

| depot | skills | subSkills | energySkill |
| --- | --- | --- | --- |
| 501 | 100540 | 10035, 10101, 10001, 10102, 5005010 | 无 |
| 701 | 100550 | 10035, 10101, 10001, 10102, 5007010 | 无 |
| 504 | 100543, 10067 | 10101, 10001, 10102, 10006, 5005010 | 10068 |
| 704 | 100553, 10067 | 10101, 10001, 10102, 10006, 5007010 | 10068 |

`attackModeSkill` 四行均无正引用。`subSkills` 是本次新增范围；此前 849 条检查不包含它们。`Avatar.java:976-977` 也将 subSkills 用于技能合法性判断；存在性不证明入场必定调用该分支或技能效果正确。两角色 `avatarPromoteId=12`；504/704 的 talents 为 71–76、inherent proud skill groups 为 721/722，相关表及能力、角色成长曲线、武器成长/突破和其他字段引用仍未核，不标通过。四个 depot 的 ability group 为 Boy/Girl 的 Common/Wind，内容与实际加载尚未验证。旧测试号必须按其实际角色/武器/保存状态另核，不能用此新号模型替代。

本次实际运行新工具两次，输出逐字节一致，报告 SHA-256 `a5974ef30f2d8e01e607b83e8ef68dcd577423a5a9ad1ab11db662bce9f5698f`；三项聚焦测试通过，覆盖两性、零哨兵、缺武器、缺技能、错误 depot 和负数拒绝。完整结果在忽略目录 `local/migration72/evidence/d02-r3-avatar-references{,-repeat}.json`。未运行 Gradle、生产加载、数据库或客户端。此项只验证固定候选中的选定引用存在，不验证客户端 revision、技能业务或实际装备存档。

主线审核时标准模块测试入口因绝对导入失败；本次修复包内/脚本两种导入后，实际执行下列命令，3 项通过、失败/错误/跳过为 0，模块方式生成报告也与原报告逐字节一致：

```powershell
python -m unittest tools.test_audit_d02_resources72 -v
python -m tools.audit_d02_resources72 --source local/migration72/sources/AnimeGameData/d54f6f0d423ded9d055bb740a3e5f3aa8f2667f1 --commit local/migration72/evidence/resource-source-commit.json --root-tree local/migration72/evidence/resource-source-tree.json --excel-tree local/migration72/evidence/resource-source-ExcelBinOutput-tree.json --output local/migration72/evidence/d02-r3-avatar-references-module.json
```

## 尚未闭合

生产新号 `GameConstants.START_POSITION` 为 `(2747,194,-1719)`、`Player` 初始 scene 3；这不是独立确认的 7.2 安全落点。必须继续按实际索引、邻近 group、加载顺序和触发器核验，不能将 13,616 组顶层加载当运行事件通过。旧号可能处于其他保存位置，不可删档。根 fastRequire=true 保持，已知固定审计 fast 407 组/449 错误事件及 inline 零错误的范围与[真实加载](CLIENT_72_PRODUCTION_SCENE_LOAD.md)、[嵌套 require 修复](CLIENT_72_REQUIRE_BINDINGS_FIX.md)相同，未证明目标运行兼容。

客户端内置 revision `49008628/49008628/48722240` 与候选标签 R/S/D `49086080/49210135/49230962` 的对应和热更新状态仍未知；须等 R2 自然观察，不能人为改 revision。scene 3 索引、具体触发器及 ScriptLib 后代、目标实体数据和实际落地仍待验证，59 个缺实现 API 不能整体假定必需或可后置。完整资源链及实机为 R3/R4/R6 开放项。

## 子合同 B：出生点网格候选及组加载首个业务边界

`Player.java:495,499` 新玩家初始使用 `GameConstants.java:31` 的坐标和 scene 3；`Scene.java:839-850,1205-1230` 脚本初始化成功后按网格检查组，并另行检查区域。`SceneScriptManager.java:585-714` 缓存未命中时遍历所有非动态组建立网格；`Grid.java:36-67` 根据 vision 配置和实体错误保护查询。指定出生点并不只加载出生块中按组坐标最近的几组。

在独立 JVM 使用固定旧审计 JAR `2566f46604df80ddaad05801fb182a7217b9d057a2c8619e21fc54e49db083fe`、真实 LuaSerializer 和候选 Gadget 表，复用已有 inline 序列化器构造**源码等价网格模型**；脚本 manifest、旧审计固定源码、旧 JAR 与候选 Gadget blob 均实际核对。当前视觉配置六层为 `20/80、40/16、250/1000、1000/4000、20/40、20/20`（宽度/范围），实体错误保护为 true；`Scene.getPlayerActiveGroups` 只查询 0–3 层，而宽度1000的层3被500米规则跳过。六层网格 hash 全部等于先前固定审计；新 helper 又将生产 `SceneIndexManager.queryNeighbors` 查询结果与矩形逐格结果比较一致。两次网格报告 JSON 对象一致，但原始字节因无序集合排列不一致，不能称逐字节一致。

固定坐标模型选出 **25 个去重候选组**：`133003022、133003024、133003025、133003032、133003105、133003128、133003275、133003288、133003289、133003313、133003317、133003332、133003333、133003426、133003429、133003504、133003505、133003601、133003785、133003801、133003802、133003803、133003804、133003807、133003901`。第0/1/2/3层分别24/2/1/0组（层间有重叠）。这是固定候选数据、旧 Lua 和 inline 模型下的空间候选，不是已运行生产 `getGroupGrids` 的结果；旧号位置、脚本初始化、动态任务组、插件/缓存、已存套件及调度可能改变实际集合。

再使用已核准的 require-bindings 归档 JAR `7ca18e10c056fd0715e57cdbbb13b4607e324362675a7127b2ed28b5269e8f5f` 和既有 hash 门禁，在独立 JVM 用实际 `ScriptLoader`、`SceneGroup.load` 按固定升序加载25组。fast 与 inline 各25/25在该限定审计范围内完成，错误、缺脚本、缺命名 trigger、业务调用哨均为0；两模式对应组的 bindings 和 typed 对象 hash 均相同。统计为301 gadget、62 monster、33 region、31 suite、53 trigger。两次独立运行各模式的报告分别逐字节一致，SHA-256：fast `3da276d8c85df78df7889fb55f78a3363dc0219cf0089e18f2ef2c871bb9b0f0`，inline `fc163f3a4928483d2dc8ae85595511055d6ecc25825bec353f51add410a48ca8`。这只证明该顺序和进程中的顶层声明/绑定；不能覆盖全场索引实际顺序、其他运行状态、实体生成或触发器执行，也不改变全场 fast 已知错误。

该25组中，`scene3_group133003505.lua:39,53-72,162-170` 的初始 suite 1 绑定 `GROUP_LOAD_505010`；`Scene.java:1375-1416` 加载组后会派发 GROUP_LOAD。隔离 fast JVM 中实际用 ScriptLoader 和 SceneGroup 验证初始 suite 绑定，再直接调用该 Lua action：真实 `ScriptLib` 首次调用 `GetGadgetStateByConfigId` 前由观测哨抛错停止，不返回假值；两次相同，未初始化数据库、GameServer、HTTP 或 SceneScriptManager。后续根据实体状态可能调用 `SetGroupGadgetStateByConfigId`、`KillEntityByConfigId`，真实事件派发、装置状态与后续业务均未执行，仍阻塞 R3。`scene3_group133003504.lua:35-41,74-101,176-229` 的 suite 4 包含战斗/死亡 trigger，引用目前缺实现 `LockMonsterHp`/`UnlockMonsterHp`；默认 suite 1 为空，但任务或持久状态是否切换未知，不能仅凭默认值排除。`scene3_group133003785.lua:25-31,53-74` 的 region 在另一高度且属于 suite 2；实际区域进入和套件切换仍待验证。

原始工作和日志位于 `local/migration72/evidence/d02-r3-{grid-model,scene-loader-fast,scene-loader-inline}*.json`、`local/migration72/d02-r3-{grid,loader,trigger-probe}*`；新 helper 为 `tools/Client72D02ResourceAudit.java`。本机执行脚本 `local/migration72/d02-r3-{grid,loader,trigger-probe}-run.py` 保留隔离配置与 hash 守卫，报告中 `resourceReady` 保持 false。本轮没有运行场景 manager、服务器、数据库或客户端，没有触碰原资源子模块差异或根配置。

只复现上述 **25组的真实 loader 对照**（不重跑13,616组网格模型）可在 checkout 根目录执行下列命令。它使用已跟踪的审计器、固定旧报告/脚本哈希门禁、已记录的25个模型候选，编译 `tools/Client72ProductionSceneLoadAudit.java` 到独立目录，分别在两个新 JVM 运行并比较规范 JSON hash。原报告写入时采用 Windows 换行，故其原始文件 hash 与此处规范 JSON hash 分开记录。运行前须保留对应归档 JAR、固定旧 inline 报告和 portable JDK；若 replay 目录已存在则拒绝覆盖。

```powershell
@'
from pathlib import Path
import hashlib, json, sys
repo = Path('.').resolve()
sys.path.insert(0, str(repo / 'tools'))
import audit_production_scene_load72 as a
base = repo / 'resources'
jar = repo / 'local/migration72/builds/LunaGC-before-player-data-7ca18e10.jar'
parent = repo / 'local/migration72/evidence/scene-grid-inline-audit.json'
jdk = next((repo / 'local/runtime').glob('jdk-17*'))
review = dict(a.REVIEWED_SOURCES)
review['src/main/java/emu/grasscutter/scripts/ScriptLoader.java'] = a.REQUIRE_BINDINGS_LOADER_SHA256
inputs = a.verify_inputs(repo, base, parent, jar, runtime_sha256=a.REQUIRE_BINDINGS_RUNTIME_SHA256, reviewed_sources=review)
groups = [133003022,133003024,133003025,133003032,133003105,133003128,133003275,133003288,133003289,133003313,133003317,133003332,133003333,133003426,133003429,133003504,133003505,133003601,133003785,133003801,133003802,133003803,133003804,133003807,133003901]
work = repo / 'local/migration72/d02-r3-loader-replay2'
work.mkdir(parents=True, exist_ok=False)
classes = work / 'classes'
classes.mkdir()
a.compile_helper(repo, jdk, jar, classes)
expected = {'fast':'0752c7bdced83db0b620a1e7b1f21763a854961093a6a600b5359ae88e2d6e09', 'inline':'035c29b15b56270fdc78f0c2bd23f8424473e4448f448b7bfe1ca016bad4cb93'}
for mode in ('fast', 'inline'):
    result = a.run_process(jdk, jar, classes, base, groups, inputs['files'], mode == 'fast', True, work / mode, work / (mode + '-evidence'))
    raw = (json.dumps(result, sort_keys=True, indent=2) + '\n').encode('utf8')
    (work / (mode + '.json')).write_bytes(raw)
    digest = hashlib.sha256(json.dumps(result, sort_keys=True, separators=(',', ':')).encode('utf8')).hexdigest()
    if digest != expected[mode]: raise ValueError(mode + ' report drift: ' + digest)
    print(mode, len(result['groups']), digest)
'@ | python -
```

## 子合同 C：候选 depot 特殊字段的生产加载红测

候选 depot 504/704 的 `LHFOOPMJHBN` 内均出现 group 751，而现行 `AvatarSkillDepotData.java` 未将此键列为 `specialProudSkillOpens` 的可接受名称。以根 JAR `eee183715cf27c5d2c25da0591c890f4c144116317ba0bf0afe8bb0030aeae0e` 在独立目录调用实际 `ResourceLoader.loadFromResource → JsonUtils.decode → onLoad`：两行原样加载后 `special=null`、`questGroups=[]`；仅在隔离对照夹具将该键重命名成已支持字段时，均得到 `special=present`、`questGroups=[751]`。此对照证明当前 loader 没有绑定原键，**不独立证明该混淆字段的 7.2 业务语义**，不据此自动添加别名或改生产。

最初一次运行因 PowerShell 参数解析失败，第二次因隔离目录的启动前置未齐导致初始化失败；保留各自日志。补齐隔离目录结构及先初始化配置后真实 loader 跑通；class-init 未见数据库、GameServer、HTTP、DispatchServer 或 ScriptLoader 初始化。实验源码、两份受控输入及日志保存在 `local/migration72/d02-r3-depot-red/`，没有修改候选表、原配置或原资源。后续需独立目标语义及实际受控角色依赖证据，再评估是否需要生产修复。

## 子合同 D：有限成长、突破和武器实体引用

从同一固定 commit 取得 `AvatarCurveExcelConfigData`、`AvatarPromoteExcelConfigData`、`AvatarTalentExcelConfigData`、`ProudSkillExcelConfigData`、`WeaponCurveExcelConfigData`、`WeaponPromoteExcelConfigData` 六表到忽略目录；逐文件校验根 tree → Excel tree → Git blob 和字节数。扩展 `tools/audit_d02_resources72.py --extended-source` 从前述四 depot/skill 实际行推导天赋及 proud group，验证 level 1 的角色 HP/攻击与武器攻击/暴击曲线、角色突破 12/0、武器突破 11101/0、天赋71–76、proud groups 721/722/730/731/732/739 对应 level 1，及武器 11101 → gadget 50011101 存在，共21项；后两类仅为资源引用存在，不证明新号在零突破时使用了这些效果或目标业务语义。group 751 和其混淆键保持未知。

两轮报告逐字节一致，SHA-256 `70ddb0ca31b4dff5a09962c7e2992035c51d5608096bcae9515162b0ae2dde6e`，本机 `local/migration72/evidence/d02-r3-avatar-extended-v3{,-repeat}.json`；3项原有+1项新增 D02 聚焦测试、7项原资源测试共11项通过，负例包含缺曲线、proud 行和武器 gadget。原有 A 范围不带 extended 参数重新生成，SHA 仍为 `a5974ef30f2d8e01e607b83e8ef68dcd577423a5a9ad1ab11db662bce9f5698f`。完整能力 group、资产资源、其他等级/存档与目标运行兼容仍未知。

```powershell
python -m unittest tools.test_audit_d02_resources72 tools.test_audit_resource72 -v
python -m tools.audit_d02_resources72 --source local/migration72/sources/AnimeGameData/d54f6f0d423ded9d055bb740a3e5f3aa8f2667f1 --commit local/migration72/evidence/resource-source-commit.json --root-tree local/migration72/evidence/resource-source-tree.json --excel-tree local/migration72/evidence/resource-source-ExcelBinOutput-tree.json --extended-source local/migration72/d02-r3-candidate --output local/migration72/evidence/d02-r3-avatar-extended-v3-replay.json
```

## 子合同 E：505 组真实事件及实体状态

`src/test/java/emu/grasscutter/scripts/D02Group505LoadTest.java` 在独立测试 JVM 使用真实 `Scene`、非持久 `SceneGroupInstance`、真实 `EntityGadget`、`ScriptLoader/SceneGroup` 和 `ScriptLib`，将组505的初始 suite 注册后，通过 `SceneScriptManager.callEvent(EVENT_GROUP_LOAD)` 异步派发并等待 Future。使用 dispatch-only `GameServer` 容器与真实 `DropSystem`，不调用服务器 start。输入为固定旧 scene3 group Lua（SHA-256 `8df671f4adc438e6277f3d716d80b64904ebc35715305b4779ec089fa744d3ba`）、候选 7.2 Gadget 表（`5debe3c457b0fddc5264620aff1a9585cb19f24408a7f1974291244415ee1411`）、原 `ConfigGlobalCombat.json`（`df4b1d9dce5756251195995a4c30ae40f66c792735bb0977d166dd988f9965c0`）与原 Server DropTable（`25baed72a5fe12705dcf870d0c705819f6ca7cc7f7694563fc015fd65634f940`）；后两者为旧资源，不冒称已证 7.2 来源。drop表不存在ID 0，非持久实例不写数据库。

最终定向 **2项通过，失败/错误/跳过为0**：当505001为 GearStart、505002为 Default 时，真实 action 将505003设为 Default、移除505001并记录组内删除标记，而505002/505004保持；重复 GROUP_LOAD 仍保持。505001不存在时，真实查询的 -1 分支将505003设为 Default 并记录删除。额外真实 EntityGadget 使用另一 group、相同 configId 的反事实夹具，事件前后对象和状态均不变；这不是声称该实体来自真实第二组。测试前保存并在结束精确恢复 GameData combat/drop/gadget、run-mode override 和脚本开关；ScriptLoader 与其缓存仅由一次性 JVM 持有，进程结束释放。

初轮2项失败源于测试容器缺 GlobalCombat 及重复初始化 loader；第二轮1项失败源于 dispatch-only 容器缺 DropSystem；两轮原结果均保留。改为真实资源和真实 DropSystem 后通过，未模拟 ScriptLib 返回值。最终测试日志未见 Lua/condition 错误，class-init 未见 database、HTTP 或 DispatchServer 初始化；GameServer 是未启动容器。证据在 `local/migration72/d02-r3-group505-test/{first,second,review}-*`，最终源码 SHA-256 `0acc550510c7d29675e19cf7d8fed6f5a5db4c2464fbaddebe228f26ccf7b0f9`、测试 class `476e136f54fe84a594dce4a96f067807c37b0543566c9a15361f05f3ac119932`。JAR、根配置与原 dirty 资源的 hash 保持前述值。该合同手工注册选定套件，没有运行完整场景 tick、生产持久数据库或客户端；其他组、实际任务状态、实体/触发链仍阻塞 R3。

复现须使用 portable JDK17、独立工作目录和已固定候选 Gadget 表。以下脚本在新目录生成测试专用 config/Gradle init，不修改根配置；通过 JUnit 自身的 hash 断言固定资源。测试将编译项目测试代码，执行前需避开其他并行构建；不会启动服务或 MongoDB。

```powershell
@'
from pathlib import Path
import json
r = Path('.').resolve()
w = r / 'local/migration72/d02-r3-group505-replay'
w.mkdir(parents=True, exist_ok=False)
config = {'version':14,'folderStructure':{'resources':str(r/'resources'),'scripts':str(r/'resources/Scripts'),'data':str(w/'data'),'plugins':str(w/'plugins'),'cache':str(w/'cache'),'packets':str(w/'packets')},'server':{'fastRequire':True}}
(w/'config.json').write_text(json.dumps(config), encoding='utf8')
init = r"""allprojects {
    tasks.withType(Test).configureEach {
        workingDir rootProject.file('local/migration72/d02-r3-group505-replay')
        systemProperty 'user.dir', rootProject.file('local/migration72/d02-r3-group505-replay').absolutePath
        systemProperty 'd02.resources', rootProject.file('resources').absolutePath
        systemProperty 'd02.gadgetTable', rootProject.file('local/migration72/sources/AnimeGameData/d54f6f0d423ded9d055bb740a3e5f3aa8f2667f1/ExcelBinOutput/GadgetExcelConfigData.json').absolutePath
        environment 'LUNAGC_RESOURCES_DIR', rootProject.file('resources').absolutePath
        jvmArgs '-Xlog:class+init=info:file=' + rootProject.file('local/migration72/d02-r3-group505-replay/class-init.log').absolutePath.replace('\\', '/')
    }
}
"""
(w/'d02-test.init.gradle').write_text(init, encoding='utf8')
'@ | python -
$d02Jdk = Get-ChildItem local/runtime -Directory -Filter 'jdk-17*' | Select-Object -First 1
$env:JAVA_HOME = $d02Jdk.FullName
$env:GRADLE_USER_HOME = (Resolve-Path local/gradle).Path
$env:LUNAGC_RUNTIME = 'portable'
./gradlew.bat --no-daemon test --tests emu.grasscutter.scripts.D02Group505LoadTest -PskipHandbook=1 -I local/migration72/d02-r3-group505-replay/d02-test.init.gradle --console=plain
```

## 子合同 F：生产网格和出生点初始 suite

固定旧脚本、候选 Scene/Gadget 表、旧 GlobalCombat 与归档 JAR `eee183715cf27c5d2c25da0591c890f4c144116317ba0bf0afe8bb0030aeae0e`，在独立 JVM 调用真实 `SceneScriptManager.getGroupGrids` 和 `Scene.getPlayerActiveGroups`，位置 `(2747,194,-1719)`。fast 的实际配置和有效值均为 true，记录 449 个 Lua 加载错误；同输入仅改 inline 后错误为 0。两者均无业务边界调用，六层网格分别 28645/3103/167/6/2847/202 个格，规范化完整网格及出生点 25 组相等。首次 fast 实验未打开缺失脚本日志，零错误计数无效；上述结论采用打开日志后的重跑。两模式并不因此证明 Lua 绑定或运行行为等价。

对该 25 组另行使用真实 `SceneGroup.load` 检查可能初始 suite，16 组声明随机 suite；按现有生产 `findInitSuiteIndex(0)` 实际索引规则检查，31 个候选 region 在出生坐标的 `contains` 均为 false。初始 suite 包含 GROUP_LOAD 的为 505，包含 GADGET_CREATE 的为 505 和 601；这只是静态候选，真实事件见 E 和 H。该检查没有派发 region 事件、执行场景 tick、读取存档或添加任务动态组。原始报告与重复报告位于忽略的 `local/migration72/d02-r3-real-grid-{fast-observe,inline}/` 和 `local/migration72/d02-r3-suite-v3{,-repeat}/`；脱敏摘要见 `protocol/cn-7.2/d02-resource-observations.json`。跟踪 helper 已按以下代码实跑，fast/inline 报告 SHA-256 分别为 `1100155338d25a14f9d676e9cf636537f558367407738dc954b44383299050f2` 和 `a7f5c875398d282791e68f9923864e42db4f9bfa7f56bcf18ff4a444e00262a5`；suite 报告 `03fdc36889737211bdbbb975eeb89a4ea6ca6c9af542eeca1f83fb74681a950d` 与先前重复一致。跟踪版新增组快照，故 fast/inline 报告 hash 与早期探针不同；该复现直接对照格数和组 ID，六层完整规范化网格的相等性来自早期探针的独立比较。

以下从已跟踪 Java 源编译并运行独立复现，先通过现有审计门禁校验脚本、资源和旧归档，再核对候选表 Git blob；每种模式新建目录。需已有对应候选文件、树证据与两个归档 JAR，输出记录到各自目录。运行前协调其他构建；本命令只用 javac，不启动服务器或数据库。

```powershell
@'
from pathlib import Path
import hashlib, json, subprocess, sys, zipfile
r=Path('.').resolve(); sys.path.insert(0,str(r/'tools'))
import audit_production_scene_load72 as g
from audit_resource72 import verify_tree, git_hash
jar=r/'local/migration72/builds/LunaGC-before-avatar-wire-62a86fb.jar'
old=r/'local/migration72/builds/LunaGC-before-player-data-7ca18e10.jar'
assert hashlib.sha256(jar.read_bytes()).hexdigest()=='eee183715cf27c5d2c25da0591c890f4c144116317ba0bf0afe8bb0030aeae0e'
review=dict(g.REVIEWED_SOURCES); review['src/main/java/emu/grasscutter/scripts/ScriptLoader.java']=g.REQUIRE_BINDINGS_LOADER_SHA256
g.verify_inputs(r,r/'resources',r/'local/migration72/evidence/scene-grid-inline-audit.json',old,runtime_sha256=g.REQUIRE_BINDINGS_RUNTIME_SHA256,reviewed_sources=review)
with zipfile.ZipFile(jar) as a, zipfile.ZipFile(old) as b:
 for name in ['emu/grasscutter/scripts/ScriptLoader.class','emu/grasscutter/scripts/SceneScriptManager.class','emu/grasscutter/scripts/data/SceneGroup.class','emu/grasscutter/data/server/Grid.class','emu/grasscutter/scripts/SceneIndexManager.class','emu/grasscutter/game/world/Scene.class']: assert a.read(name)==b.read(name)
src=r/'local/migration72/sources/AnimeGameData/d54f6f0d423ded9d055bb740a3e5f3aa8f2667f1/ExcelBinOutput'
entries=verify_tree(json.loads((r/'local/migration72/evidence/resource-source-ExcelBinOutput-tree.json').read_text(encoding='utf8')))
for name in ['GadgetExcelConfigData.json','SceneExcelConfigData.json']:
 raw=(src/name).read_bytes(); assert len(raw)==entries[name]['size'] and git_hash('blob',raw)==entries[name]['sha']
combat=r/'resources/BinOutput/Common/ConfigGlobalCombat.json'
assert hashlib.sha256(combat.read_bytes()).hexdigest()=='df4b1d9dce5756251195995a4c30ae40f66c792735bb0977d166dd988f9965c0'
jdk=next((r/'local/runtime').glob('jdk-17*')); out=r/'local/migration72/d02-r3-grid-replay'; out.mkdir(exist_ok=False); classes=out/'classes'; classes.mkdir()
sources=['Client72SceneScriptAudit.java','Client72SceneSerializeAudit.java','Client72ProductionSceneLoadAudit.java','Client72D02ResourceAudit.java']
subprocess.run([str(jdk/'bin/javac.exe'),'-encoding','UTF-8','-cp',str(jar),'-d',str(classes),*[str(r/'tools'/s) for s in sources]],check=True)
rootcfg=json.loads((r/'config.json').read_text(encoding='utf8')); game=rootcfg['server']['game']; assert game['gameOptions']['isPreventEntityError'] is True
for mode in ['fast','inline','suite']:
 w=out/mode; w.mkdir(); cfg={'version':14,'folderStructure':{'resources':str(r/'resources'),'scripts':str(r/'resources/Scripts'),**{x:str(w/x) for x in ['data','plugins','cache','packets']}},'server':{'fastRequire':mode!='inline','game':{'enableScriptInBigWorld':True,'visionOptions':game['visionOptions'],'gameOptions':{'isPreventEntityError':True}}}}
 (w/'config.json').write_text(json.dumps(cfg),encoding='utf8'); result=w/'result.json'
 main='Client72D02SuiteAudit' if mode=='suite' else 'Client72D02RealGridAudit'
 args=[str(result)] if mode=='suite' else [str(combat),str(src/'GadgetExcelConfigData.json'),str(src/'SceneExcelConfigData.json'),str(result)]
 p=subprocess.run([str(jdk/'bin/java.exe'),'-Xmx2g','-Dfile.encoding=UTF-8','-Xlog:class+init=info:file=class-init.log','-cp',str(classes)+';'+str(jar),main,*args],cwd=w,capture_output=True)
 (w/'stdout.log').write_bytes(p.stdout); (w/'stderr.log').write_bytes(p.stderr); p.check_returncode()
 lines=(w/'class-init.log').read_text(encoding='utf8').splitlines()
 assert not any("Initializing '"+x in line for x in ['emu/grasscutter/database/','emu/grasscutter/server/http/HttpServer','emu/grasscutter/server/dispatch/DispatchServer'] for line in lines)
 data=json.loads(result.read_text(encoding='utf8')); print(mode,'errors',len(data['errors']),'attempts',len(data['businessAttempts']),'sha256',hashlib.sha256(result.read_bytes()).hexdigest())
fast=json.loads((out/'fast/result.json').read_text(encoding='utf8')); inline=json.loads((out/'inline/result.json').read_text(encoding='utf8'))
assert fast['groupIds']==inline['groupIds'] and fast['gridCells']==inline['gridCells']
print('selected groups and grid counts agree; compare normalized full maps using the recorded summary')
'@ | python -
```

## 子合同 G：受控主角能力定义引用

`tools/Client72D02AbilityAudit.java` 固定候选 8 个 BinOutput 文件、3 个 loader 相关源码 hash 与归档 JAR hash，隔离调用真实 `loadAbilityEmbryos` 和 `loadAbilityModifiers`。PlayerBoy、PlayerGirl 的受控基础及元素能力名称共 28 次引用均找到定义；两个新 JVM 的正常报告逐字节一致，SHA-256 `75bd293d69a559a55ecc9c288958e26a801ca3be441acadad4a7d18d628a1b1a`。反例仅不加载 Wind 定义文件，准确报告 9 个缺失引用，报告 SHA-256 `dac698ee78119ba8e14d11d3e95f7da7a2baf222fa8a43abfb0cec59861ec5c2`。原始证据为 `local/migration72/d02-r3-ability-contract-{first,repeat,missing}/`，class-init 未见数据库、服务或 ScriptLoader 初始化。本实验没有证明客户端能力语义、运行版本或完整资源闭包；候选 group 不能直接整体覆盖旧资源。

`missing-wind` 是审计预期反例：该模式成功识别 9 项缺失后正常返回 0，并非资源完整。早期另一个故意使用非法 group 名称的探针以非零退出，两者不是同一测试或退出合同。

下列复现使用源码内置的输入 hash 门禁、独立目录和归档 JAR；候选文件须已放在 `local/migration72/d02-r3-candidate/BinOutput`，不得使用根 JAR 替换固定归档。

```powershell
@'
from pathlib import Path
import subprocess,json,hashlib
r=Path('.').resolve(); jar=r/'local/migration72/builds/LunaGC-before-avatar-wire-62a86fb.jar'
jdk=next((r/'local/runtime').glob('jdk-17*')); bin=r/'local/migration72/d02-r3-candidate/BinOutput'
out=r/'local/migration72/d02-r3-ability-replay'; out.mkdir(exist_ok=False); classes=out/'classes'; classes.mkdir()
subprocess.run([str(jdk/'bin/javac.exe'),'-encoding','UTF-8','-cp',str(jar),'-d',str(classes),str(r/'tools/Client72D02AbilityAudit.java')],check=True)
for label,mode in [('first','normal'),('repeat','normal'),('missing','missing-wind')]:
 w=out/label; w.mkdir(); (w/'resources/BinOutput').mkdir(parents=True); (w/'resources/ExcelBinOutput').mkdir(parents=True)
 (w/'config.json').write_text(json.dumps({'version':14,'folderStructure':{x:str(w/x) for x in ['resources','scripts','data','plugins','cache','packets']}}),encoding='utf8')
 result=w/'result.json'; p=subprocess.run([str(jdk/'bin/java.exe'),'-Dfile.encoding=UTF-8','-Xlog:class+init=info:file=class-init.log','-cp',str(classes)+';'+str(jar),'Client72D02AbilityAudit',str(r),str(bin),mode,str(result)],cwd=w,capture_output=True,timeout=60)
 (w/'stdout.log').write_bytes(p.stdout); (w/'stderr.log').write_bytes(p.stderr); p.check_returncode()
 lines=(w/'class-init.log').read_text(encoding='utf8').splitlines()
 assert not any("Initializing '"+x in line for x in ['emu/grasscutter/database/','emu/grasscutter/server/game/GameServer','emu/grasscutter/server/http/HttpServer','emu/grasscutter/server/dispatch/DispatchServer','emu/grasscutter/scripts/ScriptLoader'] for line in lines)
 data=json.loads(result.read_text(encoding='utf8')); print(label,'checked',data['checked'],'missing',len(data['missing']),'sha256',hashlib.sha256(result.read_bytes()).hexdigest())
assert (out/'first/result.json').read_bytes()==(out/'repeat/result.json').read_bytes()
'@ | python -
```

## 子合同 H：601 组装置创建事件

`src/test/java/emu/grasscutter/scripts/D02Group601CreateTest.java` 在独立 JVM 使用真实 Scene、组、实体、ScriptLoader、事件派发和 ScriptLib，执行初始 suite 的 EVENT_GADGET_CREATE。2 项通过、失败/错误/跳过均为 0：Default 状态的 70510013 真实触发 `SetWorktopOptionsByGroupId`，产生 option 173；另一组同 configId 的反事实装置未受影响。受控非 Default 条件不改变选项也不广播。测试捕获真实 `PacketWorktopOptionNotify` 的构造及场景广播边界，当前生产包号为 799，payload 所见为实体 ID 和 option 173；**该号和字段尚未验证为国服 7.2 wire**，测试未连接客户端或发送网络包。

本次测试没有启动 GameServer、HTTP 或数据库；资源和缓存属于独立 JVM，GameData、runMode、脚本开关在结束时恢复。固定 group Lua SHA-256 `f7c117fa210345e73ffab39d3225f740fcedf27f8dd9beaf4e0db2191e1abc0e`，候选 Gadget 表和旧 GlobalCombat hash 与 E 相同。测试源码 SHA-256 `259c236daf5cd153eb56bfcf1cb8cb6de5fd3943bf21b0e31429578d4b1c723b`，class `b63fa8d7474e4a115323de035558af63bc1a1d9237baa2f1d93efe87bb06a27d`；原报告、日志和 class-init 证据在 `local/migration72/d02-r3-group601-test/`。未覆盖完整 scene tick、实际存档或客户端。

复现前须与其他构建串行。以下生成独立配置、数据和缓存目录，只执行该测试；测试本身核对输入 hash。

```powershell
@'
from pathlib import Path
import json
r=Path('.').resolve(); w=r/'local/migration72/d02-r3-group601-replay'; w.mkdir(parents=True,exist_ok=False)
cfg={'version':14,'folderStructure':{'resources':str(r/'resources'),'scripts':str(r/'resources/Scripts'),**{x:str(w/x) for x in ['data','plugins','cache','packets']}},'server':{'fastRequire':True}}
(w/'config.json').write_text(json.dumps(cfg),encoding='utf8')
init=r"""allprojects {
 tasks.withType(Test).configureEach {
  workingDir rootProject.file('local/migration72/d02-r3-group601-replay')
  systemProperty 'user.dir', rootProject.file('local/migration72/d02-r3-group601-replay').absolutePath
  systemProperty 'd02.resources', rootProject.file('resources').absolutePath
  systemProperty 'd02.gadgetTable', rootProject.file('local/migration72/sources/AnimeGameData/d54f6f0d423ded9d055bb740a3e5f3aa8f2667f1/ExcelBinOutput/GadgetExcelConfigData.json').absolutePath
  environment 'LUNAGC_RESOURCES_DIR', rootProject.file('resources').absolutePath
  jvmArgs '-Xlog:class+init=info:file=' + rootProject.file('local/migration72/d02-r3-group601-replay/class-init.log').absolutePath.replace('\\', '/')
 }
}
"""
(w/'d02-test.init.gradle').write_text(init,encoding='utf8')
'@ | python -
$d02Jdk = Get-ChildItem local/runtime -Directory -Filter 'jdk-17*' | Select-Object -First 1
$env:JAVA_HOME = $d02Jdk.FullName
$env:GRADLE_USER_HOME = (Resolve-Path local/gradle).Path
$env:LUNAGC_RUNTIME = 'portable'
./gradlew.bat --no-daemon test --tests emu.grasscutter.scripts.D02Group601CreateTest -PskipHandbook=1 -I local/migration72/d02-r3-group601-replay/d02-test.init.gradle --console=plain
```

## 子合同 I：每日委派持久化及动态组边界

`src/test/java/emu/grasscutter/game/dailytask/D02DailyRuntimeMongoTest.java` 使用真实 `DailyTaskManager.onPlayerLogin`、独立 MongoDB 和真实保存及同 UID 新 Player 冷读。第十轮是 **1 项测试**通过（失败、错误、跳过均为 0），不是十项测试：受控 level 1、新库和两个分别初始化的非持久 scene3 夹具中，daily 尚未生成时 `loadActiveGroups` 返回 0；生成 4 条后冷读、同日再次登录不重抽，第二场景的调用返回 4，四个实际初始 suite 的 14 个 monster config 均查到实体。那次随机结果任务为 `32290/32314/32272/32282`，组为 `133302164/133301195/133315145/133303185`，不是固定结果。第九轮同库另一次结果 4 组 11 个 monster config；所有原始结果均位于 `local/migration72/d02-r3-daily-runtime-fcce07a744a148689663eb146307e1cd/`。当次只断言按 config 查询到非空实体；后续扩展的类型和 ID 断言见 J，不据此声称初始组完整可用。

输入是混合夹具：当前旧 producer 的 DailyTask、DailyTaskLevel、DailyTaskReward 表、scene3 Lua 和 GlobalCombat，加上固定候选 commit `d54f6f0d423ded9d055bb740a3e5f3aa8f2667f1` 的 Monster 与 MonsterCurve 表。目标 Monster 和 Curve 的 Git blob 分别为 `0d2d5eb481738cf185d59bd707a33006f020645e`、`eac2a23c119e7e327044192a11089c561f58144f`，SHA-256 分别为 `b0efcdb6bca52eca8c2b89fe2e43f8f5a65bc1596fd250e7ddc375acd92989cc`、`3014983fd6292b612c040fb4106d6bfe10aef2da0ceffcba01bd10872b832f84`。旧 DailyTask 表 SHA-256 `44e9de619028ac8fa2f329b565486de6df020d9aae9f2ec61703023e68687048`，旧 GlobalCombat 见 E；I 阶段测试对这四项 hash 有断言，J 另加 Gadget。测试执行当前源码 classes，根 JAR SHA-256 `dfdebbda0352055540c44a54e2eebfd4ffdfe679db74270044d3d55aeb006ec3` 用于独立 Mongo 正常关闭辅助程序，并非测试类来源。

首轮直接执行完整 Player 属性初始化，因未提供网络 session 而失败；第二轮真实生成并保存后在 Packet 发送边界因空 session 失败；第三轮对已绑定 manager 调用加载，未产生新对象，冷读断言失败；第五轮真实场景缺 GlobalCombat；第七轮未加载 Monster 表，组加载返回 4 但首个怪物实体为空。均保留原日志。加入真实缺失资源后才进行相应断言；第四、第九、第十轮分别通过不同范围。测试用 Player 子类只记录 `PacketDailyTaskDataNotify` 的构造及交付边界，没有向网络发送，也未断言目标协议 wire。隔离 Mongo 用专属目录和 UUID 库，测试结束保留，真实 admin shutdown 后日志显示 exitCode 0；原 27017 未操作。

此合同用两个受控场景模拟 daily 生成前后调用，并未运行 `Player.loadFromDatabase` 的后台线程、完整 EnterSceneDone handler、实际账号或客户端；动态组的事件后代、网络通知和其他随机组均未闭合。运行前需要协调独占 Gradle 和 27027，准备固定候选资源、便携 JDK17/Mongo 和上述 hash 的根 JAR。下面脚本创建唯一目录及数据库、保留测试结果和数据库，并只在核对监听 PID/目录归属后对其发正常 shutdown；已按 J 完整实跑。

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File tools/audit_d02_daily_runtime72.ps1
```

## 子合同 J：实际随机组的初始实体身份

在 I 的基础上通过真实 `ResourceLoader` 增加候选 Gadget 表，并按每个实际随机组的 active suite 逐 config 断言 `EntityMonster` / `EntityGadget` 类型及其资源 ID；同时断言 suite 中声明的怪物、装置 config 均解析出来。一次新的唯一测试库运行，仍为 **1 项测试**、0 失败/错误/跳过：任务 `15207/15600/15302/15601` 对应 `133101104/133104406/133103292/133102555`，4 组初始 suite 共 **23 个怪物及 41 个装置**逐一通过类型、ID 断言，4 组 NPC 列表均空。实际组由随机选择决定；此结果不涵盖其他组或玩家保存状态。测试继续验证真实保存、冷读和同日不重抽，以及两个受控已初始化场景在生成前/后的 `loadActiveGroups` 返回 0/4。

固定候选 Gadget SHA-256 `5debe3c457b0fddc5264620aff1a9585cb19f24408a7f1974291244415ee1411`，Monster/Curve 和旧 daily/combat 见 I。旧 DailyTaskLevel 和 DailyTaskReward 的 SHA-256 为 `1eabd15e6d0bf810c97eef57cb3b24067fb19dd1a78112702380a1d573503964`、`3e8fd31291d833ab39f2069ea21539c9d1b04ace80e50fb3762cb603f74935f9`；旧 scene3.lua 为 `d547a2ebeb1292358685d8f317bf6b5676a53327e42aa618f18ef361cd32a9f9`。四个实际组 Lua SHA-256 依上述组顺序分别为 `ab9a974e8321e3e759a49110469f25d7edfcc305c1c6403ae6c80fa7933ff26a`、`ffe23d8733f075c4ce319327f973c3aa8bd0ffa144a9faa8398c22bd7170c08a`、`0d3e2dfa0555c72816c8da7d68f7a298923a617002276a1372142e2196897915`、`97d931df6d37ff2054f5d6612bcab434d8f013b2fe1f105e8e6a69d365f6cef8`。这些是本次实际输入指纹；可复现脚本不强制抽到相同组。

当前测试源码 SHA-256 `4d0df007a8f8f5231241f3081c0a9f6383b53b4bb32173ad4b3505ba2107bdc8`，class `9ac082bce062c611a9e5ec0a9157a8e6fd035c2251ee7824bfbce92077c3958c`，XML `382aa9d10eafcc9a37f372b59f14115dc3784232eebf6512ccea81760f9a26d0`。原证据及完整输入指纹在 `local/migration72/d02-r3-daily-replay-a7bd7db7bca549e19bdc39cd457a90e9/`。驱动第一次在 Windows PowerShell 将编译器 stderr 提示当异常提前停止，Mongo 正常关闭；修正原生命令提示处理后新库运行返回 0、XML 确认 1 项未跳过，Mongo 正常 shutdown exitCode 0、27027 释放，原 27017 保持。首次失败保存在 `local/migration72/d02-r3-daily-replay-09a47bdb6295456992bc4b4a5131fe2f/`。

主线审核了最终测试源码、实际编译class、该次新XML及逐实体输出，独立重算13份输入指纹全部相同，并核Mongo最后的正常退出记录；未重复执行随机测试。审核证明在`local/migration72/evidence/d02-r3-daily-main-review.json`，原XML为1项通过，不能将I历史轮次或主线审核再计为新增测试。

四组初始 trigger 均有 ANY_MONSTER_DIE，其中组 `133104406` 另有 ANY_MONSTER_LIVE；后者由怪物 tick 发出，测试未执行 tick。场景装入会提交 GROUP_REFRESH/GROUP_LOAD，真实装置 onCreate 会提交 GADGET_CREATE；这些异步事件及后代未等待或断言，不能由实体通过推断 action 执行成功。实际 Player 后台调度、完整 Done、战斗死亡、协议网络和客户端仍未验证；R3/resourceReady 未完成。

## 子合同 K：出生点生产选组、初始实体与 601 事件

`src/test/java/emu/grasscutter/scripts/D02BirthInitialEntitiesTest.java` 在独立 JVM、空缓存、非持久 scene3 和 `(2747,194,-1719)` 使用真实 `getGroupGrids → getPlayerActiveGroups → Scene.checkGroups → onLoadGroup → refreshGroup/addGroupSuite`。未启动服务器或数据库，网络广播由测试场景记录构造边界。读取旧脚本、旧 GlobalCombat/DropTable，以及候选 commit `d54f6f0d423ded9d055bb740a3e5f3aa8f2667f1` 的 Monster、MonsterCurve、Gadget、Scene、Gather 表；Gather 表为本实验发现的真实依赖：`GadgetGatherPoint` 按 pointType 查询该表，再创建其 gadgetId 子实体。目标 Gather 的 tree blob `d546754989c48c64770f9ddc338c25237ee23a63`，SHA-256 `c805f2ebde4bdd37de5ecd612e8ddf4e30f193f78876b35fdd30790bbb81cbb0`；候选 Scene 表 SHA-256 `5a906393d1de46a6d397ae169cdcfb76a9ee77fc1c9e38dd3aec752656978550`，其余固定输入见 E/I/J。

按根配置 `fastRequire=true` 的第三轮，生产入口实际选择 25 组，初始实体断言全部执行到末尾，601 装置真实 GADGET_CREATE 后在限时内观察到 option 173；但全场网格加载实际记录 449 个 Lua ERROR，最后错误断言失败，**1 项测试失败**。449 条涉及 27 个不同脚本；25 个选中组自身 Lua 文件没有字面直接 require 这些脚本，这不能排除传递调用，也不能把所有错误视为已解决。第一轮隔离 Player 缺 TeamManager，第二轮夹具缺 Gather 表，首失败均留存；第三轮的原 XML、日志和隔离目录为 `local/migration72/d02-r3-birth-initial-a5be3779f81a4bb598defdc08c7e8597/`。

仅改变隔离配置为 `fastRequire=false`、使用新 JVM 和空缓存的 inline 对照，真实入口同样选择 25 组，**1 项通过、失败/错误/跳过为 0，记录加载错误为 0**。实际选中 suite 由生产随机逻辑决定：本轮 429 组为 suite 2，其余 24 组为 suite 1；这些选择恰与上述 fast 样本一致，不代表其他运行一定相同。测试逐组断言 active suite 声明的怪物和装置 config 均解析，并对每个 config 查询到的实体断言类型与资源 ID；将实际 suite ID 与先前 25 组 Lua 报告独立交叉统计，声明数为 **52 个怪物、299 个装置**。该数是事后交叉统计，本轮 XML 逐组打印 suite 而非逐实体列表；没有涵盖其他候选 suite。25 组 NPC 列表均空。601 的 option 173 是本轮实际异步结果的完成信号；505 的 GROUP_LOAD 在选中 suite 中，但本轮没有等待其专属完成信号，其受控事件合同见 E。GROUP_REFRESH、GROUP_LOAD、GADGET_CREATE 及其他后代不因 601 完成就被视作全部完成；未执行 region tick、主动战斗或客户端。

inline 实际 source SHA-256 `7249713e3df03ddb1c74511340c86e5bc1f0e30c5d45b585ae0c37983c98a790`，class `852d5eaaa715de2f9169daf8754b326485571002e563c4b1eb7767aab6b90140`，XML `f2c17cd6c5af2953dd8a8dc705924b37e22aa7d35ed7f2625d504f9987472ab2`；完整输入及 25 组 Lua 的本机 hash 清单位于 `local/migration72/d02-r3-birth-inline-86563b38420d4457b5a25ff91e4a1eb1/input-sha256.json`，该文件 SHA-256 `0f72cd2d52f6092f76caf8b894ac3ec4ee3fc7ca002f138faadfa0b63319ae3b`。class-init 未发现 database、HTTP 或 DispatchServer 初始化。此合同不证明根 fast 配置通过、生产完整运行调度、网络 wire、版本 revision 或客户端入场；不得标记 resourceReady。

主线审核了源码、实际class及inline/fast两份XML，独立重算该清单的35份输入hash相同；另将`selected-suite-reconstructed-references.json`的351条config/资源ID逐条与固定Lua核同，并与XML中的25个实际suite对应。重建清单不是直接运行轨迹，真实逐实体断言依据执行的测试循环；没有重复运行本测试。主线证明在`local/migration72/evidence/d02-r3-birth-main-review.json`。原根配置和已有资源差异仍保持。

以下复现 inline 定向测试，需先协调独占 Gradle，准备上述固定候选表。脚本从根配置读取 vision 参数，创建独立 config/data/cache；不启用 Mongo，也不修改根配置。实际使用的 fast 开关仅在这个独立 config 中设为 false。

```powershell
@'
from pathlib import Path
import json,uuid
r=Path('.').resolve(); w=r/'local/migration72'/('d02-r3-birth-inline-replay-'+uuid.uuid4().hex); w.mkdir()
game=json.loads((r/'config.json').read_text(encoding='utf8'))['server']['game']; assert game['gameOptions']['isPreventEntityError'] is True
cfg={'version':14,'folderStructure':{'resources':str(r/'resources'),'scripts':str(r/'resources/Scripts'),**{x:str(w/x) for x in ['data','plugins','cache','packets']}},'server':{'fastRequire':False,'game':{'enableScriptInBigWorld':True,'visionOptions':game['visionOptions'],'gameOptions':{'isPreventEntityError':True}}}}
(w/'config.json').write_text(json.dumps(cfg),encoding='utf8'); t=str(w).replace('\\','/')
init='''allprojects { tasks.withType(Test).configureEach {\n workingDir file("%s")\n systemProperty "user.dir", file("%s").absolutePath\n systemProperty "d02.resources", rootProject.file("resources").absolutePath\n systemProperty "d02.excel", rootProject.file("local/migration72/sources/AnimeGameData/d54f6f0d423ded9d055bb740a3e5f3aa8f2667f1/ExcelBinOutput").absolutePath\n environment "LUNAGC_RESOURCES_DIR", rootProject.file("resources").absolutePath\n jvmArgs "-Xlog:class+init=info:file=%s/class-init.log"\n} }\n'''%(t,t,t)
(w/'birth.init.gradle').write_text(init,encoding='utf8'); (r/'local/migration72/d02-r3-birth-replay-path.txt').write_text(str(w),encoding='utf8'); print(w)
'@ | python -
$d02Work = [IO.File]::ReadAllText((Resolve-Path 'local/migration72/d02-r3-birth-replay-path.txt'))
$env:JAVA_HOME = (Get-ChildItem local/runtime -Directory -Filter 'jdk-17*' | Select-Object -First 1).FullName
$env:GRADLE_USER_HOME = (Resolve-Path local/gradle).Path
$env:LUNAGC_RUNTIME = 'portable'
./gradlew.bat --no-daemon test --tests emu.grasscutter.scripts.D02BirthInitialEntitiesTest -PskipHandbook=1 -I (Join-Path $d02Work 'birth.init.gradle') --console=plain
```
