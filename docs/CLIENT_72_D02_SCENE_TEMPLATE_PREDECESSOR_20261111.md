# F-03d3s2 / SCENE-TEMPLATE-PREDECESSOR（2026-11-11）

## 中文有界计划

| 项目 | 本轮合同 |
| --- | --- |
| 目标 | 只回答原365B wrapper在关联新模板前调用的 `148d8e3f0` 的真实流程、参数、字段/组件/引用变化及正常/错误边界；未证前不称“释放旧模板”。 |
| 已有 | 准确World row、Pool/recycle边界、365B wrapper→585B模板关联，field138373/+140和slot114缺输入审计；MPLevel外部候选仅partial。旧已验项直接复用，不重做slot114缺失审计。 |
| 原caller | owner28174/method219638 `PMGOIJCCDCN` /148da0a30，完整365B SHA `1a3a905d174604d2edb9c531d86569364f232240de6a5d036369feda86d8c0ec`；原 `148da0aa7` 把保存的同receiver交RCX，`148da0aaa e841d9feff` CALL目标148d8e3f0，原return148da0aaf；row留在原wrapper局部，不是该call参数。 |
| 原callee身份 | metadata唯一owner28174/method219315 `EJBPNMFBPNI` /148d8e3f0、零声明参数、flags86、非虚slotFFFF。旧signature extractor省略零参声明；不因此发明参数或返回类型。 |
| 预算/方法 | 新进程fresh GAME_PATH与五full SHA后，核完整365B/call setup、metadata/AOT原两QWORD，再核唯一exact/containing pdata；先保存pdata/边界，完整body≤8192B/连续≤2048条才取。只解释本窗；可读既有冻结字段/直接callee身份，不新取子callee，不追helper树。 |
| 依赖/外部 | 当前Scene ROW_LIFECYCLE、SLOT114、GITHUB_INCREMENT及fixed源；README优先的既有GitHub fixed/partial直接复用，本已知准确caller小续项不新检索/clone。 |
| 范围 | 只新增本页、`tools/client72_d02_scene_template_predecessor.py`、必要同名test、私有 `local/migration72/d02-resume-20261110b/scene-template-predecessor/`。Proud及旧B写权已交主线，不再修改。共享Project/Harness/旧源码/Proto/patch/resources/config只读。0xdata/子callee/CPU/live/新REA/网络/构建/数据库/进程/Git。 |
| 产物/验收 | 原code/pdata/AOT/caller与方法typed来源固定完整SHA，字段保留原index；准确区分引用清理、组件回调和真正创建/替换/拆除。两fresh原件/机械审核与离线门禁测试，列已证/推断/未知、最小下一输入及主线第三复现。 |
| 停止/恢复 | 身份歧义、漂移、非唯一extent、超8192B/2048条或decode不完整先留诊断停止；安全/Daybreak门禁立即记录，不换方法/模型重试。形成明确规格或准确缺输入即交Sol xhigh/Terra并释放max，不自行开下一卡。slot114动态目标、已有实体148d813d0、MPLevel原组、通知与完整Scene/D-02独立未验。 |

## 当前状态

**本有界静态子出口完成：普通路径在新模板关联前清空旧模板引用field138373/+140。** 旧row非空时可能先对其编码ID调用自定义容器helper154db7400；其所有权/销毁效果未闭，因此本结论只称“清除旧引用”，不称释放旧对象或拆除组件。唯一592B/119条原窗两fresh八原件一致；没有取子callee、xdata或执行CPU/live。

## 原窗口、caller及完整来源

每次新collector进程均实际重新调用本checkout的 `tools/game_path.ps1` 解析GAME_PATH，核profile五文件大小/完整SHA、固定metadata/signature、365B原caller所有bytes和8B setup+E8；再核普通AOT两个原QWORD、唯一exact/containing pdata，先保存pdata/pre-body后才读取一个完整body。实际592B/119条低于8192B/2048条；不以邻方法或反汇编猜end。所有地址仍是固定PE首选VA，不是新的ASLR/heap身份。

