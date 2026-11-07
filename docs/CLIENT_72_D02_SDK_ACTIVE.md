# D02-R2：SDK、HTTP 与信任合同的当前离线核查

2026-11-07，基线父仓库 `52999ce1fc20f791c2f51d19ee0a03dc63dd117f`、patch `3675ea789c71a6667c995f104271d1e031c34018`。本次只读源码、已有证据和文件哈希；未操作客户端、调试器、服务、数据库或补丁。资源子模块已有工作区差异，未更改。R2 和 D-02 均未验收。

## 固定输入与范围

本次重新从 `tools/game_path.ps1` 解析客户端并读取下列五文件 SHA-256；前三值与 `tools/extract_client72.py` 的固定输入相同。后两个文件不因哈希相同就被认为运行时已加载或 ABI 已适配。

| 相对客户端根目录 | SHA-256 |
| --- | --- |
| `YuanShen.exe` | `8b014f64b5dc12fbc476e0c362b053abfdd7a1378cd282e4ad81057621dbd6b5` |
| `YuanShen_Data/Managed/Metadata/global-metadata.dat` | `45dc7a44dc9fc187146c52f6c33de57e2d1c1dcfec57f8736431553460f6ef2a` |
| `YuanShen_Data/Managed/Metadata/startup-metadata.dat` | `b314810840ca6f495822c9c44483dde2394b9f09ed3c6b13c2782092f04d5105` |
| `YuanShen_Data/Plugins/Astrolabe.dll` | `87e0a005ac46515156636af2120d3f824bcd28d69253e7d7235b959b54a0b442` |
| `YuanShen_Data/Plugins/AccountPlatNative.dll` | `adecdf0bd5442e321b1b66b0ca9e98bef76d5067326aa4add5b75266055a6c25` |

本次源码输入 SHA-256：`build.gradle` `3df763bda12ef41d255a577283ed4a6e7df10312b2911cdfdb54693d450461b0`；`RegionHandler.java` `b808b99e1e678d98b8b7f2c422aadd8b170ef88e711a92c1a73d49df72f6e0b6`；patch `security.rs` `14724a279b02aa430c57a21b1ca379c2647d5f01ca0a997c7c5ebe7925b16ec1`、`trust72.rs` `8a3e219e0a28d7b97455d3e91e684e3ab08555a698f7c34f5ae34533a9189d51`、`http.rs` `9db5004109004c82a83599e8b55654d116548db8458f38dbadbee0b912bcea92`、`winhttp.rs` `7fa33d7ed90a4150f0566c4eb586bab1b3968ac8e8aa0d1de7f58fca65d07053`、`native_sdk.rs` `d6341c771bb7c663cc4c68f0a1e18826fa34c5789ae12d5893e9b8e5a3ffbdf4`、`marshal.rs` `15ad5b8f5f8b7fcb7c69cd2aa136d61945c285ed4f47e16ffce1190bf123fc59`。观察器 `observe_client72_startup.py` 为 `2298ef9e7258bd0abbd1458be6306a285eec175d42f4e3fee37d179825f37928`，单槽实现 `client72_single_hardware.py` 为 `afdd662087817a77a6839821b1666d5f803eac0ebc604408c196bbde9b02c24b`。提交或其他代理后应重新核对，不把本次哈希当作未来运行事实。

## 已有合同与未闭合处

