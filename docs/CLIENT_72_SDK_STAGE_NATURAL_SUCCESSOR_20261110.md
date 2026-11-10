# SDK live1 阶段语义与最小自然 URL-map 后继

任务：`R2-CONFIGSYNC-EARLY-BRIDGE` / `R2-ROUTE-CONFIG-SELECT`。本页消费 2026-11-10 新 live1 和主线新提取的唯一 URL writer 原窗，不覆盖旧 SDK 专题或 pin。完成的是冻结材料的语义审核与后继实施合同；没有实现新 observer、执行 native/CPU、读取现场、构建或提交 Git。

## 本轮实际成立的范围

本轮 90 秒、45 次模块 metadata monitor 已结束，adapter 退出 0。主线独立核同次 raw 的 30 读 / 10415B、完整代码/trampoline/转发绑定及 15 项审核通过；用户反馈页面正常、正常退出、无报错。主线 stock 恢复、移除本轮 alias 后，五 stock、SDK carrier、13 保护项、portable/config 保持，相关进程及固定端口为 0。这些是本轮主线和用户证据，不是本分析代理另一次实机执行。

| 本轮输入（均相对仓库） | 完整 SHA-256 |
| --- | --- |
| `local/migration72/d02-resume-20261110/b-implementation/helper-preparation/live1.private.json` | `99a63b5352d30593f12f2a741681186db9d4ace5f9b035db045de46633221ef5` |
| 同目录 `live1.private.json.reads.private.json` | `1896e03311d3e6713a0608a2974d88d87b3794c62863e1c4a0200ac927d7238f` |
| 同目录 `live1-installed.private.json` | `15b019c82e14903a343fb83c3af8fe68dc62b377d7ac3f68b37abcc671c5f56e` |
| 同目录 `live1-finished.private.json` | `841aca745d4edcc5e7df686a3a9f08ce09dd4606f6896a9a492a9066688aa363` |
| 同目录 `live-plan1.private.json` | `f7b8ca2c77d8085e45772fce715ecb3c43a97919746278ce418ac291631ce193` |
| 同目录 `main-live1-acceptance.private.json` | `9ca7be02728749d5e8ac10c5cd099318b720e79348680585aab2b32c1eb76f33` |
| `local/migration72/d02-resume-20261110/post-sdk-stage-live1-inventory.private.json` | `7bc1939bd8463a3c5a41832773510536027001fa10d3e604790a2cca11edab62` |

只接本轮 33 源 / 105 pin 的 accepted `77296f80138e094ac5a76eae167ac436e85691189246b5cad5e9ccb2b4057abd`，生产 DLL `e517a6cf28a395fde0649a1c08c7f1a11b2bc6da05cac0db443b6d4a77dca286`。旧 prefix13 的资源等值布尔、旧 accepted、旧 PID/基址、旧 ready 均不拼接成本轮输入。

从首末完整 256B raw 独立按结构解出：schema2、phase5、event2、mask255、facts1023、env DWORD0、failure0、Win320；首末全 256B 相同。它不是只看 JSON 标签或 phase5 的结论。

