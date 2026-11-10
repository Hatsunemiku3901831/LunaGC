# R2-ROUTE-CONFIG-SELECT：配置后的必要 route 消费者

本批结论：**普通成功账号回调的必要后继是托管 `RequestComboLogin(40108)`；它读取托管 `URLConfig.Instance.comboLogin`，经 `String.Concat` 后直接交托管 `NetworkManager.PostRequest(33107)`。这条路径不读取 SDK native URL-map。** 主线后来报告本轮五点自然 map 分类为 `cn-route-env-0`，这仍属于 stored-map 子出口，不能代验这里的 route 采用、managed env 选择或本服认证。

这是冻结材料的有界静态结论。主线为准确缺口提取了一个原 exception body；本分析没有读取 GAME_PATH/当前 PE，没有 API/native CPU/backend、构建、客户端、DB、部署或 Git 操作。只新增本页与 `local/migration72/d02-resume-20261110/b-route-consumer/`。共享 Project/Harness/HANDOFF 及提交归主线。本分析没有新观察器，也未自行取得自然 map、账号回调或 HTTP 结果；后来的主线 map 通知单独标明来源与待验范围。

## 当前起点与停止边界

- 八点自然轮已验同原配置 writer/parser 接受、非空 env 转换后 DWORD0 store、正常 epilogue 后 native 返回。P5/P6 仅证明分支到达；stored0 不等于托管 env 已选，不重做这八个问题。
- 五点 P0/P1/P2/P7/M0 的构建、两 fresh、第三、24 Python、真实文件门禁和 CheckOnly 已由主线验收；静态 proof 封存时没有该轮自然结果，之后主线用本轮新实时 ready 实际运行90秒 helper，通知结果见下一段。本分析未查询现场，旧 ready/PID/VA 不复用。
- native map 值、native getter 返回/缺失 fallback、托管 URL 模型、真实请求实参、HTTP 接受和本服认证分别验收。正常页、静态调用和本页断言通过不代替自然采用。
- 旧 GitHub 定向分支补查已完成，固定 `hk4e-patch-universal` main `2b589125874b0b96f8694bfad31e34811eba8573` 无新增量。本小后继复用本机原码/metadata，没有新的准确复用目标，不再搜索或 clone。

**静态封存后的主线通知，未混入89项 proof：** 本轮 helper exit0、installed-route-code-bound，DATA `phase5/event2/mask31/facts1023/failure0/route_class256`，固定分类 `cn-route-env-0`，同 writer/frame/M0 lookup/key非空/known/epilogue/nativeReturned均true，25读/9262B；实际报告 SHA `62cc6a700e3e811250e34c3150bdceaca3cf35fd4032277dbecf5a5f828b1b84`。用户反馈正常页、无报错并已完全退出，主线已核相关进程0，正在 stock 恢复；独立 raw replay、全保护末盘和最终自然验收链接仍待主线封存。本分析没有读取该自然 raw或代做现场复核。后继因此明确转向 managed URL 消费，不再为相同问题加 map 观察器；managed `+B80/+E0` 与真实 PostRequest URL 仍未知。

## 准确 caller、AOT 和唯一新窗

原 `MiHoYo.SDK.Win.ComboManager.OnMDKOrAuthTicketLogin`（owner5508/method40103）的成功处理路径在 EXE RVA `197FFE91` 直接调用 method40108，正常返回 `197FFE96`。原窗 `[197FF180,19800520)` 的 code SHA 为 `b775785d7cd5a739fdf4d90a4c6bb2ec1adf75cf3bf3d3b19e54d8d627e9c203`。

该 caller 在 `197FF215` 检查 CallbackModel 的 ret；非0转 `OnComboLoginCallback` 错误分支，为0才在 `197FF24E` 取 AccountModel 并核非空，随后按 login_type 分支。已审普通 uid/token 请求片段最终到上述直接 call。这证明**条件成功回调上的 D-02 必要消费者**，不证明全部账号模式、当前 SDK 页或本轮自然进入了40108。成功账号也可能来自既有会话/票据；不能将它简化成必须输入密码，更不能造 ret0/空账号逼调用。

主线重新核五 stock、固定 metadata/AOT 和原 caller，取得唯一新原窗：

