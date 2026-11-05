# E-01c4：继承脚本的场景owner UID只读查询

2026-11-06。承接[固定API来源清单](CLIENT_72_SCENE_SCRIPT_API.md)，本批只迁移`GetSceneOwnerUid`的正常只读契约。**这是本项目对继承脚本的适配，不是独立恢复的7.2原生业务契约；E-01c、E-01和D-02仍未完成。** 没有实现GetGadgetConfigId或猜测缺owner的返回码。

## 来源与唯一生产修改

固定manifest中42处调用、317个静态组均只传Lua context，没有其他业务参数。Common/V3_0/HookLook.lua:143-145把返回值直接作为GetAvatarEntityIdByUid的UID入参。既有Scene.getHost转到World.host，Player.getUid明确返回玩家ID；与场景玩家列表首项、当前触发玩家、当前实体所属玩家是不同来源。

ScriptLib仅增加一个无参int方法：

```java
public int GetSceneOwnerUid() {
    return getSceneScriptManager().getScene().getHost().getUid();
}
```

它复用既有线程上下文中的SceneScriptManager，通过当前Scene取真正World host；不创建玩家、查询存档、修改世界或发送通知。Lua的context仍由既有userdata桥接作为Java receiver，不把context.uid当owner。没有新增低概率判空/竞态分支、缓存、默认成功或缺owner返回值；无效receiver继续由原LuaJ反射桥接拒绝。

## 实际红测到绿测

新增`src/test/java/emu/grasscutter/scripts/SceneOwnerUidLuaTest.java`。先保持生产源码不变，实际执行9次测试：**4次失败**，3个owner UID样本和后续avatar查询均报`attempt to call nil`；4种错误receiver及一个未知成员拒绝测试通过。收到主任务生产写入放行后才增加上述方法；相同9次测试全部通过，测试格式整理后再跑9次也全部通过。

测试实际构造ScriptLib与ScriptLibUserdata，通过LuaJ运行以下两种真实桥接形式：全局ScriptLib.GetSceneOwnerUid(context)，以及event context成员转发。context由生产createEventContext生成，metadata.uid为guest 27；library.currentEntity也是guest avatar。场景玩家列表故意把guest放第一，owner放第二，另有玩家28。

- owner UID分别为1、10001、Integer.MAX_VALUE，实际Lua int返回完整保留，均不等于当前guest UID。
- 同一context把返回UID传给真实GetAvatarEntityIdByUid，找到owner avatar 7777；传context.uid则找到guest avatar 7001，验证后续消费没有混淆身份。
- nil、普通table、number、string作为receiver均沿用LuaError拒绝，并与既有GetAvatarEntityIdByUid桥接作对照；未知成员保持缺方法错误，没有placeholder。

Scene、World、Player、Avatar、EntityAvatar和manager采用已有测试先例中的Unsafe纯对象装配，跳过构造器；没有new GameServer、创建真实世界/玩家或接触存档，也不执行trigger/action、场景加载线程或数据库操作。真实ScriptLib静态logger会初始化Grasscutter的配置/默认目录检查，因此聚焦Test JVM的workingDir与user.dir均定向到本批忽略目录，读取独立最小配置，resources只读指向当前checkout。测试额外断言实际读取的data配置为该夹具的`./data/`，与根配置的运行数据路径不同；JVM属性日志保留工作目录证明。

## 隔离复现

在仓库根目录生成本任务专属忽略夹具，不能把下面的配置写到根config.json：