| 位置 / 原指令意义 | 本轮证据上限 |
| --- | --- |
| P0 EXE `6D809B5` | 原 name Marshal 已返回，准确 caller/name 校验通过。实际覆盖 14B→C3；C3/C6 与 C9 native CALL 原位，不能沿用旧 20B→C9 文字。P0 本身位于 payload Marshal CALL 之前。 |
| P2 SDK `37894A`，原 `test bl,bl` 前 | 首次被选 writer 的 caller 为已证 `36FC80`；保存的 BL 非零，表示原外层 parser 返回接受。不等于独立验证完整正文、NUL 边界或全部字段语义。 |
| P3→P4 `378A07`→`378A2A` | env 临时字符串非空路径到达；原 `378A1E call4903AC` 返回后，`378A23: mov dword ptr [r15+8A0],eax` 已执行，存入 DWORD **0**。这次不是默认零值推断。没有读取转换输入，单凭此不能证明托管选择了资源 env0，也不能证明输入是规范十进制 `"0"`。 |
| P5 `378E4A` | 非空 app_key 的原 copy 分支到达。`378E54 call138160` 位于该点之后；本轮没有该 copy 的独立返回位，也没有 config+218 与资源等值。 |
| P6 `379862` | 嵌套 URL parser 已返回接受、URL owner 非空的分支到达；之后还有 `37986E call14D2B0` 和 `37987D call380E60`。没有独立的 map 更新完成/内容等值位。 |
| P7 `379D5B` | 原 cookie CALL 返回后的正常 epilogue 到达；原 RET 是 `379D7D`。P7 不是 RET，也不证明异常透明。 |
| P1 EXE `6D809CF` | 同 owner TID / 原 bridge RBP 的原 native 调用已经返回；发生在释放两临时 Marshal 缓冲之前。结合 P7 可称“正常 epilogue 后原 native 调用返回”。void 没有业务成功码。 |

在固定正常控制流中，P7/P1 与前述分支说明调用继续向后运行；不能将这种结构关系改写为独立采到 `copyReturned/mapEqualsResource/HTTPAccepted`。xdata、异常跳转/展开、完整 Init、自然 getter 采用、key/信任及认证仍未验证。`completePayloadValidated`、`naturalInitOrRoutePassed`、`exceptionUnwindVerified`、`voidIsBusinessSuccess` 均保持 false。

## 选择的下一出口

只验证**所选 native Init writer 实际新写的 map 中 `comboLogin` 的固定类别**，在原锁仍持有的同步区间读取；不再次扫描 payload/Marshal 页，不异步追 Config 根或已释放节点，不重开 UI 注册。现有 [托管 loader](CLIENT_72_SDK_ROUTE_LOADER.md) 已证明资源 `url:object` 必须序列化成 native Init 的 `url:string`；[map 合同](CLIENT_72_SDK_NATURAL_CONFIG.md) 已证明 owner 长驻不保证节点稳定。live1 P6/P7/P1 使该原 writer 成为有实际到达证据的最小后继。

本出口不要求在零凭据启动中先发生 combo 请求。相比只等原 reader `37E430`，这里使用已经到达的配置 writer；不假定某个 UI 动作、注册项或账号回调必要。结果只能叫 `storedComboRouteObserved`，仍令 `naturalComboRouteUseObserved=false`，不能称 route 改写、请求发送或认证成功。

## 唯一新增原窗及原锁边界

旧冻结记录是 `local/migration72/d02-resume-20261108/r2-route/main-third.json` 的 `static.sdkWindows[9]`，文件 SHA `bae776a1a43c678b2953e69d71b6f198f0418cb966f275b0f8a9b8200bee1471`；独立 `r2-natural-config/main-third.json` 的 `codeWindows['0x380e60']`，SHA `396b9ceb4bff969213724c0c28440cd13bd7b80e6cf2b007f9f52167228b76d2`，同样固定 `380E60..380FA0`、320B 与完整码 SHA：

`29c2a60f06dd4a43c84ae595acc887e41b66508c7257fa49bf91a0a2890bed88`

主线已在恢复 stock、重核五 profile 后只提取该 320B、87 条指令及原 12B pdata，目录 `local/migration72/d02-resume-20261110/b-url-window/`。`extraction.private.json` SHA `17f7f3ab640466b031305211fff645372589759a943e8069e63bb4c9a12260a0`，`main-readonly-review.private.json` SHA `fc5cf5a1a38d532196223c916c6f182d812b988da2ca866c3e4cca5a1e9ba1e6`。原脚本完成守卫/写原件后，末尾 summary 因把 Path 传入 hash 而 TypeError；原快照与错误保留，独立只读审核通过，没有重新提取或把错误记为目标失败。

