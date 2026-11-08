# R1-RUNTIME-INPUT-REPLAY

## 任务、来源与当前事实

本卡只回放主线本轮已完成的输入，不再live。固定raw为`r1-runtime-inputs/live/runtime-live1.private.json`，530,177B/SHA `04cbe1ffd685244d4599a384186297fa4fdf19e50c2d7c6864f6b9cb4a3b48ac`；R1实际231请求5869B，新增20/772，70个成功unique在所有新增结束后完整复读。另SDK scalar4/4独立保留，不加入本回放语义。主线14项独立审核SHA `2f20e7768d56744207f6c3776b02f66f0f16194404aa32f90bdb05986ddb0c5c`；post-exit SHA `5f63126c1e9e03e04f928260770d1c80b1240e311a7dbd51a9a14c7315d74998`。用户反馈只有“正常退出”，没有明确页面反馈；本页不写页面正常。

准确source仍为冻结collector `2c9ba14c652823a71771a2fee385de44a75161ad9bba947d263b4164aa39fffa`、本轮adapter `c388f4d555ce019b143d125a9effda5c32da8cab642d10019a7a983af5dc54ad`及原class80/hierarchy/replay各冻结源码和其输入。source verifier核raw/monitor/sidecar/third/静态pins、原五stock文件、原函数窗；前后身份、五stock与13保护一致仅引用本轮已封存证明，不操作当前进程/端口。没有沿旧PID/ready/base，本轮ready已消费。

仅新增`tools/replay72_avatar_runtime_inputs.py`及对应test、本页；私有产物在`local/migration72/d02-resume-20261109/r1-runtime-replay/`。旧所有工具/文档/raw保持冻结；不Git/构建/DB/客户端/进程/REA/安装/patch/resources/config或共享索引。

## Exact-span输入与审计

新增独占overlay接受最多208B的本轮实际raw跨度，逐byte合并矛盾即拒绝，保留原record ordinal和时间；只给实际有源bytes，不把mapped页零或208B header推成其它pointee。所有来源输入只读，缺byte/任一外写/漂移/错误context/index/type/flags立刻停。用本轮源的时间序列重投影冻结pure collector，并逐字段比较其全部采样结果；不改原内部复读或最终复读。不把旧SourceMemory的56B限制放宽。

新overlay所有host读共用一个跨两个出口的累计ledger，最多4096请求65536B，含raw审计、投影、alias读取、native读核、错误及最终读出；原固定文件/PE hash属于来源门禁，单列而不冒称captured bytes。输入64位pointer、MethodInfo、function pointer都原样保存，不因ASLR重写。

## 第一出口：实际warm getter

仅本轮10288完整56B MethodInfo作RCX，给其同轮owner208B、GenericClass32B、rgctx slot8B、Default cell8B，以及已采Default对象身份跨度。严格definition1234/1239、同一canonical、backref、flags和槽等值由源重投影确认。执行原`157d7fe90..157d8000f`，SHA `3fba97a567bac84fac55704f814b1145cedc8621b483232c0330df805469faa3`，至真实ret sentinel；验证RAX等于本轮实际Default。没有getter/返回成功模型，不能以人工ret代验。任何cctor/lazy RGCTX/helper、未知读/外写在执行前停。对象identity只验本次1239/context，不能代替自然caller。

## 第二出口：有条件复用原七调用

从本轮`cacheCollector`投影出不超过56B的cache-only capture，附本轮mainModule身份，交冻结replay参数；其余208B自然输入仅由新overlay提供，不能让旧SourceMemory吞208B。旧capture常量/原raw不改，若旧内部硬编码source、首88记录注释或地址冲突不能可靠隔离，保留准确适配缺口而不混图。原六前缀继续与冻结第三逐项核name/failure/error/regs/path。

只允许原六个cache root storage alias，加一个`B+5bc8f20→preferredPE+5bc8f20`；alias只改变根的存储位置，raw pointer value不改。新增普通root须在原第七调用对应的第二factory入口才发布，避免影响旧六前缀。本轮MethodInfo owner来自本轮完整header，不拿本轮canonical配旧epoch owner。旧Object/三类template/metadata及owned cold class仍是原明确controlled来源，整个组合不能称一个自然runtime状态；若新span与旧来源重叠矛盾，先停并明确来源冲突，不覆盖。

