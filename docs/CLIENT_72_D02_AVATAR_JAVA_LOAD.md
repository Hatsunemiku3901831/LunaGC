# E-01 / R3-AVATAR-JAVA：已批准出生属性片段的真实加载测试

## 有界计划（2026-11-09）

原生 [Avatar entry](CLIENT_72_D02_AVATAR_ENTRY_RESOURCE.md) 工具、Python测试与first/second报告已经冻结。主线授权本后继只新增 `src/test/java/emu/grasscutter/data/D02BirthAvatarResourceContractTest.java` 与本页，私有准备放 `local/migration72/d02-resume-20261109/r3-java-avatar/`；不改旧工具/报告、生产Java/Proto/资源/补丁/配置，不执行Gradle/JVM/DB或现场操作。

固定本卡原生报告SHA `32cf0c85dd4df7976a78a33fca92464b6b6bdb01f1fd7e29dace9852de1c222e` 和Promote第三SHA `546b95ce69f5ace87761d2fe83c287da6a91ad9e9d19cbcb146147caed70fef1`，缺输入或hash漂移即fail。复用后者完整100条AvatarCurve输入，只将本卡确实新验的level1/20/100与Java结果对照，不推为100level均执行过新native helper。

四项限定测试：100 Curve实际JsonUtils/onLoad安装；两出生Avatar批准HP/攻击/防御/grow片段的真实loader和18个native值；missing binding行拒绝不丢另一正常行；99-level缺曲线的当前Java拒绝。`critical/icon/能力`等未知不填，不建完整Avatar/Player，不手设loadedAll，不将Java缺键零当target默认或完整运行许可。

按既有ResourceLoaderIsolation惯例使用临时JSON与protected `loadFromResource(Class,Path,map)`，由真实loader执行Gson→onLoad→getId→map.put。每测试独立备份/清空/恢复GameData Curve和Avatar maps；AvatarData读取的私有fetters缓存会lazy填充，临时换新cache并在结束恢复原引用，避免污染已有状态。其余只读map不修改。

停在源码审查和私有输入准备；测试编译/真实执行由主线审核并串行安排，不能将准备完成写成Java通过。

## 已交付的四项测试

| 测试 | 实际路径与断言 |
| --- | --- |
| `everyDeclaredCurveRowSurvivesActualJsonAndOnLoad` | 100个此前已验完整Curve输入→临时JSON→真实ResourceLoader/JsonUtils/Gson→AvatarCurveData.onLoad→实际GameData map。逐level、每行4个原有序元素/type/arith/float32与派生curve map核对；不声称100level的新native消费者都已执行。 |
| `birthHpAttackDefenseAndGrowthMatchTheEighteenActualNativeCases` | 两个批准字段片段经过同一真实loader和AvatarData.onLoad；恰5/7进入map。HP/攻击/防御在1/20/100三level的18个真实native结果及factor逐float32 bits相等；不读取/认可critical默认，不加入icon或ability。 |
| `missingGrowthBindingRejectsOnlyItsRowThroughTheRealLoader` | 受控删第一行propGrowCurves，真实loader报告1个拒绝/row1，另一正常出生行仍安装。没有旧map行残留充成功。 |
| `missingMiddleCurveDoesNotBecomeAValidAvatarByGetterFallback` | 受控移除level50而保留100，使当前onLoad按size99分配的数组在level100越界；真实loader报告2个Avatar拒绝且Avatar map为空。它只验证当前Java行为，不是target客户端的缺curve处理许可。 |

每次@BeforeEach先保存两个map及原`fetters`cache引用，再清空Curve/Avatar maps并临时换新fetters cache；@AfterEach恢复原值/原cache引用和Curve原有序映射。`loadedAll`只读保存并在收尾断言未变。直接调用`loadFromResource(Class,Path,map)`，不会改ResourceLoader类加载缓存，也不调用loadAll或任何Manager/Player/DB。

输入入口只校验一次：system property必须存在，读取原bytes核SHA后对同一bytes调用JsonUtils.decode；没有网络/候选JSON/外部仓库输入，缺文件或hash错误直接失败，没有跳过分支。两个property为：

```text
-Dd02.avatar.resource=<checkout>/local/migration72/d02-resume-20261109/r3-avatar/first.json
-Dd02.avatar.promote=<checkout>/local/migration72/d02-resume-20261108/r3-promote/main-third.json
--select-class emu.grasscutter.data.D02BirthAvatarResourceContractTest
```

第一项可替换成本卡主线第三报告，但bytes必须匹配上表固定SHA。临时JSON和测试日志/XML应写入本后继私有目录或JUnit @TempDir，不写生产resources。主线可用已配置独立JUnit驱动编译后执行本类；本卡未运行或准备任何Gradle/DB进程。

## 准备状态与交付身份

`r3-java-avatar/init.private.py` 已实际以标准库校验两个报告和原生工具/测试的冻结SHA，核100个连续Curve、两条仅含五个批准canonical键的Avatar片段、18个实际native比较case及四个JUnit方法；生成`prepared.private.json`。该准备不是Java编译或JUnit执行。原生source、Python测试、first/second报告未变。

测试源最终13,691B，SHA-256 `06bc243cf7ff4499c44ab9bf19324406f661f48879b4bede0c68c43766555a9f`。本页及准备/源文件身份见本后继`delivery-manifest.private.json`。下节记录主线实际编译和运行，准备状态不代替该实测。

## 主线实际验收

主线审核测试、真实loader入口、全局map/cache恢复及输入后，以便携JDK17经Gradle实际编译/运行本类4项，并关联32项 `ResourceLoaderIsolationTest`；同一次36项失败/错误/跳过均0，23秒完成。两份真实XML及 `main-gradle.log` 保留在 `local/migration72/d02-resume-20261109/r3-java-avatar/`。负例的解析/拒绝堆栈是测试预期，实际XML为通过；没有删除日志或把它们并成完整Avatar成功。

```powershell
$env:D02_AVATAR_RESOURCE = (Resolve-Path 'local/migration72/d02-resume-20261109/r3-avatar/main-third.json').Path
$env:D02_AVATAR_PROMOTE = (Resolve-Path 'local/migration72/d02-resume-20261108/r3-promote/main-third.json').Path
./gradlew.bat --no-daemon test --tests emu.grasscutter.data.D02BirthAvatarResourceContractTest --tests emu.grasscutter.data.ResourceLoaderIsolationTest -PskipHandbook=1 -I local/migration72/d02-resume-20261109/r3-java-avatar/test-inputs.init.gradle --console=plain
```

100条曲线及两出生**批准片段**实际进入新空map，18个原生结果与Java getter的float32位一致；缺binding只拒一行，缺中间curve导致两行拒绝，loadedAll保持未变。critical/icon/能力省略并未被批准为完整默认。仅测试/文档变化，不需GS重建部署；完整Avatar、自然revision/loadAll/Manager及D-02仍未验。
