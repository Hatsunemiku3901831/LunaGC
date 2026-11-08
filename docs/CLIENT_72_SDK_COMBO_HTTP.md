# R2-COMBO-HTTP：原生请求与真实Java HTTP接收

## 实施前计划（2026-11-08）

目标是在独立测试库和临时HTTP端口中验证准确SDK combo外层字符串/内层data能被当前Java handler及真实默认认证链接收，认证结果和保存一致；将真实HTTP成功/错误响应在内存交准确原生响应消费者。该卡不代替自然SDK登录或D-02账号验收，不新增签名策略。

已有来源是[完整原生签名/发送前builder](CLIENT_72_SDK_COMBO_SIGNATURE.md)、[响应合同](CLIENT_72_SDK_RESPONSE_CONTRACT.md)、R2-LOG有效红绿与193项关联Mongo回归、R2-SESSION真实HTTP/Java→原生桥梁。当前DefaultAuthenticators按内层uid/token认证，成功生成并同步保存login token；sign没有被默认认证器消费。outer app/channel在原生是字符串，data保留JSON字符串及64位uid字符串，不能先变double或重序列化改签名字节。

范围只新增测试域Java HTTP/Mongo fixture、测试及供Python PIPE交互的test main，必要新`tools/verify_client72_combo_http.py`/test和本页。生产Java/既有测试/Proto、SDK frozen tools、patch、资源、配置和旧raw不改。复用现有Javalin/HttpClient/Morphia依赖，不添库。原始实验仅 `local/migration72/d02-resume-20261108/r2-combo-http/`，所有Gradle/Mongo/端口/Java main运行由主线统一。

真实Mongo使用新UUID测试DB与新runtime目录，主线按已有27027便携隔离方案启动并正常admin shutdown；固定8088与原27017不使用。Javalin只开loopback临时端口，真实AuthenticationHandler/default authenticator/Account保存方法执行；不要用save/token-generator成功桩替代这些行为。fixture可以用反射替换测试Datastore并在finally恢复，按现有Mongo回归惯例，账号/角色原数据库不动。

最小案例：受保护账号与准确native形状成功；错误/空token、未知uid认证失败不出combo_token且BSON不变、不创建账号；大于2^53的uid原样字符串、内层嵌套/非ASCII通过，不发生double往返。成功需HTTP200、retcode0、open_id归属正确、生成token与真实Mongo新值一致、sessionKey/账号数不被意外改变；重复SDK认证的既有token轮换语义保持，不能把session verify不轮换套到combo。只测有来源分支，不为未复现的外部攻击加入政策。

原生桥接复用原SignatureFixture/准确3BE5C0机器码并明确新的参数化owned输入，保留原发送call前停止与完整signer执行。Java fixture stdin/stdout和HTTP正文只经父Python内存PIPE，Javalin/日志输出另走stderr且不得含正文/凭据。真实Java HTTP response再交冻结原生combo parser/formatter；未闭完整callback时先交准确入口/停止，不补成功桩。所有报告只输出结构/长度/固定来源/状态/等值bool，不记录或hash请求/响应正文、token、签名输入或cipher；断言不用可打印敏感值的assertEquals。

验收为实际Java测试编译、独立Mongo真实HTTP正反例零skip、真实原生→Java→原生PIPE回放及固定输入/可重现公开报告，生产源码不变。首次失败按工具/fixture/输入/生产实现区分，原日志保留。若生产差异真实复现，先给最小红测/修复规格，不擅改认证/会话/保存或强化签名门。缺自然配置/route/客户端输入不阻断该独立边界验证，但不据此标本服登录成功。

## 已交实现及真实 Java 验证

只新增三个test-domain Java文件：`ComboHttpMongoHarness.java`共用真实库/HTTP生命周期，`ComboLoginRoutesMongoTest.java`提供6项HTTP断言，`ComboLoginNativeHttpFixture.java`提供UTF-8双向PIPE main。均位于`src/test/java/emu/grasscutter/server/http/dispatch/`。不覆盖既有Session只读Datastore夹具，不替换Account、随机token生成器、认证器或save；反射只替换测试Datastore，显式绑定真正`DefaultAuthentication`，并在finally恢复Datastore/runMode/auth/autoCreate。

主线实际串行`compileTestJava`与`d02RuntimeClasspath`成功，exit0/19秒；证明为本卡raw `compile-main1.log`、`compile-main1.private.json`。随后主线在新的`r2-combo-http-green-main1`测试runtime用独立27027执行本类**6项，failure/error/skip均0**。每项使用`lunagc_combo_http_`加UUID的新数据库；结束后UUID库全清，仅admin/config/local，正常admin shutdown/exit0、27027无监听，原27017未启动。这些是主线真实执行结果；本实现代理没有运行Gradle、Java、Mongo或端口。

