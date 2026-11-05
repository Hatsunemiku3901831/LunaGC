# 国服7.2详细交接

更新时间：2026-11-05。研发已按用户明确要求暂停：完成当前子任务后先暂停，再完成交接。未经用户恢复，不继续运行下一项研究、修改运行内容或部署。原目标仍是A-04d/B-02沿任务路径做到D-02完成并验收；**D-02尚未完成，不能标记目标达成**。

本次写入前检查了HANDOFF2.md：文件不存在，因此无旧文件可备份，本次为首次创建。以后每次交接必须先备份原文件到忽略目录，再覆盖本文件；HANDOFF.md原则上只追加。

## 1. 先读与工作区

按AGENTS.md依次读README、Project、Harness、CLIENT_72_MIGRATION、CODEX_ONE_CLICK_DEPLOY；认证工作另读三个ACCOUNT_AND_SAVE专题。Project管理任务/验收，Harness管理复现索引，详细会话证据集中在[CLIENT_72_SESSION](docs/CLIENT_72_SESSION.md)。旧章节是历史实验范围，后续章节明确记录消除的边界，不把旧opaque模型误当最新实现。

当前分支`7.2-dev`，本次研发提交`c7556a0`。暂停时另有两项用户既有差异，必须保留且不要纳入研究/交接提交：

- 根AGENTS.md：编码规范和交接要求等修改；已阅读遵循。
- resources子模块：`ScriptSceneData/flat.luas.scenes.full_globals.lua.json`修改。子模块HEAD仍为`dac2a9d43332ef51879522cf39da2831b0ca0543`。

patch子模块HEAD仍为`f2874103ab70afc56260ee12d1eaca96247933b2`，本批未修改补丁或子模块指针。所有提交仅本地，未推送。共享文档使用相对路径/配置变量，不写客户端绝对路径、凭据、私钥、日志或数据库。

## 2. 完成状态及验收范围

| 任务 | 已完成的实际工作 | 尚不包含 |
| --- | --- | --- |
| A-04d5a/d | client seed来源、保存；解密后seed/XOR/MT64会话密钥与Java对照 | SDK真实初始化 |
| B-02c2 | 响应token保存至会话，再经真实Login调用参数到wire | 完整Login调用者前缀 |
| A-04d5b | 四个登录请求/响应业务身份与CmdID | 全部字段/P0闭包 |
| A-04d5c/e/f | 限定环境的构造和两请求发送链至封包入口 | 封包函数体、另一传输/socket |
| A-04d5g/h | 原生seed公钥验签/RSA/Base64，接入完整Token构造 | 实际SDK初始化/客户端运行 |
| A-04d5i/B-02c6 | 响应wire→原生解密/SHA256/验签→4096字节密钥；明确本地签名信任输入 | 原始服务器有效签名、补丁实际信任配置 |
| A-04d5j/B-02c7 | Handler入口至warmup分支、密码学、UID及token保存 | 后续robot-check/版本/UI、真实PlayerPrefs |
| **A-04d5k/B-02c8（当前已收口）** | SDK认证来源检查/会话写入→真实seed构造→Token getter/writer，新增5字段 | 完整JSON parser、回调尾段/实际SDK调度 |
| A-02b | 动态发送来源审计和漂移守卫 | 运行期插件/事件/P0闭包 |
| D-01a | 4个命名HTTP/SDK入口参数前缀，12组离线样本 | 完整函数、钩子安装/实际加载 |
| E-01a | 固定资源候选7表及849条选定正引用 | revision对齐、Lua/场景/最小资源全闭包 |

四个登录消息身份：

| 业务 | 目标类型/index | CmdID |
| --- | --- | --- |
| GetPlayerTokenReq | NJAALDCMHEP / 82491 | 24604 |
| PlayerLoginReq | KOHBOMDDFCB / 76410 | 23172 |
| GetPlayerTokenRsp | CLDBKDNJHFG / 37481 | 27808 |
| PlayerLoginRsp | NDENMDAIAMC / 40503 | 20835 |