| 项目 | 准确值 |
| --- | --- |
| owner / method | 5508 `MiHoYo.SDK.Win.ComboManager` / 40108 `RequestComboLogin`，flags `81`、slot `FFFF`，三声明参数 |
| 原 AOT | `QWORD[QWORD[VA1422F98E0+70]+40108*8]`；原 table=`1428A54D0`、slot=`1428F3A30`、值=`159801220` |
| 原 caller5B | RVA `197FFE91`，`E8 8A 13 00 00`，相对解码确到 `159801220`；现已是主线重新取到的原 bytes，不只是由旧指令重编码 |
| 原 extent | RVA `[19801220,19801368)`，328B / 76条指令；不是拿邻 method 当 end |
| 原 code SHA-256 | `2fdf917e9fa54a1bf1db172fa32fec4c8c212a404ee57d79e5d8023884bc05ac` |
| 原 pdata | RVA `1B22CBB8` 的12B：`2012801968138019c8794005`，SHA `3b000cc2eece98d94da90ef0fac17ddd1930ee22c7fe7d4f84d3283739deec77` |
| unwind | RVA `54079C8`；xdata读取0B。原范围身份成立，异常语义/展开没有验收 |

原件根为 `local/migration72/d02-resume-20261110/b-combo-request-window/`，extraction SHA `4b6d28e74e687498e32397f92365339363d9236b5fad500ea0c5873c81dc15e6`。主线提取只有328B code、12B pdata、两个 AOT QWORD 和 caller5B，0 CPU/live；本分析只消费这些冻结原件，没有新取第二个函数窗。

## 40108 的直接 route 来源与消费者

metadata 声明为 `RequestComboLogin(string body, Action<NetworkResponseModel> callback, Dictionary<string,string> extHeaders)`。参数行47149/47150/47151及 typeRef33/6748/457586均由冻结签名缓存核对，不按名字给任意对象套布局。

| 原指令位置（EXE RVA） | 可复核的数据关系 |
| --- | --- |
| `1980122B/2E/31` | 分别保存原 R9/R8/RDX 为 R15/RSI/R14；body 与 extHeaders 仍是入参，不读取它们的正文/字典 |
| `1980126B→1272→1282` | 从共同 storage pointer cell `586B600` 取 storage，读 `+B80`，再读该对象 `+E0` |
| 类型绑定 | field20504 为 `URLConfig.Instance`；其声明类型为4313 `URL`，父类型4137 `MiHoYo.SDK.ConfigUrlModel`；后者 field19544 是 `comboLogin:string`、实例偏移 `E0`。`URL` 自己无实例字段，因此此处是继承字段，不是 native Config+860 |
| `19801289→1290→1295` | 第二参来自准确 literal cell `5ACB668`，随后直接 call method3018 `System.String.Concat` / RVA `19A8D050`，返回完整 URL 保存于 RSI。主线只解此唯一编译期 global usage：row767032/sourceIndex777，原 literal 为单字节 `?`。因此是原 `comboLogin + "?"`，没有取得自然 URL 本体 |
| `198012E1→12E8` | 同一 storage cell `586B600`，读 `+B90` 作为 RCX；field20764 声明为托管 `MiHoYo.SDK.NetworkManager.Instance` |
| `19801312` | 原 bytes `E8 79 B5 12 00`，直接 call owner4362/method33107 `MiHoYo.SDK.NetworkManager.PostRequest` / RVA `1992C890`，正常返回 `19801317` |

这里 `+B80/+B90` 来自准确原 load/store，不是屏蔽 metadata raw offset 的高位猜出来。metadata 中 `4000B80/4000B90` 只作声明交叉核对。

原 `PostRequest` 六个显式参数由原 callsite 与独立签名缓存对应：

| 位置 | 原值来源 / 原声明 |
| --- | --- |
| RCX | 同 storage `+B90`，this |
| RDX | 这次 `String.Concat` 的返回值，`requestUrl:string` |
| R8 | 原40108 body，经 R14 原样转发，`bodyString:string` |
| R9 | 新建的 callback wrapper，保留原 callback/this；`Action<NetworkResponseModel>` |
| 原 CALL 前 `[RSP+20]` | 原40108 extHeaders 经 R15 原样转发；`Dictionary<string,string>` |
| `[RSP+28]` 低DWORD | bits `40A00000`，原声明 `timeoutSecond:float`，值5.0 |
| `[RSP+30]` 低DWORD | 原声明 `retryTime:int32`，值3 |

CALL 后的 `19801318..1323` 为正常栈恢复/RET。这里的静态转发证明最终 URL 的准确消费者；没有执行 PostRequest、本次异步请求、重试、发送、成功回调或本服认证。原 null/class-init 错误分支保持，未补 host 成功。