六个案例为success、wrong_token、empty_token、missing_token、unknown_uid、repeat_success；JUnit最后一项先成功再重复成功，PIPE则按这六项固定顺序交互。受保护账号ID为准确字符串`9007199254741009`，未知ID为字符串`9223372036854775807`；内层还含嵌套非ASCII文本及`INT64_MAX`，外层app/channel/data均字符串。成功要求HTTP200/retcode0、open_id字符串精确、真实新token等于Mongo新值且不同于此前login token、sessionKey和玩家归属/账号数保持。失败要求-201、无combo_token、完整BSON等值及无建号。所有断言只有固定消息的布尔比较，没有可打印token/BSON/请求正文的assertEquals。

## 原生边界与新 owned 输入

新工具`tools/verify_client72_combo_http.py`延迟加载冻结`SignatureFixture`/`ResponseFixture`，不修改原签名/响应工具、九份Java快照或生产代码。先严格核五输入与签名卡固定来源、冻结签名工具SHA `cca96bb3a066c8e3322bd3a63ceed2a994627e714e6a6a85c145bb9afbe15f9e`、响应工具SHA `792dd8f1597b577673caac064cd3da1b3fcd13ca1e906eae382bde901b6628e5`和响应报告SHA `77ffd6e3c378000456608197cd8ca197a6183c9b4378198ae969cf48f0442992`。冻结Java epoch与当前源码是否一致另记布尔，不把旧快照冒充本次已编译实现。

新的参数化owned输入来自Java seed的测试UID、该UUID库随机session、实际loopback URL；config/app4/channel1/device/route与**非资源**测试key明确为自有输入。复用准确app_key writer、原字符串/map及完整signer，执行原`3BE5C0`入口至`3BEC2C`真实发送call前，真实`3BEA13→3958E0→3BEA16`返回。内层JSON仅作为原有C字符串，outer原生UTF-8字节与独立HMAC/oracle逐字节在内存比较。然后把原字节通过PIPE交Java，Java HTTP发送保持该字符串，不由Python重建请求。没有原生network send或完整builder返回。

真实HTTP response正文直接送RequestManager原始入口`39EB30`，不是重编码成JSON后的替代正文。准确来源为固定SDK `6D2218+8` vtable slot，其值确为`39EB30`；冻结`3CA000`完整代码窗SHA `70c360fcf4cc4e02f0275b634f50f2883b42c041c0accf84e1f65b2c3bf6288a`。原caller `3CA14F..3CA174`按Windows x64 ABI设置RCX=request、RDX=原response C-string、R8D=0、R9=status指针、stack+20=message std::string、stack+28=data CJsonObject。新桥梁完全保留这六参；成功data另交冻结已验`3C91A0`转换，核combo_id/open_id/combo_token/account_type/guest及channel_token等值。失败只保留原生状态并核无token，不把错误data送成功formatter。

此处`39EB30`与`3C91A0`是有来源的两个准确consumer调用；**完整`3CA000` UI/heartbeat/fatigue/外部callback及后续`3CFF40`未执行**。没有给UI或网络补返回/成功模型，也不把两个consumer返回当自然callback闭合。底层模型仍为冻结夹具的存储/单线程同步等已声明环境；遇到未建模边或原生差异立即失败，不退回Python自制成功结果。

## PIPE schema 与保密边界

Java main入口：`emu.grasscutter.server.http.dispatch.ComboLoginNativeHttpFixture --pipe`。必须由父Python带stdin/stdout/stderr三个`subprocess.PIPE`启动；不能单独在终端运行、重定向、tee、保存或hash管道。每行是UTF-8 JSON：

| 顺序 | 固定结构；敏感字段只在内存 |
| --- | --- |
| Java seed | schema=`combo-http-v1`、phase=`seed`、uid、session_token、url |
| Python request ×6 | 同schema、case（固定六顺序）、request_body（原生outer原字节的UTF-8字符串） |
| Java response ×6 | 同schema、phase=`response`、case、http_status、body（实际HTTP正文）、checks（固定布尔域）；只在成功另含expected_combo_token（Mongo值） |
| Java complete | 同schema、phase=`complete`、cases=6、uuidDatabaseDropped=true、httpStopped=true；在HTTP停止、UUID drop和全局恢复之后才输出 |

库/HTTP初始化输出转stderr并关闭main日志；stderr也只留父Python内存，结束核无已知会话/token/请求或响应正文。任何额外stdout行、乱序、缺失布尔见证、schema/身份/loopback端口错误都拒绝。父工具180秒等待上限，异常先关闭stdin给main正常finally清理；仅所建owned Java子进程超时才依次终止该子进程，不操作客户端或其它服务。未收到complete不能声称UUID库已清理。

公开报告只含固定文件/源码/class身份、状态、原生固定代码证据、布尔与case名，不包含正文/token/sign/签名输入或这些值的hash。数据库真实保存是本卡被测业务，不额外导出BSON或凭据。错误报告仅固定failureStage/type与`COMBO_HTTP_BRIDGE_FAILED`，不写异常message/cause或child streams；Java main只给固定`COMBO_HTTP_FIXTURE_FAILED`。报告的naturalSdkLoginVerified/fullComboCallbackExecuted/D-02均false。

