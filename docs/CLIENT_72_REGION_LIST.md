# 国服 7.2 RegionList 的来源与消费合同

D02-R2 / D-01b5d。本卡从独立原生caller、字符串、class-cell与列表codec确认`87445 MADDBHHMFNG`是GlobalDispatch的QueryRegionListHttpRsp输入，重复子类型是`13336 JCOJNGKFIOM`。固定JAR的六组实际Java输出通过八组原生reader完整返回和三组损坏输入拒绝。没有运行HTTP传输或客户端，没有安装补丁，R2/D-02仍未验收。

本卡接续[D-01b5c当前区合同](CLIENT_72_HTTP_DISPATCH.md)，没有重做已经闭合的当前区/CRT/Marshal研究，也未改其冻结文件。输入仍为[固定三份EXE/metadata](../tools/extract_client72.py)和JAR `dfdebbda0352055540c44a54e2eebfd4ffdfe679db74270044d3d55aeb006ec3`。全部原始轨迹、反汇编、日志和Java字节在忽略`local/migration72`下。

## 从候选到有来源的类型

最初只按“五个根字段与四个string子类型”的形状找到候选，未据此晋级。之后核到以下相互独立的固定文件证据：

1. 网络管理器`23177/DHABIHPMOAH.CLDMBMJBNJF`入口`1512250E0`保存第二参数string，在`151225469`使用根class-cell `1458C5708`分配对象，`151225498`调用`System.Convert.FromBase64CharPtr`，随后`1512254A7`把字节交给`147EA0940`消息merge入口。
2. 同方法的异常诊断引用metadata literal `35025`，通过usage row `777368`及cell `145B0E4A8`独立解码为`[GlobalDispatch] Parse QueryRegionListHttpRsp: `。调用点、类型与字符串共同限定身份；不是依据仓库Proto名称猜测。
3. 根clone `152FEEF00`独立使用同根class-cell；根`.cctor` `152FEF100`取子class-cell `1458D5748`，建立wire key `0x12`（tag2/length-delimited）的message codec，写全局块`+29040`。子clone `14E455CA0`独立使用同一子cell。
4. 原始typed reader入口分别是`152FEF410`、`14E455B20`，metadata virtual slot均19；根reader原文件入口跳向原有VM区，实际离线执行该路径，没有替换其返回或移除跳转。原生列表helper `158DC41F0`及子reader确实被进入并返回。

[trace_client72_region_list.py](../tools/trace_client72_region_list.py)在入口核固定hash、上述代码字节、metadata方法/九个实例字段、嵌套泛型参数及literal来源后才执行。首次正式运行因我方手录了同义但不同编码的`mov rdi,rdx`字节而被守卫拒绝；已按保存的原文件反汇编改正为`48 89 D7`，首失败日志保留。这是研究工具输入错误，不是客户端代码漂移。

## HTTP字节如何到达该consumer

静态上游是`23186/CJBLPCECDAB.MoveNext` `14F8949B0`：它读取closure `23181/FCLFPIMPBAF+18`的正文string与`+20`错误string，只在正文非空且错误为空时，经`14F894D3E`直接调用上述parser。这个直接call证据补足了最初RIP引用/MethodInfo usage搜索没有找到注册点的缺口；未将空搜索解释成“没有调用者”。

该协程按配置选择三条请求支路：

| 分支 | 已核静态来源 | 仍缺的证据 |
| --- | --- | --- |
| 0 | 分配`23178/KLBFJKFILFI`，MoveNext `151D8D620`调用`UnityEngine.WWW..ctor`、错误/响应头接口；取文本后`151D8D9CB`写同closure+18 | 未执行真实WWW、未观测实际URL/响应或选中该支路 |
| 1 | `151219230`调用`System.Net.WebRequest.Create`、`HttpWebRequest.GetResponse`、response stream与StreamReader，文本Trim后`1512194FA`写closure+18 | 未执行真实网络/证书处理/完整错误分支 |
| 2 | 分配`23199/NIBPGILCHJE`，MoveNext `15122A290`经`151223770`调用`MiHoYo.SDK.HoYoSDKNetworkInterface.InitEnv`（`14705BA70`）与`MiHoYo.SDK.HttpDns.HoYoSDKHttpWebRequest.CreateHttpReuquest`（`1471D08E0`），随后线程池工作项 | worker completion未闭合，SDK加载/初始化条件和本轮运行分支未知 |

