# R1-AVATAR-WARM-USAGES

## 任务、固定来源与边界

本卡优先完整Avatar原caller的自然warm输入，不把反射cold factory当作唯一前置。只新增本页、`tools/collect72_avatar_warm_usages.py`及test，原件在`local/migration72/d02-resume-20261109/r1-warm-usages/`。没有新ready，不采集/接Windows/打开目标，不运行客户端/进程/REA、Git、构建、DB或安装；不改旧文件/文档/raw/共享索引/runtime/patch。主线独占未来adapter/live。

固定原Avatar/uint32 HashSet/get_Default声明来自`protocol/cn-7.2/avatar-initializer-observations.json` SHA `1a0889afe94d0bd815a2ff99604fb4c6d48bbade5f49e0d5a8995071ad92acaf`；原精确入口分别`15183fe60`、`157d97690`、`157d7fe90`。HashSet enum comparer overload15591的固定class RGCTX源为`avatar-deep-rgctx-observations.json` SHA `a8b29f1916799b754669bb1f609fd89f9a8427466217db1460a9503d7df642e0`，不能猜间接code pointer。继续核冻结R1 collector/replay及37pins、`warm-caller-source.private.json` SHA `d1cbd0227980db8c4c275778588ff9dbea3ac953b55b0dda4e0a4b49f917cc8b`。原3个静态文件按已有固定SHA读取；新native窗0，不执行客户端机器码。

## 准确有界图与原消费者

先完整调用冻结`probe72_avatar_method_cache.collect(read,B)`重取本轮enum canonical与10287..10294，原内部复读保持不动。新图按完整入口必经顺序采集下表；某路径null/短读/flag/context/type/slot错误只停止它的后续链，不补0或initialized/default/loadedAll，也不沿未知pointee扩图。

| 路径 | 真实源/消费者 | 条件字段上限 |
| --- | --- | --- |
| List<uint32> allocation class | `15183fe7d`读`B+588ca20`，随后原`1405336c0`接class；固定type1255/instance11/uint32 | root8→header208→GenericClass32→GenericInst16→argArray8→type16，共6读288B |
| List空items槽 | `15183fe92`读原全局8B，`15183fe99`只把它写入List+10 | 仅slot8B；不读array body、不伪称array identity/allocator已闭 |
| HashSet<uint32> allocation class | `15183fea1`读`B+5897cf0`；固定type2147/instance11/uint32 | 同上6读288B |
| 较早uint32 comparer Default | `157d97698`读`B+594fd78`，再+38/static cell；非null原`976e6`路径无需reflection factory | owner同上6读288B，再static cell8→对象头8→derived header208/GenericClass32/GenericInst16/argArray8/type16，共13读584B |
| HashSet<enum20060> allocation class | `15183fec1`读`B+58fd7c8`；固定type2147/instance5917/enum20060 | root8/header208/GenericClass32，context严格等于同轮旧collector canonical，3读248B |
| enum MethodInfo/RGCTX桥 | `15183fed0`读`B+5a30968`→M.owner+30→slot10 getter；slot18→ctorMI，原`15183ff11`用其+8原pointer | usage8/M56/owner208/GenericClass32/两槽16/ctorMI56，共最多6读376B；可复用已验证allocation owner，getter56来自本轮原10288 |
| enum既有warm输入 | 原`157d7fe90` consumer；复用冻结R1 helper的类型/flags/context/backref/slot规则 | owner208/GenericClass32/slot8/Default8/对象8/derived208/GenericClass32，共7读504B |

固定RIP目标用原指令边界计算并在source verifier核EXE，List空items准确RVA同样由已pin声明求出，不猜新的array/class usage。上表保守总42读2296B，复用已校验owner/context时实际更低；实现总硬上限留64读4096B，不扫描整table或GenericInst graph，不采未知Default vtable。