四消息合计35字段有语义来源：TokenReq 7、TokenRsp 5、LoginReq 18、LoginRsp 5。整个mappings还含PacketHead的2个verified字段，不能把全文件37计成登录35之外的新进展。Token原始writer共28字段，Login外层55字段；原始wire恢复不等于全部业务语义确认。

当前子任务新增：

| 语义 | 源头 | session偏移 | Token偏移/tag/类型 |
| --- | --- | --- | --- |
| account_uid | JSON data.open_id | +78 | +18 / 9 / string |
| account_token | JSON data.combo_token | +F8 | +40 / 2 / string |
| channel_id | SDK info_get_channel_id，经UInt32解析 | +138 | +B0 / 3 / uint32 |
| account_type | 可选JSON data.account_type | +13C | +A4 / 15 / uint32 |
| is_guest | 可选JSON data.guest | +15F | +C4 / 11 / bool |

account_uid是字符串，样本`9007199254740993`无数值转换；响应uid为uint32，与账号ID不同。响应token/tag6→session+98→Login请求+D8/tag1也不同于请求account_token/tag2。guest在原生路径中先清零；缺account_type保留原值，本组夹具预置0。

## 3. 本批实际测试、产物与提交

当前源代码及文档提交：`c7556a0 research: bind CN72 SDK credentials to native Token wire fields`。

- `trace_client72_auth_sources`原生9组两次运行通过，trace和共享摘要逐字节相同。4成功来源：非零、uint32最大、高数字账号文本/可选字段缺失、account_type溢出回落0；5拒绝：错误ret、缺open_id、空token、无效渠道、渠道溢出。拒绝时会话凭据未被改写。
- 现有完整Token工具5组重跑通过；复用函数抽取前后trace/摘要逐字节相同。
- 160项研究工具/映射/资源审计回归通过；聚焦回归24项通过。这里不是Java完整集，也不是160个真实游戏场景。
- `validate_protocol72.py`退出0；`--ready`退出1，未核实P0/字段继续拒绝发布。
- `git diff --check`通过。本批只修改离线工具、测试、映射摘要和文档，没有运行Java/正式Proto/配置/补丁/资源变更，因此未重启、未部署。

本机忽略证据，均相对`local/migration72/`：

- `evidence/auth-sources-trace.json`、`auth-sources-trace-repeat.json`、`auth-sources-observations-repeat.json`、`auth-sources-determinism.json`。
- `evidence/token-complete-auth-refactor.json`、`token-complete-auth-refactor-summary.json`。
- `evidence/auth-sources-mapping-check.json`、`auth-sources-mapping-ready.json`。
- `logs/auth-sources-tests.log`、`auth-sources-focused-tests.log`。

已提交共享摘要：`protocol/cn-7.2/auth-sources-observations.json`。原始目标二进制、反汇编、私钥和完整trace留忽略目录，不能加入Git。

最近检查点：`b0ca919` Handler核心；`caa7bea`响应密码学；`cdbc275`完整Token；`823762e`原生seed；`db5245e`补丁入口ABI；`8638ea5`发送链；`423330e`Token外段；`e87baa7`动态来源审计。详见Project任务卡。

## 4. 可复现入口

恢复后先核对本机`.env`/`.env.local`的GAME_PATH与portable配置，不点执行环境文件。Python不会自动读取.env。输入必须匹配`protocol/cn-7.2/profile.json`/工具HASHES：

- YuanShen.exe：`8b014f64b5dc12fbc476e0c362b053abfdd7a1378cd282e4ad81057621dbd6b5`。
- global-metadata.dat：`45dc7a44dc9fc187146c52f6c33de57e2d1c1dcfec57f8736431553460f6ef2a`。
- startup-metadata.dat：`b314810840ca6f495822c9c44483dde2394b9f09ed3c6b13c2782092f04d5105`。

本批入口（暂停期间不再执行）：

```powershell
python tools/trace_client72_auth_sources.py --client $env:GAME_PATH --output local/migration72/evidence/auth-sources-trace.json --summary-output protocol/cn-7.2/auth-sources-observations.json
python -m unittest tools.test_client72_auth_sources tools.test_client72_token_complete tools.test_validate_protocol72 -v
python tools/validate_protocol72.py
python tools/validate_protocol72.py --ready
```

