# 7.2 Avatar 构造精确片段与省略消费边界

任务 `F-03d3f4d1 / D02-R1`。本卡修正继续沿用f1零分配对象会产生的假缺项判断，执行真实18163构造器的可验证片段、必经子构造和有限省略消费者，建立完整handler尚未闭合的准确停止表。**完整构造器未验，完整Avatar handler未验，入场必要性未建立。** 没有把未知间接调用做成成功stub。

仅新增独立工具、测试、摘要及本文档；旧runtime、Proto/Java、sender、opcode、mapping、资源和配置保持原样。`completeConstructor=false`、`completeAvatarHandler=false`、`runtimeStatus=not-tested`、`targetProtocolReady=false`。按最新暂停要求，此项收口后不启动f4d2、g2或其它新研究。

## 固定来源与实验假设

每个正式进程先通过当前 `tools/game_path.ps1` 取得路径，再重新验证三份 `extract_client72.HASHES` 输入并重提目标类型/具体AOT/PE函数范围：

- EXE：`8b014f64b5dc12fbc476e0c362b053abfdd7a1378cd282e4ad81057621dbd6b5`
- global：`45dc7a44dc9fc187146c52f6c33de57e2d1c1dcfec57f8736431553460f6ef2a`
- startup：`b314810840ca6f495822c9c44483dde2394b9f09ed3c6b13c2782092f04d5105`

e8、f1、f2a/f2b、f3、f4a/b/c摘要按工具 `EVIDENCE_SHA` 逐字节pin。另复验f4c固定的候选commit/root/Excel tree和四表blob/hash；这些来源没有在本卡成为完整客户端资源或真实角色状态。实验使用合成Avatar1001、受控高64位GUID、可选合成scene/entity字典；非空装备样本沿用f1的合成Inventory物品10001/GUID `0x100000001`，并不代表实际初始weapon11101已完成构造/应用。

实际完整AvatarInfo初始化入口为 `15184e100`；`151849670` 是生命状态子调用。真实18163 `.ctor` 为 `15183fe60`，其中存在多次间接泛型构造，不能把已有零对象fixture称作真实构造默认模型。实验先独立核非零旧scalar被构造覆盖，再运行读取器产生的非null空集合，对比旧值清理、保持和runtime null等不同路径。

## 实际构造片段及准确缺口

四个片段均从目标机器码执行，后续三个有显式 `r14=model` live-in。它们是独立片段，不是跳过未知调用后声称连续执行整个构造器。

| 原生范围（stop之前） | 实际验证 | 未执行部分 |
| --- | --- | --- |
| `15183fe60 → 15183ff11` | +284从777写1，+2b0从0写true；+70 List<uint32>、+a0 HashSet<uint32>；真实 `157d97690` 和 `157d7fe90` comparer getter | enum20060 HashSet的comparer重载间接调用 |
| `15183ff1a → 15183ff71` | +68 List<uint64>真实创建/存储；分配typed SortedDictionary | SortedDictionary comparer重载间接调用及+1c0存储 |
| `15183ff7a → 15183ffb4` | typed34054对象分配、真实调用前缀 | 34054继承构造的间接目标及+50存储；+1c8/+40同类分支未运行 |
| `15184003d → 151840331` | List/编码字典直接构造stores及真实服装子构造，停止于构造器epilogue之前 | 未把中间缺口或epilogue计为执行 |

具体零参AOT重新提取：`HashSet<enum20060>/2147/5917 .ctor=156fbc020`；`SortedDictionary<32758,51041>/2034/12373 .ctor=1570f5f20`；34054父类 `69137/5278 .ctor=155b229f0`。这些零参wrapper又经rgctx调用更深构造，不足以证明其间接后继地址。没有套用同形int32/u32或其它泛型实例填补该缺口。

