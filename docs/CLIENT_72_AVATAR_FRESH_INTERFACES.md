# F-03d3f4d2-fresh-interfaces：准确接口后的构造继续

本卡限定后继已完成主线验收：第三独立新进程为 **2,114,437B / SHA-256 `d89df39ba1eb67f58add0bd276ad4ab057c21f5fcfbc2b8739004d31d3e9e970`**，与冻结双跑逐字节一致；对第三报告执行22项测试全过。主线标准库另核23项source/五stock/两个精确PE窗、前六路径及寄存器、三类壳/双向发布、64B原生清零、原首缺和投影边界，见 `r1-fresh-interfaces/main-review.private.json`、`tests-main-third.log`。独立审核helper曾将数值offset24误写成字符串`0x18`，原失败及helper副本保留；只修审核表示，不改producer、原报告、断言或重跑原生。

原native现在发布400/400/208B三个类壳，未投影case仍保留1234方法表首缺；仅不可变index投影的独立case准确止于method-cache mask。最小3根/24B合同不是完整lookup或可执行采样许可，`samplingReady=false`、完整类初始化/default写回/Handler/ready均false。当前没有完整g2实现规格，不启动正式发送。

用户最新要求当前在途完成验收/提交/交接后暂停；本卡收尾后不打开方法cache模型、后继窗口或新实机采样。下方原实施计划保留以便追溯，恢复需用户重新授权。本项仅离线研究工具/测试/文档，无运行代码或部署增量，D-02未完成。

## 实施前计划

目标是接续已验fresh-ancestor真实停止点，不再把cache1169/1249的缺头当阻塞；用本次有固定身份的新接口输入执行准确类初始化/cold/default后继，优先取得完整Avatar必要构造，输入足够时立即向正式Java/Proto实现交规格。完整Handler和D-02在实际验收前仍未完成。

已有来源：冻结 `client72_avatar_fresh_ancestor.py` SHA `3715861c8c6570a37d69a5de7967633a563671eba2b185e3e77759d4142c548c`、第三报告1,703,202B/SHA `dc738c4f3f3d80cdb72bb852d2250de685657fa489818f436ce8e8cc9933256b`。其精确首缺是`15a4d6bf1`读取cache1169；后继静态接口1249已列合同。Object表示及两动态类壳发布已验，旧Array/Type、受控Nullable分支和已验装备/技能片段直接复用。

新的原始90秒采样 `r1-interface-sampling/live-first.private.json` SHA `3c08c1f72e181084bc3d22e7199cf08ca306083322d0638cd6322ef42064d040`，monitor SHA `8136125522ddc6983a0c22e37bffe5556da58fbb981416493e5b1a6442d7e6fc`；主线29项独立核验及用户正常SDK页/退出已完成。1169/1249头各208B、kind18、70B metadata相符、compact parent/declaring均0。未读未知指针、自然消费前时序或线程；TLS索引0供Scene另用。

输入仍须核当前五stock/profile、原来源/模型和metadata/精确代码窗。两轮快照不是同一自然内存图：只把明确字段/metadata重绑定到受控模型，原绝对指针是opaque，未被采的对象不能按旧地址读取。保持自然输入、受控存储、独立对照和完整真实执行的层次。既有empty cache/tombstone、Nullable唯一身份对照不能晋级为自然状态。

允许只新增本页、`tools/client72_avatar_fresh_interfaces.py`及对应test，raw仅`local/migration72/d02-resume-20261108/r1-fresh-interfaces/`。不改旧工具/测试/报告、Java/Proto/patch/resource/config或Project/Harness；不操作Git、共享构建、DB/端口、客户端、调试器、安装或live；不使用Astra、不委派。主线负责复验、集成和提交。

预算：复用冻结精确前缀，仅为当前首缺绑定两接口有来源字段；最多12个直接后继新代码窗、4组有界原生case，每次最多2,000,000指令。不得重开旧解包/Array/Type研究或整EXE分析。达到未采真正必需头/descriptor/运行时对象时，保留原始失败并交准确位置、宽度、类型、消费顺序和可验证来源；先查静态或现成输入，不通过零对象、删除通知或成功桩越过。

交付：准确连续前缀和真实停止点、已消费的interface/初始化字段、必要省略/默认语义、完整构造/Handler范围，模型和未验标志；未知保持未知。非零/64位、嵌套、错id/kind/metadata/引用等相应检查，双新进程确定性及主线第三命令。新证据若已足，立即交最小完整规格给Sol/Terra实现；不足时交最小输入合同，不持续扩展研究数量。

