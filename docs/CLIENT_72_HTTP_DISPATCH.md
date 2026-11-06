# 国服 7.2 HTTP dispatch 的 Java 输出与原生消费

本专题记录 D02-R2 / D-01b5c 的独立离线前置。固定构建 JAR 的实际 `QueryCurrRegionHttpRsp` / `RegionInfo` Java builder 输出，已被原始 7.2 对应类型的原生 reader 完整读取返回；本批只覆盖下表八个字段。没有执行客户端 HTTP/SDK、没有向暂停目标注入调用，也没有变更 Proto、路由、校验或补丁。D02-R2、D-01及D-02仍未验收。

## 固定来源与边界

- 客户端输入沿用 [extract_client72.py](../tools/extract_client72.py) 的 EXE、global metadata、startup metadata 三份固定 SHA-256；执行前逐一验证，详见 [脱敏结果](../protocol/cn-7.2/http-dispatch-observations.json) 的 `inputSHA256`。
- Java 类来自本机隔离副本 `local/migration72/d02-http-dispatch/input/LunaGC-dfdebbda.jar`，SHA-256 为 `dfdebbda0352055540c44a54e2eebfd4ffdfe679db74270044d3d55aeb006ec3`。不使用并发更新中的根 JAR；独立 `javac` 输出留在同一忽略目录，不占共享 Gradle/build/classes，不启动 Grasscutter、RegionHandler、服务或数据库。
- 根对象为类型 `30677 IBOFGKFAEPI`、原生 reader `153A598A0`；嵌套对象为 `67961 IENBACJFBGI`、reader `149BB4EB0`。类型、字段名/类型/偏移、reader virtual slot 19 和 clone 的 class-cell 引用由固定 metadata/代码核对；HTTP 当前区输入的既有来源见 [SESSION 的 A-04d5n 系列](CLIENT_72_SESSION.md)。本批不凭字段形状重认当前区对象身份。
- 2076 字节仓库公开 fixture seed SHA-256 为 `2258b87182df6e8e679c15a86184ddaf8d4b5eda24cd81bf12e85bb8a9e00637`。这里只证明 ByteString 字节消费；不把 seed 字节读取当成线上密钥协商。

[Client72HttpDispatchFixture.java](../tools/Client72HttpDispatchFixture.java)调用固定 JAR 的生成 builder 并写实际二进制；[trace_client72_http_dispatch.py](../tools/trace_client72_http_dispatch.py)将这些文件交给原始 typed reader，断言返回对象。入口的两组 RegionInfo 及根嵌套编码另有独立 literal-wire 断言，不以同一个编码器回解自身作为唯一证据。

原生 typed reader、嵌套 ReadMessage 和长度限制路径实际执行；托管对象/类分配、默认 string/ByteString、界限内 tag/varint/blob helper 与部分泛型运行时仍采用已有显式模型。没有真实 GC/CRT、HTTP 运输或 SDK 环境，不把它称为运行时 ABI 验收。

## 八个已消费字段

| 原生类型 | 原生字段 / 对象偏移 | wire | Java producer 名称及已验值 |
| --- | --- | --- | --- |
| 当前区根 | LDLIEKOAFBP / +80 | tag1 int32 | retcode：0、-4005；追加显式0清除负值 |
| 当前区根 | EHCHDIJEJEJ / +48 | tag2 string | msg：fixture-denied |
| 当前区根 | NBBEAACDEFM / +72 | tag3 message | region_info：非空、显式空、缺失、重复字段合并 |
| 当前区根 | MNJHOKMKIIO / +64 | tag11 bytes | client_secret_key：2076字节逐字节一致 |
| 当前区根 | DGHGFAELIDO / +32 | tag14 string | Java 名 connect_gate_ticket，文本9007199254740993逐字节保留；原生业务语义仍未知 |
| RegionInfo | GOFEDFBICAJ / +208 | tag1 string | gateserver_ip：127.0.0.1、fixture.invalid |
| RegionInfo | KJGNCFLEJLK / +316 | tag2 uint32 | gateserver_port：22101、4294967295 |
| RegionInfo | IJNBKLIHNJP / +216 | tag36 string | game_biz：hk4e_cn |

Java 名称只标明测试生产端。tag14 的大整数外观文本没有转成数值，也未提升为 UID/token/加密参数语义。HTTP 无 game CmdID，本批没有创建 CmdID→类型映射，也没有提升全字段 schema 状态。

## 实际执行与失败边界

