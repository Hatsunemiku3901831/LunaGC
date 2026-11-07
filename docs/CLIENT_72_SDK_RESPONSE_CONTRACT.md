# D02-R2：SDK登录响应与会话交换合同

## 独立攻坚计划（2026-11-08）

1. 目标：沿固定AccountPlatAccountLoginJobber→RequestManager→NetworkManager的准确返回回调，闭合loginByPassword响应字段、错误/默认/省略、所有权与处理顺序；继续定位session verify/exchange和游戏combo的必要producer/consumer，形成可交主线/Sol的明确合同。
2. 证据：profile五输入、SDK `adecdf0bd5442e321b1b66b0ca9e98bef76d5067326aa4add5b75266055a6c25`、父transport/login-chain/crypto冻结source与raw；Crypto最终raw `c91ebc70a0afee16cb9379286fab86a10deea2a1ec8cef10a0c49fc9a7d17313`和REA `3c14ee95de5234b04ceb559b13e724ef78819cad1042fde6f08d19673cbcf91c`；认证三Java源使用已审日志epoch1。最新自然99span仅提供初始化身份，不证明响应消费，未采字段不能当null。
3. 未知：准确success/error回调身份、retcode/data/user_info/token等键的类型和必经条件、tokenType与交换顺序、目标响应默认行为及Java现状差异；自然登录、服务端互通、callback及combo均未验。
4. 候选：`34ECA0`所创建的回调functor/vtable、RequestManager回应lambda、Account jobber处理函数及session请求worker；先复用REA与精确PE指令/RTTI，必要时只对同一9MB SDK有界查询，不做450MB EXE整分析。
5. 最小实验：先闭call/vtable/返回参数与writer/reader，再执行原始JSON解析/业务guard短段。合成非零64位、嵌套/列表、错误、缺字段/类型边界输入只在内存生成；记录固定字段名/类型/长度/bool/状态/源码身份及显式模型，不记录账号、query/header/body、token、明密文或其hash。
6. 范围：仅`tools/client72_sdk_response_contract.py`、对应test、本专题及`local/migration72/d02-active/sdk-response-astra/`。父三卡冻结文件不改；禁止Git、Gradle/Cargo、DB、端口/服务、生产、客户端/调试器、采样/注入及实际网络。若需REA，用完close/export；不占主线运行现场。
7. 失败出口：输入hash变化、未闭间接边或真实runtime前置不足时保留首次失败、精确停止点与唯一首缺输入；不得以callback/parse/guard成功桩替代业务。可离线解决的缺口继续文件研究，需要natural则给准确稳定字段/代码窗/有界条件，不自行实机。
8. 验收：固定source/全部输入hash、目标机器码与实际call/return、明确模型、独立正反和64位/嵌套/列表/错误断言、双新进程确定性与第三复验步骤；生产规格列JSON键或字段tag、类型/转换/枚举、Java默认/省略/顺序、允许/禁止文件和后续验收。子项完成不等于本服SDK或D-02完成。

## 结论与边界

本卡发现并原生复现一个实际响应格式缺口：当前 `ma-cn-session/app/verify` 共用旧 `VerifySTokenResponseJson`，成功响应只给 `data.tokens[]`。固定SDK这条会话恢复链读取 `data.token`，另在刷新阶段读取 `data.new_token`。在合成的原有type1/stoken用户上，执行原始 `342B10→338990→31F3F0` 后，数组中的新token没有被消费，UserInfo的type被默认值0覆盖；继续执行原始 `342460`，结果 `combo_token` 长度0。相同输入改为单个 `data.token{token_type:1,token:<占位>}` 后，type保持1，formatter输出与输入token内存相等。两个样本均由原始代码返回，未替换JSON解析、业务guard或callback成功。

`exchangeToken` 请求的 `dst_token_type=4` 在本SDK中具有准确的 `GetCookieTokenBySToken` / `get_cookie_token` 原生literal和producer身份。它是cookie-token交换；本卡没有把它称为游戏combo必经步骤。登录、session verify、cookie exchange、combo响应各自分开记录。

完成范围是固定磁盘原生代码的离线执行和字段合同。最新自然证据仍只有登录页初始化99span；没有自然输入凭据、响应消费或成功游戏会话。未采字段不作null解释。磁盘模块hash也不等同全部已加载内存字节hash。UI/合规阶段、注册表持久化、真实TLS/HTTP状态、传输超时/取消回调、最终SDK回调到游戏及本服联通尚未验收，D-02未完成。

## 输入和可追溯证据

