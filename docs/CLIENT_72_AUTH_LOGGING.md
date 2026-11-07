# D02-R2：认证日志内容修正

## 执行前的限定计划

目标是在本服账号实测前，消除已经存在的认证正文、解密明文和会话token片段日志。现有AuthenticationHandler三处debug拼接rawBodyData；RSADecryptionUtil成功与plaintext fallback记录返回明文；MaPassportAuthenticator记录账号/手机号、异常正文及采用token的前12字符。它们是已见源码行为，不是推测的新边界。基线三个文件SHA分别为cfbd745d…c979b、98d89ad5…908b、c2173919…ac83，原字节在本机忽略目录备份。

未知仍包括7.2实际SDK key/padding、route与窄patch装载。这次只修日志，不能据此认可旧多key/fallback/短信或token策略；SDK合同另由Astra关闭。候选改法是只记录固定操作和结果标签，移除动态认证值、含输入的Throwable和printStackTrace，不引入过滤器/额外校验/新认证分支，也不调整响应、数据库字段、密码校验、密钥顺序、fallback或原生验签。

最小实验先用合成数据执行实际handler、RSA成功/已有fallback及已有隔离账号fixture；在DEBUG采集日志和stderr，证明原行为泄漏，然后用同样输入验证不出现正文、明密文、账号或token片段，响应和解密返回保持。所有输入均为测试合成值，不使用用户凭据。HTTP handler用Context接口fixture，相关数据库测试只用新独立Mongo目录/27027和随机测试库，原mongodata/local/data不动。

修改范围仅AuthenticationHandler.java、MaPassportAuthenticator.java、RSADecryptionUtil.java、必要定向测试和账号专题；不改Proto、版本、配置、patch、资源、认证行为或ready。共享Gradle/数据库/构建由主线串行控制。失败保留有效红测、运行目录和原始日志，检查首个实际失败，不靠删断言或跳测试求通。

验收为实际红绿日志、对应账号认证回归、便携构建和JAR编译类核验。R4完整协议/资源/SDK前置未齐时不部署或启动猜测服务；必须记录此部署限制，不能把日志修正、测试或构建称为D-02登录/入场通过。

## 实际修正与回归

三个生产类只改日志/相关注释，保留固定操作、结果、exception class和固定key/padding标签。Astra独立对修改前后真实Java去comment/日志语句后作token比较（保留其它字符串字面量），三份全部相同；证明 `sdk-crypto-contract-astra/java-input-epoch1/audit.json`。密码、密钥/padding顺序、已有fallback、token采用/保存、路由和错误响应业务保持。

AuthenticationLoggingTest实际调用三个handler拒绝分支、exchange错误分支、合成RSA成功和已有plaintext fallback；DEBUG日志及stderr捕获先6/6因内容泄漏失败，再6/6通过，响应400/500及解密返回另断言。已有PasswordResetAuthenticationTest新增真实随机Mongo账号的token采用回归；assertAll分别复现账号与旧/新12字符片段三个失败，修后日志不含这些值且新token正确保存。不批准旧策略用于7.2。

最终独立Mongo/运行目录两套35项通过（6+29），失败/错误/跳过均0，XML在 `auth-logging/mongo-review3/green/test-results/`。第一轮关联34/35：撤销token测试key_id=0，被原配置的加密入口提前返回；仅将测试请求补为已证key_id4，未改生产配置/guard，随后完整35项通过。首个Mongo驱动的额外red自检还误期望内存ListAppender内容出现在stdout；保留失败，改为三个明确assertAll失败标签核验。有效红测、关联失败与最终XML全部保留，未重复假称通过。

三个新Mongo目录均保留，均实际admin shutdown/exit0/27027释放；关前仅admin/config/local，原27017/原数据未操作。所有数据为合成值，源备份及原始日志在忽略的 `local/migration72/d02-active/auth-logging/`。

## 便携构建与部署边界

生产提交`1ea32a4`后，`LUNAGC_RUNTIME=portable tools/local_windows.ps1 -Mode build`实际30秒成功。磁盘JAR SHA-256 `64a381dcc1bc02dbf80280fb14a6c4c4df41a8d9f299afa97eb6fe995b4e0aab`；46,304类无增删，三日志类及三编译内联metadata类共6变化，逐一与真实编译class相同，其余46,298类同旧归档。嵌入GIT_HASH为`1ea32a4`，原版本标签仍7.1.0；这是当前源码归档，**未批准为7.2部署产物**，没有改字符串或guard求通。旧f475dbf6归档已先逐hash备份，原配置/.env/resources既有差异不变。

证明 `auth-logging/build/artifact-proof.json`、构建日志 `portable-build.log`。实际status退出1，OS无本服/游戏/调试器/Mongo，固定TCP/UDP无监听；没有运行JAR、HTTP健康或实际DLL装载对象，不称这些通过。完整R4/资源revision/SDK信任与loader仍未齐，所以没有部署、重启服务或本服账号实测，D-02未完成。
