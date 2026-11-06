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
