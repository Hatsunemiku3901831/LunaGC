# R1-RUNTIME-INPUT-COLLECTOR

## 任务与依赖

本卡仅准备下一次实机所需的纯只读输入collector、自有测试和owned离线证明。组合冻结`probe72_avatar_method_cache.collect(read,B)`，保留其内部全部root/unique-span复读；本卡不实现Windows adapter、不采样、不打开/启动目标进程、不调用remote/native代码，也不运行REA、Git、构建、DB或安装。仅新增本页、`tools/collect72_avatar_runtime_inputs.py`、对应test，原件留`local/migration72/d02-resume-20261109/r1-runtime-inputs/`。旧source/test/raw均冻结。

前置class80 native主线第三1,371,957B/SHA `75e3bc05bb9a8044318c3fce5d30fc2d9f7a51138ede8af23a7e953e8a361283`，主线已实际完成23项检查及11项独立审核/27pins。固定third为`r1-class80/native/main-third.json`，next合同SHA `6c5be2eb618e9e16e457ef741dab7c2626147d1dcca821e5e99c010f4b8d54e5`。冻结旧collector源码SHA `6053f724185e2b2c7e75503021beb2f5cbde18319c7aac6edb1dfec7ed7d8544`；本卡不改它或推迟它的内部复读。入口verifier核准确third/next投影及全部原source pins一次，并从固定EXE再次核下面两个既有消费者窗；没有新native窗口或CPU执行。

## 字段与来源

原普通MethodInfo getter `140526fb0..1405270a2`、SHA `62df1ca1bb7c777bfa056102f32302fdeb197ccec09bee3570b74348519b64e5` 的首缺为`140526fc5`读取`B+5bc8f20/8`。只收四个已固定vtable index3870/3872/3873/3876：根8B，各index slot8B，非null MethodInfo完整56B，核+18 U32 index；再读其非null owner+c9的1B。bit20置位则只停止该index的未闭inflation路线，不读它的pointee、不替返回；其它index和warm路径独立继续。root/slot/owner null均原样记录并停止相应依赖，绝不提供空表或0补全。

warm路径从同次旧collector的完整10288 hit开始。旧canonical必须是已读且内部复读一致的实际语义命中，10288的index/context必须准确；旧采集失败不能当作warm依赖成功。新增owner208B header，核实际+c8 bit0和genericinst kind21/byref0；从+68读取GenericClass32B，核definition1234、完全相同的canonical classInst、null methodInst和owner backref。从`[owner+30]+10`读8B实际comparer class槽，仅接受它等于该owner；从`[owner+38]+0`读8B实际非null Default。任何null、flag、类型、definition/context/backref/slot不符只停止warm相应阶段，不修改任何值。

若getter输入成立，再读Default对象头8B→实际derived class208B→其+68 GenericClass32B，核actual definition1239、相同canonical/null methodInst/backref、kind21/byref0及实际初始化flag。不满足只停止对象身份阶段；已经采得的getter字段保留，不能声称完整warm对象或Avatar输入。

原warm getter `157d7fe90..157d8000f`、SHA `3fba97a567bac84fac55704f814b1145cedc8621b483232c0330df805469faa3` 的直接nonnull分支只消费MethodInfo+0、owner+c8/+30、rgctx+10、comparer+c8/+38及Default cell；本卡只收原字段，不执行getter，也不为后续HashSet/Avatar接口表/vtable请求无界graph。

## 预算独立复核

旧collector首遍最多1716请求29472B，原内部复读最多同量，合计3432/58944；内部复读仅重读已经存在的exact span，不增加旧unique集合。普通新增最大13请求268B（根8+4×(slot8+record56+flag1)）；warm新增最大7请求504B（208+32+8+8+8+208+32）。新增首遍合计20/772，max single208。

在所有新增读取结束后，对**所有旧和新成功unique exact(address,width) span**再完整复读一次；旧集合不超过1716/29472，新集合不超过20/772。因此保守上限为`3432+20+1716+20=5188`请求、`58944+772+29472+772=89960B`，maxsingle208。独立复核成立，错误/短读也消耗原请求数/请求bytes；预算拒绝的不发起请求另记原因。旧Ledger在非法uint64地址时会本地拒绝而不调用read backend，本卡仍按其原records计账并标`backendCalled=false`，不能只数backend调用漏掉这些记录。

