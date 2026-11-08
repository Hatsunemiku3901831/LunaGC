# R2-SESSION：国服 7.2 Session verify 响应适配

## 依据与范围

固定 SDK SHA-256 为 `adecdf0bd5442e321b1b66b0ca9e98bef76d5067326aa4add5b75266055a6c25`。原始 consumer、失败对照及字段合同见 [SDK 响应合同](CLIENT_72_SDK_RESPONSE_CONTRACT.md)。本批按 [D-02 恢复计划](CLIENT_72_D02_RESUME_20261108.md)推进，不把旧暂停描述当成本批限制。

已核实的缺口是 `/account/ma-cn-session/app/verify` 与 passport 的 `verifySToken` 共享 handler，成功只输出 `data.tokens[]`。固定 SDK 的 session consumer 读取 `data.token`，不能用数组代替。本项只适配认证成功后的响应形状，不改变凭据验证、旧账号兼容采用、密码、数据库或 token 生成策略；`exchange`、`new_token` 与自然 SDK 调用链另有合同。

目标顺序为现有 `MaPassportAuthenticator.verifySToken` 调用一次 → 失败直接保持原响应 → 成功构造独立 session DTO → 现有 `ctx.json`。成功保留 `data.user_info`、`data.ext_user_info`，将已有成功结果的 type1 token 映射为单个 `data.token`；不再次认证、生成、轮换或写入。aid/mid 保持字符串，links 保持列表；错误不补 token。三个现有 passport `verifySToken` 路由继续输出数组。

## 当前实现与验证状态

主线已执行并保留有效红测：9 项、1 失败、0 错误、0 跳过，失败准确为 session 成功响应缺少 `data.token`；证据为 `local/migration72/d02-resume-20261108/sdk-red2/test-results/`。随后实施 `AuthenticationHandler.maSessionVerify` 和 `SessionVerifyResponseJson`；失败直接输出原认证结果，成功 DTO 复用已有 user_info/ext_user_info 与唯一 type1 token，不额外校验内部结果或改变认证策略。

`src/test/java/emu/grasscutter/server/http/dispatch/SessionVerifyRoutesTest.java` 使用真实 `AuthenticationHandler`、`MaPassportAuthenticator`、Javalin HTTP 和 `JavalinGson`。测试专用 Datastore/Query 代理只返回内存 Account，拒绝其他存储操作；账号夹具禁止替换或生成 session key。因此它检查 HTTP 路由、序列化、认证调用次数和内存结果，不检查 MongoDB 的加载、保存或确认语义。

原红测选集为 9 项：session 单 token 成功、三个 passport 数组成功、受保护账号错 token 与 passport 错误一致、缺账号，以及 null/畸形/字段类型错误三种正文。成功使用大于 `2^53` 的非零 ID 文本，检查 aid/mid 均为 JSON string，token 为嵌套 object，links 为空数组，已有 token 内存相等且查询一次。修复后另增一项纯适配 HTTP 序列化：既有成功结果手工放入两个非空 links，以及互异的 `9007199254740993`/`9223372036854775807` 字符串 ID；真实认证当前只生成空 links，此新增项不冒充真实账号数据或原生验证。主线实际10项HTTP/序列化检查全过。

由主线在已核对的隔离 portable 测试环境执行：

```powershell
& .\gradlew.bat --no-daemon test -PskipHandbook=1 --tests '*SessionVerifyRoutesTest'
```

测试生成合成 token 及请求/响应正文仅在内存使用；token 比较采用布尔断言，不把正文、token 或其 hash 写入测试报告。测试使用临时 HTTP 端口，数据库代理不连接任何 MongoDB。Gradle、数据库、端口、客户端、构建部署和 Git 由主线独占。

## 仅内存 Java → 原生 bridge