| 原件（predecessor1/2） | 实际身份 | 完整SHA-256 |
| --- | --- | --- |
| code | `[148d8e3f0,148d8e640)`，592B/119条 | `c152e481dd8b80373fd22b4eee6e77dc59f8919513e27b48b3aea0b72e39a3dc` |
| pdata | RVA1a7cd4d4，12B=`f0e3d80840e6d808304bf103`；unwind3f14b30，未取xdata | `b3b18578da6d41d2c805b44e02e712c8986a46f6773ec8998cff5730c6436abc` |
| AOT cell | VA1422f9950，8B=`d0548a4201000000`，P=1428a54d0 | `68f5f1908ac9f789c3fc0b5e71c5b8af00962605561f7b8ea2cc4718f00a83da` |
| AOT slot | P+219315*8=142a51a68，8B=`f0e3d84801000000` | `c336d4eba6a5e15b11f9ba52b71427d3e55726c00b47c46ad8e8d2ff4a0e74f7` |
| asm JSON | 各15353B，119条连续原hex完整拼回code | `ed3d6b2d5b18783ae2704ae323c8c867b60841721e50d44e26ae99ad2216a846` |
| extraction报告 | 各10880B，八原件两fresh均逐byte一致 | `2acce57c6faf23b3f32a710ab418ac062bf5ada859b65e9639a47716e16661ce` |

原caller为owner28174/method219638 `PMGOIJCCDCN`，原范围148da0a30起365B、SHA `1a3a905d174604d2edb9c531d86569364f232240de6a5d036369feda86d8c0ec`。wrapper先保存原receiver与新row；148da0aa3恢复同receiver到RSI，148da0aa7/aaa的原8B=`4889f1e841d9feff`，RCX=this，直接CALL219315，正常return148da0aaf。新row此时仍保存在wrapper `[rbp-30]`，没有交给该零参方法。

metadata唯一owner28174 `GCGEJOPPGAE` /method219315 `EJBPNMFBPNI`，零声明参数、flags86、非虚slotFFFF。签名缓存没有此项，因为固定extractor省略零参声明；本卡没有借缺项发明参数或返回类型。普通RET已在原body内，RAX/业务返回语义仍未恢复，不能把“正常返回”称bool成功。

五输入实际完整SHA仍为EXE `8b014f64b5dc12fbc476e0c362b053abfdd7a1378cd282e4ad81057621dbd6b5`、global metadata `45dc7a44dc9fc187146c52f6c33de57e2d1c1dcfec57f8736431553460f6ef2a`、startup metadata `b314810840ca6f495822c9c44483dde2394b9f09ed3c6b13c2782092f04d5105`、SDK `adecdf0bd5442e321b1b66b0ca9e98bef76d5067326aa4add5b75266055a6c25`、stock Astrolabe `87e0a005ac46515156636af2120d3f824bcd28d69253e7d7235b959b54a0b442`。原件根 `local/migration72/d02-resume-20261110b/scene-template-predecessor/`，机器路径/命令/退出与原bytes只留私有根。

## 普通流程：旧引用清理与新引用关联

原入口保存RCX到R14；非dispatch路径在148d8e409从**原entity的+140**取旧模板引用。这不是新row参数，也不是Pool回收接口的对象。

| 原分支/site | 原始行为 | 已证/未知 |
| --- | --- | --- |
| 旧+140为null，e410/413→e547 | 跳共享正常栈恢复/RET e550，不查类型、不调cache、不执行clear store | 字段本来为null；不说明实体、组件或能力已初始化 |
| 非null且属52115类型链，e419..43f→e4d3 | 读旧row+18、交14aef9db0解码；按原byte14587dab1和初始化前置决定是否取GLOBAL+25e30 | 类型cell1458a1300与旧row的klass层级比较有原bytes/metadata来源；自然bool未读 |
| 非null且属40472类型链，e445..465→e46b | 读同旧row+18并解码；按原byte14587cb61决定是否取GLOBAL+237d0 | 类型cell1458a47c8；本真实Gadget40472适用的条件静态边，不认定自然已走 |
| 两类型均不符，或匹配但cache gate为0 | 直接到e53c写 `QWORD[this+140]=0` | 没有解构旧对象或清组件的直接指令 |
| 匹配且gate非0，容器非null | RCX=相应自定义容器，EDX=旧解码U32，R8=原MethodInfo来源，e537 CALL154db7400 | 该call正常返回后，才到e53c清引用；缓存算法、引用计数、淘汰/销毁效果未知 |

原receiver唯一直接成员访问正好两处：e409 load+140与e53c clear+140。字段是owner28174.field138373 `IJDEFJLOMKF`、声明基类27264；World entity46314继承28174。旧row读取的+18是27264.field134163、类型58209，原两处decoder call为e477/e4df。它不是secondary ID/+20，也没有Default_Item/+50或MPLevel/+58读取。ID0没有新的过滤分支；不将其解释成空、无配置或失败。