所有成功读取，包括旧内部复读、新增跨度与最终复读，都检查逐byte重叠一致。保留每次原始返回、序号、错误、requested/returned bytes、地址和begin/end UTC；失败不会填0。最终复读不能沿用旧内部复读，某个复读失败/漂移后仍按冻结有界read-list完成其余复读，不沿新值扩链。最终global一致性失败时不晋级任何路径为ready。

## 验收与停止

先测普通/warm/对象身份正例与完整64位地址，再测独立null、bit20、初始化flag、错误context/index/type/definition/backref/slot，短读、backend error/OSError、内部和最终漂移、跨宽度矛盾重叠、非法地址、计数/字节/maxsingle边界，以及旧collect/source保持不变。明确区分各路径输入是否完整和global复读一致性。owned proof用已声明人工内存而非live/raw冒充，两个fresh Python执行输出除显式固定测试clock外全按实际请求产生，并记录source/输入SHA、命令、stdout/stderr/exit。

固定-source verifier只读固定文件；owned CLI只能读取本卡忽略目录的显式fixture，不提供任何Windows/进程backend。所有结果始终`candidateGraphOnly=true`、`atomicSnapshot=false`、`naturalLookupObserved=false`、`naturalCallerObserved=false`、`naturalWriterExcluded=false`、外写/remoteCall/native CPU为false。初始化/loadedAll不假设不补写；完整cold/default原写回/Avatar/Handler/g2/D-02仍未验。

后续实机由主线独占另接adapter，使用新明确ready、当前五stock/profile/模块/PID/创建时间身份，不沿旧地址。当前B只来自调用者所供的同轮模块身份，所有后继只来自该轮实际成功读取；前后身份/所有root、descriptor和跨度的末尾复读只能证明输入一致性，不能保证没有writer/ABA或atomic snapshot。任何超范围字段、新消费者窗或新pointee都停在准确首缺，不扩图。

## 实际API、记账与错误语义

主线adapter先保持原`probe.verify_sources(binary,metadata)`门禁，再调用`verify_sources(binary,metadata,verified_sources=原pins)`。新verifier返回dict，含`sourcePins`、`reusedSourcePins`、`executable`、`executableSHA256`、两个`windows`、`metadataArgumentUsed=false`、`newNativeWindows=0`、`nativeCpuExecuted=false`、`fullFiveStockGateRun=false`。已有同SHA pins不重读，冲突拒绝，传入dict不修改；third/next仍各读核一次并比较完整投影。binary由调用者提供已读bytes，本层hash一次并核原普通getter和get_Default准确PE bytes；metadata参数兼容旧调用，本层不用。source verifier不加载/执行Unicorn，也不调用target。纯`collect`不自动再读这些依赖，主线负责在同一入口先执行门禁一次。

采集入口为`collect(read,module)`，read约定`(bytes,error)`、成功error为None；可选keyword `now`仅供owned clock测试。返回关键字段为：

- `moduleBase/collectionBeginUTC/collectionEndUTC`：调用者本轮B和本函数时间。
- `cacheCollector`：冻结原collect的完整原样结果，包含其原内部复读和原错误；新层不重写它。
- `ordinaryMethods`：root和4个index各自的slot/MethodInfo/returnedIndex/ownerFlag/status/failure；不存在统一顶层failure，失败分路径保留。
- `warmDefault.dependency/getter/identity`：10288与canonical来源、getter字段、可选对象身份及其独立停止原因。
- `records`：从1开始的全局ordinal、phase(cache/new/final)、address/requested/bytesRead/完整返回bytesHex/error/beginUTC/endUTC/backendCalled；cache另保留其原cacheOrdinal。旧本地非法地址拒绝没有实际backend时间，UTC为null且backendCalled=false，仍计入请求和bytes。
- `inputConsistency`：最终unique数量/bytes、每项firstOrdinal/repeatOrdinal/结果、矛盾重叠和预算拒绝列表；最终复读只重读既有地址，不沿变化后的值扩链。
- `budget`及`ordinaryInputComplete/warmGetterInputComplete/warmObjectIdentityComplete`：只表示相应候选输入完整且全局复读通过，不表示消费者或自然调用执行。

null/flag/type/context/index失败只停止该路径的后续链，其余路径仍按原有界列表采集；identity语义失败时getter阶段保留。任何读取错误、短读、最终漂移或矛盾重叠使全局一致性失败，所有InputComplete均false，仍保留各路径已成功取得的字段。请求失败也记账；预算拒绝未发起的请求另记deniedRequests，不冒称已经读出。