- 工具：`tools/client72_sdk_response_contract.py` SHA-256 `792dd8f1597b577673caac064cd3da1b3fcd13ca1e906eae382bde901b6628e5`。
- 独立断言：`tools/test_client72_sdk_response_contract.py` SHA-256 `1b2cf31e212533bd88a2426f4137b2418a5779979857f181094e4fcffed33963`。
- 输入清单：`local/migration72/d02-active/sdk-response-astra/input-manifest.json`，26项，SHA-256 `23410d0432a53786b151c5978a9fd1fd318432a36c4cc57fb163f51b2f2f5821`。包含profile、父三卡source/raw、导入工具、自然cold原始证据和12份Java快照。程序逐项核查；另外重新核对profile五文件及父自然11模块磁盘hash，共13个去重文件。
- 父transport source `43b334be3040fa4d266b3462985313ab6fd99e29a148ec7dc682426a41881237`，父login source `53da43c970a179699d80a1eab570bff47b9b619d0424c83597766b96d670cad2`，父crypto source `9b8c8f10f88260bbd3dfc2c62b76632c3fad9a0e0d9d7de2fb74e04f22b66395`。父三卡未改；父专题允许主线追加审核，所以不将可变父doc作为代码输入。
- 认证三源使用主线日志修正epoch1：AuthenticationHandler `0c839b9db6f36debdc2ed03f45bcf5d29f16bdebf5bc441e205061d3adbe732e`；MaPassportAuthenticator `6b31741deba5dce597438c3cf46e3f3342760a23fd2c8dc7ef9e3cba79d81488`；RSADecryptionUtil `e559eb977c51dfb6f85f837cac49e8553e6a8f9c0c454a2a97a13b9e9b55fae5`。
- 另固定DefaultAuthenticators的本卡epoch0快照 `ea46b557f4b7626aebab626aaf871210d850f66c23b34b90253112a53a578f3f`，用于combo业务对照，不代表之后的工作树版本。该快照的SessionKeyAuthenticator仍含账号/token前缀日志，已通知主线负责移除；本卡未修改生产。
- REA只分析同一9MB SDK，最终导出 `rea-final.json` 3506583B / SHA `3ffeabe2276021f9bd4fcdc77a3172cb3fd52b93cfde706a18d7c401f9e3528a`，152条记录；`rea-snapshot.json` 5334646B / SHA `036e113fbe4411692d64c8e14fbc558ea793a8e1fb3e2a19b8bdfcb9449ca7f2`。已close；未开EXE大分析或live session。完整provider限制保存在导出及最终raw，反编译名不当原始源码名，终止调用/缺.pdata边界明确保留。

SDK统一以RVA表示，VA=模块基址+RVA；固定PE首选基址为 `0x180000000`，不能用于自然ASLR地址猜测。最终raw含34个完整.pdata窗口或明确标为有界入口窗口的机器码、反汇编、SHA和9组精确虚表槽。`340E90`、`156530`没有.pdata，导出只称有界入口窗；原生调用仍实际执行到返回/指定边界。

## 准确回调和处理顺序

1. AccountPlat账户worker `34ECA0` 创建success functor，VT `6C5430` 的+10槽为 `34B5D0`。其RDX是“指向响应C字符串指针的指针”，不是字符串本身。
2. 没有覆盖callback时，functor+48取jobber；jobber VT `6C5390` +28→`359F50`，读取jobber+68保存的std::function implementation。其生产者是jobber+20→`359EC0`，`35B820`接收service `32F460`克隆的函数对象。
3. service callback functor VT `6C2C00` +10→`340E90`，把RCX调整到“service指针槽”，解引用响应指针，再进入 `32F640`。
4. `32F640` 构造error/data CJsonObject及UserInfo，调用AccountPlat service VT `6C2CA8` +198→`342B10`。success后才复制UserInfo到service+38，更新状态、执行 `3448D0` 和 `345910`阶段链。失败分支另处理错误/UI，不能把parse返回0当SDK最终登录成功。
5. 本卡以准确writer布局构造合成functor，实际走 `34B5D0→359F50→340E90` 21条机器指令，在 `32F640`入口停下；64位owner槽、owner值及响应指针均相等。此边界没有伪造返回值。独立执行下述业务consumer，不声称整个UI callback生命周期已动态覆盖。

