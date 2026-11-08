# R1-METHOD-CACHE：Avatar 方法缓存后继

## 实施前边界

本卡承接 F-03d3f4d2-fresh-interfaces 的准确停止点。当前授权是恢复持续 D-02；历史文档中的暂停仅作追溯，不覆盖本轮指令。先核当前 checkout 和固定五输入，复用三类壳、两接口及不可变 method-index 投影，不重开旧前缀或补空缓存。完整 Avatar/default/Handler/g2 与 D-02 均未验。

原未投影 case 在 `14051e387` 读取 definition1234 的 `+98` 方法指针表首 8B 停止。独立不可变投影 case 仅提供 64B 指针表及八个 MethodInfo `+18` 的 4B index10287..10294，进入 `1404eea50` 后在 `1404eeb80` 首缺模块 `+5bc7290` 的 mask8B。旧合同另外列出 `+5bc72b0` blocks8B、`+5bc72b8` probeLimit8B，共 24B，明确 `samplingReady=false`；三根不能替代完整 lookup/context 输入。

本卡先审原 `1404eea50` lookup、直接 context equality 与 miss 分支的准确固定 PE 窗口，闭合命中、失败、上下文读取和原生写入。能由真实静态或已采字节解除的输入才执行；缺自然输入时交主线独占执行的只读采样合同/采样器规格，明确寻址、ASLR、完整预算、null 分支、生命周期、停止点和不可证明范围。受控模型的地址/hash 不作自然 bucket 证据；null/空缓存不作成功输入。

只允许新增本页、`tools/probe72_avatar_method_cache.py`、`tools/test_probe72_avatar_method_cache.py`，私有产物在 `local/migration72/d02-resume-20261109/r1-method-cache/`。旧工具、报告和 raw 只读；不改 Project/Harness/README/HANDOFF、Java/Proto/resources/patch/config，不提交 Git、不构建或操作数据库，不启动客户端/调试器，不改动 REA 共享会话。本卡使用 reverse-engineer-anything 技能审阅现有证据和本机固定输入，仅做离线分析。

新工具须保留源/输入/代码窗哈希守卫、逐条原生读写及有界停止，独立正反 case 和双新进程确定性。每次原生调用指令上限 2,000,000；新窗口只取准确直接边，首次未审边或缺字节即停止，不读整缓存或未知对象。后续完整规格若仍缺输入，清楚列出解除条件，不把局部通过升级成正式实现许可。

## 当前状态

限定离线合同、pure collector 和原生正反对照已完成；主线第三与21项检查已通过并与双跑相同。主线另冻结独占现场 adapter，新的 ready/stock90秒启动及采集在途，其未返回的自然结果不计入本卡离线结论。本卡没有自然 method-cache 字节，不把新增 fixture cache 喂回旧连续前缀。原未投影 `14051e387` 和已投影 `1404eeb80` 首缺保持。完整 Avatar/default/Handler/g2 与 D-02 未验。

## 固定来源与规范化上下文

五个当前 stock 文件沿用原五输入哈希守卫；复用原 `verify_sources`、原接口第三报告及 24B 合同，严格核全部 bytes。第三报告为 2,114,437B/SHA `d89df39ba1eb67f58add0bd276ad4ab057c21f5fcfbc2b8739004d31d3e9e970`；新报告不重新声称第三报告的旧六前缀已经在本卡重跑。