最后一项预期非零，不能为了通过将未知项标verified。完整160项模块清单可从本机`auth-sources-tests.log`中的测试类取得；Harness/各专题保留各批独立复现命令。原生工具默认第三方Python库在`local/migration72/python`；Java对照使用本机便携JDK17，先核对实际目录再设置JAVA_HOME。

## 5. 当前代码与证据关键位置

`tools/trace_client72_auth_sources.py`：`AuthSources72(TokenConstructor72)`复用会话/SDK模型。入口14F1917D0，成功停止14F191E18，错误ret停止14F191882，必需字段/渠道拒绝停止14F192592。owner55739/JHOMPMDBELI、方法463149/MKOCIOCEIEN的日志literal59246为`Genshin Start Log: LoginMainPageContext OnSDKLoginCB`。

SimpleJSON.JSONNode8987虚槽：get_Item槽6、get_Value槽8、ContainsKey槽14是容器模型；get_AsInt槽22/1471680C0、get_AsUInt槽24/147168150、get_AsBool槽34/147168440实际原生执行。下层十进制/规范bool解析为明确模型。缺键检查从usage row701114解出kind1/source typeRef472036→JSONLazyCreator9000，缓存registration1428A5460+48→145886000、目标cell145891A78。声明和目标代码共同约束，不单凭相似类名构造模型。

`tools/trace_client72_token_complete.py`的`construct_native_token`是真实seed helper+构造复用入口。auth来源与构造分段运行、共享状态；它会观察helper原始返回指针并检查保存到request+90。Token writer之后独立parse_wire核对五字段；没有用预先声明的字段值代替原生会话读取。

响应工具：`client72_response_crypto.py`执行native RSA/SHA/验签，`trace_client72_handler_core.py`从1507BE0E0连续到15A4E0D9A。有效响应使用明确的离线本地信任输入，不覆盖验签结果。仓库SigningKey与目标原始签名公钥不匹配；目标解密私钥的公钥部分匹配仓库4_Pub.der。此前patch强制验签成功的钩子没有启用，不能作为真实验收方案。

发送链：151214D20→149935970接口→所选AKFCDHJBCHD槽8→1472CD100→14A447E00入口。最后函数体尚未执行。Login调用参数现有分段证据从1507C21EB开始，RDX取session+98，R8取manager+1D8（语义未知），构造151213730保存token至+D8/tag1。

## 6. 未完成、阻塞与解除条件

没有必须等待外部才能开展的整体硬阻塞；暂停源于用户指令。以下是进入D-02的真实缺口，不能用离线通过绕过：

1. **A-04d/B-02**：其余请求/响应字段、完整Handler/回调、实际SDK前置、P0三层映射未闭合；session+B0→Token tag965仍未知。需要目标来源/独立wire和分段或完整原生验证。
2. **A-03**：114名称/471文件只是静态下界，需继续管理器/事件/脚本/嵌套依赖和真实序列收敛。静态盘点完成不等于P0运行闭包。
3. **B-03/C-01**尚未开始：确定性生成及正式网络接入需消化已核实映射；禁止旧号或未知候选兜底，事件替换后校验最终包，冲突注册失败。只改Proto源并经Gradle/protoc生成Java。
4. **D-01**：真实SDK初始化、字符串工厂/完整HTTP路由、签名信任配置和补丁安装加载未验。需保持真实验证算法，不能把强制验签成功当解决。
5. **E-01**：固定候选DimbreathBot/AnimeGameData提交`d54f6f0d423ded9d055bb740a3e5f3aa8f2667f1`声明CNRELWin7.2.0_R49086080_S49210135_D49230962；本机内置revision为49008628/48722240。七表已核对，但revision、Lua/场景完整性仍缺证据，不能覆盖现有resources。
6. **D-02待开始**：必须在独立数据库/运行数据和核实的7.2构建上，实测新号选人、旧测试号、初始化/场景握手、心跳、退出重登。当前无任何7.2客户端验收通过。

历史Java全量10,563项中217项失败未因研究工具通过而消除。恢复时按具体变更选择适当回归，不照抄历史测试数量或PID作为当前结果。