所有class header仅覆盖原208B ABI。GenericClass核实际definition、null methodInst、完整64位backref；enum classInst严格等于同轮canonical。UInt32 GenericInst要求实际count1/argArray/type kind9/byref0。原`1404f0740/1404f0820`对kind9按kind/byref做hash/compare，primitive data不参与，不能把固定descriptor里`data=13f`猜成definition或强制运行时相等。List/uint32之间按实际参数语义验证，不凭低32位指针假设它们共享canonical，也不声称额外观察了UInt32 intern-cache hit。

uint32 Default不固定假设1235或借用enum1239。先读其实际对象/class/GenericClass.definition，再对照有限、已静态核的EqualityComparer<T>继承关系及VAR0替换、实际UInt32 context/backref来命名；nullable/byte专门父参数和未知definition均停止对应identity分支。有限源定义1235/1237/1239/1240/1241/1242的参数0继承链来自原70B metadata、registered parent descriptor/startup pair/VAR原解码；不据此证明cold factory选择发生。没有把header+50猜成运行时parent指针；已有自然样本该字段为0，本卡不追registry父链。

getter槽必须是本轮10288，ctorMI必须真实methodDefinition15591、owner为同一enum HashSet类/context。M未被原调用使用的index只原样记录，不猜其methodDefinition。所有+8 function pointer完整64位保存，只记录实际目标，不写指针/代码、不把captured B当preferredPE。后续dispatch须先保留真实失败，再另审最小代码地址绑定；本collector不执行它。

## 预算独立公式

选择cache-only基线，不调用完整`RUNTIME.collect`，因此不读取这条路线无关的4个ordinary方法。旧阶段最大3432请求58944B，旧成功unique最多1716/29472；全部enum warm和新usage首遍共同最多64/4096。所有新读取结束后，对旧+新每个成功unique exact span再读一次，最多1780请求33568B。

总硬界：`3432+64+1716+64=5276`请求；`58944+4096+29472+4096=96608B`，max single208。错误/短读/旧本地非法地址拒绝都计账，原内部复读不能冒充末尾这一遍；末尾漂移/矛盾/错误后仍完成有界read-list，不沿变化值扩链。采用冻结R1 ledger的原raw/ordinal/UTC/重叠/错误语义，只有本卡budget入口单独限制，不改旧源。

## 验收与停止

owned人工图与真实正反case覆盖：List/HashSet/uint32 Default/enum MI全路径、不同primitive data仍相同kind语义、错误count/kind/byref/definition/context/backref/slot/index、null/lazy/flags、低32碰撞、实际function pointer不变、短/error/OSError/重叠/末尾漂移、计数/bytes/maxsingle边界和旧source/原完整cache结果不变。source verifier只读固定EXE/metadata/startup与已封存证据，不加载/执行native CPU。双fresh owned结果与实际执行/测试日志留本卡目录，交主线独立第三。

输入完整只表示本轮候选图中的已取字段，不代表typed managed allocator、完整constructor、实际Avatar对象或自然caller完成。原caller还会触及其它未采usage/array/SortedDictionary/custom类型；到这些准确前沿不补模型。atomic/writerExcluded/naturalLookup/caller均false，完整Avatar/Handler/g2/D-02未验。下列经主线提供准确合同的String分支已纳入同一预算，没有全GenericInst扩展。

## 实施前范围补充：独立optionalString

主线提供并批准R3准确合同后，本卡将String作为独立结果加入同一纯图。冻结来源`r3-string-comparer-input/sampling-contract.private.json` SHA `7722ea05b8fabc04527fafec20585b31f6a3d985ab683037c05e90b15e9df20a`；String getter原383B窗`15746c870..15746c9ef` SHA `fb37ad9d367cb55a85501b2ad6d8520a095aa0c40d9feaf37ade46cf8c103ef1`由R3已提取并复用，不另开native窗或执行CPU。primary只读`B+596c848`，不启动fallback/cache枚举。