| 精确原函数窗 | 字节 | SHA-256 | 本卡范围 |
| --- | --- | --- | --- |
| `1404eea50..1404efc2e` | 4574 | `b92006506acefc36c21a50d0ae69e021ee7e249e88ded04f5267d0e18e3c70c2` | 复用，审 lookup/hash、静态 miss 写入位置；受控原生执行止 hit 返回/解锁前或 miss producer 入口 |
| `1404f0640..1404f0732` | 242 | `a732127238b3ae56a752335f201297dd9cde672c3d79a06fdf6e86047fb9f74f` | 新直接 equality 窗，完整执行 null/count/两类 list 对照 |
| `14052d590..14052dc89` | 1785 | `c8067fb7db51dbbf8797f88f94ee7381b75810d9abd55be2ce0d899403f566b5` | 新直接 miss descriptor-intern 窗；仅静态读/写/后继审核，未执行 writer |
| `140526fb0..1405270a2` | 242 | `62df1ca1bb7c777bfa056102f32302fdeb197ccec09bee3570b74348519b64e5` | 新直接普通 MethodInfo getter 窗；仅静态审核，未执行初始化/写回 |
| `14051fb70..14052017f` | 1551 | `e938268a84c1cde8f26336d7c6293069c1d27f3974c24fc9bf79fed5041a332b` | 复用，GenericInst 内容查找的确切根与探测规则 |
| `1404f0740..1404f081d` | 221 | `1284709e440ff94b4541a1f3f8261edbae44f843aa5e5f53afaa0d14d877ce97` | 复用，原 type hash；固定 enum descriptor 实际执行 |
| `1404f0820..1404f09e0` | 448 | `cf72db3eb508e80f0ef5dd462b7cfead910bf0013b5a83376eba0651a0363ee4` | 复用，原 type comparison，不用 Python 结果替换返回值 |

仅新增三个直接窗，共 2,269B。全部范围以当前 PE exception directory 的准确 extent 及 SHA 同时核对；没有整 EXE 分析或新增 REA 会话。逐指令反汇编、静态 memory operand/RVA、真实执行读列表及路径 SHA 都在私有正式报告；静态发现不冒充自然执行。

registration 的 `+70` 指向 16B instance 表。准确 index5917 的静态 RVA 是 `2377940`，count1，argArray RVA `55ce7f8`，唯一参数 descriptor RVA `2f1e560` 的内容为 data20060/kind17/byref0，SHA `91343bede82fc057fcbc5a5bdb62ddee8f48b703694859a1f83f57a1b6786dee`；与原独立 99-span 采样的 class20060 内嵌 descriptor 逐字节相等。这里建立的是**查询内容**，不建立自然 canonical 指针。

旧 `142377940` 是原注册指针在受控空 intern cache 中插入的结果。自然 canonical 必须通过**同次 fresh GenericInst cache**按内容取得：模块 `+5bc8c18` mask、`+5bc8c38` blocks、`+5bc8c40` probeLimit，各 8B。原 `14051fb70` 对单参数以 count1 为种子，原 `1404f0740` 给 type hash，因此查询 hash 是 `(0x1cfaa2db + (17*0x1cfaa2db)*0x1cfaa2db + 20060) mod 2^64`。packed entry 是单个 8B GenericInst 指针；只有候选 count1 才读它的参数数组首指针和 16B type descriptor，不递归未知 kind。没有命中就保留 canonical-context miss，不猜静态注册地址或另一个相似实例。

## 精确 hash、hit、miss 与读写边界

令 `P(x)` 为原 `1404eeaaa..1404eeb70` 中的 64 位 pointer mixing，所有乘加按 64 位截断。完整 key hash 是 `((P(classInst)+sign_extend32(methodIndex))*0x1cfaa2db + P(methodInst)) mod 2^64`。原函数在未解引用 context pointee 前先计算这个 hash；不能换成 type 内容 hash。高 32 位不同、`ffffffff`/`80000000` 的 method index 及非零 64 位 method context 均有原生独立对照。