组合原class80连续harness，准确16/32B存储模型仍总7次，两个层累计128读1024B，不重置或添加后继storage/成功返回。其余冻结模型保持原来源声明。原native每call2M；warm1+原7总8call、最多16M，所有初始化/失败调用均累计并列出。新native窗从0开始；实际新入口最多2个，只能有准确已知caller并先记录理由/原bytes hash，不能因此授权新pointee/allocator/consumer成功模型。遇ASLR function pointer或复杂ABI输入缺口，先保留真实失败，不做大范围代码重定位。

新增代理在首个`emu_start`才安装最终code/read/write观察器，避免被冻结class80的prepare hook替换；其余原hook原样转交。实际来源span在原snapshot准备时逐byte碰撞核验后才加入，7个preferred root alias不在prepare提前发布。若新Object owner scalar所在页与旧受控Object模板同页，vtable开始前只允许原已提供模板跨度并记录旧controlled来源；第一`14051cd3a`以后，该页超出本轮实际span的字节一律停，防止把旧Object header当本轮MethodInfo owner新pointee。所有新capture input范围/alias的native写始终拒绝。

## 验收与停止

实际执行足够输入的warm出口和可隔离的cold七调用，双fresh全报告比对。自有测试覆盖非零64位、源/identity/source pins、错误context/index/type/flags、重叠/短读/漂移/洞、read/write拒绝、alias仅storage、原六前缀、所有累计预算与精确停止。负例先在有源span/guard边界拒绝，不另外扩正式8call预算。没有新自然采集或成功模型授权。

验收分别记录warm原返回、Default对象identity、cold初始化前缀、原default写回、完整Avatar/Handler/g2/D-02；后五者不能由warm返回或静态命中替代。始终`atomicSnapshot=false`、`naturalLookupObserved=false`、`naturalCallerObserved=false`，不排除writer/ABA。完成后提供source/raw/pins、每call/读写/stop记录、双跑与tests、主线第三命令，以及准确最小后继；本卡不自动继续下一存储或采样。

## 实际执行、边界与冻结

warm出口实际25条指令，7个原数据read共42B，走`157d7fed5→157d7ff4b→157d7ff55 ret`；RAX等于本轮采得的非零64位Default。没有调用cctor/lazy RGCTX/helper，没有getter/返回模型，没有执行原`157d7ff48` default写。输入identity来自本轮definition1234/1239和同一canonical/backref，不能把该独立原消费者回放写成自然caller发生。

cold出口实际组合成功，cache-only仍为141读3048B、首88/unique53，208B自然header仅映入新overlay。原六前缀的name/failure/error/regs/path与冻结class80第三一致。第二factory即第七调用才发布普通root alias；旧六cache root同时由原注入点绑定本轮图。三个动态GenericClass的context均等于本轮canonical，未改MethodInfo owner或任何本轮function pointer。旧RuntimeType slot21/82的原受控rebase继续按旧来源声明存在，不能将“本轮捕获pointer未改”扩大成整个旧harness没有任何既有rebase。

原vtable reader实际消费固定48B中的前5个word，依次3870/3872/3873/3876/10294。四个Object MethodInfo由原getter返回，原`14051cdee`写4个完整MethodInfo指针到owned1234的inline method数组；原`14051cce6`写4个原始ASLR function pointer到对应inline函数数组，逐64位等于本轮MethodInfo+8，无code pointer改值或替返回。新记录共271项cold非stack原生写，按原call ordinal/PC/地址/宽度/值保留；模型初始化写仍按原模型报告单列。

准确首缺为`140526fcf`读本轮普通MethodInfo root+10294×8的8B；当时ECX=10294，准确entry caller return为`14051cdc7`，来自原class80入口真实栈读。原失败`Unmapped access ... at 0x140526fcf`、`Invalid memory read (UC_ERR_READ_UNMAPPED)`均保留。没有用已采到的**inflated**10294值替普通表slot。此处尚未调用捕获的ASLR函数地址，所以没有实际ASLR dispatch失败或大范围重定位。开发`probe2`把函数prologue中途`[rsp]`误名为stackReturn；原source/报告保留，正式版明确叫stackTop，它不是caller证据。