停止条件为源/身份/元数据漂移、未建模间接边、真实新缺输入、预算耗尽或与既有证据冲突。保留第一次失败与分类，不为通过改断言/ready/required，不将interface输入获得等同完整构造或自然入场。该有界子任务返回后主线继续其它独立分支，不自动停D-02。

## 接手后的实现前细化

已只读核新raw及29项主线审核：新schema使用`records`，不是旧`reads`；7个接口相关读取共580B，另一个Scene专用4B不参与本卡语义。两头的`+be`实际均为0，`+c2`均为2，不能把“两个方法”猜成两个vtable slot；原`1405179f7..140517a1d`公式给1249的后续新类壳208B，不沿用400B或猜240B。

新raw因两类parent/declaring都是零，没有读registry根。因此不能用这些fresh绝对地址减旧registry来声称跨轮对应。实现将两208B头放到受控registry中两个新位置，cache槽明确绑定该位置，`+58`只绑定已核固定70B row。其余绝对指针按同一新epoch内的相等关系换成**未提供存储的opaque引用**；未知compact引用也保持未提供，不将其按旧地址解引用。复制opaque字段可以证明复制，不能把其指向内容补成零。若发生需要跨epoch身份比较或解引用，立即作为准确未闭消费边返回。

复用冻结`continue_cold`与原断言，通过新扩展层只接受本次确证的1249/208B allocator caller；必要时在同一旧metadata页精确增加VAR362的14B固定行，原VAR366白名单不放宽。新14B不需要live，也不允许其页内其它字节变输入。Array/Type、已验vector/intern、两类壳、受控Nullable身份等原片段不重研。

预算落实为最多4个逻辑原生case组；首先只做一组连续后继，双新进程是该已审case的复现。冻结bootstrap的既有producer断言继续执行，每次`emu_start`均由新适配层硬限制到2M，不能继承旧工具20M上限。最多12个新直接后继函数窗，遇到未审新入口先停并保留请求，只在核该准确PE窗口后许可；原已验窗口不重复计数。没有整EXE分析、新REA会话、其它对象扫描或现场动作。

入口独立检查错id/kind/metadata、cache链和opaque引用；新增存储按原caller/宽度验证，保留非零64位尾哨兵和嵌套构造记录。真实运行时首缺、新窗口预算或2M耗尽均为停止，不用补零/成功桩越过。最终报告将分别列已消费字段、未消费opaque字段、构造发布/初始化/default/Handler状态和准确最小后续输入。

## 后继窗口与静态解除候选的有界计划

`probe1`保留了首次未审入口停止；只许可直接后继`14051e2c0..14051e718`，1,112B/SHA `1771e00105e3fcdc2d06a1c942e0002bbb417ce9b3b8444fa8c1ffbaf5c8eeef`。原路径已发布第三个GenericClass1249及208B类壳；`probe2`停在该方法初始化函数的准确pool caller `14051e35b`，申请64B。原窗口`14051e331/34b/356`从1234已采methodCount8计算`8*8`，`14051e368`调用清零。因此只加64B owned分配/清零及独立64位尾哨兵，不加任意allocator或新OS语义；冻结原拒绝保留。

下一轮首先完成未投影case，保留`1234+98`方法指针表的真实首缺。另一个最多第2组的候选，是从同一原producer精确证明的不可变method index投影：ordinary分支`14051e469/46d`从固定70B metadata的`+0c xor5abb91bd`取起点，`14051e5d6`写每项MethodInfo的`+18`，`14051e4c6/4ca/4ce`按顺序写方法指针表并以0x38递增record、index加一。冻结`extract_client72.py::recover_members`独立解同一methodStart和methodCount。若实际70B row、已采头count8和上述来源一致，可仅为64B方法指针表与8个`+18`的4B字段建立明确受控投影；不声称自然MethodInfo地址或其它52B内容已采。不改captured头的非零/空分支来强迫构造，不向整页补零，不修改原失败case。

该候选若实现，原missing case与受控投影case分别记录；任何读取其它未提供MethodInfo字段、运行时cell/对象或新间接边仍立即停止。静态index不能据此证明完整MethodInfo、虚表、方法缓存、实际对象/默认写回或Handler完成。新窗口仍累计不超过12，case组不超过4，每次2M；若静态来源不足则不启候选，直接返回精确解除条件。

## 本卡结果与冻结范围

