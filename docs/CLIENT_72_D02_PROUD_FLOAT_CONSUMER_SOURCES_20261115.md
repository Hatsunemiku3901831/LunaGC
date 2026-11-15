# D02-R3：Proud Float 正常输出与三字段消费来源

任务 `R3-PROUD-FLOAT-CONSUMER-SOURCES`，父项 `E-01d1 / D02-R3`。本页记录 Sol max 的语义交付与 Terra 的独立离线机械字节／数学断言。既有 31A 三字段布局和 31B getter 来源直接复用，不重取原窗。

已将 166B 原窗的正常输出、必要输入和长度门明确为可检验关系。仍没有 Proud 三字段到目标客户端业务消费者的准确桥；实际 M、encoded 值、同 epoch seed/table、三业务 alias、完整 row 和当前采用均未知。此结果不晋级 `resourceReady`、D02-R3 或 D-02。

## 1. 固定来源审核

起点 HEAD `e09271e0bb954b21ff32e6d1963e10de57fc26eb`。本包只读已保存材料；没有打开原 PE、目标进程或 REA 分析会话，没有重新采集任何 getter、caller、global、seed 或 table。下表的来源根 `S` 指 `local/migration72/d02-resume-20261111c/`，`F` 指 `S/proud-managed-row-remaining-triage/float-getter-window/`。

| 来源层 | 固定材料与 SHA-256 | 可支持的结论／边界 |
| --- | --- | --- |
| 31B 验收回执 | `S/integration/acceptance31b/completion1.private.json`，3387B，`62ab88a57a0d31fc0c82afd938a32d3fe74f067947746d3626e34aa3c5e5884e` | 原 source-only 验收；不是 Float 语义或实际 row 验收 |
| 原窗 | `F/main-third/body.bin`，166B，`852140f52b31030047d743fe9fda0a8d2b59a107f8ad154b6e9b6d8b5208bf64` | `[14f905140,14f9051e6)`／45 条物理指令 |
| 完整来源报告 | `F/main-third/result.private.json`，9901B，`55659bd305fa894ab0a9e8e5dbdcd539411774c98460f6e7da6eb237ad3b5e24` | 普通 54724.m454810、AOT／唯一 pdata、两个 RIP 和边界 |
| 31A 布局／条件输入 | `S/proud-managed-row-remaining-triage/triage.private.json`，24816B，`b246d17f58eafd95a7b1883a352033fe70ea3081d306c5926f67b35721b2ab41` | 中性字段身份、条件文件输入、host 消费和冻结边；actual 均为 null |
| 元数据投影 | `local/migration72/analysis/current-structures.json`，302812743B，`d5d7f671a622439b2f9a3ed0018ce551aa5c573cd9ca68bed3b006712b8a98aa` | 声明类型、字段 offset 和普通方法登记；不是字段使用索引 |
| 条件文件前缀 | `local/migration72/d02-resume-20261110b/c-proud-base-prefix-reference/main-third-v2.private.json`，27306B，`e01108b35842e1c2412e4343dd506b14cbf1715c3de285185154193c298267f6` | 25 个 setter 输入位样及 22B 条件字符串块；不是实际 managed 值 |
| 单 key 条件报告 | `local/migration72/d02-resume-20261110b/c-proud-single-key-reference/main-third-v3.private.json`，82835B，`30b99d804468f9572a54f60b1e3dfc0838d38e55c52e8ae11e285f7fcd1d9a2c` | 两个属性元素的原默认分支和条件输入 |
| host Java／catalog | 31A 的 Java 六源及 `resources/BinOutput/Talent/AvatarTalents/ConfigTalent_Ayaka.json` 均复核匹配；完整 pin 在本包私有清单 | 本 checkout 的 Java 要求和精确候选 key，不证明目标客户端 alias 或配置同源 |

31A 与 31B 报告合并后 28 个固定 pin 的大小／SHA 全部匹配。`fresh1/fresh2/main-third` 的 8 类共 24 个已存原件逐类相同；本次只核这些原件，不执行取窗工具。原 31B 独立产物审计 1961B、SHA `909f60d5ec879ff6281185249fa59845fe7830a8c7237838aedc79e64b041c02` 只读复用。

全量来源清单、当前观察及 Terra 规格位于 `local/migration72/d02-resume-20261115/proud-float-sources/`。五 stock 的旧采集 epoch 哈希保留于原报告；本包没有重新核当前安装，不能据此宣称当前客户端与旧 epoch 相同。