新增16/32B存储仍7个模型，累计72次569B读出，不超过7/128/1024。warm1+原7共8call，各2M、总16M；实际steps为warm25及13/13/13/13/185/466/8066，共8794。没有新native窗，没有新storage/allocator/inflation/consumer成功模型。cold整类初始化未完成，Default原写回、完整Avatar/Handler/g2/D-02均未验。

新overlay host累计1098请求17521B，不超过4096/65536，max single208。包含所有231 raw source审计、原collector精确时间序列重投影、7 alias审计、两出口实际读核、错误/中途stackTop以及**原cache reader全部267次1760B**；后者仅包计账，保留旧SourceMemory构造器56B限制，作用域结束即恢复，不给它208B记录或新fallback。原已声明harness其它读出/模型不冒称属于这个新overlay预算。

第七调用停止后，在engine关闭前，另外完整读回70个本轮unique exact span和7个alias共77项2105B，全部与来源相同。输入native写尝试0，终读漂移0，callback failure0。Object旧受控模板与本轮owner scalar同页时，7次vtable前的旧已知模板read均明确标为原controlled来源；vtable开始后超出本轮实际span一律停，不把旧header当当前MethodInfo新pointee。所有来源关联、原read地址/返回/顺序、终读ordinal和缺字节均保留。

正式`first.json/repeat.json`各1,839,053B，SHA `ddd04bb05d43f7104f3561026b749c37e930175f895ca79d667c1772f5574839`，逐byte相同。source30,646B/SHA `5d4bd541ca074e081d1f832acd4e22766213588e57b11dcff697f9bcaf599780`；test14,834B/SHA `cb4d1075a3f2e6036a53e458999086a9f20e8a19fea493633253357b55d241b0`，现在冻结。两份明确指定报告各22项测试通过，另19项独立审核/37pins通过。覆盖真实ret、4×2原store、完整64位、wrong epoch/context/index/type/flags、矛盾/短/error/洞/漂移、warm helper/输入写拒绝、vtable前后旧来源限制、alias仅storage、旧56B限制、原六前缀、全部累计预算与false边界。

实际argv/UTC/stdout/stderr/exit/source副本与test SHA在本卡`run_replay.private.py`及各execution proof；双跑/逐项比对在`verify_replay.private.py`和`determinism.private.json`。主线第三实际执行新工具`--client <当前GAME_PATH> --output local/migration72/d02-resume-20261109/r1-runtime-replay/main-third.json`，再指定`CLIENT72_AVATAR_RUNTIME_REPLAY_REPORT`执行`python -m unittest tools.test_replay72_avatar_runtime_inputs -v`，22项通过；第三1,839,053B与双fresh逐byte相同。主线另审实际源码、37pin/五固定输入、同轮raw和原六前缀、真实ret/8原store/ASLR pointer、七storage预算及77项终读等16项，全部通过；`main-review.private.json` SHA `965e321eb85d2774ccec2f5d235d3a7871a7b6e9b94d7f1ba7dbb42681471898`。本验收只完成列明回放子项，不完成cold初始化、自然caller、完整Avatar或D-02。

## Cold后继一次集中审查的最小输入

正式next合同2322B/SHA `787f2c8a848bcdd27f55dd1f749f219f40e463b8d969d3c0899b38eea9c816f9`，first/repeat相同。当前准确缺口仅普通root+10294×8/8，root本轮有源、slot没有。后续固定12-slot中剩余8个payload实际为`10294/10293/10289/10290/0/0/10291/10292`；不是8个都为1028x。两个0是固定metadata实值。按已审原`cd45..cdc2`分支，0也会传普通getter的index0；不能自行跳过、补null或称miss。

若将来选择cold路线，可集中审7个unique普通index：10294/10293/10289/10290/0/10291/10292。条件最小追加首遍为7个slot8B、各非null MethodInfo56B及非null owner+c9的1B，最多21读455B；逐exact span复读后42读910B，root已在同轮基础R1采集内。未来若完整重采基础R1并加这组字段，保守总界可按原5188/89960增加42/910得到5230请求90870B；这只是后继审查规格，**本卡未授权或实施采集/改collector**。

null/短读/错误、真实owner bit20、code pointer为null或其后的descriptor/pointee都仍是原分支和停止点，不能因为fixed slot存在就声称自然表已取得或消费者会成功。本轮ready已消费，进程已正常退出；新epoch必须重取其root/cache/owner，而不是沿当前地址补8B。本轮已采inflated10287..10294无法替普通表中的同号definition MethodInfo。

