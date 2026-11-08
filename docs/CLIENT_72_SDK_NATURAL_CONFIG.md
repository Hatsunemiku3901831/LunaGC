# R2-NATURAL-CONFIG：SDK 永久配置的有界只读合同

## 实施前计划（2026-11-08）

任务 ID 为 `R2-NATURAL-CONFIG`。目标是从已验自然 SDK 根和真实 `server_pc` producer 出发，确定登录页阶段可以只读核对的永久 Config/env/url-map/comboLogin/app_key 状态；不追临时 request、body、用户、token 或 cookie，也不把静态字段名当成自然读取许可。

现证：SDK 固定 SHA-256 `adecdf0bd5442e321b1b66b0ca9e98bef76d5067326aa4add5b75266055a6c25`；99span 的 aggregate/engine/NetworkManager 自然初始化证据；[登录链](CLIENT_72_SDK_LOGIN_CHAIN.md)、[真实资源与 map writer](CLIENT_72_SDK_ROUTE_LOADER.md)、[combo app_key/signature reader](CLIENT_72_SDK_COMBO_SIGNATURE.md)。`aggregate+40` 是 Config 的候选来源，ConfigModel 内嵌 `+E8`、app_key 在 config `+218`、URL map 在 `+860`；这些已验后半链仍需补准确 root writer、vtable 和生命周期。

未知：自然 Config 是否已完整初始化、选择的 env、真实初始化后的 map/key 是否仍等于资源行，以及当前客户端登录页时的稳定读取条件。当前透明加载实验只包含主线已经固定的 4 读/781B；本卡不插入该轮或复用它的 user ready。

依赖及范围：只新增 `tools/client72_sdk_natural_config_contract.py`、对应测试和本专题，raw 仅 `local/migration72/d02-resume-20261108/r2-natural-config/`。优先读取已有 REA 导出、原始指令/代码窗、99span 及冻结 reader 源；最多追 8 个准确 SDK 函数/窗口，不开 EXE 分析、不重新解包资源、不扩旧 HW/ETW。若已有材料不能闭合 root/生命周期，记录唯一首缺，不伪造复杂链。主线 Git/Cargo/Gradle/DB/端口/客户端/安装独占。

产物：逐层 writer/reader/vtable/生命周期证据表、固定代码/源身份、精确读取预算、从复制后的自有 byte segments 读取的纯 collector/formatter，以及自有高 64 位合成内存正反测试。最终不提供进程 read 回调或 live backend，避免把模型调用误作自然读取许可。map 只按固定 `comboLogin` key 有界查找，禁止全树/全值 dump；字符串只在内存处理。app_key 只与准确资源行比较并输出匹配 env 集合的等值布尔，不记录其字节、长度、原文或 hash。URL 只输出预审固定 host/path 分类，未知不保留原文/hash。

验收：独立合成输入覆盖高 64 位指针/整数、非零、SSO/heap、空、缺 key、错误容量/指针/边界和预算；输出不能携带敏感字符串或其衍生 hash/长度。实际自然读取、进程、DLL 加载、目标函数调用、网络和 hooks 均不执行。先向主线交准确预算和 pure collector；自然时序若尚不能授权则准确标明最小事件。

停止条件：固定证据或 SDK 身份漂移、没有准确 producer/reader、临时或可释放对象混入、map 布局/字符串独立检查失败、预算耗尽或时序无法证实。保留首次失败。主线实验期间 Astrolabe 可能临时变化，本卡不修改 profile、不跳输入 hash；受影响的完整五输入复验等待主线恢复 stock 后才运行。

## 结论与实际边界

已闭合 Config root、准确类型、env/app_key/URL-map producer 和固定 key reader；完成仅作用于不可变自有内存的 collector/formatter。本卡**没有批准自然 map/app_key 深读**。`Config` owner 是 lazy singleton 分配的长驻对象，但 URL map writer 会在持锁状态下释放旧节点；已有材料也没有提供 Init 完成的可采 latch，deleting destructor 的完整调用方未闭。owner 的存在不能替代节点、字符串和初始化阶段的生命周期证明。

99span 实际只读了 aggregate cell 与前 16B、engine/NetworkManager 的 cell/header，以及 thread-pool 描述。aggregate 没有 vtable，且其前 16B 不覆盖 `+40`，因此旧自然报告不证明 Config 已被采到。主线随后真实透明加载的 4 读/781B 证明加载和 SDK 固定代码窗，没有读取本卡 Config；本卡没有将新读数插入该轮或复用旧 PID/ready。