## Python 测试、身份与主线执行

`python -m unittest tools.test_verify_client72_combo_http -v`：16项纯Python/mock通过，无Java、Mongo、HTTP、原生模拟执行或DLL加载。覆盖六帧顺序/原字节、UID字符串与临时loopback限制、真实DB见证缺失/失败、失败无token、stdout只PIPE且错误不泄漏、超时/EOF和仅owned子进程生命周期、classpath缺失/重复、冻结源gate先于native导入、公开报告secret guard及CLI异常脱敏。首轮15过/1错误为测试`read_text()`沿Windows默认GBK读取含中文源码；只改新测试显式UTF-8，随后16全过，Java不变。

| 文件 | 当前冻结SHA-256 |
| --- | --- |
| ComboLoginRoutesMongoTest.java | `e6b9e21489032ade3fb4a104369ceb42bd8119daa1ca29a2f53e4db85eaf809b` |
| ComboLoginNativeHttpFixture.java | `8905b9059977163670f3ed3e835b01cfcc792575901e7352ac258ffba92796d1` |
| ComboHttpMongoHarness.java | `1a8785f385b5814478873b5397323e1b18f42bf1c5a6df8d256d3767883a2952` |
| verify_client72_combo_http.py | `32a3fe0022e92941d18122aea337f808048fbde58a8af4f17b28829b8749ab72` |
| test_verify_client72_combo_http.py | `79bdffd2a3716936e5c996b7bead408b9c114acf728caca273a77c2e6ac82094` |

主线复验类为`emu.grasscutter.server.http.dispatch.ComboLoginRoutesMongoTest`，不允许skip。Java已编译且源冻结；若源码改变须先重跑compileTestJava与本6项。由主线按既有portable方式启动27027的新Mongo runtime/db目录，不能复用原数据或原27017；runtime/CWD/classpath文件等本卡新产物都放专属raw目录。环境及准确runner argv：

```powershell
$env:LUNAGC_RUNTIME = 'portable'
$env:LUNAGC_TEST_MONGO_URI = 'mongodb://127.0.0.1:27027'
python -m unittest tools.test_verify_client72_combo_http -v
python -m tools.verify_client72_combo_http --client $client72Path --java $javaExe --classpath-file $comboClasspathFile --working-directory $comboRuntime --output local/migration72/d02-resume-20261108/r2-combo-http/native-http-main1.private.json
```

`$client72Path`取当前已核GAME_PATH；`$javaExe`是此次实际编译用JDK的java.exe；`$comboClasspathFile`为主线已执行`d02RuntimeClasspath`生成的单行、绝对条目完整testRuntimeClasspath；`$comboRuntime`为新隔离CWD。不能在日志中展开PIPE。runner按classpath准确查找14个fixture/receiver class并记录其文件SHA，不假设root buildDir，拒绝缺失或重复class；结合当前源码SHA供主线核本轮compile产物绑定。主线须对成功报告和类/源身份独立复核，正常shutdown Mongo并核UUID库/端口。

当前子项状态：真实Java编译/6项HTTP已过；原生→HTTP→原生PIPE尚待主线实际执行，不能提前关卡。若准确native入口失败，保留脱敏failureStage并按该入口/ABI修夹具；若真实HTTP/保存实现失败，提交最小红测供主线，不改生产。自然env/key/route选择与本服客户端登录仍是后续独立证据。

## 主线实际验收

上述待执行步骤已由主线完成：`compileTestJava`及准确runtime classpath生成成功；新Mongo27027/独立runtime中6项真实HTTP JUnit全过，失败/错误/跳过为0。随后另一新Mongo/runtime执行完整PIPE，六例均完成原生builder/真实signer→真实Javalin/default认证/同步Account保存→原生`39EB30`，两成功例另返回原`3C91A0` formatter。外层/内层字符串、64位ID、嵌套非ASCII、token与Mongo等值、原session/归属不变和失败BSON不变均通过。

主线报告 `native-http-main1.private.json` 为 **23,602B / SHA-256 `4d8691f3cdea97e21708b74b1b6382f347a5f47f5bc91e01c758854776994b37`**。独立标准库复核当前source/14个class、全部固定输入、六例原生/HTTP/保存证明、输出保密及未晋级边界，见 `r2-combo-http/main-review.private.json`。主线另跑16项Python/mock全过；不与真实HTTP/native例相加成一次测试。JUnit入口为 `r2-combo-http-green-main1/`，PIPE运行与正常停库证明为 `r2-combo-http/native-main1-runtime-proof.private.json`。两新实例关闭前都仅admin/config/local，正常admin shutdown/exit0/端口释放；原27017、mongodata和local/data保持。

`R2-COMBO-HTTP`在该限定范围已完成，无生产代码、Proto、配置或资源改动，无需服务重部署。没有记录或hash任何PIPE/HTTP正文或凭据。完整native网络、UI/callback、自然Config/SDK与本服登录仍未验；后继继续实际key/route汇合，不重复签名算法或本卡已验边界。