两组case已完成正式双新进程回放并取得相同首缺，尚不具备完整Avatar/g2或Handler实现规格。原native实际发布definition1239、1234、1249三个GenericClass的cached-class，类壳大小依次400/400/208B；1234的16B接口数组由原指令写入1169类与新1249类。第三类只是`IEqualityComparer<enum20060>`壳；保留冻结instance5917的注册指针与原enum20060 descriptor，不把它归一化为其它primitive。完整类初始化、comparer实例、默认写回、完整Handler、协议ready及D-02均为false。

仅新增本专题、`tools/client72_avatar_fresh_interfaces.py`和`tools/test_client72_avatar_fresh_interfaces.py`。原fresh-ancestor、父卡、key源码、旧断言/raw、sampler及共享索引均未修改；无Git、构建、部署、DB/端口、客户端或live动作。本卡等主线完成短暂DLL实验并确认stock恢复后，才重新执行正式五输入gate；没有许可临时proxy身份。后续现场与key observer不属于本卡。

| 绑定或新增存储 | 准确来源与执行结果 | 限制 |
| --- | --- | --- |
| 两接口新epoch | 1169/1249的8B cache槽、208B头、70B metadata；准确kind18/双零compact/+68零。新样本没有registry根 | 两模型头放入受控arena；仅+58绑定既有metadata body。其它绝对指针保留同一新epoch内相等关系并改为未映射opaque token，compact非零项也不按旧registry解释。`+8=1`等scalar不冒充指针。 |
| VAR362 | 固定metadata `4fb2580`的14B，SHA `6d1df52b90dc9d2aa7c4298132a074cc0c20e357f93a0aa63a6596eb3b938596`；原`1404eda27`只读`+6`的2B | 加在已验旧VAR366页的准确14B范围，既有reader白名单不放宽，页内其它字节仍拒绝。 |
| 1249类壳208B | 已采`+be=0`，原`1405179f7..140517a1d`公式给208B；准确pool return`140517a1d`、原memset return`140517a2d` | 只接受1249模板和registry owner；先dirty，由原memset清零，原机器码写字段；尾哨兵`fedcba9876543210`保留。 |
| 1234方法数组64B | 原`14051e331/34b/356`读取已采methodCount8，pool return`14051e35b`，memset return`14051e36d` | 只接受owned400B的definition1234、准确template/owner和64B；不提供任何方法内容，尾哨兵独立保持。 |
| 第二组不可变字段投影 | 同一原ordinary producer与独立parser均给start10287/count8；metadata SHA `00b9a09dbe29589ee7a40b147d587b1cdbbd1f4c1e8eee101a9d886a500837f7` | 64B受控指针表及8个MethodInfo `+18`的4B，合计96B；其它record字节不存在。非零`+98`改为明确模型表，未强置零来改变原分支。不是自然MethodInfo采样或完整56B record。 |

旧Array/Type、vector/intern、受控空GenericClass缓存、Object/Nullable身份、两类壳等模型沿用冻结来源，未扩新OS模型。opaque指针只能复制或执行不依赖跨轮身份的空值检查，未提供pointee或跨epoch身份运算即停止。原六条独立producer前缀的path SHA、寄存器、failure/error均与冻结祖先报告一致；旧断言未删，另加最终精确停止与发布/尾边界验收。

## 两个实际停止点

第一组保留未投影的自然输入缺口：`14051e387`读取已采1234头`+98`指向的方法指针表首项8B。报告为`Uncaptured natural-address memory read`、`error=null`；没有补数组或相邻页零。此前第三类壳已发布，方法数组仅分配/清零，未填任何元素。

第二组只提供上述固定index投影。实际仅消费模型表首项8B（`14051e387`）和该record的`+18`4B（`14051e38f`），随后原指令构造24B调用参数：methodIndex10287、class instance为冻结原注册instance5917的指针、method instance0、copy/intern参数byte1。原`14051e3ad`进入`1404eea50`，没有把原函数替换成返回值。

第二个且最后一个新窗为`1404eea50..1404efc2e`，4,574B/SHA `b92006506acefc36c21a50d0ae69e021ee7e249e88ded04f5267d0e18e3c70c2`。实际在`1404eeb80`试读模块`+5bc7290`的8B method-cache mask时停止：`Unbound runtime global cell read`、`error=null`。这是运行时输入缺失，不是运行成功；不存在将mask/blocks/probeLimit设为空以继续的模型。两case连续后继分别4,339/4,423步，所有7次调用（含冻结bootstrap）均硬限2,000,000。共2个新窗口、2个case组，未使用剩余预算扩研究。