主线通知 stock 已恢复、目标已退出之后，本卡才运行完整五输入 gate：五文件全部匹配初始清单，stock 为 `87e0a005…b442`，SDK 为本页固定身份。没有改 profile 或跳 hash；只读磁盘、已有证据与自有 bytes，没有打开进程、加载 DLL、调用目标函数、启动网络或安装 hook。

## 准确链、类型和生命周期

地址均为固定 SDK RVA；自然地址必须使用当次已核 loaded SDK 基址，不能使用旧报告地址或 PE preferred base。

| 层次 | 准确 producer/reader 与边界 |
| --- | --- |
| aggregate 根 | `module+7ACC80`，getter `153820` 的 lazy producer 已由登录链卡验证；无 vtable。`{aggregate}+40` 的对象 pointer 由下列准确 writer 发布，不按 aggregate 首 QWORD 当类型。 |
| Config singleton/发布 | `1538E0` 中查 `global7E79A0`，空时分配/清零 `A98`，调用 `3637D0`，发布 global，再写 `aggregate+40`。REA `ev_5b12d476f10b85a99c25a0ed74e6e0700b880c830b19b656a0105ee7af243b9b`。两根应同值；不同或任一 null 只报未就绪/未知，不猜另一地址。 |
| Config 类型 | ctor `3637D0` 写 VT `6C6CD8`；实际 PE 槽 `+80→376EA0`，`+D8→363450`，`+60→373D10`。`+0→364220` 为 deleting destructor 入口，但本卡没有闭合其所有调用方。源码对象寿命与进程退出/SDK teardown 保持分列，不能仅凭全局非空宣称永久不释放。 |
| ConfigModel | `363450` 准确 8B 指令为 `lea rax,[rcx+E8]; ret`。其 `+130` 是同一 config `+218` app_key；不存在额外 ConfigModel pointer dereference。 |
| native env | `378850` 取 literal `69409C="env"`，非空字符串经 `4903AC` 转为 DWORD，写 `config+8A0`；ctor/`3751E0` 默认置 0。此字段不等于 `config+80`，也不等于独立 AccountPlat `passport_env` 表。存值为 0 不能证明自然 env0 或 Init 完成；报告名仅为 `storedEnv`。 |
| app_key | `378DDA..378E70` 读取已解析 Init 的 `app_key`，非空才复制到 `config+218`。combo `3BEA13→3958E0` 的 R9 来源为该字段。它是普通 MSVC string：inline 16B / QWORD length `+10` / QWORD capacity `+18`，heap pointer 在首 QWORD；值和元数据只在内存使用。 |
| RequestUrl owner | `3751E0` 分配 `70` 的 `_Ref_count_obj2<MiHoYoSDKRequestUrl>`；对象位于 control block `+10`，首字段为 map head，`+8` 为 size，`+10` 是 mutex。通过 `37AC60` 把 shared-pointer 的 object/control pair 写 config `+860/+868`。因此 `+860` 是对象 pointer，不能按内嵌 map 解释。REA `ev_680b136bb7811e62559a0428bbb4fb9bff082ba2f1bfb66e505a23e6698e2113`。 |
| URL writer | Init `379701..379882` 只接受 `url:string`，JSON parse 后调用 `380E60`。后者锁 object `+10`，经 `166270` 释放旧树、重置 head/size，再用 `18F1E0` 插入，最后 unlock。节点可能被释放，禁止由 owner 长驻反推节点稳定。REA `ev_e6d68238bd8049d7f7b319145f60e8d52ef4bfe03bf4b65d091fa415f70ca19d`。 |
| URL reader | `376EA0` 从 config `+860` 取对象；R9 override 非空时可能 reload，正常 combo builder 明确 R9=0。`37E3E0` 锁同一 mutex，按 unsigned-byte 字典序做 lower_bound，再复制找到的 string；缺 key/空 value 时原 getter 使用 loopback fallback。REA `ev_2886e561f88dba52eb5581258346521892828bc371f3688c6537a415b168a788`。 |
| node/string 布局 | head 的 `+8` 是 root，head `+19` isnil=1；普通 node `+0/+8/+10` 分别 left/parent/right，`+19` isnil=0，`+20` key string，`+40` value string。来自准确 `37E3E0` 指令/reader 和已验原生 map writer，不凭一般 STL 版本猜布局。 |

