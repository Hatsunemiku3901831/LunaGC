# F-03d3f4d2 / GC-DEFAULT-INPUTS 实施规格

主线Sol xhigh定规格；Terra xhigh实现纯图与测试，root独占adapter/helper/现场。原v1五profile/六工具及历史98源材料不改；当前第二L64 helper仅静态合同，0CPU/写/返回/GC权。新纯profile为avatar-r1-pool-head-ctor-second-gc-default，新增module tools/client72_d02_gc_default_inputs.py及同名test；共享adapter仅root修改。可写独立专题docs/CLIENT_72_D02_GC_DEFAULT_INPUTS_20261111.md（本文初稿交Terra接管）及私有a-gc-default-inputs；其他生产/Proto/patch/配置/资源/旧源码、AGENTS/Project/Harness只读。

已有V1 fixed source和GC64全路径，固定GC64主线第三15094B/SHA665a737fc7815d5468d8c424ed9f9be090191ff98c63d11576536e95638afe3a；原helper904B/SHA34287f706e27541c8119091aa9e55c7e2916610935f90c9b74992856b9654b31。准确源根a-second-gc64/contract-main-third.private.json列十pin和原36+36；A2 capture ea554e7664bbdaa134dc636ee413caa4fa46bd9b13343c5fc4376bced2c843a5及rootreview79daef5fcfa83173a117a05c8ae3750d28d5b554b47fd78831cdaf6034b184ec。旧v1源码 tools/client72_d02_second_allocator_inputs.py和所有pin先完整冻结，source gate调用其verify_sources并验证本私有实施规格/GC64合同与所列十输入，任何漂移在现场身份/API前拒绝。

## 准确问题与读取合同

沿同一次新A事务采旧TLS/R1/Pool/head/items/HashSet/第二block图；不得调用已final的旧collect后追加。可只在新module复述旧collect正常流程，复用旧内部函数/ledger per-instance去重/一次final；不改旧READ_PROFILES/旧flags/rawbytes/旧Ledger全局。首clear16沿原已验；backend首短读/失败/overlap矛盾立即停止所有后续，final仍按旧合同一遍，无retry。

新组件ctorInputs.gcHelper记录借自真实第二head的形状、原控制/分派结果；Default仍ctorInputs.default，只存原QWORD opaque。原v1的无GC支持路径直接复用；helper收图仅L2=64/flags8/完整H2且H2%32=0、原notify/registration/C7已支持。其他helper长度/对齐不猜、不读其控制/table，记录unsupported。第二head未完整、class/ordinary/TLS本身不支持或任何真实读失败，按旧原partial停止，不借此采Default。

当第二完整head/已支持class路径已取得，root明确授权GC分派组件和Default为两个独立只读图：helper unsupported、F触窗外或table unexpected只标组件partial，仍可依下文读Default；不得把独立Default采得当helper/allocator已返回，不启用任何构造。此项是新scope独立图授权，不修改v1停止规则。Default读失败仍真实首失败全止；U/P零仅止链、D0保留cold、非零opaque，整体partial及未知不清除。

helper源caller 140563d85、RCX=H2+16、EDX0、R8=48。固定顺序：
- 读I=U32[B+56afdd0]/4，再T0=U64[B+56afdd8]/8。
- I<3：48>T0才读F=U8[B+5eeeb08]/1；该条件下F&2置位则unsupported-external-tail-at141fdfc80→141fdf9a0，不读table。否则读B+6d19140/4，仅原DWORD=01fdfd35可标窗内输入候选。
- I>=3：48>T0才读T1=U64[B+56afde0]/8；仅T0<48<=T1才读同F/1，若F&2则external-tail-at141fdfaac→141fdf9a0，不读table。其余读B+6d190f4/4，仅原DWORD=01fdfb94可标窗内输入候选。
- 比较为完整unsignedU32/U64，48等号两侧准确；只读实际需要的一项table和控制。table不是期待值保持原DWORD/准确stop PC，不读其他target/body，也不因其落904B内就接受。最多AVX5读25B、SSE4读17B。无helperCPU/hostCPUID/磁盘初值/写。

Default固定B+594fd78/8=U，非零读U+38/8=P，非零读P/8=D；U/P零不碰零页，D=0记录cold未闭，D非零不读pointee，不声明Comparer身份/自然cctor已发生。

同ledger对所有成功unique（原图/两完整block/条件controls/唯一table/Default）统一final一次，重叠一致检查/去重沿旧规则。GC/Default任一partial保留具体子原因，overall仍partial；即使全图稳定candidate，也所有CPU/写/自然线程/GC所有权/HashSet/完整Avatar/g2批准false。新schema/PROFILE明确不可误用v1scope。主函数正常流程，入口一次校验，不堆低概率防御或通用memset抽象。

## 预算、产物与验收

新ledger fixed49/1209首、98/2418总、单208；这是旧完整44/1184已含Default加GC最坏5/25的保守上界，不承诺同一具体图能达到。当前32/64实形若AVX最坏且Default热，预期44首833B、88总1666B；SSE更少。不要伪造不符L64的图来宣称到49/1209。原API primary/TBI2/96/close协议未扩。

人工测试：两侧48阈值等号/完整高64、I边界2/3、F0/bit2、正确/错/窗外table、互斥table不读、unsupported L/align、Default独立在semantic partial后可读但真实失败后禁止、U/P零与D0/热、每个新首/final短读失败/漂移及严格一次final/预算/来源gate。旧v1测试及源码哈希不变。两fresh独立driver输出一致、完整sourceSHA与请求transcript，原文件不覆盖；人工数据不声称自然。