## 2. 166B 原窗的正常路径关系

以下是对固定原字节的静态推导，假定入口所访问的普通内存有效；不是实际执行结果。记：

- `B = QWORD[14586b600]`，`A = QWORD[B+e970h]`。原窗先读 B，再读 A；只对 A 作零检查，没有在此处验证 B。
- `S = DWORD[145872d30]`，`E = DWORD[entry RCX]`，`Y = U32(E xor S)`。
- `L = U32[A+18h]`，`T[i] = BYTE[A+20h+i]`。L 是此窗实际加载的 DWORD，不据此恢复其他数组头字段。

`byte0` 为最低有效字节。四个索引分别为：

```text
i3 = 0x400 | byte3(Y)     i2 = 0x500 | byte2(Y)
i1 = 0x600 | byte1(Y)     i0 = 0x700 | byte0(Y)
```

`14f905174/187/196/1a5` 按上述顺序作 unsigned 比较。A 非零且四次 `index < L` 后，`14f9051aa..1cf` 的正常输出为：

```text
U = U32((T[i3] << 24) | (T[i2] << 16) | (T[i1] << 8) | T[i0])
XMM0.low32 = U          // 14f9051d1 MOVD；14f9051da 正常 RET
```

这段代码没有浮点算术、有限值筛选、零值归一化或符号转换。若按 Float32 消费，U 直接作为 IEEE754 位样；`+0/-0`、subnormal、Inf、NaN 的位样不会在此正常路径被改写。metadata 的 formal logical return 仍为 `not-recovered`，不能把物理 `MOVD` 单独升级为返回类型恢复。

四个区间严格递增：`i3∈[400h,4ffh]`、`i2∈[500h,5ffh]`、`i1∈[600h,6ffh]`、`i0∈[700h,7ffh]`。因此，对固定 Y，四个长度门的合取等价于 `L > 700h + byte0(Y)`；支持任意 Y 的长度门充要条件为 `L >= 800h`。该化简不证明真实 A、L、表内容或物理存储有效，也不要求为某个固定 Y 补齐整张表。

只有四个被索引的表字节参与一个固定 E 的输出；若 E 未知，就连这四个索引也未知。即使将来给出 E，也还需要同一有效来源／epoch 的 S 和所选表字节。31A 的 `3e99999a/40c00000/+0` 是 setter 输入，不能当 E，也不能直接代替 getter 输出。此窗仅证明四分区查表关系；既有材料中的 “inverse quarter” 叫法不额外证明表是排列、与 setter 互逆或当前已初始化。

## 3. 非正常出口的准确边界

| 条件／site | 原控制目标 | 本包可说到的位置 |
| --- | --- | --- |
| A=0，`14f905156` | `14f9051e0`，CALL `1404c2be0` | seed 和 entry DWORD 加载尚未发生；callee 合同未知 |
| 任意 `index>=L`，四个 JAE | `14f9051db`，CALL `1404c2650` | 四个表字节加载均在长度检查之后；callee 合同未知 |
| 正常分支 | `14f9051da` RET | 仅此分支有本页已推导的返回位样 |

没有读取两个错误 callee、xdata 或下游。不能给它们命名为具体异常，也不能假定 CALL 不返回；失败出口之后的行为不属于正常路径公式。无需通过第二 allocator、Default 或热消费来解释上述静态控制关系。

## 4. 三字段：布局、条件输入、候选业务名与实际值分开

共同条件仍是独立合法的既存标准 cache P，且 `M=QWORD[P+40h]` 非零并确属 41105（继承 26072）。本包没有取得这样的 M。

| 中性字段／布局 | 已有条件输入与候选关系 | 准确客户端消费首缺 | 实际状态 |
| --- | --- | --- | --- |
| 26072.field127896，M+10h，54724[]；unboxed 元素4B，field271929/+0 | native295066 的25个 setter 输入：0.3f、6f、23个+0；`paramList` 候选 | 需要把此数组的元素地址追到 m454810 的原调用，再追到参数解释／业务消费；本 getter 自身没有 Proud 字段读取 | 实际 pointer、长度、E、S、表字节、输出均未知 |
| 26072.field127897，M+18h，52802[]；元素8B：enum32419/+0、54724/+4 | native295065 条件count2，两 enum 为 direct DWORD0，两 float setter 输入为+0；`addProps` 候选 | 需要证明此数组元素的 enum 与 +4 值成对流入具名属性安装／累加；仅 `FIGHT_PROP_*` enum 名和结构形状不够 | 实际两元素及所有值未知；不能预填空数组 |
| 26072.field127898，M+20h，System.String；length在S+10h，UTF16在S+14h | 22B条件块ASCII视图 `Ayaka_PermanentSkill_1`；`OpenConfig` 候选 | 需要证明此 String 字段进入目标配置查找／应用，并核目标配置同源身份；原块未构造成 managed String | 实际 pointer/null、length、UTF16/text 均未知；未读不等于空串 |