| 入口 | Win64参数/返回 | 原始执行范围 |
|---|---|---|
| `342B10` | RCX service；RDX NUL终止response；R8 error CJsonObject*；R9 UserInfo*；栈+28 data CJsonObject*；EAX有符号状态 | 完整返回；调用实际RequestManager、engine parser和用户decoder |
| `2F6190` | RCX engine；RDX response；R8可选whole JSON；R9 int32 retcode*；栈+28 std::string message*；栈+30可选data JSON*；AL bool | 通用HTTP JSON解析，不认证 |
| `338990` | RCX service；RDX data CJsonObject副本；R8 UserInfo*；AL bool | 取user_info，通用decoder，再取单token与覆盖字段 |
| `31F3F0` | RCX helper；RDX user_info序列化C字符串；R8 UserInfo*；AL bool | 原始JSON解析及字段写入、links vector插入 |
| `337B20` | RCX service；RDX data JSON；R8 UserInfo*；void | refresh专用new_token消费 |
| `33CA40` | RCX lambda捕获区（functor+8）；RDX response C字符串；void | 完整cookie响应解析/缓存更新/错误淘汰，报告入口只观察不替换 |
| `3C91A0` | RCX combo；RDX输出CJsonObject*；R8可空message；R9 HTTP data JSON；栈+28 UserInfo*；RAX=输出64位地址 | HTTP retcode已由caller判断；这里原生转换内部SDK返回JSON |

## 登录HTTP响应合同

这些是JSON字段名，没有protobuf字段tag。保守的生产成功形状是 `retcode:number=0`、可选`message:string`、`data.user_info:object`、`data.token:{token_type:number=1, token:string非空}`；aid/mid必须按字符串保留。原生解析器本身比此生产合同宽松，不应依赖其宽松边界构造登录成功。

| 字段 | native类型与省略/错误行为 | Java对照 |
|---|---|---|
| `retcode` | int64节点取低32位或double截断为int32；本层初始-1。缺失/string不改；非零直接返回。实测0.75截断为0 | DTO int。成功0，认证失败负数 |
| `message` | 只接受JSON string，复制到std::string；缺省保留空 | DTO String；不是成功必需字段 |
| `data` | 存在且非null则复制；缺/null使后续user解析失败 | LoginData；错误data=null |
| `data.user_info` | 缺/null→schema错误（service+8初始-2）；原生实际也接受可序列化的JSON string或空object，返回parse成功但未验完整登录 | UserInfoData应输出object，不利用宽松行为 |
| `user_info.aid` / `mid` | 字符串路径保留>2^53文本；aid覆盖uid别名。numeric aid不保证文本相等 | Java String，来自account id |
| `data.token` | 单对象路径。存在时token_type默认1；int/double覆盖；token只取string，写入UserInfo+170，即使token_type=0也写+170 | LoginByPasswordResponseJson已有单token/type1；expires_in本段不消费 |
| `data.tokens[]` | 不被这条consumer读取；不能替代单token | VerifySTokenResponseJson现用数组，是session/verify格式缺口 |
| `data.new_token` | 不由338990读取；随后337B20消费。type默认1，非空token：type1写+170，否则写+150；空token不替换原字符串 | 当前verify响应无此字段。无需为了最小修复额外模拟刷新 |

原生case覆盖：完整、null/空/畸形正文、缺/string/fraction/nonzero retcode、缺/null data、缺/null/string/空user_info、缺token、错误类型、默认type、type0、verify数组。null/空/畸形及缺/错retcode返回-1；data/user缺失为-2；非零retcode -101保留。普通retcode成功与schema成功是两层不同结果。原始parser/decoder的call和return均在raw记录，不能用通用JSON解析AL=1替代全部结果。

`31F3F0` UserInfo是0x300B；原始constructor `156530`、destructor `1552F0`。std::string是0x20B，长度+10/capacity+18；短串内联，否则+0存64位指针。主要布局如下（字段来源为固定机器码；粗体语义由合成样本执行覆盖）：