六组实际 Java 当前区响应为 minimal、uint32-boundary、empty-region、missing-region、error-retcode、raw-text。再将两个实际响应串接验证嵌套合并/覆盖，以及在错误响应末追加独立 `08 00` 验证显式零覆盖，合计八组原生完整返回。根 reader 每组进入一次；缺失嵌套不进入 RegionInfo reader，显式空进入一次，合并进入两次。完整的原生访问轨迹只留本机忽略证据。

三组无效输入均拒绝，但来源分别记录：截断 seed 与超出输入的嵌套长度由有界 wire helper 拒绝；负嵌套长度到达原生 `1509CAFA0` 错误分支。初版报告用 startswith 检查错误字符串，遗漏了地址前缀，误把第三例的 `nativeErrorBranch` 写为 false；已修正归类并重新执行两次，原版 raw 与日志保留，没有将错误处理模型误称为原生异常对象完整执行。

Java 两次生成的14个文件逐字节相同（12个当前区/RegionInfo文件、manifest、一个尚未消费的 RegionList fixture）。修正后的两次原生 raw 逐字节一致，SHA-256 为 `81474612f4a18ac77d29ab290017c14b625cc19390d15113d635cac35c567326`；共享摘要 SHA-256 为 `7f9d3843bf7f30f178f81c8ae71f5378a1204ad5fd8799d6c176cdf00c13e1c9`。本机来源为 `evidence/d02-r2-http-dispatch-v2-{first,repeat}.json` 与 `d02-r2-http-dispatch-determinism-v2.json`。

6项本专题输入/摘要守卫加已有 graph/reader 守卫合计22项实际通过，失败/错误/跳过0，日志 `logs/d02-r2-http-dispatch-tests-v2.log`。这些测试不启动客户端或服务。研究工具和文档修改无需便携部署。

主线在独立目录用固定JAR重新编译并生成14文件，逐字节匹配原输出；22项定向复跑通过，第三个全新Python进程完整原生回放与上述双跑raw/摘要逐字节相同。另核只读阶段七份raw hash及固定版本线程选择源码，追加摘要后17项观察守卫通过。主线产物为`local/migration72/d02-http-dispatch/main-review/`和`evidence/d02-r2-http-dispatch-main-review.json`；不把这些重叠执行相加为新样本数量。第一次观察测试命令模块名拼错的失败日志保留，修正后17项通过。

```powershell
$jar = 'local/migration72/d02-http-dispatch/input/LunaGC-dfdebbda.jar'
$httpJdk = (Get-ChildItem local/runtime -Directory -Filter 'jdk-17*' | Select-Object -First 1).FullName
& (Join-Path $httpJdk 'bin/javac.exe') -cp $jar `
  -d local/migration72/d02-http-dispatch/classes tools/Client72HttpDispatchFixture.java
& (Join-Path $httpJdk 'bin/java.exe') `
  -cp "local/migration72/d02-http-dispatch/classes;$jar" Client72HttpDispatchFixture `
  local/migration72/d02-http-dispatch/java-first src/main/resources/keys/dispatchSeed.bin
$clientRoot = & tools/game_path.ps1
python tools/trace_client72_http_dispatch.py --client $clientRoot `
  --fixtures local/migration72/d02-http-dispatch/java-first --jar $jar `
  --output local/migration72/evidence/d02-r2-http-dispatch-next.json `
  --summary-output local/migration72/evidence/d02-r2-http-dispatch-next-summary.json
python -m unittest tools.test_client72_http_dispatch tools.test_client72_graph tools.test_client72_reader -v
```

## 尚未闭合的部分与下一项

本批没有执行完整 RegionHandler 请求/事件、JSON/content/sign producer、签名/路由信任闭包、其余 RegionInfo/ResVersion 字段、config消费或完整原生 dispatch。既有本机签名 fixture 的原生密码路径证据不等于线上或私服 HTTP 端点已可用；尚未部署服务，不进行猜测连接。

RegionList 的 Java fixture 已生成，尚未交给已确认身份的原生 consumer。结构搜索仅得到候选 `87445 MADDBHHMFNG`，含重复子类型 `13336 JCOJNGKFIOM`，记录在忽略 `d02-r2-http-region-list-{shape-candidates,declarations}.json`。字段形状不能确认 endpoint 身份；下一项先从实际 dispatch parse/caller 和 class/codec 引用独立核身份，再进行实际 Java list 非空/重复/边界消费。不会把候选直接写入全局映射。

现有客户端独立保持暂停；自然 SDK/HTTP/非空 Marshal 调用及返回仍缺。运行观察状态与自有线程选择恢复问题另记 [STARTUP_OBSERVER](CLIENT_72_STARTUP_OBSERVER.md)，本卡不替代自然调用验收。
