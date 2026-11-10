# E-01/R3-OLD-PHASE6-PROUD：旧号 phase6 的准确 proud 首缺

所选旧号的121个角色都是 level100 / promoteLevel6。当前Java旧档恢复会在 `AvatarStorage.postLoad` 对每个恢复角色执行 `recalcStats`，随后清空、按当前Depot重建proud。因此不能保留保存的410条proud充当最终运行结果，也不能用已验出生phase0、空规范化或空解锁集合绕过本题。

**本包交出一个已明确的数据后继规格，尚不交proud投影或alias。** 四名活动角色的12条保存Skill等级、24条talent、16条proud及各自目标source已固定；四个实际保存Depot的原payload区间SHA也已复核。但固定mapping的 `selected` 尚没有这四行字段快照。缺少实际字段样本和缺少group/level、promote职责是两类问题；只补样本不会自动批准语义。全121恢复、完整资源与D-02保持未验。

## 计划、范围和固定证据

先写完整有界计划 `local/migration72/d02-resume-20261110b/r3-proud-phase6/plan.zh.md`，SHA-256 `6a9b4ed548c46828d6fb6a6c383773168f4c93cd766329e77df7284ed7e56b92`。本包只写本文与该独立私有目录；不改共享生产/Proto/helper/生成器/矩阵、资源、配置、旧pin或任务索引。没有新GitHub方向、检索或clone，复用既有固定来源及前轮有界检索结果；历史partial不改写成完整覆盖。

| 本次直接固定输入 | SHA-256 |
| --- | --- |
| `old-account-snapshot/run-20261110-2/selected-dependencies.private.json` | `3ef64138cd0525442df1625e823bb41a66089c3ea114deb99c7da24f201ec0b7` |
| 同目录 `selected-manifest.private.json` | `9fffdc25ddb24376552e3163db9ed6de441548d843fbda82649996fb59d4910b` |
| 同目录 `acceptance.private.json` | `80a39b3943d0e4658cdfae3bc561cafebd0ddcbbc755aeefc7d0df2d4f447667` |
| 前轮proud `main-third.private.json` | `a0a1f93cb980ddcd39fc46c70c46742f20c8ef154a8690816c2b068c7fdfbc9d` |
| 前轮proud `main-review.private.json` | `e6adcd73b22be3f9491afcf4a7757b03a934a60e657b45a8831990fefe2b2764` |
| `AvatarStorage.java` | `6d6b07102d5f8b9f95ebbd4e18d1489f9143d502100dcfaadff0235db138e784` |
| `ProudSkillData.java` | `bacd37a17a85d409b4cc701cc0c0e180fdea8fcf208026a148e73f1afaeadef2` |
| `tools/client72_avatar_natural_tables.py` | `920395dfd4817523951d2c766167ca481d8f6b2b64288980fee2cade2ae9ecad` |
| `tools/client72_d02_resource_bridge.py` | `a61531b49894a69a864c0467d683bb8f749dfcfe57051eda83b17a1cebdbbc11` |

快照路径相对 `local/migration72/d02-resume-20261110b/`；前轮proud相对 `local/migration72/d02-resume-20261110/c-depot-proud-successor/`。Java准确路径分别为 `src/main/java/emu/grasscutter/game/avatar/AvatarStorage.java`、`src/main/java/emu/grasscutter/data/excels/ProudSkillData.java`。直接读取私有manifest中的原导出SHA，不将共享专题的手填文本当immutable输入，也没有再次读取原BSON或连接数据库。

另逐一重核[前轮proud](CLIENT_72_D02_DEPOT_PROUD_SUCCESSOR_20261110.md)的原20pin，包括固定metadata/signatures、mapping、原Depot payload、5901B/304B/351B及其asm/pdata、Avatar与Depot源码。它们是复用证据，不重计为新窗口、native执行或字段闭合。四个目标Depot原source跨度来自固定mapping，分别对实际26024B payload区间重新计算SHA；没有从mapping的对象hex解码新字段。

## 真实Java先读什么