本次有界检索覆盖 `docs/`、`tools/migration72/` 对上述三个字段编号／混淆名及 getter 编号／入口的文字引用，另核固定 metadata 的 26072、41105、52802、54724 声明与既有 31A/31B 材料。没有找到上述三条 Proud 准确消费桥；这不是全项目不存在消费者的证明。26072 仅有 `.ctor` 与 parser-like `CNLIBMNOALB` 两方法，41105 同样仅有 `.ctor` 与重写方法；不能从 metadata 编造三个属性 getter。

**“31B 没有原 caller”只限其来源包，不能扩大成整个项目没有 getter 原 caller。** 后续已验 whole carrier 窗增加五个局部原E8 caller及输出用途，见[后继语义](CLIENT_72_D02_PROUD_WHOLE_CARRIER_CONSUMER_SEMANTICS_20261115.md)；其数组未绑定26072/59500实际数组，也不代表全项目caller覆盖。 已有[其他资源消费专题](CLIENT_72_D02_AVATAR_CRITICAL_CONSUMER.md)记录 `1505f5cda/5cf4/5d11` 三次同 getter 调用，来自63287的 `+98/+78/+a4`，再进入 typed 三 float installer；它们的接收对象要求17494，不能连接到 Proud 的26072三字段。本包只复用该已验专题，未重验其六窗／8768B原件，不借旁证批准 Proud alias。该专题当前14447B、SHA `e13383d34c971cf7d86bab4a36ae9a503df6a82ba370aae0519dcff9292a280c`。

## 5. 可独立复用的 host 消费关系

当前 Java `ProudSkillData.onLoad` 直接使用 addProps 和 paramList 的 length；addProps 在 `propType!=null && value!=0f` 时保留，paramList 的所有槽位以从“1”起的索引写入 paramListMap。`Avatar` 将已加载 Proud 属性加入角色属性并把 openConfig 交给 ability embryo 处理；`DynamicFloat` 读取 paramListMap；`AbilityTalentResolver.addProudSkill` 使用 openConfig 与 paramList。上述均为本 checkout 的源代码关系，不证明目标字段 alias 或客户端效果。

当前 catalog 的候选 key 精确含一个 UnlockTalentParam 和两个 ModifyAbility，后两者分别引用 `%1/%2`；条件0.3f/6f有明确 host 对照位置。不能裁掉后23个参数槽，不能将条件两元素改写成“源数组为空”，也不能把配置 key 命中当目标配置同源或当前采用。

## 6. Terra 最小成熟包与解锁范围

已执行后继 `R3-PROUD-FLOAT-NORMAL-PATH-ASSERTIONS`（规格任务名 `R3-PROUD-FLOAT-NORMAL-PATH-MATH-AUDIT`），完整固定输入、合成向量和停止条件见私有 `terra-normal-path-spec.private.json`。只检验已保存166B正常路径和合成输入；不接受目标路径／进程／实际 E、seed、table，不实现完整 codec，不重新执行来源工具。

| 项目 | 移除 A/B 动作及产物后是否独立 | 可以解锁 | 不能解锁 |
| --- | --- | --- | --- |
| 固定166B重组、索引／移位／MOVD锚点审核 | 是；仅使用已验原件 | 本页静态推导的字节绑定可复核 | caller、metadata formal return、任何实际输入 |
| 非零与特殊位样、四分区顺序、unsigned长度边界的合成断言 | 是；只使用固定合成值 | 本页正常输出和门条件的数学合同 | 同 epoch 材料、实际Float、row、三alias、资源ready |
| 三字段业务桥 | 本包当时没有成熟取源规格；后续虽定位五个局部getter caller，三字段到该数组的准确桥仍未证 | 仅保留本页精确缺口 | 本条件断言包不自动包含追加采样／原窗或parser构造；已有独立普通来源按各自已审规格另验 |
| 既存M／String／数组实际值 | 当前缺独立允许M及已审范围／规格；不执行 | 取得独立允许M且新范围／规格经主线审阅后另验；现场另需fresh与实时ready | 旧52RPM/298B不自动覆盖String/Float；原ready不复用 |
| host 消费与catalog关系 | 是；源码及现有精确项独立存在 | 候选投影必须保留的形状和槽位 | 目标客户端field→业务名、配置同源、effect/current |