首遍路径：usage8→MI56(index10288)→owner208→GenericClass32(def1234/null method/backref)→GenericInst16/arg8/type16(kind14/byref0)→rgctx slot10/8(严格owner)→Default8→对象头8→derived208/条件GC32→Equals函数8→伴随MI槽8→MI56→若owner不同header208/条件GC32。最多17读920B。原FindEntry伴随槽准确为`derived+110+u16(header+be)*8`，要求vtableCount至少9；两个函数指针仅保持完整原值，不强制相等或重定位。

String generic派生只接受已核VAR0父链，ordinary1243仅在kind18/data1243、GCnull且固定父类型为EqualityComparer<String>时接受。实际伴随owner必须属于derived的固定父链，并持同一完整String context；1234/10289是abstract，停止。kind14的data/attrs不参与原hash/equality，不能把`data=125`借作definition；count1原hash是`15*1cfaa2db=7292816085`。null/未warm/类型/父链/slot错误仅使String分支partial，不改mainWarm语义。所有raw/错误继续进入总ledger；主图自身最终exact spans完整一致时，保留主图独立就绪结果，跨图重叠矛盾仍拒绝。

原42读2296B和本17读920B均为**首遍**，合计59读3216B，小于64/4096。统一末尾复读与总硬界仍是5276请求96608B、单次208B，不增加图上限、未知pointee、写入、模型或live适配。

## 冻结实现、真实owned验证与主线第三入口

冻结collector `tools/collect72_avatar_warm_usages.py`为33542B，SHA `667fa5948f1d645feb923f5b166efa7bb1667cb12ad6b8135d2911bffd4e26b7`；test为26602B，SHA `b33db775f14333a718085d646396c3f1bcf092ab0e85c63b24b6de504a6b926d`。旧cache源保持`6053f724185e2b2c7e75503021beb2f5cbde18319c7aac6edb1dfec7ed7d8544`，旧runtime helper保持`2c9ba14c652823a71771a2fee385de44a75161ad9bba947d263b4164aa39fffa`。本卡没有改任何旧源/test/raw。

R3的窄伴随owner补充`owner-pair-contract.private.json` SHA `bf03a9d29054fba2f467c2a54220ee966ad964b4a937ba91d2035ab243716291`同样pin：1235→1235/10296，1237→1237/10310，1239→1239/10324，1240/1241→1239/10324，1242→1242/10333，ordinary1243→1243/10339。不得接受同String sibling或abstract1234。两函数指针可以不同；原FindEntry分别取vtable函数和伴随MI再调用，没有比较MI+8。实现保留这个差异。

`verify_sources(binary,metadata,startup,*,verified_sources=None)`只核固定3静态字节、52个冻结依赖pin、9个已经提取的已知窗及有限metadata切片，不执行CPU/target。它独立重算六个VAR0父链与原14B参数position0、ordinary1243的实际EqualityComparer<String>父类型、6个相关Equals/slot8/flags方法行。`verified_sources`可传已核path→SHA映射以避免重复读取共同依赖；冲突pin拒绝。`inheritedFiveInputPins`仅保存R3既有五输入来源，`fullFiveStockGateRun=false`；本卡没有读取当前插件或宣称安装态门禁通过。未来live的identity/五输入或批准alias门禁由主线adapter执行。

纯入口保持`collect(read,module,*,now=None)`；`read(address,width)`返回`(bytes,error)`，自然输入全部只读。它完整调用旧cache collector、原序列/内部复读不变，使用旧helper的enum warm语义，最后统一复读每个成功unique exact span。每个实际backend调用都有独立begin/end UTC、ordinal、原raw/error/长度；本地非法地址也记账并标backendCalled=false。采集起止与每次读的时间界仅表示观测顺序，不能证明writer无并发。重叠矛盾或末尾漂移均拒绝，不将跨时刻字节拼成原生entry或宣称atomic。