`ConfigDispatchUrl`包含DispatchName与DispatchUrls；调用`MoleMole.Miscs.GetGlobalDispatchUrl`（`1531E0A10`）在配置URL后附加version、lang、platform、binary/time、channel_id/sub_channel_id等query，部分分支另含account_type、dispatchSeed、key_id、aid。这里只恢复固定代码中的生成来源，没有取得本次暂停客户端的实际URL、配置候选排序或当前连接类型。

因此，加载WinHTTP或未在先前88条DLL事件中见AccountPlatNative/Astrolabe，都不足以确认当前走哪条支路。现有[PATCH_ABI](CLIENT_72_PATCH_ABI.md)对MakeInitialUrl的参数证据只覆盖其已验函数；不能据该证据自动声称这三条路都已被HTTP重定向覆盖。本卡不新增hook、不跳过证书或检查、不把SDK InitEnv静态命中当作自然调用。

## 九个Java字段的实际原生读取

| 对象 | 原生字段 / 偏移 | wire | Java producer字段 |
| --- | --- | --- | --- |
| 根87445 | LDLIEKOAFBP / +52 | tag1 int32 | retcode |
| 根87445 | PNECJNBHCNG / +32 | tag2 repeated message13336 | region_list |
| 根87445 | MNJHOKMKIIO / +24 | tag5 bytes | client_secret_key |
| 根87445 | AMANAEJDNNF / +40 | tag6 bytes | client_custom_config_encrypted |
| 根87445 | LCCLHKIFBMJ / +48 | tag7 bool | enable_login_pc |
| 子13336 | OKDLHHPJOLA / +40 | tag1 string | name |
| 子13336 | HDLLHEMPPCF / +32 | tag2 string | title |
| 子13336 | NOEEBKJAFAG / +24 | tag3 string | type |
| 子13336 | MJCIFCHMPGH / +48 | tag4 string | dispatch_url |

根五字段及子四字段覆盖固定metadata的全部实例字段；这不是完整HTTP回调、任意字符串/容器容量或线上业务语义的验收，也不修改全局mapping/ready。HTTP没有分配游戏CmdID。

[Client72RegionListFixture.java](../tools/Client72RegionListFixture.java)只加载固定JAR的生成builder，分别产生空响应、单条、两条、显式空条目、负retcode、原样文本六组wire及对应Base64。非空seed为仓库公开fixture的2076字节；config夹具为`00 01 7f 80 ff`，并未声称它是合法加密配置。两个条目的name/title/type/URL不同，能核顺序与内容；`9007199254740993`只作为原样string，不赋予UID或数值语义。

再串接两个真实单条Java响应验证重复根字段追加两条，以及在负retcode响应末追加独立`08 00 38 00`验证显式零同时清除retcode与bool，合计八组完整返回。空响应不进入子reader；显式空条目仍进入一次；两条及追加均进入两次。独立literal-wire断言核单条根/子编码、seed/config/bool及空条目/缺失区别；Java Base64由有界主机解码与其binary比较，**本批没有执行原生Base64函数**。

三组无效输入为截断子string、负子长度、截断seed。前两类中的截断由显式有界wire helper拒绝；负长度走原生`1509CAFA0`分支。typed reader、原生list append、ReadMessage/limits实际执行；托管class/object、默认string/ByteString、数组及固定容量泛型context是模型。没有真实GC/CRT，也不把helper抛错称作原生异常对象完整运行。

6项本专题守卫加已有graph/reader守卫共22项实际通过，失败/错误/跳过0。独立JVM生成目录为`d02-region-list/java-{first,repeat}`；class-init日志明确写入绝对忽略路径，没有在根目录生成日志。研究工具/摘要/专题变更无需便携部署。

两次Java生成的13个文件（6个binary、6个Base64、manifest）逐字节一致。最终两份完整原生raw同为SHA-256 `e17f84438d8308cb4935f95f42ff117d02103e7eadfdd2366057f5495fa79d8c`，共享[摘要](../protocol/cn-7.2/region-list-observations.json)为`04bb034e7990d77585a5b6c039847b541e3835fceb138df49f6014c449b8dd57`。原始来源为`evidence/d02-r2-region-list-v4-{first,repeat}.json`，一致性报告为`d02-r2-region-list-determinism.json`；补充上游来源前后的traces/results/rejections亦逐值相同。最终测试日志为`logs/d02-r2-region-list-tests-final.log`。

