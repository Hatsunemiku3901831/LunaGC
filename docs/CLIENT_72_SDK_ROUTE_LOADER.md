# R2-ROUTE：SDK server_pc 配置资源加载链

## 本轮有界问题与退出条件（2026-11-08）

目标是从固定 7.2 SDK 的 `Config/server_pc.json` 读取失败后备链，恢复 `global7AD200/7AD240` 的函数对象注册 producer、准确外部回调或资源索引，取得配置的真实资源身份。只有取得原始配置后才继续核对 `env` 数组选取、`MiHoYoSDKRequestUrl` 与 combo 签名输入；不按名字、root 非空或静态字符串猜本服路由。

固定输入是恢复清单的五个文件，SDK SHA-256 为 `adecdf0bd5442e321b1b66b0ca9e98bef76d5067326aa4add5b75266055a6c25`。首先复用 response/login/crypto 三卡已冻结的 REA 导出与离线夹具，不修改父工具或原始证据。当前已知边界：`364910` 先从 config `+8C8` 目录读取；失败后 `140940` 通过 `global7AD238`（函数对象 `7AD200` 的 implementation 槽）virtual `+10` 获取资源，经 `7AD278` 释放。`3646A0/380E60` 更新 config `+860` 的 `MiHoYoSDKRequestUrl`，combo 签名还依赖 ConfigModel `+130`。

候选解释分别保留：SDK 内部固定资源 provider 注册；SDK 导出 API 接收的 EXE/托管回调注册；客户端包内准确索引资源。普通文件搜索未见只说明该搜索边界未命中，不证明包内不存在。

预算：先读取已有导出并扫描固定 SDK 的该 globals 精确引用、导出与注册代码；如仍缺具体函数反编译，最多增加同一 9 MB SDK 的 12 个准确函数/数据窗口查询。EXE 只允许已证 callback/导入注册点的文件窗口或固定 metadata 映射，禁止 450 MB EXE 整库 Ghidra。新原生离线实验最多 8 组，每组最多 2,000,000 指令；不允许真实网络或目标进程执行。达到预算时只在一个明确首缺输入上停止，不扩展成全 SDK。

验收出口：准确 callback producer 和 ABI/所有权、可复核资源身份；若资源可得，固定私有原文并只输出公开固定 host/path 分类、选择条件、map 与签名输入来源。输入 hash 改变、间接回调未闭、真实索引或调用上下文不足时，保留首次失败、已查边界和准确解除条件。原始账号、headers/body/token、明密文及其 hash 均不得写入输出；配置原文仅留忽略目录。

本卡只可改本专题、`tools/client72_sdk_route_loader.py`、对应测试及忽略目录 `local/migration72/d02-resume-20261108/r2-route/`。主线独占 Git、Gradle/Cargo、DB、端口、客户端/调试器及部署；本卡不更新共享 Project/Harness/HANDOFF，不进行任何 live 操作。仅离线工具/测试/文档无需重启。反编译/离线模型/自然实机范围分别记录，当前本服 SDK 与 D-02 实机增量为零。

## 结果：准确配置已取得，普通磁盘文件缺失不是唯一前置

**已从固定客户端准确 SDK 资源包取得 `server_pc.json`，并闭合另一条真实配置 producer。** 正常托管配置入口通过 `SDKDelegate.LoadFile` 加载包内 TextAsset，再把选中配置的 `url` 对象序列化成字符串供原生 `378850`。原生该入口直接解析 `url` 字符串并写 config `+860`；这里不调用 `364910/140940`。因此，工作目录中没有普通 `Config/server_pc.json` 文件，不能单独判定为自然 combo 的阻塞。

当前仍未观察自然选中的 env、完整初始化参数、实际 combo 请求或本服会话；不能把资源中的 env0、原始 SDK 页正常显示或原生 getter 成功代为实机证据。签名输入来源已闭合，签名算法和自然签名仍未执行。本卡不批准 route patch 或绕过签名，D-02 不晋级。