输出主线接口：`status`和`candidateInputs`仅代表main warm图；`optionalString.status`/`optionalStringCandidate`独立。`inputConsistency`保存全图结果，`mainInputConsistency`保存所有main/cache成功span在String结束后的最终复读。String的null/语义cold/读取失败使它partial；其raw/error全部保留，主图自身记录与最终span一致时仍可单独就绪。任何重叠矛盾保守拒绝两个结果。所有`naturalLookupObserved`、`naturalCallerObserved`、`naturalWriterExcluded`、`atomicSnapshot`、`prototypePointersModified`、`nativeCpuExecuted`等边界均false。

人工完整合并fixture为`owned-combined-fixture.private.json`，14154B/SHA `326ca59b32ac72b60292c494af43232ee1e93e98ae2476c52964098fa775689f`。它含独立高64位context/对象/函数指针、kind9和kind14的非零opaque data、String1240→1239实际伴随owner；不称自然对象。两次fresh Python执行`owned-first.json`和`owned-repeat.json`逐byte相同：212636B/SHA `82b0f647b68f10f8d27ff5cf1b3fd66ebeb0bbd03f705db86e08f0b92e33ee17`。报告时间使用明确标记的owned合成单调钟，实际执行UTC另存execution proof。

| 实际owned阶段 | 请求 | 请求字节 |
| --- | ---: | ---: |
| 冻结cache全部原读/原内部复读 | 110 | 2384 |
| 全部新首遍main+String | 48 | 2856 |
| String结束后统一exact span复读 | 85 | 3792 |
| 总计 | 243 | 9032 |

String子图实际17读920B达到其保守边，main因真实context/owner复用而31读1936B。最大单读208。全部main候选与optionalString在人工图中collected，所有真实返回字节/读地址/原cache结果/顺序逐项核对，不表示客户端caller运行。

37项自有测试实际0失败，日志`tests-final.private.log`。覆盖UInt32与String不同primitive data仍相同原hash语义、count/kind/byref/null/flags/definition/context/backref、enum getter/ctor实际slot/index、低32碰撞、String ordinary1243/六generic模板/继承伴随owner、abstract与同String sibling拒绝、原函数指针可不同、实际vtableCount公式、短/error/OSError errno、交叉图重叠、旧/新末尾漂移、预算末边和源变更拒绝。测试不修改真实依赖。另独立`review-owned.private.py`/`owned-review.private.json`完成22项独立审核加52pin：原cache整体/原始地址返回序列相同，全部243行逐字节等于fixture来源，85 unique完整末尾复读；独立sibling负例、String data0正例与原根漂移负例均实跑通过。没有Windows adapter、客户端采样或native CPU。

主线第三可在仓库根执行（输出不存在时）：

```powershell
$warmClient = & .\tools\game_path.ps1
python tools/collect72_avatar_warm_usages.py --client $warmClient --owned-input local/migration72/d02-resume-20261109/r1-warm-usages/owned-combined-fixture.private.json --output local/migration72/d02-resume-20261109/r1-warm-usages/main-third.json
$env:CLIENT72_AVATAR_WARM_USAGES_REPORT = (Resolve-Path local/migration72/d02-resume-20261109/r1-warm-usages/main-third.json).Path
python -m unittest tools.test_collect72_avatar_warm_usages -v
```

## 采集后可执行的后继与准确未知

本卡交付主线独占Windows adapter的纯合同，不启动下一次live、不消费ready。未来采集应取同一module epoch/cache canonical与完整有界图，再统一末尾复读；任何路径partial就保留对应准确首缺，不用旧epoch owner或static usage index充runtime pointer。SDK标量或临时安装态证据不计入本卡，不改变本图只读预算和边界。