| 偏移/宽度 | JSON来源与转换 |
|---|---|
| +0/+20，各std::string | **uid后aid / mid；64位ID用文本** |
| +40/+60，各string | name / email；338990再以account_name覆盖+40 |
| +80 BYTE | **is_email_verify经原生转字符串后与"0"比较；缺字段也得到true，数字0/字符串"0"为false** |
| +88/+A8/+C8/+E8，各string | area_code/mobile/identity_card/realname；338990以identity_code覆盖+C8 |
| +108/+128，各string | safe_area_code / safe_mobile |
| +148 DWORD | **通用decoder token_type默认0；登录单token层默认1** |
| +150/+170，各string | **通用token / stoken；登录单token强制写+170** |
| +190 BYTE；+198/+1B8 string | **is_guest bool-only**；guest_id/account |
| +1D8 BYTE；+1DC DWORD | is_login bool-only；login_type数值 |
| +1E0 string；+200 DWORD | payload；**channel_id默认1，非零37样本** |
| +208/+228/+248/+268，各string | asterisk_name/accessToken/deviceId/country |
| +288 QWORD；+2F0 QWORD | **thirdLoginTimestamp / loginTime；2^40以上非零精确保留；2^53以上样本经原始数值解析发生舍入，不能宣称所有JSON数值64位无损** |
| +290/+2B0，各string；+2D0 DWORD | account_display_type/imageName；loginPattern数值 |
| +2D8/+2E0/+2E8，QWORD指针 | **links vector begin/end/cap；元素0x60B，nickname/thirdparty/union_id三string，两个独立非空元素、空列表和错类型覆盖；实际destructor释放vector storage** |
| +2F8 BYTE | **agree_persistent_login_data bool-only；缺字段初始true，数字1不覆盖，false会覆盖** |

UserInfo上既有字符串可在字段省略时保留，但数值默认写入并不一致；尤其不能假设tokenType会自动保留。顶层ext_user_info和其他Java附加字段未在本段消费；不据此声称整个SDK任何其他分支都不消费它们。

成功后 `345910` 从service+3D8注册表按阶段推进。原始constructor注册7项；stage0 `344A30` 读取 `reactivate_info.required` bool及ticket，true会转UI；部分AccountPlat覆盖槽为销毁JSON后false，stage4依赖Risk/realname组件；stage6 `345840→33BDB0` 发布成功和最终回调。表非空不等于阶段完成；本卡未用空表或固定false桩跳过这些guard。

## 会话恢复、cookie交换、combo分别闭合

### Session verify

`331730` 是AccountPlat `LoginByToken`。type1且mid非空时，实际构造：`mid:string`、`token:{token_type:1,token:string}`、`refresh:true`；Config+90/`3773C0`取 `tokenVerify`，RequestManager+ C0→`3A5990`。type0或缺mid的分支会取 `getTokenByGameToken`，不能混为同一路由。

本卡原生执行到 `3A5990` 入口，27062条指令，字段集合、非零64位ID文本、type1和token内存相等；在该边界停止，没有提交网络请求或假定其成功。合成UserInfo明确is_login=false，Application/UserCenter只提供存在性前置、无UI调用；首次采用constructor默认is_login值触发UI接口而缺具体对象时在 `331A4F`失败，保留在probe10，未用该接口成功桩掩盖。

success lambda VT `6C2FB8` +10→`3409C0`：有外部override callback则转发；无override则克隆先前UserInfo，进入 `332F60`。后者再走service+198 `342B10`，成功后发布用户、`337B20(new_token)`、`337FC0`和阶段链。若只给tokens数组，generic parser将type置0；本卡已用准确formatter `342460`闭合其选择+150而得到空combo_token的影响。完整自然恢复仍需要真实路径进入此无override分支。

### Cookie exchange

service `33C130`（literal GetCookieTokenBySToken）读取helper当前用户和以stoken为key的cookie缓存；RequestManager VT `6D2218` +E0→`3A5E00`→AccountPlatRequestManager `3ADC20`。原生构造 `mid`、`dst_token_type=4`、`src_token{token_type=1,token}`，经 `exchangeToken`路由到 `3AC990`，再NetworkManager+18。原生producer已执行到 `3AC990`边界，20468条指令，结构和值仅做内存比较。

success functor VT `6C27A0` +10→`33F920`→`33CA40(functor+8,response)`；捕获区+40为service指针，+48为UserInfo副本，+1B8为该副本stoken字符串，+348为旧cookie字符串。consumer先执行真实HTTP JSON parser，再只取 `data.token.token` 非空string；没有检查返回token_type。type4、type9和省略type的三样本均入缓存。它不是用返回type4选择game-token槽。

helper位于service+368；cookie map在helper+3E8，sentinel+3F0、size+3F8，mutex+398；节点+10是key std::string、+30是cookie std::string。成功原生复制字符串到缓存；retcode=-100原生删除对应node/字符串并把size从1减到0；-101保留错误；retcode0但token缺/空/错类型→-1。没有外部caller callback的合成上下文只关闭结果投递，consumer/report/cache全部执行；报告状态由原始 `33E210`入口R9D观察，未替换报告函数。该缓存含凭据，不作为主线RPM采集建议。

