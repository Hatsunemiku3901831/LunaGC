# E-01 / R3-CRITICAL-CONSUMER

## 有界实施计划（2026-11-09）

准确目标仍为固定国服 7.2 客户端的 Avatar58272 继承63287字段 `+98/+a4` 与 `FIGHT_PROP_CRITICAL20/CRITICAL_HURT22` 的职责关系。原生 parser 已知 float32 位值仅作定位，不以常见数值、上游同名、7.1 或 Java 默认批准 alias。

本轮与已做直接 helper/caller 搜索的区别：先核旧16 site/14 opcode候选的真实参数和 owner 字段类型，并向 Avatar 的声明父类63287、27264扩展**metadata 参数引用**；再只在这些 typed 职责候选内辨别整块读取、字段地址传递或复制后消费。旧 `+a4` load 后40B直接getter过滤会漏间接路径，本轮不重复该全体搜索，也不把未命中当不存在证明。

1. 通过 `tools/game_path.ps1` 解析本机当前输入；固定 profile 五stock、签名缓存及复用raw的原字节/SHA。只读复用 `r3-critical/windows{1,2}.private.json` 八个已审窗和 `r3-avatar/critical-exact-load-types.private.json` 的真实typed材料。
2. 先完成 metadata 候选分类，不新开窗：明确参数、继承链、owner所持目标资源与其它资源对象；对于可能通过父类型取得Avatar行的职责，列出具体可核理由。准确排除不同资源/错误offset归属的候选，保留未知返回类型和未恢复间接边。
3. 最多新增六个 PE exception 完整准确窗。每个必须先有上述typed理由，不全EXE盲扫；保留extent、原字节SHA、签名与指令。已完整排除的 `14c9e8400`、`148bde5a0` 和主线八窗不重开；HP/curves/Promote整资源不重跑。
4. 只有数据流真实连接Avatar字段与20/22时，向主线提交最小原生正反验证合同（非零/交换/缺项、typed live-in、模型与预算），未审新模型先交主线，不自行扩大CPU。本轮默认只做静态离线分析。
5. 无桥时交付准确候选排除、剩余输入/解除条件及可独立后继；字段与D-02保持未完成。所有private材料仅写入 `local/migration72/d02-resume-20261109/r3-critical-consumer/`。

范围仅本页与本轮private目录。不得改共享索引、冻结工具/文档、Java/Proto/resources/patch/config，不操作Git、构建、数据库、端口、客户端、调试器、安装或共享REA会话。本卡不运行生产材料，故不触发构建/部署。若需新增可交付离线工具及test，先向主线交具体实现计划并获确认。

## 当前结果

本轮得到**同声明父类字段到三浮点安装函数的准确静态链**，首缺已缩小到 `1498e2200` 的职责。没有得到 `+98/+a4 → FightProperty20/22` 最后一段证据，仍不批准 `critical/criticalHurt` alias。六个新准确窗预算已用完，未执行原生CPU、Java或实际客户端。

旧 `critical-exact-load-sites.private.json` 的 `1505f5420` 不是已排除的无关资源：其一条普通路径把共享63287中的 `+98/+78/+a4` 三字段送入真实安装函数。该路径实际先要求子类17494，**不是 Avatar58272 的自然调用路径**；共享字段声明和参数顺序已知，属性名与Avatar自然使用仍需分开证明。其余旧候选不因此被排除。

## 固定来源和准确typed范围

输入由当次 `tools/game_path.ps1` 解析，未写入机器安装路径。profile五stock在开始与收尾静态核对均相符；主线中途临时观察Astrolabe期间，本卡没有重开五stock gate，只读取固定EXE及已经核对的metadata。没有操作客户端、DLL、进程或调试器。