## 7. 下一项可执行动作（用户恢复后）

优先从已获得证据继续，不重跑已经确定且未变化的整套重型样本：

1. 从认证回调14F191E18后追踪可选字段/调度，或从session+B0的写入来源恢复Token tag965。忽略目录`analysis/sdk-auth-full.txt`已含14F1917D0后续反汇编；这部分还没有正式执行验收。
2. 回调尾段静态线索：allow_community→15217B1B0，allow_ugc→15217A220，allow_pay→session+118，country→+E0，age_group→+184，lrsag→+16C。14F192396调用14EA895F0，页面调用14F18E5A0/14F18B7A0，page+2C4=7后跳15A4DCD76；保护续段最终关联14F1924A1、app_id及14146B130。都只是接续索引，需实际验证，不能直接晋级映射。
3. 可独立继续14A447E00封包体，或15A4E0D9A之后Handler路径/1507C21EB之前Login发送者；目标是减少实际D-02路径缺口，不扩展无关SDK功能。
4. 每个可验收子项及时更新Project/Harness和受影响专题、跑真实样本/必要回归、独立本地提交。运行文件一旦变化，必须完成相应构建/便携部署和日志、健康、TCP/UDP、PID、运行JAR SHA-256核查；前置不满足则明确未部署原因。

补充探索索引均在忽略目录：`analysis/sdk-auth-literals.json`、`sdk-auth-users.json`、`probe_auth_source.py`。ComboUserModel4112/LoginResultModel9886只是辅助命名类型来源，不是当前native callback读取的对象，不能把类型字段直接套到协议。另一候选引用14D280396/owner86532未探索；MiHoYoSDKServer.SetLoginResult1470E3120使用不同JSON库，不与SimpleJSON混用。

## 8. 弯路与避免重复踩坑

- `analysis/current-structures.json`约300MB且可能单行，禁止直接rg打印命中整行。用Python按type index/address过滤，只输出目标行；`research.py`旧types缓存可能缺structures。
- 不对保护字节/int3填充作线性语义猜测；例如认证反汇编14F1923DE..14F1924A0有填充。原生实际执行或明确控制流边界才是证据。
- seed helper的早期inArray异常已确认是夹具遗漏运行时空byte[]常量，不是目标客户端自身错误。现在有正式工具，不要再从旧失败探索重做。
- NativeCrypto72的OS/TLS/分配/随机输入是显式模型；验证/RSA/SHA指令实际执行。140580000..140780000用基本块轨迹加速，其他区域逐指令；块数不是指令数。
- 构造/回调每次使用的寄存器、栈、对象和GLOBAL存储需要按正式工具准备。`Offline72.prepare`不清全部heap/global；`fragment`会替换before_instruction观察器。未知调用应停下定位，不能默认成功。
- 真实请求/响应指针、writer tag、CmdID getter三者分开核查。没有handler错误不等于登录成功；warmup返回-2/-1后原生继续也不等于初始化成功。
- 共享摘要只保存虚构夹具/哈希/状态，不发布客户端密钥、实际凭据或原始抓包。旧资料只用于理解业务，不维护双版本或回退运行环境。

## 9. 运行与交接结束状态

本批未启动/停止/部署服务或客户端，也没有重查当前PID/端口；历史ENV-01部署记录只能作为索引。下次启动前必须重新核对.env/.env.local/config.json、进程路径/命令行、TCP8088、UDP22101及Mongo27017归属。HTTP/SDK/Mongo固定端口按源码核查，不能仅改环境变量声称隔离。

只使用Windows portable入口`tools/local_windows.ps1`及对应.cmd，不启用Docker/WSL/虚拟化。保留已有mongodata/、local/data/，不得清库、强杀未知进程或操作无关客户端。当前研究数据应继续与旧数据隔离，实际配置以本机文件为准，不打印凭据。

恢复工作的第一步是确认用户已撤销暂停，再读本文件、Project任务卡和Harness最新条目，检查Git差异归属。暂停期间不继续下一研究子任务；交接文档提交后，仅留下上述AGENTS.md和resources的既有修改。