停止时类锁仍持有；第二组是同一已验critical-section实例的同线程重入。没有声称停止后的解锁/异常展开、并发或死锁验证。三个`140517c6f`写回只证明三个类壳发布，不证明类初始化和默认字段完成。

## method-cache准确解除合同

独立合同为`r1-fresh-interfaces/next-runtime-contract.private.json`：3,843B/SHA `c7b6b89421149b7ab533f97e3b8d4fa53297ad360229c5e3e7fb4203541e83f3`，使用实际UTF-8/LF bytes计数。它与正式报告`firstMissing`逐字节序列化相同。这里只交未知与精确consumer，不执行或启动后继采样。

| 单一fresh module的准确RVA | 原reader | 宽度 | 语义 |
| --- | --- | --- | --- |
| `5bc7290` | `1404eeb80` | 8B | sparse method-cache mask；当前真正首缺 |
| `5bc72b0` | `1404eeb87` | 8B | blocks指针；原下一条必读 |
| `5bc72b8` | `1404eeb8e` | 8B | probeLimit；原接续必读 |

最小解除根是3读24B，不是完整lookup图或可直接执行的采样计划。原同窗已给条件链：`slot=hash&mask`，`block=blocks+((slot>>6)<<5)`，bit为`slot&63`；lookup实际读block `+0/+8/+16`三个8B字段。occupied时由`popcount(occupied & ((1<<bit)-1))*16`定位packed entry，entry的两个8B分别为descriptor/返回MethodInfo指针；descriptor key含index和class/method context。冲突按原probe计数前进，clear occupied bit仍须按tombstone/probeLimit判断，miss会进入真实materialization/interning。不能把zero/null当ready，也不能用当前受控指针/hash替代下一次fresh进程的bucket。

本卡没有审核/执行后继context equality `1404f0640`、miss-side `14052d590/140526fb0`或返回MethodInfo之后的虚表/default消费者。完整hit/miss条件闭包及总预算尚不能准确给出，合同明确`samplingReady=false`、`fullLookupClosureOrBudgetClaimed=false`。若未来另获批准，应先核这些准确直接分支及同一新epoch绑定，采选定key所必需的有界span；不抓整缓存、未知方法对象或任意type。本次收口后不自行开新窗口、新模型、新采样或研究卡。

## 正式复现与主线第三验收

正式`first.json`、`repeat.json`均为两个独立新Python进程；两次五固定stock输入gate完整通过，退出0、stderr0，2,114,437B，SHA均为`d89df39ba1eb67f58add0bd276ad4ab057c21f5fcfbc2b8739004d31d3e9e970`，逐字节一致。新工具31,980B/SHA `79272fc90238e0c5e5de237b4cec4e817fa3bdef14330375edd31bbcc7c6dd89`；独立test25,159B/SHA `5745fbe6baccb247dbf42405dfa92959488b172acac202c48223a6328d01b642`。raw/精确命令、stdout/stderr、来源侧车、合同和manifest均在本卡专属忽略目录；共享文档不收自然地址图或原始字节。

两正式报告各22项独立测试通过，覆盖错id/kind/compact/context/metadata/cache链与短读、opaque隔离、仅字段投影及未提供字节、非零高64位owner/指针、208B/64B精确caller与宽度/错owner/尾哨兵/相邻字节、VAR362精确reader/边界、硬2M预算、原六前缀、嵌套双向发布/接口数组、两个真实首缺与false完成标志。第一次owned测试有一处合成fixture漏`length`而KeyError，其余11项通过；仅修fixture，记录在`initial-owned-failure.private.json`。`probe1..4`及各自实际源码侧车均保留：未审窗口、allocator拒绝和真实缺口没有覆盖或抹成成功。

主线第三可运行以下命令；`main-third.json`必须是新文件，已有证据会被入口拒绝：

```powershell
python tools/client72_avatar_fresh_interfaces.py --client $env:GAME_PATH --output local/migration72/d02-resume-20261108/r1-fresh-interfaces/main-third.json
$env:CLIENT72_AVATAR_FRESH_INTERFACES_REPORT = "local/migration72/d02-resume-20261108/r1-fresh-interfaces/main-third.json"
python -m unittest tools.test_client72_avatar_fresh_interfaces -v
Remove-Item Env:\CLIENT72_AVATAR_FRESH_INTERFACES_REPORT
```

主线负责第三验收、本地提交和共享Project/Harness/HANDOFF收尾；本代理只交本卡来源、模型、失败及停止条件。当前任务交付后暂停，不推进method-cache或其它新工作。