| 复用材料 | 原字节 / SHA-256 |
| --- | --- |
| `local/migration72/evidence/client72-signatures.json` | 78,649,549B；`28377e7a8d3c891cd61be97ddb74f8502a22f2f2f039d6a91dba3b092d7822fe` |
| `d02-resume-20261108/r3-equipment/metadata.private.pkl` | 117,201,003B；`df52eb70fe8e70dfecd1059e898d15e288cfa98bcefc78bcaabf753ae150349f`，并与冻结equipment工具的pin一致 |
| `d02-resume-20261109/r3-avatar/main-third.json` | 949,711B；`32cf0c85dd4df7976a78a33fca92464b6b6bdb01f1fd7e29dace9852de1c222e`；只核hash并复用，不重跑HP/growth |

上述短目录均位于 `local/migration72/`。本轮 `metadata-inventory.private.json` 保存14个完整输入pin、目标声明链、旧候选完整owner继承字段、typed字段持有者和准确参数投影。签名缓存的 `typeReferences` 以 **typeIndex** 为键；本轮按参数的真实 `typeDefinition` 分类，不能直接用类型定义数字作缓存键。首次显示尝试在观察到该字典结构后停止，未产生窗口或CPU结果；后续投影与独立收尾脚本均使用正确结构。

直接参数引用实际为 Avatar58272 **21**、63287 **0**、27264 **3**、FightProperty32419 **84**。零引用只指该签名缓存中的直接参数，不覆盖返回类型、内部强转、泛型/数组中的嵌套、虚调用或字段持有；缓存返回类型仍为 `not-recovered`。

共有两类继承63287：Avatar58272和17494。后者有5个准确参数消费者，其中 `14980eb20(FightProperty32419,17494,12532)`、`1505f71d0(float,FightProperty32419,17494,40932)` 同时具有属性参数。第二个的owner69628恰包含旧直接getter读取点 `1505f5420`，成为最后一窗的具体选择理由。没有按相同偏移在全EXE开窗。

## 六个新准确窗

均取固定EXE的PE exception完整extent，保存全部原始指令bytes、地址、大小与解码文本；总 **8,768B / 1,896条指令**。本轮不新增第七窗。

| 入口 / exclusive end | 字节 | SHA-256 | 本次结论 |
| --- | --- | --- | --- |
| `148d6ebf0 / 148d6ee39` | 585 | `8670d03e37b4155990e0f8fa4dc07238dc090d0143378df352abe61a2d583f65` | 真实27264参数；普通路径只解 `+18` 并将整个资源引用存入owner28174的`+140`，不是目标浮点字段复制。 |
| `148da0a30 / 148da0b9d` | 365 | `1a3a905d174604d2edb9c531d86569364f232240de6a5d036369feda86d8c0ec` | 真实27264参数；包装调用上述安装引用方法，没有目标字段职责。 |
| `14e91fee0 / 14e91fff7` | 279 | `b62d515cb3243ad80f8594cbbf193ecc469ff55d24e07b653c1e7a186d2f00bb` | 真实27264与`Action<uint64>`参数；类型分支取`+88/+c8/+188`的qword交callback，未读取`+98/+a4`。 |
| `14980eb20 / 14980ec9e` | 382 | `3fef2caee8e180dcb16b4c52561a3d431d76b53b0edb97b87db39a986468eaba` | 17494+FightProperty消费者，但只沿继承`+48`绑定列表匹配并读取12532曲线项；没有目标两标量的职责。 |
| `1505f71d0 / 1505f7287` | 183 | `e6f55d58d516f333ac50f1e2ea76687bddeae61e9c2090c338840ff4e42b3b8b` | 同样按17494继承`+48`匹配属性，再转成长helper；没有目标两标量读取。 |
| `1505f5420 / 1505f6f5e` | 6,974 | `a66deffc6ab5d432cea85fb2a591b4bf8c30b97e553e4efa81c0ee31d6849e5b` | 旧直接getter候选的完整窗。本轮确认目标声明父类字段→三浮点consumer链，保留为下一锚点。 |