## 精确 producer 与类型归属

所有 EXE/SDK 地址均为固定磁盘身份下的 VA/RVA，不是当前 ASLR 地址。新增 REA 只开同一 SDK；没有 EXE 整库分析，已导出、close、reset。复用 response 卡导出 `3ffeabe2…e3528a`；本卡 `rea-final.json` 为 3,580,584 B、164 records，SHA-256 `da35fb0b37ef5b661a3b6c76040b9bb3ba1c4119d949ccda06bd28e14603dc53`。

| 环节 | 固定证据与实际边界 |
| --- | --- |
| native callback 对象 | Ghidra exact xrefs 与独立完整 SDK 可执行段 RIP 扫描共得到 8 个已核指令边界引用。`7AD200/240` 只有退出析构取地址，`7AD238/278` 只有 reader、退出读取与清零。无直接注册 producer；不排除间接/外部写入，不称永远为空。REA IDs 为 `ev_ef4d634b8d51d456c4648b9ada939572b614fef1b9c99881c7041752e7a97c82`、`ev_c27a975a594b58c6557b406aea4497847715644e68de9dc7b3be3535d80e6efc`、`ev_66acb73fc79f260c3e60faf55811633e30e92b2aa9a6b77a8ac7562904b86926`、`ev_3dea3804ac61572a79452d47236a4b0a02edd21dcc603c1017896dd12a083efd`。 |
| native 磁盘目录 | EXE `InitNativeEnv` method70156 / `146D7F190` 在 `146D7F217` 调 P/Invoke method70164 / `146D81AD0`。第一个参数来自 `1404CBDA0→1404FD760→KERNEL32!GetCurrentDirectoryW`（IAT RVA `3BAC2F0`）。SDK `13CE60→VT694980+0→132650→Config VT6C6CD8+60→373D10` 复制至 `+8C8`。该路径供 native 文件 reload 使用；当次自然工作目录未在本卡读取。 |
| 托管文件委托 writer | `MiHoYo.SDK.DelegateRegister.Register`，type9857 / method70395 / `146E36690`。`146E369AB..146E369FA` 构造 delegate 并在 `146E369F3` 写共同静态存储 `+1530`；存储 pointer cell 为 `14586B600`。 |
| 真实委托类型 | **`MiHoYo.SDK.SDKDelegate.LoadFile`，type4013 / field18453**。metadata 原始布局编码 `04001530`；运行偏移以准确 writer/reader 证实，不能只屏蔽高位获得。近名 `MiHoYoBaseSDK.LoadResource` 是另一 field48228 / raw `04005A68`，不用于本绑定。 |
| 准确回调 | writer 使用 MethodInfo cell `14597A3E8`。从固定 metadata 独立解 usage734367、tag3 得 methodDefinition71136：`AssetUtil.LoadJsonFile` / `147185B20`，再调 `AssetUtil.LoadFromAsb` / `147185870`。不是按附近类型名猜测。 |
| 相对包名和 asset 名 | literal14111 / cell `145AE5718` 为 `MiHoYoSDKRes/PC/mihoyo_sdk_res`；literal14110 / `145AE5710` 为 `Assets/Plugins/MiHoYoSDK/Resources/`；literal15092 / `145AE75C0` 为 `Config/server_pc.json`。三者均从真实 global usage 解码。 |
| bundle 加载边界 | `147185870` 将 native path getter `14146B300` 结果与相对包名交 `Path.Combine`，缓存 bundle 在共同静态存储 `+8FD8`。未缓存时先尝试独立 `LoadAsb` delegate（`+5A40`），缺 delegate 则走 `14146C630`，最后 `AssetBundle.LoadAsset` / `15A23A2F0` 读取 prefix+asset。根 getter 的符号名、自然 delegate 分支和当次已加载 bundle 未在本卡执行；不把磁盘候选称为自然已选文件。 |
| 真实托管消费者 | `ConfigManager.LoadLocalConfig` / `159776120` 在 `1597761E1..1597761F0` 把 `Config/server_pc.json` 交上述 delegate，再用真实 JSON parser 和数组逐项选择。它还存在 env 与可选 `cp_app_id` 条件、错误/fallback 分支；本卡未执行其完整托管选择。`URLConfig.GetURL` / `1599AE770` 读取其结果中的 URL 模型。 |