## 完整Avatar原caller的warm路线：可走，但本轮尚缺桥接来源

完整cold class初始化不是唯一入场路线。本轮已经直接执行原get_Default，证明其在这些实际flags/slot/Default下走warm原ret；原`140517980`也已有明确快路径：`140517999`读GenericClass+18，非null经`1405179a2`到`1405179b1 ret`，不会进入`1405179b9`普通类查询/分配。当前自然1234和1239的GenericClass32各已有本轮canonical/backref。这说明未来可以让原消费者使用真实warm状态，而不是不断模型化空cache/冷分配；本卡没有额外执行这个快路径或扩8call预算。

完整Avatar原入口仍是`15183fe60`，RCX要有其真实或明确controlled的Avatar对象存储。本轮R1没有Avatar对象及该入口的各managed allocation class usages。该函数的最早未采字段是`15183fe7d`读`B+588ca20/8`（List<uint32>类usage），随后原`1405336c0`分配；当前不能从这个完整入口直接运行并把前置分配略过。更早于enum路径的`15183feb5`还调用HashSet<uint32>构造`157d97690`，其第一Default来源`B+594fd78`及static area也未采。EnumEqualityComparer已warm不能证明uint32 comparer也warm。

精确enum getter原caller窗口在同一已知函数的`15183fed0`：

1. 读`B+5a30968/8`得到HashSet MethodInfo M；读M+0的owner H，接H+30的rgctx；原`15183fede`从rgctx+10取get_Default MethodInfo，再由`15183feeb`真实call原getter。
2. 原`15183fef3..ff07`再从同一M.owner的rgctx+18取得带comparer参数的ctor MethodInfo，`15183ff11`用其**实际+8 function pointer**间接调用，RDX来自原getter的Default返回；不需要host返回Default。

本轮首个桥接缺口就是`B+5a30968/8`，并未采得。R1只覆盖EqualityComparer的owner1234/Default派生1239，没有覆盖该HashSet owner的MethodInfo/GenericClass/RGCTX，不能把前者header回填到owned cold class或另一个owner。条件字段规格是：usage8、M56、H208、H.GenericClass32、`[H+30]+10/16`两槽、ctor MethodInfo56，首遍最多6读376B/复读12读752B；其中HashSet definition2147/current canonical/null methodInst/backref须实际匹配，getter槽须等于**同epoch**10288，slot18/目标指针必须真实取得。该规格不包括对象分配/存储、前面的uint32 comparer、间接目标代码执行或后续Avatar消费者，不能叫完整Avatar预算，也没有获得采样/模型授权。null/未解usage/lazy RGCTX保持原分支，不改flags/槽。

如果已有上述自然resolved MethodInfo链，原caller直接走warm getter，不需要先经过反射factory或GenericClass缓存。若仍坚持旧`159b6eb50` factory入口，原`1404edf20`必须通过本轮GenericClass cache实际找到同一descriptor；本轮未采`B+5bc7228` mask、`+5bc7248` blocks、`+5bc7250` probeLimit与`+5bc7490` lock来源/选中entries。已采GenericClass+18非null不等于证明这些cache中有它。只有原查询返回该实际descriptor，`140517980`才可自然快返backref；若miss或backref null，后续`B+5bc8f18`普通class-cache根/selected class header/写路径仍未提供，不能host返回owner、拼空表或补成功模型。

上述caller说明仅复核旧已pin声明`protocol/cn-7.2/avatar-initializer-observations.json`，411250B/SHA `1a0889afe94d0bd815a2ff99604fb4c6d48bbade5f49e0d5a8995071ad92acaf`，其固定三输入与本EXE/metadata/startup相同。原Avatar窗SHA `1b6d394af556f8647e6ee911cd32de784078ab64e29fb9fa37d3368019cd0f5e`，uint32 HashSet窗SHA `ac214998df8fd99397a345192248ca9a43d4b22ec74c374c05fb5e4dc20d2550`；没有新增映射/执行/反编译窗。字段可用性逐byte核本轮raw：上述usage/class-cache/GenericClass-cache字段全未采。单独来源证明在`warm-caller-source.private.json`，5098B/SHA `d1cbd0227980db8c4c275778588ff9dbea3ac953b55b0dda4e0a4b49f917cc8b`；本卡实际source/test与8call正式epoch保持冻结。