实际Windows负例确认`OSError(5).winerror`为None，单纯`getattr(...,errno)`会丢5。新读取和最终复读已用显式None→errno回退并测试raw error保留5。冻结旧Ledger的原错误格式保持不变；全局cache记录另附`backendException{winerror,errno,normalizedError,frozenLegacyErrorPreserved}`，既保留原collect结果，也不丢实际errno。没有改旧源码或倒写旧原件。

## Owned实际验收与冻结

人工fixture是49个明确span、6,895B JSON，SHA `d1bdd3c464bab8cbe1ff549061078b50422873478985efb9c30411a82b25bdd3`；它声明`owned-artificial-memory`，普通/warm字段由测试写明，旧hash函数只用于放置人工cache keys。没有把模型hash或这些内存当自然cache证据。CLI只接受本卡忽略目录中明确声明owned的fixture；不存在进程/Windows backend入口。

两个fresh Python实际proof `owned-first.json/owned-repeat.json`各138,380B、SHA `f3a87ec65eae42521cc97005edf2a3dc392807400b18045e6a1e4a98bb4f275f`，逐byte一致。时间使用显式单调synthetic owned clock；独立execution proof记录真实begin/end UTC、argv/stdout/stderr/exit/source/test SHA，不把人工clock说成实机读取时刻。

实际共187请求4,864B：原collect完整110请求2,384B（包括其内部复读），新增首遍20请求772B，所有新增结束后57个unique exact span共1,708B再完整复读。旧unique37个936B，新unique20个772B；末尾读取的地址/宽度/返回bytes/序列独立逐项比对一致。普通、warm getter、对象身份三个候选输入标志均true，但native CPU/实机采集/自然caller/writer排除/完整runtime相关标志全部false。

两份明确指定报告各27项测试通过：正例、旧完整结果及其原请求序列、末尾完整再读、各null/bit20/flags/type/byref/definition/context/methodInst/backref/slot/index、语义相同但canonical地址不同、短/error/真实OSError/overlong、旧内部及新末尾漂移、矛盾跨宽度重叠、旧非法地址本地拒绝计账、输入未写、计数/bytes/maxsingle最后一项恰达上限与额外拒绝、错误binary/旧pin冲突、source pins与全部false边界。另16项独立比对通过；source verifier实际核30pins/两个既有窗，另实际验证复用28pins、原dict不变，无新native窗和CPU。

冻结source19,511B/SHA `2c9ba14c652823a71771a2fee385de44a75161ad9bba947d263b4164aa39fffa`；test20,865B/SHA `8cbd48131df1fe7b7dc509d12b1685be90efa3f7b2735dd1179e0b33aabe83e2`。主线另写Windows adapter与其自有测试，本卡没有实现或操作它；可能另收的SDK两个scalar前后4读4B不在本collector、文件或预算中，也不能代替这里的末尾复读。

主线可复现第三：`python tools/collect72_avatar_runtime_inputs.py --client <当前GAME_PATH> --owned-input local/migration72/d02-resume-20261109/r1-runtime-inputs/owned-fixture.private.json --output local/migration72/d02-resume-20261109/r1-runtime-inputs/owned-main-third.json`。指定`CLIENT72_AVATAR_RUNTIME_INPUTS_REPORT`为该第三报告后运行`python -m unittest tools.test_collect72_avatar_runtime_inputs -v`。本卡双跑实际命令见`run_owned.private.py`与各execution proof；比对/日志见`verify_owned.private.py`及`determinism.private.json`。此段尚不声称主线第三已经完成。

## 取得新raw后的原消费者输入规格

本卡不执行后继CPU。未来先核新adapter/source/profile/五stock与前后身份、完整raw无改写及末尾一致性；各路径用其InputComplete判断，不能只看整个集合的status。所有地址仍为这一次采得的完整64位值，不能换成旧owner/canonical，不能把208B header推成其它未采pointee。

warm getter可独立用本轮10288的完整56B MethodInfo作为RCX，映其owner208B、GenericClass32B、rgctx slot8B和Default cell8B；保护来源span只读，执行已核`157d7fe90`原入口至真实ret sentinel，2M以内，在任何cctor/lazy RGCTX/helper/原default写或未知读前停。可选Default对象头/header/GenericClass只用于实际identity和以后消费者，不需要把它们回填成冷路线的owned class。这证明给定候选图的原warm返回，不证明自然caller。