| 原 consumer | 准确读取/条件 | 后果 |
| --- | --- | --- |
| `1404eeb80/87/8e` | 模块 `+5bc7290/+5bc72b0/+5bc72b8`，各 8B | mask/blocks/probeLimit；没有 null blocks 成功支 |
| `1404eebbc` | `blocks+(slot>>6)*32+8` 的 occupied8B | `slot=hash&mask`，bit=`slot&63` |
| `1404eebd3`、`1404eec44/48` | occupied 才读 block+0 packed8B；`packed+popcount(occupied&((1<<bit)-1))*16` 的 key 指针8B；key+0 index4B | 错 index 直接下一 probe，不读候选 context |
| `1404eec5c -> 1404f0640` | 两个 context 各 class/method 指针，先检 null 对应，再检 count4B，计数相等才读各参数指针并由 `1404f0820` 比较 | 不以指针相等代替内容比较；本查询 class=count1/enum20060、method=null，无需任意递归 |
| `1404eec80/87` | 未 occupied 才读 block+16 tombstone8B；只有 tombstone 位为 1 才比较 probeLimit | **仅 `!occupied && (!tombstone || probe>=probeLimit)` 为 miss。occupied 错 key/context 即使超过 probeLimit 仍继续。** |
| `1404eeba0..a9` | `slot=(slot+probe+1)&mask`，probe 从 0 起 | 宿主 32 probe 是采集上限；触限是 partial/probe-budget，绝非 native miss |
| `1404eedb3` | 命中 entry+8 返回 MethodInfo 指针8B | 原 native 不检查非零；独立 null-value control 证实可返回 0，collector 单列 hit-null/partial |

`1404f0640` 分别比较 classInst 和 methodInst 列表；任一 null 不对称立即 false，count 不同即 false，count0 不解引用 array，非零列表按序逐元素比较。受控原生包括两个非空 method 参数列表、第二元素失败、byref 失败、两 null/零 count。选定采样只需要 class count1；候选 method 非零直接失败，不读其 pointee；候选 class count 不是 1 也不追数组。kind17 的低 16 位 attributes 和尾 reserved 不参与 equality；kind、byref 和 data 的低 32 位由原 comparer 判定。

原 `1404eea93` 对 descriptor=null 的分支会进 `1404eed7d` 分配/清零 56B，而非返回 null。本卡实际停在该分配分支入口；不把它用于原非零查询。

准确 miss 后继已拆开：

- copy/intern byte 为 1 时，`1404eeca2` 进入 `14052d590`；该函数持独立锁 `B+5bc8ac0`，读自己的 sparse 根 `+5bc8af0/+5bc8b10/+5bc8b18`，需要时用 pool `+5bc8ae0` 分配 24B descriptor、插入/扩容、改 occupied/tombstone/count。它的 packed entry 是 8B descriptor 指针，与 method cache 的 16B key/value entry 不同。相关 producer `140555cc0` 不在本卡 writer 执行许可内。
- copy/intern byte 为 0 时跳过此步骤；原生负例已准确到 `140526fb0`。该 getter 从 `B+5bc8f20` 取表根并读 `[index*8]`；非零直接返回。null 分支由固定 metadata 反求 owner、初始化类/方法，再在 `140527095` 向该表槽写回。不能把静态 index 投影当自然 MethodInfo。
- materialization 用原 `1405091d0` 申请/清零 56B；类构造失败可返回 0。缓存扩容分支可复制/释放其它 block，`1404ef542..59b` 改 mask/blocks/probeLimit 及容量/计数等全局；插入分支 `1404ef8a6/95b` 调 packed insert，改 block occupied/tombstone/count 与 `+5bc72c0/+5bc72c8`。之后 `1404ef994` 起填写 MethodInfo 的 class、flags、descriptor、index 和函数入口，后继另调用 `14052e200/14052e690`。只取得三个根不能支持这个 writer 图。

上述 miss 写入是静态 source-bound 事实，未执行 writer 或这些后继。所有 lookup 原生 controls 的外部写列表为 0；仅自有栈写可执行，分配器/锁导入/成功桩均没有建模。自然缓存可能处于尚未填完字段的状态；采样 returned `+18` index 不符时只能记 partial，不据此宣称缓存损坏。

## 主线只读采样规格与完整预算