pdata 记录位于 SDK RVA `81D6B0`，原字节 `600e3800a00f3800949c7300`，SHA `f459ab0371029c1ee9c2b37a34c0bad35028deacfb538941367daa155ce91cfb`；它仅固定 `[380E60,380FA0)` 与 unwind RVA `739C94`，**unwind bytes=0**。不是 xdata/EH 已验。

原函数在 `380E82` 令 R14=this；`380E8E call45E920` 后错误分支到 `380F98`，正常路径持同一 `this+10` mutex，清旧树并完成重建循环，最后到 M0：

| M0 原位置 | 原字节与含义 |
| --- | --- |
| `380F5A` | `498d4e10`，`lea rcx,[r14+10]`；callback 必须在该原指令之前完成全部 map 读取，此时原锁未释放。 |
| `380F5E` | `e8e1d90d00`，原 `CALL45E944`（unlock）。 |
| `380F63` | `90`，nop。 |
| `380F64` | `488d4c2448`，`lea rcx,[rsp+48]`。 |
| 唯一回跳 `380F69` | 下一原 `CALL1380E0`，不搬入这 15B 覆盖。 |

完整 stolen bytes 为 `498d4e10e8e1d90d0090488d4c2448`，15B→F69。固定 ilhook 最少 14B 的完整指令选择结果为 15B；已冻结完整指令中没有来自覆盖外的直接 body jump 落进其内部。原 unlock CALL 必须仅由 mover 重定位并执行一次，不能替换为 host unlock、额外 native CALL 或新 CALL/RET 包装。

## 同调用归属和唯一尝试

为保留已验首次 writer 的寿命，采用五点独立 observer：**P0、P1、P2、P7、M0**，DATA trampoline 顺序固定为这五项。P0/P1/P2/P7 的原字节、覆盖、caller 与 ABI 复用本轮已验来源；新点只有 M0。不再装 P3/P4/P5/P6，不修改旧八点 observer。

1. 首次任意 P0 在任何目标数据读取前占唯一尝试；依次核 `[RBP+18]`=EXE `198035F6`、逐字节 `local_config\0`、原 native cell=当次 SDK `13D5B0`。保存 TID/bridge RBP。错误即封存，不等后来的正确 caller。
2. 同 owner、桥 scope 未结束时，首次任意 P2 先占 writer-attempt，再读 `[RBP+318]`；只接受 SDK `36FC80`，保存 writer RBP/R15。BL 非零只作 parser 位。writer-attempt 不复位。
3. 首次任意同 owner M0 在任何 caller/map 读取、writer 帧筛选前先占 map-attempt。随后一次核：writer-open；`M0.RBP == saved writer.RBP`；`M0.RSP+1A0 == saved writer.RBP`；再读原 `[M0.RSP+98]`=SDK `379882`。任何不符封存，后续正确 M0 不补采。其它 TID 不占该 owner 的尝试，不读数据。
4. 帧关系有原来源：writer 原 `lea rbp,[rsp-2E0]` 后 `sub rsp,3E0`，故正常 body `RBP=RSP+100`；到 URL CALL 之间无其它写 RSP/RBP。`37987D` 唯一调用此 map writer；CALL 入栈8、callee五 push共28及 sub70，故 M0 的原 RSP+1A0=writer RBP、原返回槽为 RSP+98。callee 在 M0 前不写 RBP。`379876` 从原 writer R15+860 取 owner，callee 保存到 R14。**R14 是对象 pointer，不是 Config+860 字段地址**；不得额外深读 Config 或把 callee 已清零的 R15 当原 Config。
5. P7 只有同 writer RBP/R15 才记 epilogue 并关闭 writer-open；P1 只有同 bridge RBP/owner 才终结 scope，且不能将已拒绝 event 改为成功。若首 writer 没有 URL，P7 后的同地址 writer/M0 不能串入。map 查询在 M0 callback 内结束，不保存节点/字符串指针供 P7/P1 或外 collector 追读。