若自然图完整，完整Avatar原入口`15183fe60`现在可以从真实List<uint32> usage切入，其第一必经managed allocator调用仍是`1405336c0`；本卡只取class输入，没有验证allocator、授权新的分配返回、构造函数成功模型或其额外读集。较早HashSet<uint32>原ctor`157d97690`可用实际`B+594fd78`/Default避免反射cold factory，但仍需在独立native harness中执行原消费者到准确首缺。enum caller可用实际HashSet class和`B+5a30968`的MethodInfo/RGCTX桥取得本轮10288/15591，不能用host直接返回owner/Default或回填受控cold类。String支路只闭有限warm输入与Equals原prototype槽，并未执行GetDefault或String comparison。

当前已知自然采样moduleBase不等于preferredPE；所有原function pointers原样保留。后继实际间接dispatch应先保留真实失败，再单独审核准确代码地址绑定，不能据static registered address无证大范围重定位。后继不会自动获准新的classcache根、未知pointee、存储或返回模型。完整Avatar分配/constructor/native caller、Default原写回、Handler、g2和D-02分别仍未验。本卡无Git提交，由主线第三后分项集成。

## 主线第三与新Windows适配器

主线已按上面的准确CLI实际运行第三，212,636B/SHA `82b0f647b68f10f8d27ff5cf1b3fd66ebeb0bbd03f705db86e08f0b92e33ee17`与双fresh逐byte相同。指定第三后37项纯collector检查通过；主线另核实际源码、52pin/当前三固定输入/九原窗、原cache完整结果、243请求/9032B、全部85unique统一末尾复读、继承Equals伴随MI与ASLR原指针、硬界及未验标记等12项，证明`main-review.private.json` SHA `2aad5df7f83fc23d3fec35253b60e382c229755c19afe787bf9ee10a2558d61d`。

新`tools/sample_client72_avatar_warm_usages.py` 8,314B/SHA `3f00e2185bfce87b2726c21dac38ea4d669f5f5ad7c413cea71f17fbc37efaca`，独立生命周期测试8,756B/SHA `47603c93c0d1f0fbc51e9b242f78991d6bbde083104f947ad169f66dfe21d59a`，13项通过，同次37+13共50项通过。它在新SDK早期adapter完成90秒监测后核同轮创建/模块/路径/size/hash及最终存活，再按本纯图读取；不是原版五文件入口，Astrolabe原输入明确映到原版alias，安装观察DLL单列。开头门禁失败不打开目标，读取失败保留同次原字节账本，finally关闭句柄，不增加RPM、写入或remote call。

已用忽略目录的隔离安装布局及人工monitor调用实际source gate，52pin、九窗、13保护和五逻辑输入通过；90.004秒的正常调度尾差可以接受，不把精确等于90作为条件。证明`adapter-owned/main-source-gate.private.json` SHA `e5b34be0e6d057f0fa8ec7fdfd860c225e27e4c98c3ce389fed6041a84414321`。没有调用任何真实目标进程API、安装GAME_PATH或启动客户端。

下一现场将与已验SDK早期观察器共用一次90秒启动。外部读合计硬界5285请求98058B、单次512B（本图5276/96608/208加SDK固定9/1450/512）；观察器内部仅对一次准确caller的两个托管字符串作最多65562B的临时有界副本，不保存正文、key、URL、hash或长度。完整准备、当前输入/进程/端口、备份/恢复和新实时ready另行核验；实际候选/partial、页面与退出反馈、恢复结果尚未发生。本纯图完成不自动晋级现场子项或父项。

主线现已封存合并`r2-early-bridge/live-plan.private.json`（14源pin，SHA `8a94557829a23ecffaa307fc7349536fa2fde3e8db5a848df07a86a94f6a983c`）、`run-early-live1.ps1`（SHA `73dd3b78f7fdd12678d73acbfe6c1e0342b53f5e1564bafe1923459cd9199299`）及`restore-early-live1.ps1`（SHA `f79a5a962d98d73fd4803d68a1ec13f05e2869e9b715a94be51d3c06da6dc04e`），两脚本语法检查0错误。实际`-CheckOnly`已核当前五stock、13保护、配置存在性、source/owned artifact及无alias，相关进程/TCP/UDP均0；不安装、不启动。运行分支还要求新`-UserReady`并再次完整预检，备份原DLL后才建立自有alias和安装观察器；正常退出后只对准确自有安装/alias/备份恢复，不触未知修改。SDK分支失败时，若90秒monitor及同轮进程仍有效，R1仍独立采集并保留各自结果。当前等待新的实时在场确认。