主线独立审核caller/具名literal/class/codec与三条静态上游的边界，使用固定JAR重新编译并生成13文件，与原输出逐字节相同；22项定向及第三个全新Python原生回放通过，完整/摘要与上述双跑逐字节一致。原始为`local/migration72/d02-region-list/main-review/`，证明`evidence/d02-r2-region-list-main-review.json`。这只关闭独立来源及九字段wire合同，不将完整callback、原生Base64、自然HTTP/SDK或路由信任晋级。

## 服务端路由、信任与未满足的部署出口

当前Java `RegionHandler.applyRoutes`声明`GET /query_region_list`和`GET /query_cur_region/{region}`。RegionList builder按region名排序，以`DEV_PUBLIC`生成条目；dispatch_url来自当前请求解析的scheme/访问地址/端口，再追加`/query_cur_region/{name}`；最终Base64先经QueryAllRegionsEvent，端点发送event的结果。CNRELWin等版本前缀选择CN config；生成配置的sdkenv为0。以上来自源码，本卡未运行RegionHandler初始化、事件插件或Javalin端点，因此没有把孤立builder冒充完整端点输出。

客户端parser解析后，将tag3/type与固定`DEV_PUBLIC`字符串比较并分组；根两个bytes在`151225BB4`进入既有config consumer `151204AC0`，该处模式参数为0。负retcode有独立错误路线。reader能消费字段不意味着分组/UI/config初始化成功，本卡未执行这些后半段。

初始dispatch域名/解析/真实选择仍缺；当前区响应签名、SDK、TLS与实际安装的信任材料也不能由RegionList读取证明。[TRUST_PATCH](CLIENT_72_TRUST_PATCH.md)和[TRUST_LAUNCHER](CLIENT_72_TRUST_LAUNCHER.md)的材料及子进程供给证据保留原范围，现有调试器环境/未装补丁没有在本卡变更。本卡没有部署服务、修改环境/配置、接入数据库或强造成功返回。

## 复现与下一项

```powershell
$jar = 'local/migration72/d02-http-dispatch/input/LunaGC-dfdebbda.jar'
$jdk = (Get-ChildItem local/runtime -Directory | Where-Object Name -Like 'jdk-17*' | Select-Object -First 1).FullName
& (Join-Path $jdk 'bin/javac.exe') -cp $jar -d local/migration72/d02-region-list/classes tools/Client72RegionListFixture.java
& (Join-Path $jdk 'bin/java.exe') -cp "local/migration72/d02-region-list/classes;$jar" `
  Client72RegionListFixture local/migration72/d02-region-list/java-first src/main/resources/keys/dispatchSeed.bin
$clientRoot = & tools/game_path.ps1
python tools/trace_client72_region_list.py --client $clientRoot --jar $jar `
  --fixtures local/migration72/d02-region-list/java-first `
  --output local/migration72/evidence/d02-r2-region-list-next.json `
  --summary-output local/migration72/evidence/d02-r2-region-list-next-summary.json
python -m unittest tools.test_client72_region_list tools.test_client72_graph tools.test_client72_reader -v
```

原暂停客户端未执行、未新load或设点；仍选中已知主线程，原事件为D-01b5b自有预算pause，线程选择no-op问题沿用D-01b5c记录，不重复恢复命令。下一项优先核实际配置/网络管理器阶段与分支，再决定是否在同目标安排仅一个有来源的自然观察点；候选包括GlobalDispatch正文consumer或已明确SDK InitEnv入口。需要实际到达证据和返回/错误出口，不因本卡离线成功或现目标存活而晋级R2。

另经主线明确要求，尝试一次仅本目标窗口的只读图像读取：实时Automate与独立OS路径/暂停上下文匹配后，枚举本进程3个顶层窗，只选择唯一可见“原神”窗口；未抓整桌面或无关窗。`PrintWindow(PW_RENDERFULLCONTENT=2)`返回false、Win32 error5（Access denied），因此没有保存伪图，也不能称作取得黑图。没有前台切换、输入、注入、升权或恢复执行；读取前后OS存活、debugging=true/running=false、选中CIP/RSP保持。证据为`d02-r2-d01b5d-game-window-capture.json`及同前缀日志，真实显示阶段仍未知；这一结果不阻止独立静态/离线研究，也不是D02成功截图。

本卡冻结后，用户明确要求重启游戏再观察。旧保留pause决定已被覆盖；D-01b5e按新归属核对、结束旧target后再启动固定原始客户端，目标为单一GlobalDispatch正文入口的有界自然观察。本卡未把之后的重启或结果并入离线验收，现场以e的独立证据为准。