SDK 标准 PE 资源表另完整清点为 10 项 CSS/JS/SVG/UI/版本/manifest，无 `server_pc` 项。这只证明该标准资源表边界，**包内文件实际存在**。新增反编译 `373D10` Evidence `ev_474806998ff1eed77bcc387ae5af14151b20e78e7e2edcf00d676b62247d6a6c`；其它新查询/限制完整保存在本卡导出。

## 真实包、对象和私有原文

准确本机载体相对路径为 `${GAME_PATH}/YuanShen_Data/StreamingAssets/MiHoYoSDKRes/PC/mihoyo_sdk_res`，24,223,855 B，SHA-256 `8e33ae960729065d273928d029b03b72e72cdfbee823fc1f94e83d8ab8c59cda`。固定 `pkg_version` 的该项大小/MD5与实际文件一致；旁置 `.manifest` 明确列出目标完整 asset，SHA-256 `b081285e03c4a94e7955a8a8e0a485fcec935f3beb10ee558f5a626692a4048f`。

对该文件实际执行既有 native `1415AA740/760/820` 检查、prepare、头变换和 getters。Blb03/v5、header1940、dataOffset1968、tag3、442 块，解码总长57,849,700 B，无尾。每一块执行原生 prefix 变换及独立 LZ4；首尾两块另完整执行原生 tag3 codec 并逐 byte 对照。没有把两块外推成442次完整 native codec，没有模型 file-open 或 codec 成功返回。

实际目录为 `CAB-71709f7cc1c6ee4d10462329de6a6042`（44,254,500 B）和对应 `.resS`（13,595,200 B）。前者 serialized v17 / platform19 / type-tree，79 types / 13,025 objects。独立有界 parser 读取实际 type tree、object extent、class142 container 和 class49 TextAsset；未执行完整 Unity SerializedFile consumer。

唯一 container key 为 `assets/plugins/mihoyosdk/resources/config/server_pc.json`，fileID0，pathID 的 u64 bit pattern 为 `10246702305728925693`；它指向唯一 class49 `server_pc` 对象，text offset5,995,140、text bytes475,950。该 offset 指解码后首 CAB 起点，不是压缩文件 offset。TextAsset 自带 m_Name/m_Script type-tree 结构同时核对，不能仅由首字节或同名对象认定来源。

原文仅写入每次独立忽略 artifact 目录的 `server_pc.private.json`；完整 decoded payload 同样私有。报告不输出配置原文、app_key/cp_app_key 值或其 hash，也不输出解码后配置/载荷的 hash。输入身份用原始客户端、bundle、manifest和源文件 SHA固定；双跑用 byte equality核对私有产物。

## 配置选择、map 和签名来源的限定规格

真实顶层为16项数组，原顺序 env 为 `0,1,4,6,8,9,22,19,2,3,5,7,11,12,21,20`。每项 `url` 是对象；原生 `378850` 接收的同名字段则必须是**包含该对象 JSON 的字符串**。这是两层序列化，不应直接把资源中的 `url` object 放进原生 Init packet。实际自然 packet 如何构成及所选 env仍待后继观察。

固定国服候选的 `comboLogin` 都为 HTTPS、无 query/fragment，path均 `/hk4e_cn/combo/granter/login/v2/login`：