suffix 原件为 `local/migration72/d02-resume-20261110/b-combo-suffix-literal/literal.private.json`，SHA `03a7c24111df037a76af8eacf3d5561d1e448981c2f8f32cb2af6b1bdc2d22ad`；其1B原 cipher SHA `d2e2adf7177b7a8afddbc12d1634cf23ea1a71020f6a1308070a16400fb68fde`，绑定固定 decoder `865c6f7f8fbcea164fa535a12eabc3c9d67ebb1fd0ef3f12c5ec7a1898dce6ed`。它重新核五 stock，但新增代码窗/CPU/live均0；这里只消费冻结结果，没有重新解码任意字符串。`?` 是程序自己的固定 suffix，不能为使旧“精确无query URL”分类器命中而静默去掉它；后继必须区分原最终 URL 与 base route 的分类语义。

## managed env/URL 与 native map 为何不能合并

已有冻结 `ConfigManager.SetEnv(33354)` 完整窗 `[19777010,19777464)` / SHA `43cc16e32fe5b496136fcefb6dfdbd14f8f36f55949f4436330836ef78829cbe`：`19777015` 从 EDX 保存 env，`197770A0` 原 store `ConfigModel.Instance+58`；`197770DF` 调 `LoadLocalConfig`（EDX=该 env、R8b=false），其结果非空的分支在 `19777103` 再调 `URLConfig.GetURL(env)`。

已有 `URLConfig.GetURL(32799)` `[199AE770,199AE84F)` / SHA `d6ae838ffff7049b2fe6050fb42d62ba3a7739fdbe54ff787ea2c3f52ca2d141`：`199AE7A8` 以 env/false 调 `LoadLocalConfig`；成功且返回模型 `+B0` 非空时，调用 `URL` ctor32801，再在 `199AE7EF` 写共同 storage `+B80`。另一分支在 `199AE80F` 读取**此前的 `+B80`** 返回。它不是自动等于 env0，也不能由 getter 非空认定这次刷新成功。此处 constructor 的字段复制正文没有被本卡执行或补证。

另一路 `LocalConfigSync(40117)` 的既有原 callsite `19807756/59/5C` 从 `ConfigModel.Instance+58` 装 EDX env、R8b=true 调同一个 `LoadLocalConfig(33350)`，取返回模型 `+B0` 序列化供 native `url:string`。true/false 的具体缓存/选择含义，完整 env/cp_app_id 分支和自然选择仍未闭，本页不把第二参数起名为未经核实的 force/reload。

因此现在有准确的**共同选择入口与两条后继**，但没有证据让二者跨 epoch 或跨对象自动相等。八点原 native DWORD0 store、旧前缀等值和未来五点 map 类别不能填充托管 `URLConfig.Instance` 的自然值。native LocalConfigSync 返回后，已有 `ConfigSyncWithNative(40116)` 原窗还会直接调用 ComboConfigSync、再调用 BoxConfigSync；P1/native void 返回不是这个外层完整初始化或全字段业务成功码。

native 另一条已冻结条件路径 `3BE5C0` 在 `3BEAD4→376EA0→3BEADA`、R9=0 时直接读 SDK Config `+860` 当前 map；`37E3E0` 持原锁复制值，缺 key/空值时返回固定 `https://127.0.0.1` fallback。**Config+860 owner 本身为空时，`376EA0` 跳过 map reader 并返回已初始化的空输出；不能把所有未就绪状态都称 loopback fallback。** 这次 getter 输出对象为 builder 的 `[RBP+10]`，正常送 `3BEC2C` 原 native 发送边，并到 `3BEC8A` 才析构。它属于条件 native combo 流程，不能按 `login_account_plat` 页或40108名字把它认作当前托管请求。无需再取 native getter/发送窗或重做其 owned 算法。

## 按五点可能结果推进的有限条件

| 未来该轮实际结果 | 允许结论 / 下一项 |
| --- | --- |
| 未到、首次拒绝、partial/no-map | 只报告该固定终态和首因；不借八点结果补成 map 命中，不重复相同90秒碰运气。当前托管 consumer 静态结论仍独立可用 |
| 已知 CN 类别 | 所选 writer 原锁内 stored comboLogin 类别成立；不代表 managed env/URL 或实际请求使用。后继准备准确 managed route 消费，不继续扩大 map walker |
| absent/empty | 在 owner/reader 条件成立时，native getter 静态预期 fallback；不能等同本服接线，也不能据此宣布托管 `URLConfig.Instance.comboLogin` 缺失 |
| unknown 或读取失败 | 保留固定 unknown/失败；不输出未知 URL/长度/hash，不改 env/URL/返回/校验来制造已知类 |