- 构建版本来自 `build.gradle` 的 `7.1.0`，`GameConstants.VERSION` 来自 BuildConfig；目标请求 `CNRELWin7.2.0` 的实际加密签名版本拒绝、列表九 raw 字段消费和篡改正文拒绝见 [HTTP 实际路由](CLIENT_72_HTTP_ROUTE_CONTRACT.md)。该归档及其测试范围不能当作当前运行 JAR 或 7.2 准入。不能通过改版本或跳校验制造成功。
- 当前 `RegionHandler` 注册列表及当前区路由，列表依请求生成当前区 URL；`dispatchSeed` 缺失和存在走不同分支。前次 `fixture` seed 只为测试分支，不是自然客户端参数证据。自然请求的 URL、参数、顺序、签名及实际返回仍未知。
- patch 的 `on_make_initial_url` 将可重定向 URL 改为固定 `127.0.0.1:8088`，但显式排除 `/query_cur_region`；当前区应从自然列表结果和真实后续请求核查。`WinHttpConnect` 改 host/port，`WinHttpOpenRequest` 清 secure 位；native curl hook 只对四种价格路径定向。不同传输路径不能互相代验；7.1 注释和静态特征不证明目标 SDK hook 已命中。
- [信任合同](CLIENT_72_TRUST_CONTRACT.md) 与 [patch ABI](CLIENT_72_TRUST_PATCH.md) 的已验范围包括两调用点只供 key/root/signature，原生验签保留；正文 `sign` 与 key 签名为不同门。bundle 需在客户端启动前传入 `LUNAGC_TRUST_BUNDLE`，旧调试器进程不会继承新 shell 环境。实际 DLL 载入、进程环境、两 hook 命中和自然验签尚未闭合；各模块初始化失败的整体原子性未知。
- SDK RSA hook 的 `RCX` key、`RDX` plaintext 及替换条件仅是当前 patch 源码合同；自然调用及托管字符串/生命周期未验。`Marshal.PtrToStringAnsi` 固定输入的 UTF-8→UTF-16 原生转换及分配模型证据见 [Marshal](CLIENT_72_MARSHAL_STORAGE.md)，不代表真实 GC/CRT 或 live 非空返回已验。

## 最小自然观察与失败出口

主线独占现场。若已存在目标，先核独立 OS PID/路径、调试器事件及自有点 ledger；不能无条件 run、load 或引用历史 PID。已审 `global-dispatch-hardware` 明确拒绝 `--resume-from` 和 `--continue-thread-name-from`，只支持新 load；如果保留目标，须另审该现场的有来源续跑方案，不能把以下 fresh 命令套用其上。

仅当主线确认无目标、唯一正确调试器会话、五文件与 debugger 身份、旧点归属及清理合同满足后，才可按 [单硬件合同](CLIENT_72_SINGLE_HARDWARE_OBSERVER.md) 执行一次有界自然启动（新文件名且目录已存在）：

```powershell
& local/migration72/debug-venv/Scripts/python.exe tools/observe_client72_startup.py `
  --main-observations global-dispatch-hardware `
  --prior-observation local/migration72/evidence/d02-r2-d01b5e-run1.json `
  --output local/migration72/d02-active/sdk/global-dispatch-run1.json `
  --summary-output local/migration72/d02-active/sdk/global-dispatch-summary1.json --seconds 120
```

先核 prior 文件实际存在、哈希与五输入相符、其 finalState 非 debugging，且当前持久点逐项为该 ledger 所有；不可只按名称前缀删除。系统/EP 及每次点切换按该合同核 DR、线程、栈和代码窗口。此命令观察原客户端，既不安装补丁，也不保证 SDK、HTTP 或信任链加载；调试器环境本身仍须另核。

入口仅 `1512250E0`：记录调用线程及有界正文的本地证据，关联已核唯一返回 `14F894D43`、相同 TID/RSP+8；void 返回不能将 RAX 解释为对象。成功只说明该自然入口与匹配返回，不说明路由准入、SDK认证、Marshal 或登录。正文、查询、令牌和寄存器只留忽略目录；共享只报告类型、长度、hash、来源及不含凭据的路径/键名。若自然未到此入口，不为取得正文主动调用函数或猜测请求。

遇未知/二次异常、事件缺失、断点/DR/线程/栈不符、未知返回、目标退出或120秒未命中，按现有 observer 守卫停并留 OS/PUB/log/WER、精确点/DR ledger；不归因保护机制，不自动重试。若目标与事件状态无法核清，不声称已清除或正常退出。签名失败分别区分 key 验签、正文验签、解密、reader 及版本拒绝，任何失败均保留原始验证结果。仅确认自然入口后，再根据实际调用链制定独立的 SDK/Marshal、路径、材料继承及 DLL 加载观察，不将同一次断点冒称覆盖它们。

本次文档核查未执行 Java、Rust 或 Python 回归，也无部署对象；既有测试计数和结果以各专题为准。下一项是主线对当前活跃目标状态作只读核对后，选择已有目标的合法续跑或满足条件的一次 fresh 单硬件观察，再按实际自然证据决定后续实现。