三个private窗口文件分别为 `parent-windows.private.json`、`sibling-windows.private.json`、`old-owner-window.private.json`；最后两份另带精确RIP typed usage与直接callee metadata，均不代表CPU执行。

## 字段、接收者与实参顺序

`1505f5420` 是owner69628的method576629。其继承链 `69628→41463→59769→58999→27665` 提供以下真实字段类型：27665/field136031 `+28` 是28174；28174的 `IJDEFJLOMKF/+140` 是27264；59769/field296496 `+180` 是24887。目标路径不是按同偏移猜对象。

1. `1505f5948` 从 `self+28` 取28174；`5955` 从该对象 `+140` 取 `RDI`。`5965..598c` 使用cell `14589aa60` 的真实typed usage（type17494）核继承类型；不符即走退出。因此此处 `RDI` 为17494，其 `+98/+78/+a4` 均由63287声明。
2. 该普通路径到下述读取前没有替换 `RDI`。`1505f5cbe` 从 `self+180` 取24887接收者到 `RBX`。
3. 三次原指令调用相同原生getter `14f905140`，其metadata为54724/`EBGBPCMHIGK`.method454810。当前只静态确认调用；本卡没有执行getter或用报告中的常见值替代它。

| 共享真实字段 | 取值与原getter call | 安装调用前的真实实参 |
| --- | --- | --- |
| 63287/field313963 `FNBIHFNPHBE/+98`，54724 | `1505f5cc5` load → `5cda` getter → `5cdf`存XMM6 | `5d22: XMM1=XMM6`，第1个float |
| 63287/field313956 `LDJJCBPKEDO/+78`，54724 | `1505f5ce2` load → `5cf4` getter → `5cf9`存XMM7 | `5d25: XMM2=XMM7`，第2个float；语义未知 |
| 63287/field313966 `HDJFAACKBON/+a4`，54724 | `1505f5cfc` load → `5d11` getter | `5d28: XMM3=XMM0`，第3个float |

`1505f5d1f` 设置 `RCX=RBX`，`5d2b` 调用 **24887.method193190 / `1498e2200`**，metadata准确有3个float参数，依次名为 `PGPCFDIOGIJ/EEENJILKCGN/DIMBALBONJO`。这些混淆参数名不提供20/22业务语义；owner24887中没有同名字段可直接接管职责。该owner有133字段，含30710的16B值类型；只凭固定offset或三参数顺序也不能命名。

这是共享声明字段的**静态类型/数据流桥**，还不是最终具名属性桥。17494类型门禁不能对Avatar58272自然通过；将Avatar行放在编码后的片段入口只能验证共享字段片段，必须明确声明该live-in边界。`bridge-metadata.private.json` 保留17494、24887、61723、69628相关typed材料。

## 下一最小可执行合同（交主线审，不在本卡执行）

第一步只需新卡打开固定EXE中 **`1498e2200` 的准确PE exception窗**，从三个float实参追至真实输出。先确定它是否安装到可独立绑定FightProperty32419的20/22位置；若再进入wrapper、字段表或初始化映射，交准确目标与typed证据后再定窗口，不将混淆名、参数位置或常见数值作属性许可。本卡未读取该callee体，未预设其实现或新CPU模型。

若该窗口及已有同源材料能闭合20/22职责，最小原生验证才可准备：