冷vtable后继可以把本轮普通root及4个MethodInfo/owner scalar作为有源跨度，唯一新增root存储alias是`B+5bc8f20→preferredPE+5bc8f20`，不改任何pointer值。原cache另按旧六根storage alias；不得拿本轮cache canonical配旧epoch owner。旧replay的SourceMemory有56B上限，不能把新208B记录直接喂进去或修改旧限制；后继须在新独占overlay中按本预算接受已验证跨度，合并矛盾重叠/缺字节/外写即拒绝。原caller/slot消费继续，未知flag分支/类型/owner pointee/下一storage即保留准确首缺；没有writer、resize、allocator或返回成功授权。

## 主线第三与新适配器

主线实际第三`main-third.json`为138,380B/SHA `f3a87ec65eae42521cc97005edf2a3dc392807400b18045e6a1e4a98bb4f275f`，与正式owned双fresh逐字节相同；指定第三的27项测试退出0。另10项独立审核重核source/test、30pin、每条owned原bytes、57个末尾span、顺序/实际187读4864B、新20/772、四index/full64及warm定义/canonical/全部未验边界，记录于`main-review.private.json`。审核了实际新代码，没有改旧collector/raw。

主线另实现`sample_client72_avatar_runtime_inputs.py`及9项自有生命周期/scalar检查。复用冻结旧adapter的创建时间/image/module前后身份、只读权限与配置/五输入门禁；新source hash单独固定，新collector在同一handle调用。源审核实际核五stock、12个旧pins、30个新pins（复用12，不改传入dict）和六个[SDK scalar源窗](CLIENT_72_SDK_CONFIGSYNC_SCALAR_OBSERVATION.md)。只额外采已证注册/once两1B的前后读出，4请求4B，合计上限5192/89964/max208；SDK事实与R1候选/末尾复读分列。

自有第一稿实际发现`OSError(5).winerror=None`导致丢errno，保存原source/失败后仅在新adapter显式回退errno，9项全过。接口审核还按新collector无顶层failure的真实schema调整退出后保留raw的路径，负例通过。没有改冻结旧collector错误格式。实际source gate与9项日志在`adapter-owned/`，采样器及脚本源码身份/当前预检在`live/`。

新脚本AST与来源pin已核，实际CheckOnly为五stock/13保护相同、相关进程/固定TCP/UDP均0、无alias，无目标启动。取得准备完成后的新user-ready后，主线已开始一次90秒原版monitor再采集；这是新epoch，不复用旧PID/owner/ready。实测原件和用户页面/正常退出反馈另记，不能把准备或owned正例当作自然warm输入/原getter执行，更不关闭完整Avatar/default/Handler/g2或D-02。没有运行服务材料变化或GS部署。

## 新ready原版实测

本轮实际完成90秒、44次原版监测，随后采样退出0。`live/runtime-live1.private.json`为530,177B/SHA `04cbe1ffd685244d4599a384186297fa4fdf19e50c2d7c6864f6b9cb4a3b48ac`。采样器源码SHA `c388f4d555ce019b143d125a9effda5c32da8cab642d10019a7a983af5dc54ad`，test SHA `92fce4144523c7827a21324b78c11d6b943601fcc540a2c0f8ec38053710fb66`。没有安装DLL或输入账号，不改运行资源/配置。

R1实际231请求5869B：原collect141/3048、新20/772、最后70个unique exact span共2049B完整复读；加独立SDK四1B为235请求5873B，均低于5192/89964的总界。所有读取完整、逐byte重叠一致、末尾一致；当前ordinary四index、warm getter及definition1239对象身份候选输入都完整。完整64位值只来自这次采样，旧canonical/owner不替换进本图。

主线独立14项审核核prepared source/snapshot、固定pins、90秒/创建时间/image/module及前后身份、八语义cache hits、每条raw/顺序/bytes/时刻、全部重叠/70末尾跨度、四普通index/owner flag、同轮warm definition/canonical、四scalar与总账以及未验边界，见`live/main-review.private.json`；没有额外目标内存读取。正常退出后实际五stock与13保护文件不变、无alias、相关进程/固定TCP/UDP均0，见`live/post-exit.private.json`。用户明确反馈“正常退出”，未单独明确反馈本轮登录页状态，不将其补写成页面已验。

下一项仅用这个冻结图回放原get_Default，并在准确source overlay隔离成立时接回原七调用。输入候选完整不等于原getter/自然caller、cold/default写回、完整Avatar/Handler/g2/D-02已验；本轮没有GS/DB启动或部署。