测试 main 入口为 `emu.grasscutter.server.http.dispatch.SessionVerifyNativeResponseFixture`，classpath 使用主线解析的完整 `sourceSets.test.runtimeClasspath`（test/main classes、test/main resources 及已有测试运行依赖），不新增依赖或 Gradle 任务。它复用同一 HTTP 测试夹具及真实生产路由，不复制认证实现。helper 先静音 stdout、重置 Logback 并关闭 root logger，再初始化项目；完成三次请求并正常停止 Javalin 后，才向原 stdout pipe 输出一次 envelope。

stdout 是**只供内存管道消费的敏感合成 payload**，字段如下：

| 字段 | 类型与用途 |
| --- | --- |
| `session_success` | string；真实 session 成功 HTTP 原始正文，未重新序列化内部正文 |
| `session_error` | string；受保护账号错误 token 的真实 session 错误 HTTP 原始正文 |
| `passport_success` | string；真实 passport 成功 HTTP 原始正文，保留 tokens 数组 |
| `expected_token` | string；内存 Account 的原合成 session token，仅用于内存等值比较 |
| `lookup_count` | number；预期 3，每条真实请求认证查询一次 |

主线用 Python `subprocess.PIPE` 捕获 stdout，解析 envelope，再将三个 string 的原字节交冻结 `ResponseFixture`；SDK 输入 SHA 仍为本页开头的固定值。禁止直接在终端执行 helper，禁止 stdout 重定向到文件/日志，禁止对正文、token 或整个 envelope 求 hash。只允许持久化输入源码身份、公开字段类型、状态码和布尔比较结果。此 bridge 的查询仍为内存夹具，不能把它当作 MongoDB 或自然客户端证据。

## 后续验收与保留缺口

1. 红测已保留；独立 handler/DTO、非空多元素 links 的纯适配序列化检查及内存 bridge 已写入，等待主线编译/绿测，不提前记通过。
2. 主线重跑定向 HTTP 和账号/密码/保存回归；独立 MongoDB 集成另记实际范围，不能把本项内存夹具当作落库证明。
3. 主线将真实 Java 生成的合成成功响应仅在内存交固定 SDK 原生 parser/formatter，核对 type1 和 combo token 相等；正文及 token 不落文件、日志或 hash。
4. 运行源码改变后由主线完成对应构建及产物核验。若 R4/SDK/资源前置未齐，记录未部署原因，不启动猜测协议。

自然会话恢复、真实网络状态、最终 SDK 到游戏回调及新旧账号实机仍未验。本项完成不等于 R2 或 D-02 完成。


## 主线实际验收

主线 `r2-session-green/` 新独立27027及四可写目录内运行9套193项，0失败/错误/跳过：HTTP10、认证日志6、密码/会话33、账号创建40、密码重置20、账号保存20、Player保存10、物品保存35、选人/保存19。HTTP测试账户查询为内存夹具；其余列明的Mongo套件真实连接新库。Mongo正常admin shutdown退出0；原配置及8个运行数据文件逐SHA不变。未重跑全量Java，不把历史全量结论代入。

新 `tools/verify_client72_session_http.py` 从Java helper PIPE取得实际HTTP原始JSON文本，未经重编码直接交固定SDK `342B10→2F6190→338990→31F3F0`；成功再交`342460` formatter。`session-native-final-{first,repeat}.json`两次新进程逐字节一致，三案例分别为：session成功状态0/type1/原token相等/大于2^53身份文本精确；认证错误原生-101且无token；passport数组保留且同consumer把type置0/formatter空token，作为真实格式负对照。parser和formatter的entry/return证明分段保留，未把最后一段trace冒充整个链。

报告只持久化源码/编译类身份、状态、长度、模型和布尔；HTTP正文、token、envelope及其hash均未写文件或日志。查询仍是合成Account，原生存储/单线程同步和稀疏service/config模型沿用父合同，未运行自然UI/回调/真实SDK网络。最初两份`session-native-{first,repeat}`也同字节，但只保留末调用trace；改为分段证明后新final两份替代该证据范围，旧报告不覆盖。

本地生产/测试与研究脚本由主线提交；portable构建和JAR类核对随后补充。R4完整协议/资源revision/SDK信任与合法加载未齐，因此不部署服务或安装补丁。D-02六项实机仍未通过。
