# D-01b5h1：真实7.2 HTTP请求及版本拒绝链

本卡实际运行固定归档JAR的 `HttpServer` 和 `RegionHandler`，从真实HTTP获取列表及当前区JSON，再执行目标原生密码路径。**列表九个raw字段可读；7.2当前区请求被该归档的7.1版本准入拒绝。** 正确签名的拒绝正文能解密/验签，改坏正文签名则原生拒绝。没有修改版本、生产、Proto、配置、补丁或验签结果，也没有启动游戏、GameServer、DispatchServer或数据库。

## 范围和实际输入

先复用[c当前区字段](CLIENT_72_HTTP_DISPATCH.md)、[d列表身份/字段](CLIENT_72_REGION_LIST.md)和[F-04b/c信任供给](CLIENT_72_TRUST_CONTRACT.md)；这些原先是builder/原生合同，不证明真实路由接受7.2。固定归档JAR SHA-256 `dfdebbda0352055540c44a54e2eebfd4ffdfe679db74270044d3d55aeb006ec3`，位置 `local/migration72/builds/LunaGC-d02-quest-dfdebbda.jar`；它明确仍声明 `BuildConfig.VERSION=7.1.0`，不是已适配的7.2构建。当前源码 `build.gradle` 的版本亦未调整，不发送7.1请求来使本卡成功。

每次新JVM从独立UUID目录读取配置，实际生产 `HttpServer` 只注册 `RegionHandler`，监听 `127.0.0.1:0` 后取得实际端口；正常stop后检查端口释放。该源码接口允许测试端口0，不意味着便携入口或补丁的固定8088已隔离。game端口22101只作为响应资源值，没有游戏监听。资源/data/cache均为本轮独立目录，使用真实打包的dispatch seed/key及key4公钥和SigningKey；运行前逐字节核78个相关archive class与当前build相同。根 `.env`、config和resources已有差异文件hash在运行前后保持。

实际两条请求均为 `CNRELWin7.2.0`：

1. `/query_region_list?...&platform=1` 返回HTTP200/Base64；真实列表只有os_usa，其返回的dispatch URL指向本轮HTTP端口。CN config解密后sdkenv为0；这只核生产分支，不批准目标config完整语义。
2. 对该返回URL携带 `key_id=4&dispatchSeed=fixture` 请求当前区，走正式加密签名路径，HTTP200返回content/sign；dispatchSeed在本实验只是进入已知分支的有界测试参数，不是目标客户端自然请求证据。没有用缺seed时的占位sign路径作为成功。

## 原生、独立密码学与停止边界

每个原生进程重新从 `tools/game_path.ps1` 对应客户端读取并校验 `extract_client72.HASHES` 的三固定输入（EXE `8b014f64…21dbd6b5`、global `45dc7a44…60f6ef2a`、startup `b3148108…4d5105`），重新提取结构。目标私钥只由既有受控原生来源恢复到内存，独立核其公钥数值等于实际HTTP使用的仓库key4；不输出私钥。整数RSA/PKCS#1解密与正文签名oracle独立于目标实现。

实际正文为server producer的 `retcode=11 / Connection Failed!`、空RegionInfo以及版本拒绝detail；其中明确Server version 7.1.0、Client version 7.2.0。这里的detail解析只是对服务端输出的核对，**没有批准目标StopServerInfo字段语义或消费**。

目标实际执行JSON入口、RSA解密、SHA和两类key验签，再验证正文签名；只在F-04b/c已审输入点供给预制本地公钥/签名/root，原生检查/返回值及指令窗口保持。正确正文到真实 `147EA0940` merge入口即停止，解密字节与独立整数RSA完全相同；未执行尚未闭合的版本错误嵌套reader。对同份HTTP JSON只改变sign一位，实际转到 `147161B60` 拒绝，reader调用仍为0。正文拒绝与key验签分别核查，不能因key通过宣称正文通过。

列表的真实HTTP二进制另交已审 `87445→13336` 原生reader及真实list追加，九字段与独立wire/生产预期逐项相同；没有把密码路径停止当成当前区完整parse，也没有执行native Base64或完整SDK callback。复用的managed对象/JSON/输入/数组/时间和信任供给模型均保留原合同边界。

## 实测、失败和证据

三次最终源码的新JVM各实际发出上述两请求，78 class匹配、正常stop和端口释放全部通过；每批输出分别交新原生进程验证一个正确签名拒绝、一个坏签名拒绝和列表读取。各批的端口、时间和随机密文不同，不能宣称这些raw逐字节相同；除具体fixture文件hash外，三份摘要的范围/结果一致。另对第一份固定HTTP输出再启动独立原生进程，完整/摘要逐字节相同。没有将不同批次相加为更多协议种类或客户端测试。

| 产物 | SHA-256 |
| --- | --- |
| 正式driver所产HTTP→native完整报告 | `50c365f7e976a7b4cc1aba11cd8685f245b7cccba754033a20759a58a5d0afb4` |
| 正式脱敏摘要/共享observations | `d7806baf3b350afea59e4b76a3ef1e52b318ae0da2026f751561a047f1b669af` |
| 第一固定fixture的两份完整报告 | `621af918f80fcb88324605fab27ff6aaec5708cb8edcf40dff8a6b09f0287cf0` |
| 第一固定fixture的两份摘要 | `4ad43ba5123310c0815bb3e760aecd1d73a75eb118ecbabe08ffaed19f4c03dc` |

正式原始目录 `local/migration72/d02-http-route-main-ef0f51ef96f94b3181889def8a9e9691/`；另外两批为 `d02-http-route-main-ac58bd08340e4cfcad35bd3af30d8468/`、`d02-http-route-main-ff5c99f982d04df6a01df914672941b7/`。报告 `evidence/http-route-native-{formal,probe2,repeat,fixed-repeat}.json`，摘要同前缀，主线证明 `evidence/http-route-main-review.json`；日志在 `logs/http-route-*`，实际Java/配置/class-init/stdout/stderr均随独立目录保留。Python编译检查通过；本卡的执行验证是实际javac/HTTP/原生过程，不冒称JUnit/Python单元测试数量。

首轮把准入前base query错当作实际成功正文，独立签名断言因此失败。确认归档实际版本并用真实密文/原生和整数RSA取得版本拒绝正文后，修正的是本工具预期，没有改服务或绕版本。首轮目录 `d02-http-route-main-543ad7a6afb4483289fbfcca6d2be1fd/` 保留执行源码和失败日志。第一次native启动又因工具导入了不存在的helper名称而在读取客户端前失败，修正为已有 `validate_identity`，`logs/http-route-native-probe1.log` 保留。

## 复现与后续

需先保留上述固定归档和便携JDK17，以及现有 `build/classes/java/main`；当前源码class不匹配归档就拒绝，不能换hash绕过。以下只创建本轮隔离HTTP进程，自请求后正常关闭，不部署主服务：

```powershell
$routeRun = python tools/audit_client72_http_route.py | ConvertFrom-Json
$routeClient = & tools/game_path.ps1
python tools/trace_client72_http_route.py --client $routeClient --fixtures $routeRun.evidence --output local/migration72/evidence/http-route-next.json --summary-output local/migration72/evidence/http-route-next-summary.json
```

正式runner及相同Java/native链已实际执行；原始密码材料与日志不提交。后续仍须R4完整协议/资源合同后制作正式7.2构建并验证实际准入，以及R2自然SDK/路由/信任材料和补丁加载；当前HTTP200、签名通过和离线列表消费均不替代7.2获准或D-02实机。