| 精确路径 | 这次确认的用途和边界 |
| --- | --- |
| `Avatar(data):128..156` | 新建时先建空talent/proud set，选择默认Depot，再调用setSkillDepotData。两出生已验返回只覆盖其实际phase0与空解锁条件，不代表旧号。 |
| `Avatar.setSkillDepotData:279..302` | 先补缺Skill等级，再清空proud；依次检查inherent正group、`needAvatarPromoteLevel <= 当前保存phase`、按`group*100+1`查Proud map，加入quest组，随后重算constellations与stats。 |
| `AvatarStorage.load:218..241` | 按保存Avatar ID和保存当前Depot分别找资源并绑定，先执行recalcConstellations；仅两位主角额外调用setSkillDepotData。不能把所有旧角色都描述为走了出生constructor。 |
| `AvatarStorage.postLoad:248..257` → `Avatar.recalcStatProperties:832..879` | 对每个已恢复Avatar执行，835先清空保存proud，再按实际Depot与保存phase重建；857..869读取被选Proud的addProps/OpenConfig，873后读取当前Depot中已解锁Talent的OpenConfig。四活动角色只是最小研究样本，不能代验121角色。 |
| `recalcStatProperties:651..717` | proud之前已有实际Avatar promote group / 保存phase6与level100成长消费；本包不把出生group12/phase0推广，不把这条独立资源前置算作已闭合。 |
| `upgradeSkillLocked:1043..1063` | 新等级、Proud group/level一致性、`ProudSkillData.breakLevel`及材料是技能升级条件路径。四活动样本的12条保存技能均为10级，当前Java1044已拒绝继续升级；不能把这条breakLevel或全部升级材料当成入场必读，也不能用它替代81127+10的职责。 |

这使proud方向的最小语义问题准确收敛为：**当前Depot的inherent元素如何选择可用项，以及每项如何从原pair查到真实Proud行ID。** 首先需要81127+14至pair低U32的业务身份、pair高U32的level身份、真实返回行ID关系；同时需要81127+10对已批准Avatar `PROP_BREAK_LEVEL=1002` 值的实际比较。主角/普通角色和phase0/6不能互代。这不是要求全部保存proud、talent或Skill额外字段无条件成为P0。

## 同一5901B的准确caller后继，不借同名批准level

旧直接链保持：Depot22284+18 / field105355取81127数组，元素+14 / field404854经58209 codec匹配后，原caller向14d7ae900传命中ID、常量1、false；351B函数准确构造 `(secondU32 << 32) | firstU32` 的80067 key，查询out U32，再交14d7adf70。此处没有 `group*100+level` 算式。

本次进一步核了同一5901B已存原bytes中的16个准确opcode，连接inherent命中后的两个数值消费者：

| 原site | 直接能得出的关系 |
| --- | --- |
| `150b29c5d`、`150b29c72`、`150b29c78` | 从同一typed63831 wrapper取field316249/+40→NativeConfig41105，再取field203376/+90，经原跳转进入`150b2a1b1`。 |
| `150b2a1ba`、`150b2a1bf`、`150b2a1c5`、`150b2a1ca` | +90经过原58209 getter解码，与iterator13300.field62191/+18的typedAvatar18163传给150b29150；返回U32写EnhanceDetail13298.field62176/+28。 |
| `150b2a1cd`、`150b2a1d6`、`150b2a200`、`150b2a227`、`150b2a22c` | 同NativeConfig+90再次经58209解码，并以Avatar与该值调用18163的151852510；返回写EnhanceDetail.field62179/+34。 |

这里的63831身份沿用前轮对调用方期待对象形状的typed约束：R14先作为149f0e310（owner63831/method529244）的this被消费。它不恢复14d7ae900的声明返回类型，也不是运行时类型检查或自然对象观察。

150b29150唯一metadata为owner13298/method97805，真实参数为U32、class18163。它的返回写入字段62176，与80067高U32有同混淆名 `BHLEAKEIAMH`；**同名只用于定位，没有独立证明两者都叫level**。以上没有读取81127+10，也没有证明原pair输出ID与Java算式相等，不批准group、level或promote alias。

该函数被本iterator多个类别分支共同调用，仅凭当前caller并不能证明其body会读所需81127门槛。本次没有以“可能可达”为由申请它的新窗，也没有把这次同窗检查称为proud接近完整。

## 有界排除与没有新窗口的理由

固定metadata中，typedDepot22284字段仅存在于13300 iterator、26348捕获对象、53763组件及63488静态字典；完整参数索引有6个声明方法接收该typedDepot。该索引是定位范围，不能将所有命中当成直接消费。