禁止client/现场API/调试器/DLL安装/CPU写副本/共享构建/JVM/DB/网络或Git；同任务小续项无新外部复用方向，直接复用固定来源/既有partial检索，不重爬。完成纯collector/测试即交回释放；root先独立验收，再新adapter/stock helper与API/保护/备份恢复/seal/actualCheckOnly均过，才再次通知用户新实时ready。旧ready已消费。停止/恢复：源漂移/未知形状精确partial，不补值；仅新离线输出可丢弃，所有原件/生产/用户数据保持。helper清理及第二pop/Default真实执行另立原CPU合同，不由输入候选自动授权。

## 纯图实现与人工回归（2026-11-11）

新工具 `tools/client72_d02_gc_default_inputs.py` 单独提供第六 profile，旧注册和五 profile 不改；在同一账本设置 49/1209 首、98/2418 总及单读 208，统一末遍一次。复用旧输入图、第二块、Default 和逐 span/重叠规则；仅完整第二块、已支持 class/notify 后按新合同采 GC 控制及唯一 table。外跳、表项不符或长度/对齐未支持时保留 GC 子原因，仍按独立图授权采 Default；实际首读失败则停止所有后续首读。U/P 空和 D 冷值保留各自原因，热 D 只保留原 QWORD。第二块原未知后继保留，候选字节不授权执行或返回。

来源门禁先调用 v1 `verify_sources`，另核原 v1 源完整 SHA、冻结私有实施规格和 GC64 主线第三报告完整 SHA，并逐项核其十源；任何不符报错。旧 v1 源 SHA 为 `4e1e5f901ac944d7f906e0feae6b3cf6d99b3f3a507dac6fbbc020d8a310bd38`，新 collector SHA 为 `e00a589ef08fd8e04b0a2faeecad1f204698ea09f8e3bc4043d2c6083d3e8724`，测试 SHA 为 `e7ee43079eb28ec1e4f418e17d435daa2c9e3546c13ab2db8201d935725f4bdf`。

人工测试命令 `python -B -m unittest tools.test_client72_d02_gc_default_inputs tools.test_client72_d02_second_allocator_inputs tools.test_client72_d02_tls_inputs tools.test_client72_d02_tls_pool_inputs tools.test_client72_d02_avatar_block_inputs tools.test_client72_d02_avatar_ctor_inputs`：**72 项通过**。覆盖 unsigned 阈值两侧及等号、I=2/3 与高值、F、互斥 table、错误/窗内非预期 target、外跳、未支持长度和对齐、独立 Default 与双 partial 原因、空/冷/热、每个新增首读短读和错误及末遍短读/错误/漂移、首失败、去重、来源漂移和旧 profile 回归。人工独立身份的 32/64 图为 49 首读 889B、98 总读 1778B；与既有实采形状不同，不能将其解释为现场实际或 1209B 上界可达。

私有 `a-gc-default-inputs/synthetic-driver.py` 两次 fresh Python 使用人工内存图及固定时钟，完整请求、记录和来源门禁结果逐字节相同；两份报告 SHA 均 `65af3247b207449ccdc37e7a228f26015bbb20b7e97cb3646fc37e9991031216`，driver SHA `4a34ec0f02c6550e7cf17c73148421cd63bd5e2e4e50f04260d9dd9ab79e82b4`。这不是自然输入，也不代表新现场准备完成。未操作目标、现场 API、调试器、DLL、native CPU、捕获副本、构建、数据库、网络或 Git；adapter/独立验收、新 seal、实际 check-only 和新 ready 均由主线处理。自然线程、GC 所有权、allocator 返回、Default 实际执行及完整 Avatar/g2/D-02 均未验。

## 主线集成与独立验收

主线修正新GC组件unsupported长度的R8显示为实际L−16，原固定48只适用L64；增加L56→40断言，不改输入/flags/未知状态。当前collector SHA `cc5a6bc12bdb12d264b4768a46370576e45d3feaa7a08a3e5099664dc031f1d0`。新增adapter显式GC布尔要求四前置全true，implementationPins精确七文件；新profile模块末端必须覆盖6d19144且地址不溢出，旧五profile原5eef084保持。当前adapter SHA `f1471e6fde38bb2674a2f6abfb9347dda5380aba27fbbf72fda42f9550b60a75`，API/TBI2/96/close不扩。

主线100项纯/adapter相关测试通过；独立第三人工driver与两fresh逐byte同SHA `e6ecfe17a18f587d8e1dccde8ce34d7a57cbbcca0923986557767838210090e9`。包含AVX图与外跳独立Default图，来源真实file-only gate在人工backend之前；sampling source gate的mock明确标记。旧五profile在三形状下从原备份加载、以相同动态current-path pin规范化后完整报告和请求相同，不是历史raw同SHA。main-offline-review SHA `79d863f1960a64ac4ff8314c93d95884eeb6c658b09e20c8f244abf38548f3d8`。

新stock helper另经15组人工/4 PS parser及第三、107源seal和actualCheckOnly；修复新helper metadata入口仍用旧module边界后，少一字节/上溢都在首次metadata拒绝。当前仅准备完毕，见独立stock准备专题；新ready与真实controls/Default另验。所有CPU/写/自然allocator线程/GC与完整构造批准false。

本轮准备后的新ready实采/正常退出核验已完成，准确范围与未验边界见[实采专题](CLIENT_72_D02_GC_DEFAULT_STOCK_CAPTURE_20261111.md)。原准备阶段和人工验证记录保留为历史，不复用其ready或身份。