| env | 固定 host |
| --- | --- |
| 0 | `hk4e-sdk.mihoyo.com` |
| 1 | `sandbox-sdk.mihoyo.com` |
| 4 | `preapi-takumi.mihoyo.com` |
| 6 | `devapi-takumi.mihoyo.com` |
| 8 | `ptsapi-takumi.mihoyo.com` |
| 9 | `api-beta-sdk.mihoyo.com` |
| 22 | `audit-api.mihoyo.com` |
| 19 | `hotfix-sdk.mihoyo.com` |

其它8项只作为该固定资源的分类保留在忽略报告，不构造海外运行环境。上表是固定候选，不是自然白名单命中结果，也不能按 env0 等同当前本服。AccountPlat `passport_env` 六表与本表的 env/url来源仍分开。

`378DDA..378E70` 以原生 `14DE10` 从已解析 Init JSON 读取 `app_key`，非空则经 `138160` 复制到 config `+218`。ConfigModel 内嵌起点为 config `+E8`，因此正是此前 combo builder读取的 ConfigModel `+130`。`3BE5C0` 将该输入交 CryptoManager VT `6D1CB8+58→3958E0`；本卡只绑定来源和真实槽位，不宣称算法或自然 secret 已经验收。

`379701..379882` 只接受 `url:string`，原始 `14E110` 解析后通过 `380E60` 清空/复制字符串 map 至 config `+860`。随后完整 `376EA0` 在 R9=null/empty 时直接读当前 map；非空 override 才走 `364910→3646A0` 的文件/回调 reload。缺 key或空值的原生 reader仍返回 `https://127.0.0.1`，是 fallback，不能视作本服已接线。

主 combo builder `3BE5C0` 的调用点另从真实指令核到：`3BEAC6 xor r9d,r9d`，`3BEAC9` 取 `comboLogin`，`3BEAD4 call [rax+80]`，返回点 `3BEADA`。所以**这条真实 builder静态路径不触发 native文件reload**。签名调用为 `3BEA13 call [rax+58]`，返回点 `3BEA16`。这两个准确 caller/返回点可供主线设计只分类固定host/path、等值bool与状态的后继观察；本卡不执行或批准装载/hook。

静态 `3646A0` 的 empty JSON 分支不会替换 `+860`，只继续 passport环境表的默认选择。因此，已由 Init填入map后，即使额外 native文件reload取不到配置，也不能据此断言旧 combo map被清掉；这项为已冻结指令/反编译控制流结论，未另执行完整 override/CRT链。

本轮只对真实 env0、env1各投影一项 `comboLogin` 与真实 `app_key`，用原始 outer/nested JSON parser、clone、两个精确 writer片段、map和完整 getter执行；没有 JSON/lookup/成功桩。两个正例分别执行 writer URL片段11,805/12,152条，均调用 `380E60` 一次、mapSize1；原始 JSON实际析构后，getter仍与真实资源 URL内存相等，返回64位 caller输出对象。`url` 直接给 object 的负例执行876条、不调用 `380E60`、mapSize0，完整 getter走真实 loopback fallback。app_key字段拷贝三例均相等，只记 bool。

片段入口显式分配相同 Win64帧关系 `RBP=RSP+100`，设 R15=config、R13=0、RSI=-1；CJsonObject由原生 parser/clone建立。它们是分段离线执行，**不是完整 `378850` 返回或 SDK 初始化成功**。模型仅为底层 storage allocate/free与单线程mutex；原生 `13A1B0` 对齐/owner-backpointer wrapper执行。原始 SDK/EXE/Proto、配置和资源均未改。

## 失败与准确剩余输入

保留的探索失败不能混作最终样本：