本卡复用冻结 REA 导出，没有新开分析引擎或查询 EXE。自然根 raw 固定 SHA `63d3aaf8…eef8c0`，reader 源 `2eb415af…62aae`；完整固定来源列在本卡报告 `frozenInputsAndSources`。一次尝试用磁盘 PE reader 读取 `7E79A0` 时发现它不 file-backed；它是 loader 零填充/运行时写入的全局区域，遂仅记录 RVA 与 writer，未把磁盘缺字节解释成自然 null。

## 固定代码窗口与输入证明

本卡新增核对恰好 8 个 SDK 函数/leaf 窗口，完整 SHA 由每次报告保存。除明确 8B leaf 外，范围来自 SDK 自身 `.pdata`，不是邻近地址猜测。

| 入口 RVA | 独立范围 `[start,end)` | SHA-256 |
| --- | --- | --- |
| `1538E0` | `1538E0..15475D` | `8db6d9618af4f2634efa0f9f84c22ec2d8b4e4673581bdc046804dcd5356fd2b` |
| `3637D0` | `3637D0..364220` | `dd9137d68b2d57134c076544ece830d9c645e15769beb93dc72c3012dc30a9b0` |
| `3751E0` | `3751E0..375A6D` | `fad0642a5957be5c531c3b1d4e2c9f5e38969cc36cdda0c4551fce4ad165e75e` |
| `378850` | `378850..379D7E` | `ac3c851aa14b265d5db1874325fc0abd4af46f99fbf614e9193edb7c936bb42b` |
| `376EA0` | `376EA0..377027` | `5b64c330115b27fa210607663f83772924b93c988d78220d5e83aa376dc91511` |
| `37E3E0` | `37E3E0..37E5BC` | `943449d0cb44d1ae1a0e9efdaef09c887442be2836e4e0b3641fb0688070ed8f` |
| `380E60` | `380E60..380FA0` | `29c2a60f06dd4a43c84ae595acc887e41b66508c7257fa49bf91a0a2890bed88` |
| `363450` | `363450..363458`，准确 leaf | `5775079cd746f310e8b976f87ffd0c54a92bb8a2b7f169830e3900abaf2e3558` |

实际 resource reference 只复用前卡已审 `main-third/` 私有输出：当前 carrier SHA 仍为 `8e33ae96…9cda`，缓存 TextAsset 与已证 serialized extent 逐字节相等，然后仅在内存转换 16 个 env 的 app_key/URL reference。不重新解包、不保存或 hash 原文/app_key。该步骤是复用已审解码产物及 equality，不冒充新一次完整 native decode 或自然 key 比较。

## Pure collector、预算及隐私

`OwnedMemory` 在入口复制 sparse segments，随后只读取自己的不可变 bytes。没有 PID 参数、Win32/ctypes、进程 read callback、目标导出或网络 backend，也没有可以打开 live 权限的 quiescent 开关。

根/VT/env 的后续候选读计划是 **5 次、36B**：aggregate cell8、Config singleton cell8、aggregate+40 pointer8、Config VT8、env DWORD4。此计划仍需当次模块/初始化和 owner 生命周期前置；当前不执行，也不把它加入已结束的 4 读/781B。

完整 **owned** 模型 hard cap 为 **76 次、4644B、最多32节点，每次最多256B**。预算是明确上限，SSO/空/失败可能提前停止；不输出实际 read/byte 总量，避免经 app_key 分支推断其长度或 SSO 状态。

| 部分 | 最大读数 | 最大字节 |
| --- | --- | --- |
| 两根/组件/VT/env | 5 | 36 |
| app_key header 与可选 heap bytes | 2 | 160 |
| URL shared pair、map descriptor、sentinel | 3 | 64 |
| 32 个 node 的 links/key header 与必要 key heap | 64 | 4096 |
| 唯一匹配 value header 与必要 URL heap | 2 | 288 |
| 合计 | 76 | 4644 |

map size 仅接受已证资源范围 `0..305`；size 是完整 QWORD，不截为32位，不按 size 分配/遍历全树。key heap 上限64B、URL heap上限256B；固定查询 key 为 `comboLogin`，只沿有序路径前进。非目标 node 的 value 完全不读；循环/过深、错误 sentinel、不可读、缺 key 和空值保持不同状态，预算耗尽不伪报“缺 key”。

app_key 不输出字节、长度、SSO/heap 分支或 hash，只输出 `appKeyComparisonCompleted` 及 `appKeyEqualsResourceEnvs`。匹配多个 env 是集合，不据此选出单一 env；没有对应参考值则仍空集合。URL 只作前卡八个国服 HTTPS host/固定 path 的精确分类，未知只为 `unknown`；不输出未知 URL、query、原文、长度或 hash。缺 key/空值仅标“按静态 getter 合同将 fallback”，没有调用 getter 或网络。