这是“所选 P0 bridge-open scope 内、准确 dispatcher caller 的首次 writer”的归属，沿用现有正常 Win64 scope 合同；不声称覆盖更早调用、所有线程、生产全部重入/GC 或异常交错。没有帧条件时只能报未知/拒绝，不能降格为同线程就认同配置。P1/P7 不给本轮旧 env0 或 app_key 等值补证。

## map 算法、输出和读取账本

直接移植冻结 `tools/client72_sdk_natural_config_contract.py` 的 `combo_value/bounded_string`，只查公开常量 `comboLogin`：descriptor16、sentinel32、最多32个 node；每 node 只读 links/isnil+key header64，key heap≤64；仅命中项读 value header32、value heap≤256。count 是完整 QWORD，范围0..305；inline cap必须15，heap 检查 length≤capacity、length≤上限和非零 pointer。按 unsigned-byte 次序走单路径，不全树、不读非目标 value、不按 size 分配。循环/超深/坏头/短读/不可读/超限为固定失败，不冒充 absent。

只输出 not-read、absent、empty、unknown 或八个已审 CN host+固定 combo path 的类别。比较精确完整字节，不接受 prefix、尾随 query、额外 slash 或 unknown URL；未知不保存原文、地址、长度、hash。已知类别的 env 数字是**资源 route 类别**，不是本轮 storedEnv 或托管选中 env。absent/empty 只可说“按静态 getter 将 fallback”，不称实际 getter 返回或本服 route。

| 独立账本 | 固定最大值 / 逐项 |
| --- | --- |
| P0/P2 参数 | 原 16次/37B：P0返回8、至多13×1 name、cell8、P2返回8。 |
| M0 新分类账 | **69次/4440B**，其中 caller1次8B；map最多68次4432B=`16+32+32×(64+64)+32+256`，单次≤256。它是新任务的同步原锁内分类预算，须随新实现审定；不继承旧 owned 深读许可，不给已结束 live1 加读。 |
| 新固定绑定 | **15次/6408B**：EXE桥359+准确caller46；SDK dispatcher261；原 full writer5422分10×512+302；新 map writer320。单次≤512。保留完整 writer 以核帧/唯一CALL及 P2/P7 patch，**不核新 epoch 的 export/engine/member-tail 内存**，不得沿旧 label 报全链内存绑定。 |
| 外 collector 完整 | **25次/9262B**：DATA256、转发表376、EXE359/46、native cell8、dispatcher261、writer11块5422、map320、wrapper38、五个384B trampoline、DATA256。首 DATA 不合格只1次256B，不补读。 |
| 安装行为 | 与旧账分列的 ilhook每点≤30B decoder view、entry JMP内省最多5×14B、mover/protect/patch/模块API不冒称上述总RPM。 |

固定绑定与外 collector 均低于原17/6409及30/10415上限；这不是将闲置预算转为任意读取许可。M0 分类和原参数合计最高85次4477B，各自独立封顶、失败无重试；code 绑定不在持锁 callback 内执行。现场 callback 的 map bytes 不写 raw；raw sidecar仅为外 collector 的固定代码/DATA。

新 DATA 使用独立 schema3 / 256B：保留原 module/hold/phase/error/event 固定布局；mask只低5位，trampoline依序P0/P1/P2/P7/M0，位于80..119，尾136B全零。facts bit0..9为 ordinary/name、native-return、writer-caller、parser-accepted、map-caller、lookup-completed、key-present、value-nonempty、known-CN-route、writer-epilogue。72处 routeClass DWORD：0 not-read、1 absent、2 empty、3 unknown、`0x100+k`为冻结八CN类别索引；76保留0。依赖为 known→nonempty→present→lookup→map-caller→writer-caller→ordinary；parser需writer-caller，epilogue需writer-caller，native-return需ordinary。不得将旧 schema2 的72字段当作新 route 类别。