冻结 pure 函数为 `tools.probe72_avatar_method_cache.collect(read, module)`。`module` 为该次 fresh monitor/当前模块双方一致的基址 B；`read(address,width)` 返回 `(bytes,error)`。函数不发现/启动/附加进程，不 OpenProcess、不写内存、不调用目标锁/函数，不重试；原始成功/null/短读/错误及每次请求宽度均保留。主线负责独占生命周期 adapter，并在第一次 RPM 前核当前五 stock、源码、合同、PE窗、PID creation/image 同一文件、模块 path/base/size/alive。沿已验只读 backend 使用 `0x101010`，结束再次核同一身份和存活，在 finally 关句柄。需要新的在场 ready；历史 PID、base 和 ready 不复用。

先查自然 canonical context，才取 method 根，再按顺序查八个 index10287..10294。root mask0 是有效一槽表；null blocks、occupied-null key/entry/array/descriptor、短读/error、returned index 不符或 32 probe 触限都停止依赖链并保留 partial。method miss 只记录准确边界，允许继续其它独立 index，绝不执行 materialization。hit 非零时取准确 56B MethodInfo 并核 `+18` 的 index；其它所有指针不递归。

| 输入阶段 | 最多读数 | 最多字节 | 说明 |
| --- | --- | --- | --- |
| 两表初/末各三个根 | 12 | 96 | 六个不同 RVA；末尾重读 included |
| GenericInst 查询，32 probes | 160 | 2304 | 每 probe block24 + entry8 + header16 + arg8 + type16，5读72B |
| 八个 method 查询，各 32 probes | 1536 | 26624 | 每 probe block24 + entry16 + key24 + context16 + arg8 + type16，6读104B |
| 八个命中 MethodInfo | 8 | 448 | 单读 56B，不追对象 |
| 首遍合计 | **1716** | **29472** | 未走分支不读；无整 block packed 数组扫描 |
| 所有成功精确跨度按 `(address,width)` 去重后复读一次 | ≤1716 | ≤29472 | 首遍重复/重叠 bytes 必须一致；复读任何改变/短读/error即 partial，无重试 |
| 完整上限 | **3432** | **58944** | 单读最大56B，9查询各32probe，native 每次2M指令 |

这是首遍加一次复读的完整获取上限，不宣称所有自然表都能在 32 probe 命中或证明 miss。它也不是 miss writer 的完整输入预算。实际 root/field 重用会减少复读次数；账本请求失败仍计数。根重复只检测根变化，descriptor/entry/block 等精确跨度重复只检测已观察到的字节变化；**全部相等仍不能排除 writer/ABA，也不组成原子时刻的真实 entry。** 输出始终 `candidateGraphOnly=true`、`naturalLookupObserved=false`、`atomicSnapshot=false`、`naturalWriterExcluded=false`，completed 仅表示指定重复采样候选图完成。

pure collector 不含时钟以保持离线双跑确定性。live adapter 记录整个 collect 的开始/结束；每次真实 RPM 的开始/结束 UTC 与 monotonic 可补充逐段耗时追溯，按 read 序号和 `name` 关联 canonical、八个 method、末根和 consistency。主线本轮冻结版本只记 collect 整体 UTC，没有 per-read 时钟；此缺口不影响有序重复候选图的离线输入资格，也不能支持自然时序结论，不据此无假设重启。非法地址在 RPM 前拒绝的记录不能伪造实际 RPM 时间或字节。退出/漂移保留 partial，不吞错误；原始字节、机器地址和时间只在忽略目录。

## 采后原生回放输入与停止点

完整合同在本卡忽略目录 `first.contract.private.json`，7,511B/SHA `b31ceb0249e79d0ea73a230f4189217b5ca7a09a36b920a686e1469706681a3d`。它是可交主线审核的完整 selected-lookup 规格：`samplingSpecificationComplete=true`、`samplingReadyForMainReview=true`，pure collector 已实现。live adapter 未在本卡提供，因此 `samplingReady=false`；主线冻结 adapter 后才能给出其实际可执行状态。没有完整 miss materialization 预算或完整 Avatar 实现许可。

取得 source/identity bound 的 completed、repeated-unchanged 原件后，采后回放可按以下规格立即执行，不等于自然调用已发生：