## 本轮合并实测与收尾

上述准备后，首个后台Windows PowerShell入口因PSModulePath未能自动加载`Get-FileHash`在安装前结束；只读后台诊断复现，前台同门禁通过，排除了输入漂移。第二个未提权入口已安装观察器但未取得客户端PID，0次target读；静态EXE清单为requireAdministrator，当前启动token非提升，原CreateProcessW入口不弹UAC。原DLL立即恢复。失败报告、空raw及诊断均保留，不把它们记作90秒观察。

主线在新`run-early-live2.ps1`/恢复脚本中明确导入Utility并补正确模块路径，安装前校验管理员token。实际普通token负例拒绝且没有安装、后台只读CheckOnly通过，五stock/13保护和进程端口再次通过。新UAC launcher只启动该固定helper；`live-plan2.private.json` SHA `e04fbd7a8514ba7a385d1f682d189bc027ad7c5c312156244b2744c63a0ff2e1`，run SHA `fbcbbe6872e25467e49a05d081d7551d49da4b968c57ad63e91dbaa916fa1ae1`、restore SHA `d18092456b7f65ad8843f0048968a6bee67e51683ffad41c928bf3ac72325d65`。数据/读预算、DLL和两个adapter源码均未改变；没有无假设重复旧实验。

用户已授权并通过UAC后，实际观察90秒45个监测sample。SDK固定9请求1450B，R1实际256请求8104B，共265请求9554B；最大单次512B，小于准备上限。R1由原cache141/3048、全部新首遍31/1888和最终84unique复读组成，全图与主图末尾均repeated-unchanged，主图83个exact spans。三模块/创建身份/前后存活一致，原指针未改，无写入、remote call或原生CPU。原始`live/warm-live2.private.json` SHA `cfea3e67d6795e791c541e2c140db8b3f19cc70d6c571f47e7ce84c3eb5a4204`。

7/8主候选取得：List类/空数组槽（不含数组body）、HashSet<uint32>类/Default及identity、HashSet<enum>类、既有enum warm getter及identity。同轮enum carrier index15590及owner/上下文成立，但原rgctx+10/+18两槽实际均0，准确停`enumCarrier:getter-slot-mismatch`，没有读取或补造ctor。String primary `B+596c848`实际0，准确停`stringGetter:usage-null`，没有启动稀疏后备或Default/Equals读取。返回整体partial是这些守卫的真实结果，不是短读、复读或生命周期失败；不能标完整候选图、allocator、构造或自然caller已通过。

用户明确反馈“登录页正常，已正常退出，无异常”。主线在进程结束后按自有manifest恢复stock并移除自有alias；五stock、13保护及配置存在性保持，相关客户端/Java/Mongo/调试器/guard、固定TCP/UDP均0，JAR SHA保持。post-exit proof SHA `9115095a3ffefad62b9ef9cbb1f5512610a9e361b8ed0af66b445f943af38a90`。

主线用**已采字节**重新执行SDK纯collector及R1完整时间序列重投影，地址/顺序/字节/全部输出相等；来源、预算、原trampoline、两个零槽/空usage、84项终读及退出恢复等14项通过，`r2-early-bridge/live2-main-review.private.json` SHA `12dd94c7b1457a2a15c4041de45c91931afde773eb24e680e313e326ac0ffb1f`。本子项只验收有界采集与恢复；业务未闭不改成功。用户最新要求当前工作收尾后暂停，未再加采样、执行allocator/constructor、实现String后备或开启新任务；所有旧ready已消费。