- 旧304B153459190的负结论保留：只读Depot+54，随后安排状态/delegate，不读+18/81127或+10门槛。不继续其map/queue/callback/hotfix树。
- 现有49个组件函数中，调用153456820的准确caller先传旧组件+290 Depot，随后清零+290；在153461980中又写入新Depot。它是拆除/切换上下文定位，尚未见body读取所需门槛，不能因typedDepot签名就选新窗。
- 153455160、50166/14d18b530及26348/148c823a0目前只有typed参数或捕获字段信息，没有本题所需原81127+10读取、promote比较或pair生产链。没有据此盲提窗。
- 前轮80065/1508ae890已证明为reader→out80067签名，没有typed63831输入；不改称row→pair selector，不重新走这条假设。

本包没有建立一个能回答所需字段职责的唯一新entry，因而**0新code窗、0新pdata、0PE读取、0native CPU、0live**。这不是证明目标客户端不存在consumer；准确缺输入仍是带typed来源的81127+10读取/比较，以及pair两项与真实Proud行的producer/consumer关系。

探索时两次本地查询本身失败（把list形状当dict、PowerShell通配符作为rg字面路径）；只修查询后继续，未写输入或产生native结果，不把这些工具错误当目标负结论。

## 可直接执行的下一样本

已写完整实施规格 `local/migration72/d02-resume-20261110b/r3-proud-phase6/native-probe-spec.zh.md`，交Sol xhigh / Terra新建独立driver；本max包至此释放。核心范围为：

1. 复用固定whole `14d6d54a0` / row parser `148328b60`，只解析原26024B Depot payload一次，count171、cursorEOF、key/source与原mapping一致；随后同runtime用原query `14d6d70f0`查四个私有保存Depot，不host返回pointer。
2. 只按typed field读取105354/+10的完整40302条件数组、105355/+18的81127两叶、已批105357/+28 talents、105358/+30 skills、105363/+54 id及105369/+6c energy；encoded58209调用原getter14aef9db0。保留原顺序、0、null、缺字段、参数/条件enum，不命名未知叶，不从对象hex或候选JSON解码。
3. 固定原五文件、codec、source/code/model完整SHA；复用旧nativeSources允许范围和既有分配/字符串/metadata/容器模型，不增加新code窗、unknown成功stub或预算。只做数据样本，不执行UI iterator、proud map getter、Avatar或组件生命周期。
4. 两fresh字节一致及主线第三来源/query/codec/列表核验后，按实际非零引用选后继资源族。group/level、`group*100+1`等价、81127+10职责和special条件仍各自保持未知；不能以保存值恰好相符补语义。

这一步无需GitHub重新搜索整表、全客户端索引或新现场；也不要求用户操作。实施规格中含完整路径/hash、所选source、精确字段、模型、入口、上界、failure/停止与验收条件。它只解决确切样本未被选出的缺口，语义缺口仍交主线按直接来源继续。

## 实际验证与交付范围

新私有 `audit.private.py` SHA `84bbd9b1a0856ac42bfcb04ca6eb368c1baf324fcaf259d6413b68208b25fdef` 在两个独立Python进程完成。`first.private.json` 与 `repeat.private.json` 各20547B，SHA均 `7e6010fa975587364d5a89fe5bc02cf564b5ecb80fea3efda4dc49c6b95f04e4`；已重核20旧pin、9个本包直接pin、4个实际Depot payload区间、4个活动样本及16个同窗opcode。私人角色明细只留该目录。本次不新增共享tools/test，也不重跑已验Java或资源算法。

没有客户端、调试器、Windows API、native执行、JVM、Mongo、服务、构建、部署、外部检索、Git提交或分支动作；纯文档/离线审计无需GS重启。`inherentProjectionApproved=false`、`promoteThresholdApproved=false`、`gTimes100PlusLevelProven=false`、`resourceReady=false`、`completeAvatar=false`、`d02Complete=false`保持。共享Project/Harness更新及本地提交由主线审阅后统一处理。

主线第三独立审计 `main-third.private.json` 为20547B，SHA与两fresh相同（`7e6010fa975587364d5a89fe5bc02cf564b5ecb80fea3efda4dc49c6b95f04e4`）；原20pin、4实际payload跨度和16同窗opcode复核通过。本子出口验收，后继原字段采样已单独完成，proud语义及完整恢复保持未验。