当前Java exchange回显输入token的形状与该consumer相容，但不证明服务器认证语义或安全性正确。现有注释将type4称game token、将其视为combo必经，超出了本SDK证据；可修正注释，不得借格式卡改变认证策略或为错误响应伪造成功。

### Game combo

`login_login` action `3CBE30` 将LoginManager回调接到 `3CB120`；`login_account_plat` action传入不同caller callback，不能混为同一流程。`3CB120`解析内部SDK envelope的 `ret`（不是HTTP retcode）；ret0时读data.login_type。非1分支把open_id映射为内层uid，把combo_token映射为内层token（guest例外），送入 `3BE5C0`；type1有独立直接返回分支。

`3BE5C0`原生构造外层 `app_id/channel_id/device/data/sign`；前四项来自字符串map，data是内层JSON字符串，sign由CryptoManager+58对map和config model+130输入计算。本卡不宣称该签名算法/自然secret已闭合，也不关闭签名。Config+80→`376EA0` 从config+860的 `MiHoYoSDKRequestUrl` map读取 `comboLogin`；NetworkManager+10提交，success绑定 `3CA000`。这与AccountPlatform固定环境表是两个来源。

`3CA000`先经RequestManager检查HTTP retcode，再处理heartbeat/fatigue及UI，调用 `3C91A0`转换。`3C91A0`合成四例完整执行：复制combo_id/open_id/combo_token，account_type缺/错默认1；`data`必须是能解析的JSON **string**，其中guest必须JSON bool才输出guest。直接给data object不会生成guest。输出内部envelope `{ret,msg,data}`，附加channel_token来自此前UserInfo选择的+150/+170；本段不创建login_type字段。空输入也返回转换对象，不能当完整combo认证成功。

`3CFF40`解析最终内部结果，把data复制到combo+168，并向DeviceCheckManager发布app_id/open_id/combo_token等；该后续只做静态证明，未跑完整UI/游戏回调。Java `ComboTokenResJson`已有字符串data默认guest=false、account_type=1、heartbeat=false；SessionKeyAuthenticator成功返回open_id/account id、combo_id和新login token。请求DTO的app_id/channel_id是int，而native构造字符串；实际Javalin/Gson输入转换未在本卡另行运行，不能仅凭类型声明宣称不兼容。HttpServer使用new JavalinGson；null省略的完整wire行为需主线HTTP测试，native本卡已覆盖缺省和null两种错误data。

## 仅限响应格式的Sol生产规格

主线已审真实代码、模型和原始证明，第三`main-third.json`为同一1,518,209字节/SHA `77ffd6e3c378000456608197cd8ca197a6183c9b4378198ae969cf48f0442992`，三报告逐字节相同，49原生样本/18项检查通过；证明`main/final-context-response-review.json`、日志`main/sdk-response-main-tests.log`。以下规格可供恢复后的独立实现；用户已要求当前批次收口后暂停，本轮没有开启该生产修改。准确combo配置来源、natural与签名等未知继续保留，格式卡不等本服SDK通过。

已满足离线依据的最小修改：为 `/account/ma-cn-session/app/verify` 分离响应适配层，成功给 `data.user_info` 和单个 `data.token`（token_type=1、token为已有认证成功返回的同一session token）。继续复用已有验证流程，不能重新生成/轮换token，不能采用空值，不能因本卡fixture成功就绕过密码/会话校验。旧 `.../token/verifySToken` 路由继续现有tokens数组；本分支只面向7.2，保留旧路由形状是现存业务调用兼容，不是维护7.1运行环境。

- 字段/tag：JSON键，非protobuf；retcode int、message String、data object；user_info沿用现有aid/mid等string及links list；token object含int token_type=1和String token。不要把aid/mid转double，不要把data/token序列化为JSON字符串。
- 错误：原来的retcode/message/HTTP状态不改；data保留错误null/省略行为。不能在认证失败时为了满足consumer补发单token。
- 顺序：已有认证与数据库策略执行完→检查原结果retcode→只适配成功的response DTO→现有ctx.json。不得在DTO适配前执行第二次认证、数据库写入或key生成。最小修复无需new_token；若将来有真实刷新必须另外建独立合同。
- 建议允许范围：AuthenticationHandler中的session/verify注册及独立handler、一个小的session响应DTO/纯适配器、相应认证/HTTP定向测试；可更正exchange注释。MaPassportAuthenticator只有不可避免的纯结果适配位置允许窄改，业务token选择、默认值、数据库/错误分支不得改变。
- 禁止范围：原始五文件、原生验签/信任、patch/security、CryptoManager、route选址、密码算法、数据库、dispatch key4、商城/充值、session采用策略。本卡不直接修改任何生产文件。
- 实现验收：实际HTTP成功/错误序列化断言；session路由给单token且type1/非空，legacy verify路由仍给数组；非零64位ID字符串、嵌套links、认证失败不出token；独立DB旧账号/密码/保存回归由主线执行；然后将真实Java生成的合成响应**只在内存**送本卡原生parser+formatter，确认type1与comboToken内存相等。任何真实凭据/合成正文/密文及其hash禁止落日志。