typed分配、已初始化的空数组、EqualityComparer缓存及MethodInfo/rgctx内存是明确的managed fixture。comparer只作为缓存中的不透明非null指针，未执行比较器创建或比较算法。已测getter读取缓存的机器码实际运行。未知callee pointer保持0，在调用指令前停止；报告里未写入的 `+1e8/+1c0/+50/+1c8/+40` 都仍为0，它们不是“真实完整构造默认值”。

后段实际完成12处模型容器指针stores：`+68/+70/+a0/+88/+1f0/+b8/+1f8/+200/+1d8/+b0/+60/+1e0`。各List均有非null的已初始化零长度backing array。`+b0` 是65349；其 `+20` 为具名 `CostumeSystemLayerDataItem/87323`，实际 `14ff00c60` 创建两份48287 `BeyondCostumeSetDataItem`，各有List实例13270/17072；`+18` 的77936由实际 `15152d870` 创建三个容器。所有这些目标字段stores由原生代码执行，不由fixture事后填入。

## 有限省略/旧值/非零消费

在上述已经实际构造的字段上运行完整小函数；没有拿未构造的中段字段推断业务必须发送什么。

| 输入与函数 | 实际结果 |
| --- | --- |
| tag24空uint32-list→`1518462c0` | 清除model+a0旧HashSet内容/计数；正确保留其对象 |
| tag36空uint32-list→`15184d480` | 已有model+58 HashSet时清旧；初始null且空输入则不创建 |
| tag22空message-list→`15184fa10` | 清model+1e0编码int pair字典旧值 |
| tag10空map→`15184f260` | 保持已存在model+1d0指针；没有泛化为“空集合均清旧” |
| tag21/25/31→`15184e030/15184f680/15184f910` | 受控旧值999分别被0或独立非零42覆盖；无scene条件真实返回，字段业务名仍unknown |
| tag26 child省略→`15184e472…15184e4b5` | 实际null分支跳过直接复制 |
| tag33 child省略→`151850370` | 原生函数正常返回 |
| root既有第三参数null→`151845140` | 原生尾函数正常返回；不扩大到其它调用来源 |

Array.Clear字节操作是明确模型：受控u32 HashSet slots宽12/buckets宽4，编码int pair entries宽40/buckets宽4。原生代码选择清理范围、更新version/count/free-list；测试独立断言实际调用范围和更新后的头字段，不把模型字节清零说成Array.Clear机器码执行。旧HashSet含两个有效项/三个已用slot；旧字典有三个entry。有效wire省略由真实reader产生非null空容器，与人工把容器指针改null分开测试。

## 实际slot18与scene/entity返回

重提18163方法表确认虚槽18是 `15184e020`，真实读取model+218；不能套用base类 `152f80550`。完整 `152f7ec20` 在以下受控条件正常返回：无scene；真实scene lookup查无entity；存在entity但其+80不可用。

缺entity样本通过实际 `1517ab3a0` 查询 `0x80000001`、`0xffffffff`，高位没有被截断。底层字典索引是已审storage模型，scene查询、fallback检查和分支实际执行。entity可用时，本卡停止于 `152f7ec86`，位于尚未绑定的组件上下文读取之前；没有创建假组件或delegate效果。合成scene/entity不代表客户端实机世界、对象归属或真实入场时序。

## 连续handler前缀与原始失败

同一已分段构造对象进入真实 `15184e100`，连续执行身份/生命状态、装备GUID读取及其路由，停在第一个尚未闭合的原生后继：

- 非空合成装备GUID：停 `15184a5f0`；该函数原有f1 sink模型已在专用runtime中移除。
- 空装备tag5和tag37：真实 `15183eae0` 空列表路径返回后，`15184ee42` 尾跳 **15183f290**。即使空装备仍要经过它；不得直接跳到 `15184eee0` 或技能初始化来报告整段成功。

首次连跑因此在15183f290出现“未建模原生调用”，原始 `logs/avatar-initializer-probe2.log` 与 `evidence/avatar-initializer-probe2.failure.json` 保留。收紧停止点后保留该必经依赖，没有添加成功模型。