1. 原 raw 保留只读，另产输入投影。相同/重叠成功跨度按逐 byte 一致性合并，只合并有真实字节的相邻区间，不向页洞填 0；任何冲突/缺失或未映射读即停止。代码仍用固定 PE 及精确窗。
2. 六个 root-cell **存储地址**由 fresh `B+RVA` 别名到 PE 首选基址 `140000000+RVA`；raw 的 canonical、key、packed、MethodInfo 等 **指针值与 pointee 地址保持原样**。不得用旧模型 instance/hash 重算 bucket。
3. 可先运行原 GenericInst fragment `14051fba3`：owned array 仅含固定 enum20060 descriptor，`[rbp-10]` 是此 array，`esi=r15=1`；实际 hash/lookup/type compare 执行，停 `14051fd12` 的释放锁前。采样 candidate 为 hit 时，`rdi` 及 `r12&rbx` 应证明同一 fresh canonical。缺锁/分配器不补输入。
4. 对每 index 以 owned24B `(index,freshCanonical,0)`、copy byte1 从原 `1404eea9c` 执行 hash/lookup/context comparison；hit 停 `1404efb1c` 解锁前，miss 停 `14052d590` intern 入口前。严格 2M、外写/未知读即停，核 path、实际所读 spans、返回指针及采样候选一致。56B MethodInfo 仍只证明其已采字节；不执行其函数入口。
5. 只有本次重复采样图的 source-bound 受控回放可被记通过；不能声称自然 caller、同一原子 lookup、初始化或 default 写回发生。要接完整连续前缀，须新建独立 case，让原 `14051fb70` 消费 fresh intern cache 得到 fresh canonical，保留旧六前缀 case 和所有模型声明；不能直接把 fresh cache 套给旧模型 canonical，也不能替换原函数返回值。该连续前缀接入尚未提供或验收。

## 双跑、独立验证与交付

`first.json`、`repeat.json` 为两个新 Python 进程，实际五 stock gate 全通过，exit0/stderr0，各 **438,781B/SHA `b843915a8fc910876aeb4dadc4b33dea82e6b963a2ad849c09de5f44156bc570`**，逐字节相同。各27个原生 controls：高64位/signed32 hash、深比较指针别名、occupied 超 probeLimit 碰撞、tombstone 停/继续、清空状态、context count/元素/byref/list、rank63、跨block bit63→0、hit-null、首根/准确 context 字节缺失、descriptor=null 和 copy byte0 的真实分支。每调用 2M，无外写、分配/OS成功桩或新自然 cache 输入。

每份正式报告各21项独立 unittest 通过；包括 raw 来源与窗、独立原生 hash 常量/命中失败位置、全部八个 selected key 与56B值、ASLR模块地址/高64位、zero mask/null blocks、canonical miss不查method、短读/OSError不重试、根/原地字段漂移、重叠一致性、index不符、probe上限不冒充miss、kind/byref/attributes与完整预算/false边界。测试夹具均是本卡自有稀疏存储，不冒充自然输入。`probe1`/`probe2` 和 source sidecars 保留；它们是逐步增加对照/一致性预算的开发产物，不覆盖正式双跑。

冻结工具37,437B/SHA `6053f724185e2b2c7e75503021beb2f5cbde18319c7aac6edb1dfec7ed7d8544`；测试16,684B/SHA `6cf8f46a3509c1ea13621df029a3d7a8a8e9060550f6594eb1f33f7e0e90336c`。实际 argv、stdout/stderr、测试退出码和 test source SHA 在 `*-command.private.json`/`*-tests-command.private.json`，双跑证据 `determinism.private.json`；所有新 raw 在本卡专属忽略目录。

主线第三必须选新的报告文件：

```powershell
python tools/probe72_avatar_method_cache.py --client $env:GAME_PATH --output local/migration72/d02-resume-20261109/r1-method-cache/main-third.json
$env:CLIENT72_AVATAR_METHOD_CACHE_REPORT = 'local/migration72/d02-resume-20261109/r1-method-cache/main-third.json'
python -m unittest tools.test_probe72_avatar_method_cache -v
Remove-Item Env:\CLIENT72_AVATAR_METHOD_CACHE_REPORT
```