## 模型、首次失败、首缺输入

底层只模型化存储分配/释放、单线程mutex/condition、CRT退出回调注册。原生字符串、JSON parser、schema guard、map/vector、析构、formatter、cookie错误分支均执行。malloc(0)在空JSON打印器中需要合法存储，首probe1失败后明确模型为分配1B，probe2才通过；不是把parse返回值改成true。route全局initializer首次缺CRT退出注册环境落入未映射导入，probe7/8保留；补同父卡的退出注册模型后probe9通过。verify首次UI上下文不足保留probe10；将输入用户明确设为未登录状态后probe11实际到请求入口。模型不执行真实线程、TLS网络、OS设备生成、注册表或进程退出析构；service/helper singleton留在fixture生命周期，已单独验证调用者UserInfo/vector析构，不宣称全进程无泄漏。

当前唯一首个**combo route来源**缺口是 `Config/server_pc.json` 被选择的固定资源内容/loader身份。已闭：`364910`先从config+8C8路径下读该文件，失败再 `140940`调用global `7AD238`的std::function+10取得字节，随后global `7AD278`负责释放；按env数值选数组元素，`3646A0`取url对象，`380E60`更新config+860 map。普通GAME_PATH文件名搜索未见server_pc.json；不能据此断言资源包内不存在。资源loader的具体producer/managed入口尚未闭合，当前不可把Java已注册combo路由当SDK选中值。

下一可独立离线动作：沿固定SDK global7AD200/7AD240函数对象的注册来源以及EXE对应文件回调，或固定客户端资源包索引，取到 `Config/server_pc.json`资源身份及只含白名单host/path的分类；无需重跑EXE整Ghidra。若之后必须自然观察，应在主线既有批次里仅观察准确读取入口/选址后的固定route分类，不读取整个JSON资源或任何账号参数。当前没有提交未证writer的复杂指针链给主线RPM。

最小自然entry合同（供后续独立审定，不是本卡安装许可）：SDK输入SHA固定；在 `342B10`仅记进入/返回、有符号状态和所选入口身份，不读response正文；在 `331730`仅记进入和公开分支分类；在已核 `41E6F0` option10002只做白名单host/path内存分类，未知记unknown且不记原文/hash。保持所有参数/返回/LastError及原始校验不变；本卡不写/装DLL。自然ASLR地址须实际module绑定，不能用首选VA。准确代码窗hash见raw；例如342B10..342CB0为 `5de925a24581314981e2507105345535c6830c47262d2a823cb12caa523abb37`，331730..3323D9为 `6dcbe9af6823d7146570b54ac8a39d46bb48b997fdef0ab113572b4d03ca6234`。

## 本卡验证和第三复验

49个原生样本、18项独立断言通过。两个新Python进程分别生成 `final-first.json` 和 `final-repeat.json`，均1518209B、SHA-256 `77ffd6e3c378000456608197cd8ca197a6183c9b4378198ae969cf48f0442992`，独立逐字节比较相等；候选报告同SHA。收口manifest为 `hashes-and-determinism.json`，固定本卡三文件、输入清单、最终raw/REA、probe失败历史的SHA。禁止把该数量代替自然客户端/服务端SDK验收。

主线第三复验（新输出名不可覆盖，输出只在忽略目录）：

```powershell
$sdkResponseGamePath = & tools/game_path.ps1
python tools/client72_sdk_response_contract.py --client $sdkResponseGamePath --output local/migration72/d02-active/sdk-response-astra/third-main.json
$env:SDK72_RESPONSE_REPORT = 'local/migration72/d02-active/sdk-response-astra/third-main.json'
python -m unittest tools.test_client72_sdk_response_contract -q
Get-FileHash local/migration72/d02-active/sdk-response-astra/third-main.json
```

没有Git提交、生产修改或部署；本卡只交主线审核。父卡、共享索引、配置/资源与其他代理文件均未修改。