所有分支都不能关闭 full Init、自然 route/key/信任、真实 HTTP/认证或 D-02。上表在静态设计时覆盖全部有限出口；其后主线通知本轮进入“已知 CN 类别”这一行，最终接受/恢复仍以主线 post proof 为准，其余行不作为该轮实际状态。

## 最小后继规格与明确缺输入

**route 采用的最小语义切点**是原40108的 `19801312` 原 PostRequest CALL 前：此时 RDX 已是程序自己完成 Concat 的最终 URL，能避免把资源行、map 或 getter 存值当成请求采用。只对准确原 caller `197FFE91→40108` 的一次同线程同帧调用，在原参数有效期内分类 RDX 的固定预审 host/path 类别；输出只允许固定类别/unknown 与“原 caller/原消费者到达”事实位。body、callback 内容、headers、账号、token、签名一律不读、不保存、不 hash；URL未知值及其长度/hash也不记录。是否实际发送/认证另验。

这个分类合同必须显式处理已经证明的原 `?` suffix：若使用八个既审 CN base route，只能精确接受 `既审完整base + 单个问号` 的原最终字符串类别，并单列为最终请求类。不得剥 query、接受额外 query/value、放宽成 prefix 或把原字节归一化来复用旧 map 分类器。只有 source 合同，无自然最终 URL 值；当前尚无该分类器实现或采样许可。

这只是准确语义规格，**不是已获准的新 hook 或自然采样许可**。实际 loader/最小覆盖、普通 Win64 与 managed string/GC 生命周期、LastError、首次尝试/预算、代码绑定、异常展开及恢复合同尚须由 Sol xhigh 审定；尤其 xdata `54079C8` 未读，不能抄旧 SDK writer 的 ABI/EH 结论。若无足够安全输入，应停在已知 callsite，不写伪 String/对象/TLS、补零页、强制 ret0 或 host 成功。零凭据 SDK 启动没有已证动作能触发成功 AccountModel，**不要求用户先登录官方账号来逼该事件**。

本轮到这里已足够交回明确 consumer 语义，**不再请求或等待新 body**。独立的 managed env/Init 选择首缺仍准确落在 `ConfigManager.LoadLocalConfig`（owner4408/method33350，AOT VA `159776120` / RVA `19776120`）的完整 env/cp_app_id/返回分支；已有 caller 为 `LocalConfigSync:1980775C`（env/true）、`SetEnv:197770DF` 及 `GetURL:199AE7A8`（env/false）。这些剩余未知不由本轮新328B填充。若将来单开选择卡，也须从原 AOT 和 exception 目录定唯一范围，不能拿相邻 method 当 end或扩大 UI/SDK 链。当前固定来源整理、分类器规格和准入准备交 Sol xhigh/Terra，释放 max；只有新的实际语义冲突才重新使用 max。

什么才能称为“自然采用”：在同一个新身份 epoch 下，准确原 caller 到达40108并把程序自己算出的 RDX 最终 URL 交给原 PostRequest，固定 host/path 分类有实际原参数证据，原调用不变。这个事实仍不等于网络发送成功；本服认证必须另有本服 handler/session/账号结果和客户端流程证据。要再称 managed env 自然已选，还需原 SetEnv/LoadLocalConfig/GetURL 的实际输入、分支与模型关系；不可倒用 native stored0 或 stored-map 类别补证。

## 本分析实际审核与完整身份

私有 `audit_frozen.private.py` 只 import Python stdlib。首轮20份冻结输入/36份上下文、83项退出0，原 `audit1.private.json` 为19020B / SHA `7b2e624e79e67c508afcad59d25e3a5ee54ad44dbdffee365e93aa12f8452fae`，v1源码另存原样。最终加入唯一 suffix 原件后，**23份冻结输入/37份上下文、两个 fresh Python进程均89项退出0**，`audit-final1.private.json` 与 `audit-final2.private.json` 逐 byte 相同，20425B / SHA `bf8181a269f807b8482e3859abe4d834f21535dcc08ac7b6a12b5fc81269e2b0`。只在内存改坏首个profile预期SHA的负例被首pin准确拒绝，原输入不改；实际 argv/PID/stdout/stderr/退出记录为 `execution-v2.private.json`，SHA `12127581bda44459156e00eda0299f0bdeb2cb6199abe62cf265eeb2e766edb0`。

审核核328B/76条原 asm 的逐 byte 连续拼接等于 code.bin、原 E8 相对目标、AOT 两 QWORD、pdata、metadata owner/签名/继承字段、typed Post 实参、唯一 suffix 的原指令/usage/cipher/decoder绑定、旧直接边、分列的 native getter/fallback 与全部未知标志。旧 code SHA/伪码只作为已有报告证据，没有重新读取目标 PE或伪装重新反汇编。