`nativeInitCompletionObserved`、`naturalDeepReadApproved`、`liveBackendImplemented` 始终为 false。即使模型的 env、key、URL 三者互相相符，也不晋级自然成功。

## 准确最小自然事件与停止点

当前停止的是**缺少生命周期保证的外部异步深读**，不是笼统“SDK 未观察”。已知更小的事件如下，仅交主线选择后继，不是安装/采样许可：

1. URL-map：`37E423 call45E920` 后，`37E428 test eax,eax`、`37E42A jne error`；因此 **`37E430` 是成功持锁的准确点**。`37E404` 已令 R14=this/url-object，`37E3FE` 令 RBX=原公开 lookup-key；`37E466` 才开始取 head。unlock **call 为 `37E520→45E944`，返回为 `37E525`**，不能把返回点当 call。本卡用冻结 Crypto 指令行及同窗 SHA 自动断言这些地址。只有实际 key 为已证 `comboLogin` 的该原生区间能为 URL 节点提供原锁保护；它**不保护 Config env/app_key**。
2. app_key：已验 builder **`3BEA13` call、`3BEA16` return / signer `3958E0` entry 的 R9** 是 config+218 的 C 字符串输入。若将来只需自然 key 与真实资源等值，可以另卡在这个准确原始参数生命周期中只比较 key，绝不读取四字段 map、data、token 或签名正文。入口采样预算、完整 ABI 和许可须独立审定，不纳本卡76读模型。
3. Init/env：准确 `378850` 完成返回事件可证明该次 writer 已离开；该函数返回 void，不能把 RAX 当成功标志。若仍用外部 RPM，必须另外证明整个读区间无 Init/reload/teardown writer。UI 登录页、root非零、env0、两次 header 一样，都不足以证明这一条件。

因此没有强行交一条“登录页 root→map→key 自然安全”的链，没有新增锁、重试、异常处理、轮询、hook 或隐藏行为。Config destructor 调用方及自然 writer 区间是准确保留缺口；可继续的最小后继是上述原锁区间或原生 key 参数事件，不需要盲目追临时请求队列、旧 easy handle 或完整 body。

## 实际测试、确定性与第三复验

14 项纯 owned 测试首跑及最终均通过，最终日志 `owned-tests-final-private-safe.log`。覆盖高64根/指针、完整64位 length/count 拒绝、非零与默认env0、独立 node/string 布局、SSO/heap/空、unsigned key 比较、非目标 value 不读、缺 key/fallback、未知 URL保密、容量/空指针/不可读/sentinel/循环/预算，以及64/65B map key、128/129B key、256/257B URL边界。所有隐私断言使用固定布尔失败消息，不在失败时打印 key/digest。

最终 source SHA：工具 `b5b86b31f5fb46736fae1e68168ad3298a73b501607b75d9cc2ca8214d9ae6ba`，测试 `64b42762835bdbd20312d527fd56fbd210a866fd07e9c98aaa4dedb19ad29746`。`final-first.json` 与 `final-repeat.json` 在两个新 Python 进程生成，各8100B，独立逐字节比较相同，SHA-256 `396b9ceb4bff969213724c0c28440cd13bd7b80e6cf2b007f9f52167228b76d2`；报告仅包含固定输入/代码身份、已审自然根分类、资源来源/equality、预算及不批准标志，没有当前PID/地址、key或URL内容。自然 raw 只作历史证据，不作为 live 输入。

```powershell
python -m unittest tools.test_client72_sdk_natural_config_contract -v
python tools/client72_sdk_natural_config_contract.py --client $env:GAME_PATH --output local/migration72/d02-resume-20261108/r2-natural-config/main-third.json
```

主线若再次临时安装透明 proxy，完整五输入 gate 将如实拒绝；应等 stock 恢复后运行，不放宽 hash。仅本卡三个新文件，不修改其它工具、生产/patch、资源、共享索引、cleanloader 或部署。本卡完成的是准备/owned限定合同，新的自然 Config/key/route和 D-02 实机均未验。

主线stock恢复后第三报告 `main-third.json` 退出0，与上述双跑逐字节同8100B/SHA，14项owned检查通过；证明 `main-third-review.private.json`、日志 `tests-main-third.log`。主线审核了永久owner与可释放nodes的区别、独立SSO/有序map预算、输出隐私和原锁事件地址后接受准备合同。没有实现或批准自然深读；后继优先在准确原生参数/持锁事件观察，不能用外部重试或“页面稳定”伪造时序。