- 用固定metadata/已有冻结harness创建准确24887接收存储，并执行完整原installer到原return；三输入用互不相等的非零float32位值，再交换第1/第3输入。必须经真实输出的具名属性读取链检查顺序，不能让host hook直接把参数存到假定20/22槽。
- 从已核真实source span重新执行选定Avatar5/7及独立136的原constructor/parser，复用54724既有codec。只执行 `1505f5cc5..1505f5d30` 这段共享字段片段（并执行完整installer），live-in明确为`RDI=实际parser行`、`RBX=typed24887`、有效原栈；第2字段保留实际值。5/7原位值和136仅供非零/独立样本，不反过来证明语义。
- 增加一组`+98/+a4`编码值交换、一个合法零值对照及缺接收者/缺行边界。零值、字段缺席与null保持分开；未知省略行为不转成Java默认许可。负例从相应原校验入口执行；片段本身没有判空时就记录实际内存读失败，不能新加拒绝桩。若只到原异常helper也如实记录停止点。
- 正向输出逐float32 bits比较，保留getter、setter、wrapper与显式fixture的所有真实调用计数。建议限一组不超过8个consumer/installer case，全部constructor、parser、getter与negative计入同一500,000 hook总额，单调用不超过冻结harness原2M上限；遇未审间接边或自然输入缺失立即停，先交准确新模型/首缺，不加Random、空引用、默认成功或手写属性安装桩。

上述是供主线选定实现的条件合同，尚无可执行新tool/test或CPU产物。若主线审明类型/职责与模型后实施，应先冻结新tool/test和全部输入pin，两个fresh进程逐字节一致，再做主线第三独立复验。即便共享字段片段通过，完整Avatar自然consumer、Java完整行/真实加载、active revision和D-02仍分别验收。

## 实际验证、剩余候选与交付边界

本轮private `verify.private.py` 仅作只读收尾：核14个完整输入pin、metadata缓存固定SHA、五类准确参数计数、声明/持有链、6个exception extent及全部8,768B原字节，再对全部1,896条指令重新解码逐项比较，并独立断言上述类型cell和三实参顺序。两次fresh Python进程均通过；`verification-first.private.json` 与 `verification-second.private.json`逐字节一致。它没有执行目标代码，不能当原生/Java/实机测试。

```powershell
python local/migration72/d02-resume-20261109/r3-critical-consumer/verify.private.py
```

旧16 site属于14个distinct opcode入口。其中 `14c9e8400` 沿用旧完整排除；`1505f5420` 本轮确认共享字段链；另 **12个旧入口本轮未完整开窗，不能整体排除**，准确列表见verification。旧 `148bde5a0`、主线新八窗和HP/curves/Promote结果均只复用，没有重复开窗/CPU或整资源解析。此次新5个无目标标量职责的窗口只排除各自已审普通路径，不证明不存在间接消费者或热修路径。

修改仅本页和本卡忽略private目录；未改Java、Proto、resources、patch、配置、冻结工具及共享Project/Harness/HANDOFF。未执行Git、构建、数据库、端口或客户端操作；没有运行材料变更，无需部署。本卡没有本地提交，交主线审核后分项提交并维护共享索引。

当前准确下一项为：主线审 `1498e2200` 的新准确窗及必要具名输出映射，再决定是否实现上述原生合同；不继续无界遍历旧12候选。`critical/criticalHurt`、完整资源/自然Avatar使用和D-02均保持未完成。

## 主线第三与下一范围

主线已实际运行冻结验证器，第三 `verification-main-third.private.json` 为992B/SHA `2e9cd93d058571d86500c20b51053ae70eacae63d30c10f3d7de88b77790a0f6`，与两个fresh结果逐字节相同。14输入pin、六个完整extent/8768原bytes/1896指令、类型cell及三实参顺序均通过。它仍是静态来源/数据流验证，没有CPU或自然Avatar执行。

后继[准确installer](CLIENT_72_D02_AVATAR_CRITICAL_INSTALLER.md)先只核 `1498e2200`；只有具名属性桥和接收者/模型范围充分时，才准备最小原生正反验证。旧consumer材料与六窗冻结不改；字段alias、完整Java行及D-02仍未批准。

后续状态：原installer及具名20/22消费已通过主线第三，批准基础critical/criticalHurt片段，见该[新验收](CLIENT_72_D02_AVATAR_CRITICAL_INSTALLER.md#主线第三验收)。本页静态阶段及六窗原件不变；Avatar不能自然通过原17494门禁的边界仍保留，完整行/自然调用/D-02未验。