主线已实际执行该第三回放，438,781B/SHA `b843915a8fc910876aeb4dadc4b33dea82e6b963a2ad849c09de5f44156bc570` 与双跑相同，21项测试通过；主线另有6项 live adapter 生命周期检查。本卡未执行该 adapter；其采样原件/现场恢复和后续原生回放由主线另核，不以“启动正在运行”宣称自然输入/结果已取得。

本卡只新增本页及两个工具文件；本卡代理未改旧工具/raw、索引/交接、运行文件/Proto/资源/补丁/配置，不构建/部署/DB/客户端/调试器/REA 或 Git 提交。主线独占现场是另一个已授权工作，不混成此卡代理动作。下一可执行项为取得本轮准确自然候选图后，按上述输入规格 source-bound 回放；完整 Avatar/default/Handler/g2 与 D-02 继续未验。

## 主线实际适配与新窗口采集

主线新增 [live adapter](../tools/sample_client72_avatar_method_cache.py)及6项自有生命周期测试；精确复用已验 monitor 与进程/模块身份方法，不改旧冻结源。入口重新核五 stock、profile/config、源hash和7个代码窗，核同一 PID/creation/image/module 的当前实际基址及大小；以 `0x101010` 查询/读取/等待权限开启句柄，所有后继地址仅从本轮成功读取取得。末尾再核进程/模块身份与存活，句柄在 finally 关闭。错误/短读/漂移保留失败，不扩大权限或重试；source sidecar先固定。适配器不启动进程；主线独占 runner 先做经新 user-ready 批准的原始90秒启动，再把准确该次监控报告交适配器。

本轮6项自有检查覆盖准确高64位读取/最小权限/句柄关闭、源/配置/短观察拒绝、PID重用、模块重基址和采后退出保留partial。准备原件 `live/preparation-main.private.json`；适配器 SHA `402336ad19e97245089ee61e1a6869db908960f1780986ce8ff5dd8c4fa8a73c`，原样 source sidecar 已保存。其 `samplingReady` 只适用于本 selected-graph 读规格，不扩大为自然 caller、writer 或完整 cold。

用户本轮明确选择“现在可以，开始这次实验”后，主线重新核现场无游戏/服务/调试器及相关端口，核13保护文件未变，启动唯一 stock target；未安装补丁、未登录账号、未启动游戏服务。90秒结束后实际**141次、3048B**，预算内全部完整、精确跨度复读相同；8个 index10287..10294 的采样候选均为hit，canonical通过enum20060语义查到。这里的hit仍是纯 collector 对重复候选图的判断，**尚不等于原生 lookup 回放，更不等于自然 caller 被观察**。

原件 `live/cache-live1.private.json` 为400,995B/SHA `ab042d29b67aff6735b498680e74b6152e6c72285cdeb3cc799ceef57a730998`；准确本轮monitor SHA `860c571ec1277538e269d7fc1663906bfd7b9d2853876cf3e3e3602949d31914`，在 `local/migration72/d02-active/plain/run-resume-20261109-cache1.json`。主线独立15项核source/输入、同PID/creation、末身份/存活、账本/预算、重叠bytes、复读、false边界及五stock/13保护文件，证明 `live/main-review.private.json`。用户随后确认“登录页正常，已正常退出”；主线实际核客户端进程0，记录 `live/normal-exit.private.json`，不假定旧ready继续有效。

本轮`atomicSnapshot=false/naturalLookupObserved=false/naturalWriterExcluded=false`保持；没有逐read时钟，不支持额外自然时序推断。下一项为[准确采样图原生回放](CLIENT_72_AVATAR_METHOD_CACHE_REPLAY.md)，完整前缀/default/Avatar Handler/g2与D-02均未完成。仅研究工具/测试/文档变更，无运行材料变化，不要求重启GS；本轮实际stock启动单独按上述范围验收。