```powershell
$ownerWork = Join-Path $PWD 'local/migration72/scene-owner-uid-test'
New-Item -ItemType Directory -Force $ownerWork | Out-Null
$ownerConfig = @{version=14;folderStructure=@{resources=(Join-Path $PWD 'resources').Replace('\','/');data='./data/'}}
[IO.File]::WriteAllText((Join-Path $ownerWork 'config.json'), ($ownerConfig | ConvertTo-Json -Depth 5), [Text.UTF8Encoding]::new($false))
$ownerInit = @'
allprojects {
    tasks.withType(Test).configureEach {
        workingDir = rootProject.file('local/migration72/scene-owner-uid-test')
        systemProperty 'user.dir', rootProject.file('local/migration72/scene-owner-uid-test').absolutePath
        systemProperty 'sceneOwnerUid.expectedData', './data/'
        environment 'LUNAGC_RESOURCES_DIR', rootProject.file('resources').absolutePath
        jvmArgs '-XshowSettings:properties'
    }
}
'@
[IO.File]::WriteAllText((Join-Path $PWD 'local/migration72/evidence/scene-owner-uid-test.init.gradle'), $ownerInit, [Text.UTF8Encoding]::new($false))
$env:LUNAGC_RUNTIME = 'portable'
$env:JAVA_HOME = (Get-ChildItem local/runtime -Directory -Filter 'jdk-17*' | Select-Object -First 1).FullName
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
$env:GRADLE_USER_HOME = Join-Path $PWD 'local/gradle'
./gradlew.bat --no-daemon test --tests emu.grasscutter.scripts.SceneOwnerUidLuaTest -I local/migration72/evidence/scene-owner-uid-test.init.gradle -PskipHandbook=1 --console=plain
```

红测证据为`logs/scene-owner-uid-before-verified-directory.log`与`evidence/scene-owner-uid-before-junit.xml`，初次红测原日志/XML也保留；绿测为`scene-owner-uid-after.log`及`scene-owner-uid-after-junit.xml`，最终重跑为`scene-owner-uid-final.log`及`scene-owner-uid-final-junit.xml`。测试数量、XML和源码hash汇总在`evidence/scene-owner-uid-verification.json`。根运行配置未改，原数据、资源与缓存均未修改。

## 守卫、构建与验收缺口

旧API审计的verify_sources已实际以`Review changed API dependency: .../ScriptLib.java`拒绝新源码，原日志为`scene-owner-uid-old-api-guard-rejection.log`。没有更新任何旧hash、源码索引或观察摘要来让门禁变绿。ScriptLib属于协议依赖源码，主任务需人工复核新增只读边及受影响源码/方法hash，再重建JAR、复核脚本/API审计和完整研究选集。

本批聚焦Gradle已编译生产/测试源码，没有执行完整Java测试集，没有生成/替换运行JAR或便携部署。完整7.2协议准入未满足，未启动服务器、数据库或客户端；JAR构建、运行前置与未部署原因交由主任务继续核对。缺owner、无有效scene manager等非正常上下文没有新造返回契约；GetGadgetConfigId的错误语义及其余API/资源/实机缺口仍保持未知。

## 主线构建与证据整合

主线实际Windows portable构建成功（24秒），JAR SHA-256 `2566f46604df80ddaad05801fb182a7217b9d057a2c8619e21fc54e49db083fe`，BuildConfig内嵌69c3672；ScriptLib、BuildConfig和四序列化修复class与当次编译输出相同，原四class相对上一JAR字节未变。原4dd1c47a…bbe7 JAR仅保留历史离线证据。独立JDK源码比较确认删除唯一4行后与旧源完全相同，旧220方法/构造正文、签名/public及调用选择器不变，public206→207；原48源码图逐结构仅源hash/review变化，现有节点/边/候选及开放边界不变。

三个原始门禁都真实拒绝新ScriptLib，serialization另真实拒绝新JAR。人工复核后仅更新相应源/JAR及真实父report/summary hash，未改算法。新JAR三种脚本CLI重跑：出生686通过、inline13,616通过、fast407错误/8缺ID保持；当前API反射61中2wrapper已有、59未见入口，owner42处/317组从缺口移除，但完整无缺组只增1，不能称317组业务通过。生产序列化13,616组/20,871套件再双跑，所有数据/网格/8问题与前修复结果相同，仅JAR/tool指纹变化。

人工复核后研究输入60→62→63，唯一ScriptLib源码hash更新，全部三层/Proto/候选/开放边界不变，recover双跑一致；366研究回归全过。此前580项Java与本批隔离owner9项分开记录，未重复完整Java集。证据scene-owner-{impact,delivery,build-artifact}和scene-owner-list-login-proto-review-check.json均在local/migration72；没有连接正式存档或部署。P0/SDK/补丁/资源仍未齐，运行PID/健康/运行JAR无验收对象，D-02未完成。