在原wrapper正常接回后，148da0aaf重新传同receiver，148da0ab2取原new row，148da0ab6调用已验148d6ebf0；后者148d6ed36存同field138373。因而**已证普通静态顺序是“旧引用清理方法返回→关联新row→setter存新引用”**。不跨过未知call或异常断言新store一定发生，不据此批准相同旧/新row时的所有权或幂等语义。

## 缓存、创建、组件及错误边界

两容器的字段与类型直接复用并核固定metadata：

- GLOBAL+237d0：owner54132.field269426，13732实例19433 `<U32,40472>`。
- GLOBAL+25e30：owner78883.field394856，13732实例46664 `<U32,52115>`。

它们不是已证标准Dictionary。当前CALL154db7400的两个R8来源为 **145a0b160**（原e4ca）和 **145a16a18**（原e52e）；这两个MethodInfo尚未独立解析。旧setter的145a0b138/145a16a38绑定101367/154db6810，位置和callee均不同，不能拿旧证明补成新method、Remove、release或destroy。定向搜既有工具/专题的这个精确callee/cell，没有本卡之前可直接复用的身份/语义结论；未新取其body。

本body没有直接entity allocator调用、组件字段读取或组件创建/拆除入口；普通direct callees仅14aef9db0、154db7400、140525e00、1404c2be0。此限定事实不排除未审cache helper的间接效果，也不证明任何对象已经创建或销毁。原146e055f0的IAutoAllocRecycle接口与此方法不同，仍不能当实体ctor。

原byte1457ec003非0时不执行本普通清理，而是取dispatch对象，把同receiver送RDX，恢复栈后在e57b尾跳14d8de310；自然flag/目标行为未读。普通路径的容器为null以及dispatch对象为null仍到原1404c2be0；类初始化走原140525e00冷路径。不执行或模拟错误helper，不给它们补null成功/固定RET；异常/unwind、错误/返回样本和完整生命周期保持未知。

## 最小下一输入与停止点

若后继只需要修正Scene生命周期描述，本卡已足够：将148d8e3f0记为“普通路径清除旧模板引用，之前有条件custom-cache调用”，不能记“释放旧模板/销毁组件”。可交Sol xhigh维护准确静态合同；不增加Java sender/生产资源许可。

若确需关闭cache/所有权职责，下一最小是**纯数据两MethodInfo包**：按本窗两条7B RIP load、既审metadata resolver的完整SHA，分别唯一绑定145a0b160与145a16a18的usage/methodSpec/class tuple/原AOT指针，并确认154db7400；建议选中记录/header/pointer总上界256B，无code/pdata/xdata/CPU/live。这只是下一计划，未由本卡读取或自动批准；即使方法名恢复也不代其算法或销毁效果。完整计划在私有 `next-input-spec.zh.md`，未开helper树。

slot114实际目标、已有实体148d813d0、MPLevel原分组key/原string[]与配置能力、组件/通知顺序均是独立问题。此次没重做36读缺槽审计、没读取metadata slot114候选1483df040，也不把旧外部同名MPLevel样本当目标注册或空能力证明。既有Scene GitHub fixed/partial及README筛选直接复用，无新网络/clone。

## 实际测试、复现与交付

私有collector `collect_predecessor.private.py` SHA `b4797e991cd2def021e93e7d59569a311d63ad541d15e18ee9e65e0e51d42c42`；两次fresh取证均exit0，唯一distinct code窗592B，八原件逐byte相同。新CLI只读冻结来源/两份八原件，不打开当前PE/进程或执行目标：

```powershell
python -B -m unittest tools.test_client72_d02_scene_template_predecessor -v
python -B tools/client72_d02_scene_template_predecessor.py --output <新私有basename>.json
```

实际6项测试通过：真实field/清零与未批准销毁边界、source漂移在window/metadata前拒绝、原code漂移、假邻界/填充extent、重复containing pdata与错误AOT。两fresh最终CLI各46项静态断言通过，报告各11614B、逐byte相同，SHA **`938b6b796b7362fbe7d55f588ed3065a24d1b33918fee049f812829c063c2593`**。原完整365B、592B和既有585B的精确store、receiver来源、字段index/typed容器、返回与错误边界均有源门禁。它们不是native测试、异常返回或客户端验收。

本轮未遇安全/Daybreak门禁，没有重试旧B包。只新增本页、public工具/同名测试及指定私有根；Proud旧写权、Project/Harness/旧文件/Proto/patch/resources/config保持只读，不操作Git、客户端/调试器/DLL/live、新REA、构建/JVM/DB/服务。仅离线研究无需部署/重启，新增客户端验收0；完整Scene/resourceReady/D-02未验。主线负责独立第三、共享状态和本地提交；max交回全部写权，不自行扩下项。
