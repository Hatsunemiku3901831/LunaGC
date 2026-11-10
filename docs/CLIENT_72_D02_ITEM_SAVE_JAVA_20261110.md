# E-01/R3-ITEM-ENTRY：同源出生武器局部库存保存测试准备

主线已实际编译并通过IS-01/02/03三个真实Mongo测试。验收合同及三个固定输入的完整SHA-256见[规格](CLIENT_72_D02_ITEM_SAVE_SPEC_20261110.md)。范围仅为同源未装备Weapon11101经真实loader、普通保存队列与新Player空Avatar/Inventory冷恢复；客户端、完整Avatar/Player、角色装备及D-02未验。

## 主线实际验证

独立JDK17/version14 worker与新建dbpath、显式27027测试Mongo，实际Gradle offline/no-daemon/max-workers1退出0，26秒；XML三项、0失败/错误/跳过。测试源码SHA `6184ec62e492f3d1ecf40bfaf8218676901a5bc35a5295db6fdcfea89895f595`。实际根 `local/migration72/d02-resume-20261110/c-item-java/run-20261110T005820Z-9287f146/`；`main-run.private.json` SHA `8b6439d842d456d650e5755fcec80e65554bc9fcfcf148a23cf8bc66cbc20059`，主线12项独立原件/状态审核 `main-independent-review.private.json` SHA `6a1ac760952ffa8e76848984fc2b93ae1d662103d608fceae7735a635af94f10`。等价执行入口为本批 `run_item_java.py`，每次创建新run，不覆盖证据。

实际命令账：IS-01两次`insert:items`，冷读零写；新绑定对象普通保存一次`update:items`，exp17→47、locked true→false、revision1→2且token更新，另一武器BSON保持；第二次冷读零写且状态相同。IS-02缺定义时库存跳过但原BSON保持，同source重载后新Player自然恢复；IS-03其他UID库存为空，原owner仍恢复且两次读取零写。没有Player等额外业务写。原BSON快照只在忽略目录，文档不收录ObjectId/原始数据。

10215行class-init无GameServer/HTTP/dispatch/ScriptLoader/PluginManager初始化；三报告/13保护/原JAR保持，实际Item map/gameDatastore/loadedAll复原。测试Mongo已正常shutdown、helper与mongod均exit0、日志完成，相关进程/固定端口空，独立dbpath保留。只改测试/文档/私有driver，无需GS部署；R3与D-02父项不晋级。下述“准备/未运行”文字保留为Terra交付阶段的执行说明，以本节实际结果为准。

- 三报告读取一次原 bytes、核完整 SHA，唯一 Weapon/11101 项关联同一 source，并合并六键；从临时文件走真实 ResourceLoader/onLoad，重复加载获取不同定义对象。
- 每案例显式要求 `LUNAGC_TEST_MONGO_URI`，创建 UUID 库、自建客户端及只计该库业务写命令的 listener；生产 MapperOptions、真实 GameItem/Avatar 映射与真实异步 save/Guard，排空后读原 BSON。无 URI 是失败，不跳过、不默认连接。
- 两实例、冷 Guard 再保存、缺定义及恢复、另一 UID 隔离均经真实无参 Player 的空 AvatarStorage 再 Inventory 顺序。冷读阶段断言 BSON 完整相等和所有监听业务集合写计数不变；保存阶段仅允许 items 业务写。新 worker 的 Codex 武器索引须自然为空，否则失败，不填资源或代写 Player。
- 测试前后保存/恢复 Item map 与 gameDatastore，核 loadedAll 不变；仅 drop 本案例 UUID 库，关闭自建连接。临时 JSON 在 JUnit 临时目录；不改变原始报告、Proto、生产资源或任何存档。业务写命令的名称、集合与次数打印到私有 test 日志，不打印命令正文或 URI。

## 主线独立 worker 执行参数

实际测试 Mongo、测试 dbpath、隔离 CWD/版本 14 引导、JDK 17、Gradle 缓存、临时 logback 和输出目录由主线按本轮实际状态确认并准备。**首个 Grasscutter 类初始化前**完成主线已有的隔离引导；不能以这些指令替代进程、端口、数据库和原数据保护核对。下列环境变量只是本次 driver 向 forked test JVM 传参的示意；路径由主线填本机真实绝对路径，凭据与 BSON 仅放忽略目录。

```powershell
$env:LUNAGC_TEST_MONGO_URI = '<本轮独立测试 Mongo URI>'
$env:D02_EQUIPMENT_REPORT = '<本轮固定 equipment 报告绝对路径>'
$env:D02_AFFIX_REPORT = '<本轮固定 affix 报告绝对路径>'
$env:D02_GADGET_REPORT = '<本轮固定 gadget 报告绝对路径>'
$env:D02_ITEM_SAVE_EVIDENCE = '<本轮私有输出目录的 BSON 子目录>'
```

私有 init 脚本可在主线现有隔离引导中追加以下内容（必须在 fork 的 Test 任务上设置，不能仅设置 Gradle 守护进程 JVM 属性）：

```groovy
import org.gradle.api.tasks.testing.Test

gradle.projectsEvaluated {
    allprojects {
        tasks.withType(Test).configureEach {
            systemProperty 'd02.item.save.equipment', System.getenv('D02_EQUIPMENT_REPORT')
            systemProperty 'd02.item.save.affix', System.getenv('D02_AFFIX_REPORT')
            systemProperty 'd02.item.save.gadget', System.getenv('D02_GADGET_REPORT')
            systemProperty 'd02.item.save.evidence', System.getenv('D02_ITEM_SAVE_EVIDENCE')
        }
    }
}
```

```powershell
.\gradlew.bat --no-daemon --offline --max-workers=1 --console=plain -PskipHandbook=1 --init-script '<本轮隔离 item-save init 脚本>' test --tests emu.grasscutter.data.D02BirthWeaponInventoryMongoTest
```

`d02.item.save.evidence` 是可选的私有 BSON 输出属性，主线执行时应设置；每阶段 JSONL 含自建测试 ObjectId 和业务字段，只保存在忽略目录。主线同时保留输入身份、源与 init、完整测试日志/XML、class-init、每阶段 BSON 与写计数、保护检查和测试 Mongo 正常退出记录。XML 必须核实三项、0 失败、0 错误、0 跳过；只靠队列排空、静态审阅或编译不可验收。监听仅记录 insert/update/delete/findAndModify 的实际命令、集合及次数；setup/cleanup 的数据库 drop 不作业务写计算。

如果 loader、真实 Morphia、Player/空 Avatar、Codex、其他写入或初始化触发未批准依赖，保留首次失败并返回主线，不补空能力、手设 loaded、模拟保存或绕过 Guard。客户端、完整 Avatar/talents、角色装备、完整 Player/账号和退出重登均未验；仅测试与文档变化无需部署。此准备未运行构建、JVM、Mongo、服务或客户端，且尚未提交；提交及共享状态更新归主线。