三字段的准确客户端消费来源缺口尚未解除，不能把数学断言通过当整包 ready。Proud 预览下一突破分支继续冻结；本包不使用 `threshold==phase+1` 推断当前采用。

## 7. 拒绝边与交付状态

A `A-SECOND-RETURN-CONTRACT` 的第二 allocator CALL/RET、热 Default 消费／同 epoch 归属，以及 B `D02-R2/SERVICE-TRIGGER-LOADER` 的首 Cargo 错误修复继续冻结。A 完整原拒绝提示在已有队列中明确缺失，本包不伪造；B 已保留原句 `This content was flagged for possible cybersecurity risk.`。本包没有运行它们的草稿、分配返回分析或后继。31A 列出的五处 allocator 边也不追踪，不把它们归给某个业务列。

本包没有新安全拒绝。实际完成固定来源 hash 审核、166B 正常路径语义推导、三字段来源缺口划分及下述 Terra 离线断言；未执行生产测试／构建／部署／客户端验收。专题与获准私有目录以外的共享索引、提交和集成由主线负责。


## 8. 166B 正常路径机械断言（R3-PROUD-FLOAT-NORMAL-PATH-ASSERTIONS）

基于已固定的 166B 原窗、45 行已存反汇编、原报告、来源审核和 Sol 规格，离线脚本 **419 项通过、0 项失败**。固定规格 9897B／SHA-256 `8716424bfabbdaff438f1daa137f5ccdfded04f0d813ff6a350d89895c0ba8cf`；断言前本页原稿 12470B／SHA-256 `e3dade2bce473812f359880e76133833aa15ee5d0621cb9eb25ee040b3b8f3f2`，其后只更新本页交付状态与本节。

- 45 条记录逐条检查地址及尺寸、完整重组至 166B；核 38 个字节锚点和 7 个控制目标。输入原窗 SHA-256 `852140f52b31030047d743fe9fda0a8d2b59a107f8ad154b6e9b6d8b5208bf64`。
- 四个固定合成向量逐项比对 Y、索引、正常出口、四个选中字节及输出；四分区各枚举 256 个字节证明区间有序不重叠。对最低字节的 256 种值核等界失败和下一长度成功，并由最高索引所在区间证明固定 Y 门；`L>=0x800` 的全 Y 长度门按最大索引 `0x7ff` 证明，未穷举 2^32 个 Y。
- 合成 null 门在读取 seed、entry 或表字节前退出；四个长度等界分别到达所列首个 JAE，最后长度 `0x702` 与无符号 `0xffffffff` 走正常路径。错误 callee 没有执行，其合同未知。
- 9 个特殊位样包括正负零、subnormal、正负无穷和带 payload 的 NaN，均按精确字节比较；没有执行浮点算术或断言实际 getter 返回值。五个固定来源各做同长度改字节及截短的内存模拟漂移拒绝；脚本在读取固定来源时先核长度与 SHA-256，漂移即停止。

私有复现入口（从 checkout 根运行）：

```powershell
python local/migration72/d02-resume-20261115/proud-float-assertions/audit.py
```

脚本 SHA-256 `6f7681b1a1c38caea8107ad69ea6d59244a5e232c76a2012c600d9e0477afa8b`；机器可读结果 `local/migration72/d02-resume-20261115/proud-float-assertions/audit.private.json`，SHA-256 `b869eac5cd1eef7b5d653cc98266b769cda73fd24e3602e2394af5a2da4fd5e8`，含逐项名称、状态和全部固定来源 hash。此实验仅验证已存源窗对应的条件投影及合成数据，不读取或合成目标实际 E、seed、table，也不打开原 PE、客户端或进程。移除 A/B 被拒动作与产物后，输入与断言仍独立成立。

本次断言不解除实际 M、同 epoch 输入、Proud字段到caller的桥、三个业务 alias、实际 row、当前采用或资源 ready；formal logical return、真实表长度与互逆关系也仍未知。D02-R3、D-02 和客户端验收状态不因此改变。