冷u32 comparer首次试图继续到反射helper前，但真实代码先访问尚未恢复的metadata状态，发生未映射读取。原始 `logs/avatar-initializer-probe4.log` 和 `evidence/avatar-initializer-probe4.rejections.failure.json` 保留。正式边界停在已实际到达的cold分支 `157d976ae`，位于未知metadata访问前。

## 错误与边界

7个独立样本区分：受控allocator返回null后实际进入 `1404c2be0`；冷comparer到 `157d976ae`；缺enum构造rgctx到 `140524010` 运行时解析入口前；null目标HashSet到 `1404c2be0`；runtime null tag22指针真实保留旧count3而正常返回；IFix guard到 `152f7ece3`；可用entity到 `152f7ec86` 组件上下文边界。

allocator、冷cache和损坏context都是受控错误环境，不是客户端正常上限或业务输入规则。关键native函数没有模型成功；所有unknown stop均在被停止指令执行之前。

## 测试、双跑与暂停出口

7组成功fixture包含28个构造精确片段、70个省略/尾部分段、7次flush及7条连续handler前缀，另7个错误/边界。12项新增独立断言与 f4c/f4b/f4a/f3/f2a/f1/e8 关联共82项通过。两次全新进程均重新核固定输入并重提声明，完整trace和脱敏摘要逐字节一致。

| 产物 | SHA-256 | 字节数 |
| --- | --- | --- |
| `local/migration72/evidence/avatar-initializer-native{,2}.json` | `3b314e34d191a01bd18b3394146d4cf082391fe105ca2c2dac44a363ab19af63` | 935,779 |
| `local/migration72/evidence/avatar-initializer-summary{1,2}.json` | `1a0889afe94d0bd815a2ff99604fb4c6d48bbade5f49e0d5a8995071ad92acaf` | 411,250 |

比对记录 `local/migration72/evidence/avatar-initializer-determinism.json`，正式日志 `logs/avatar-initializer-native{1,2}.log`、测试日志 `logs/avatar-initializer-tests.log`。新共享摘要发布已通知主线，使用同一摘要字节。目标原始反汇编/类型/AOT和失败日志只保存在忽略目录的 `avatar-initializer-*`；24函数规划输入仍在 `analysis/avatar-init-plan-*`。

主线已逐项审核四文件及精确片段/managed模型/停止表，独立82项及第三全新进程原生回放均通过，完整/摘要与上表双跑逐字节相同，八source pin保持。证明 `local/migration72/evidence/avatar-initializer-main-review.json`，独立输出 `evidence/avatar-initializer-main-{native,summary}.json`，日志 `logs/avatar-initializer-main-{tests,native}.log`。仅验收明确片段，三类未知间接构造没有补成功模型，完整constructor/handler及入场必要性仍不批准。

```powershell
$initializerGamePath = & powershell.exe -NoProfile -ExecutionPolicy Bypass -File tools/game_path.ps1
python tools/client72_avatar_initializer.py --client $initializerGamePath --output local/migration72/evidence/avatar-initializer-native.json --observations protocol/cn-7.2/avatar-initializer-observations.json
python tools/client72_avatar_initializer.py --client $initializerGamePath --output local/migration72/evidence/avatar-initializer-native2.json --observations local/migration72/evidence/avatar-initializer-summary2.json
python -m unittest tools.test_client72_avatar_initializer tools.test_client72_avatar_skill_depot tools.test_client72_avatar_fight_props tools.test_client72_avatar_state tools.test_client72_avatar_team tools.test_client72_avatar_control tools.test_client72_avatar_contract tools.test_client72_avatar_complete_raw
```

交主线独立第三回放及定向审核；不在工具里把审核状态改为ready。暂停交接保留的解除条件：三个构造间接后继准确绑定、15183f290/15184a5f0装备路径、151847f70与depot+68（区别于f4c的+6c）、61980生命周期、tag29辅助结构和tag38实际资源查询。这些均未在本卡启动完整恢复，g2业务producer和实际Java→native整段闭合尚未开始。主线负责Git、全局交接与统一暂停。