- 最初依据相同字段名把 `+1530` 当作 `MiHoYoBaseSDK.LoadResource`，实际 metadata布局守卫拒绝后改为准确 `SDKDelegate.LoadFile`；近名字段是独立 `+5A68`。
- 完整 `378850` 先遇 CRT OS状态，再停于原始 CRT locale 的 encoded callback（trace末 `490AF1→591860→591820`）。私有四份 `native-probe-*-failure` 记录保留；最终工具没有添加 atoi/格式化/locale成功桩，也没有继续初始化整个 CRT。
- 直接复用父 ResponseFixture 的临时对象布局处理完整大 URL输入时，临时 buffer覆盖了相邻 std::string header；后续分开输入/object地址。父 Fixture 在 `13A1B0` 的 allocation模型也不包含大字符串对齐owner；最终让原始 wrapper执行，冻结父源不改。
- 完整305项 URL片段触及本卡2,000,000条指令预算，未获完整覆盖；最终只验 `comboLogin` 这一准确投影，不宣称全 map 已原生执行。211是旧托管ConfigUrlModel声明字段数，不是实际资源map条目数；报告按真实对象逐项计数。

下一项可以直接承接**准确原始资源与 loader/map字段规格**，无需重新搜索普通 `server_pc` 文件或等待 native `7AD200` producer。实际首缺是自然选中 env、真正交给原生的 Init `url:string`/`app_key`来源等值关系及完整阶段；再闭 combo签名算法/本服处理、自然 route分类、装载/校验与会话。只需固定字段/状态/长度/等值布尔，不采账号、headers/body/token、签名输入原文或其hash。当前工具不附加live采样或hook许可。

## 复现与验收范围

仅依赖本机已冻结输入、离线 Python/Unicorn/Capstone和已有 archive context，不重新开 Ghidra。输出路径必须新建且位于本卡忽略目录，工具拒绝覆盖。第三复验命令：

```powershell
$sdkRoot = & tools/game_path.ps1
python tools/client72_sdk_route_loader.py --client $sdkRoot `
  --output local/migration72/d02-resume-20261108/r2-route/main-third.json
$env:SDK72_ROUTE_LOADER_REPORT = 'local/migration72/d02-resume-20261108/r2-route/main-third.json'
python -m unittest tools.test_client72_sdk_route_loader -v
Remove-Item Env:SDK72_ROUTE_LOADER_REPORT
```

最终两次全新 Python进程 `final-v3-first.json` / `final-v3-repeat.json` 各471,340 B，逐 byte相同，SHA-256均 `bae776a1a43c678b2953e69d71b6f198f0418cb966f275b0f8a9b8200bee1471`。两份完整57,849,700 B decoded payload和475,950 B配置原文也分别逐 byte相同，不记录其明文 hash。最终13项定向检查全部通过，无失败/错误/跳过；日志 `tests-final-v3.log`。本卡只认 v3最终源与报告，旧draft/v2及探索原样保留。

工具 `tools/client72_sdk_route_loader.py` SHA-256为 `d4816a4448ef2533d20ab7bc656ef617888a79ded69fb3416c5defe24fdf6cac`，测试为 `9fe50460e5133f679f09bfc338ed602a41f9344404dc4fa973bd555c656b94dc`。输入核对还固定Capstone5.0.7 / Unicorn2.1.4的实际DLL与关键Python包装源，不能只记版本名。原生帧/方法owner、真实包与manifest、16环境/305 URL项、单项投影正反例及未验边界保存在最终报告；`hashes-and-determinism.private.json` 记录 source/test/doc/报告身份及私有产物 equality。

本卡不调用Git，提交和Project/Harness实际状态由主线独立审核后维护；不需要构建或便携重启。本服SDK、D-02实机验收增量仍为0。

主线第三完整回放 `main-third.json` 退出0，471,340B及SHA与最终双跑相同；对应完整decoded和config私有产物也三次逐字节相同，不记录明文hash。第三13项检查通过，证明 `main-third-review.private.json`，日志 `tests-main-third.log`。主线审核准确delegate owner、class49/container join、原生map/key writer及R9=0调用点后接受限定loader合同。后继直接研究准确签名与自然Init/env，不再把普通文件缺失或未找到7AD200 producer当作该combo map的必要阻塞。