合法 terminal/no-map 是有效负结果；不能因未知类别或未触发再采第二次。源码、状态enum、异常限制、读取序列须同时进入新 verifier/collector 的单一合同，不复用旧 accepted 或伪装旧105pin。

## 可实施文件、验收与停止

本轮仅新增本页和 `local/migration72/d02-resume-20261110/b-natural-successor/` 的计划、冻结纯投影、实现桥及增量检索计划。实际实现交下一工位：新 `patch/examples/sdk72_config_route_proxy.rs`、同前缀目录下 loader/observer/fixture/assembly、新 `tools/verify_sdk72_config_route_proxy.py` / `tools/observe_client72_config_route_proxy.py` 及对应测试；Cargo example声明只由主线修改。旧 stage/key/core/table/47转发/依赖/loader/doc/tools、原 bytes/report/pin 保持只读。新 source closure/pin 数按实际 include 计，不照抄33/105。

构建、真实 owned、文件门禁、accepted、实机和恢复均归主线，精确接口/命令见私有 `implement-bridge.zh.md`。核心验收：五点真实 near/far emitter 与冻结 mover逐字节一致；M0 原 unlock CALL 的次数/RCX/原返回/LastError及完整普通 Win64 ABI与无hook baseline一致；正常跳回F69、后续原析构和RET保持。自有链验证原 RSP+98 返回槽、RBP/RSP 关联；错误首 M0/帧/TID、P7 后同栈复用和每个读取失败均不得补采。不得将自有夹具叫作真实 SDK mutex/writer 执行。

原 `739C94` 与外 writer `738AD4` xdata 均未知；搬移 unlock CALL 会改变 callee 暂时返回地址，**异常栈透明、原 finally/cleanup 没有验收**。禁止自造 unwind 注册、EH handler、原生 unlock替代或额外 CALL/RET wrapper。只准备一次零凭据正常启动；若主线要求异常透明作为前置、发生目标异常/完整性拒绝，停止并交准确缺口，不输入账号或扩大范围。

新实际轮必须新 source/artifact/owned/第三复核/accepted、真实隔离文件门禁和本轮 fresh ready；沿90秒/45次metadata、一轮collector、同创建/module、最后存活及Close一次合同。正常退出后只恢复本轮精确 stock 与新建alias，再核五stock/carrier/13保护/config及进程端口。不因 live1 已恢复而跳过新轮恢复，也不部署 GS。

GitHub 增量复用已先交中文 `b-natural-successor/github-route-map-increment.zh.md`（SHA `0e6d53526f7b9f4064c3dba918a757f8dd0d64850e9ed276f04311c8313e4125`）：只查固定 hk4e 仓及直接相关新分支，交主线派 Terra max；原 bounded-partial/超时和 C2 覆盖未全保持。现有原锁算法/ABI/原窗已足以交明确实施，不为泛化上游覆盖继续占用 max 槽。

## 本分析实际验证与未关闭项

仅运行 stdlib 的冻结纯投影：`project_frozen.private.py --output .../projection2.private.json`，38项通过，输出10805B / SHA `9c730dea9203ab1abc7dd53d79637c66850393a42ae405c297bf79b879dc0340`。包括输入 SHA、原 raw 结构、原 store/branch字节、新15B/pdata/锁边界、帧关系及五点账本算术；没有新采集、PE读取/解码、native/CPU或新observer执行。早期三点账本草稿 `projection1` 及其源快照保留，不能作最终五点合同。

后继只关闭“同调用有锁 map 分类”出口。实际 getter采用、自然SDK RSA key caller、combo HMAC key、窄信任两site自然接受、route转换/真实HTTP/账号会话和D-02仍各自未闭。SDK登录RSA `3950C0`、combo signer `3958E0` 和 dispatch信任不是同一种key；已验离线/HTTP成果直接复用，不能由 map 类别或页面正常替代。