最终完整输入清单 `frozen-inputs-v2.private.json` SHA `96f5d8f1d86f5d4a1b39590893791f0cf884d65ed986967b8847aedf82ba47f3`，位于本页对应私有根；原 `frozen-inputs.private.json` / SHA `93da462747bffd54699d4fdbade5ba938db12540e9bfbe3cf5b4f043af9f1ba9` 保留，不回填旧83项proof。以下列出关键输入；清单还保留所有实际消费的文档/源码/上下文完整 SHA，当前共享文档身份只表示当时读取，不将它们冻结成禁止主线维护的 source gate。

| 固定原始身份（未由本审计重开客户端文件） | SHA-256 |
| --- | --- |
| EXE | `8b014f64b5dc12fbc476e0c362b053abfdd7a1378cd282e4ad81057621dbd6b5` |
| global metadata | `45dc7a44dc9fc187146c52f6c33de57e2d1c1dcfec57f8736431553460f6ef2a` |
| startup metadata | `b314810840ca6f495822c9c44483dde2394b9f09ed3c6b13c2782092f04d5105` |
| AccountPlatNative SDK | `adecdf0bd5442e321b1b66b0ca9e98bef76d5067326aa4add5b75266055a6c25` |
| stock Astrolabe | `87e0a005ac46515156636af2120d3f824bcd28d69253e7d7235b959b54a0b442` |

| 实际消费的核心冻结文件 | SHA-256 |
| --- | --- |
| `protocol/cn-7.2/profile.json` | `a3b9eda630942ba8479f713fc1692c7eb7b05198ae382dfe878ecc963051fd5d` |
| `analysis/current-structures.json`（私有根 migration72） | `d5d7f671a622439b2f9a3ed0018ce551aa5c573cd9ca68bed3b006712b8a98aa` |
| `evidence/client72-signatures.json`（同根） | `28377e7a8d3c891cd61be97ddb74f8502a22f2f2f039d6a91dba3b092d7822fe` |
| callback `selected-signatures.private.json` | `d819f62bd288c20d7ba836859f82ad5d420227b9320d6776fe73967d55845104` |
| callback `ComboManager.OnMDKOrAuthTicketLogin.window1.private.json` | `d19cc6ded1a27a6517c4b166425f593643130622f2049f28c5e85e765f75e62c` |
| callback `main-third.private.json` | `6afa48db51d2db15a17de12355b74773cd62ccad8c4625b156f04278a6b29177` |
| config-select `sync-windows.private.json` | `4722fea419f51cb985648ccf0ff5f318c3a32b489c49c25ad8d07c2b25c1a9d1` |
| config-select `explore-windows.private.json` | `8871dd865a9be44f06a2950a8bce9e23ba150d60b9900a731599ace7a280a247` |
| route `main-third.json` | `bae776a1a43c678b2953e69d71b6f198f0418cb966f275b0f8a9b8200bee1471` |
| route `rea-final.json` | `da35fb0b37ef5b661a3b6c76040b9bb3ba1c4119d949ccda06bd28e14603dc53` |
| crypto `final-first.json` | `c91ebc70a0afee16cb9379286fab86a10deea2a1ec8cef10a0c49fc9a7d17313` |
| sign `main-third.json` | `f2f8f3ad6313451839920345bf51d6255fcf496c0d2311f7dad663971379d434` |
| 新窗 `caller.bin` | `dbac616158521da2f0fb43e8e65cb0d9d11997a62f46b9f44c69bc548332af0c` |
| 新窗 `aot-pointer-cell.bin` | `68f5f1908ac9f789c3fc0b5e71c5b8af00962605561f7b8ea2cc4718f00a83da` |
| 新窗 `aot-slot.bin` | `6e295efabf9f728e64418e1f1325b72f1d94e8677378170628385eabe7ce1d9f` |

callback/config-select位于 `local/migration72/d02-resume-20261109/`；route/sign位于 `d02-resume-20261108/`；crypto位于 `d02-active/sdk-crypto-contract-astra/`，新窗路径见上文。原始数据均留忽略目录。

早期一次 rg 在 Windows 将 wildcard 当路径而失败、一次把 minified JSON 按行读造成输出截断、两次对旧缓存 JSON 容器形状的假设失败，均只影响本地投影；改为明确 glob与结构化读取后核对，没有改原输入、补成功或执行目标。仅文档/静态审计，无运行修改，无需重启/部署；本服自然验收增量0，Git提交由主线检查归属后处理。
