# E-01/R3-PROUD-ROW-READER：String与两个原U32的行reader合同

## 执行前有界计划

| 项目 | 本轮合同 |
| --- | --- |
| 目标 | 只沿原606B producer的 `14d7ad98b`，确定 owner71907/method595516、原entry `14bb5b0b0` 怎样消费String与两个原U32，并分别记录bytes/start/count、零与错误边界。未证前不把两U32命名offset/size。 |
| 已有 | 原772B查询→606B typed producer，准确Proud path与2132778B原payload，63831 parser/两级ctor静态合同；完整payload不是已证row0，索引值与管理mode尚缺。 |
| 准确缺口 | 此reader完整body/返回关系及参数用途；同key的400190实际索引来源和值；不与独立mode/factory代理重叠，不消费其条件child许可。 |
| 固定来源 | 旧row-source、payload-locator、parser-contract及两份next-input-spec；新取证逐次重解GAME_PATH/五完整SHA，核606B原caller全部bytes、精确E8/signature/AOT及唯一exact/containing pdata。固定旧GitHub来源直接复用，这是已审单链的小续项，不重复全网检索。 |
| 预算 | 新静态原code仅entry `14bb5b0b0`：先保存唯一真实pdata与extent，再取完整body≤8192B、连续decode≤2048条。xdata/子callee/目标CPU/live/新REA/网络均0；不靠邻方法估end。 |
| 范围 | 只新增本页、`tools/client72_d02_proud_row_reader.py`、必要同名test、独立 `local/migration72/d02-resume-20261110b/c-proud-row-reader/`。共享旧材料/Project/Harness/Proto/patch/resources/config/Git只读；不构建、启动数据库或操作进程。B loader已入Daybreak队列，本轮不处理或替代重试。 |
| 方法 | 先解释唯一原body的参数保存/直接传递/分配/返回/零错分支，所有callee只按原边保留未知。可在已有冻结源中定向定位field400190的直接index-loader producer；不扫全方法、不新增body。需要新窗口或闭合AOT `157ffc4c0` 元组18192`<U32,75077,47647,25030>`时，只交精确机械计划。 |
| 产物/验收 | 原code/pdata/AOT/caller全SHA及连续bytes、方法/参数typed身份，两个fresh可复现静态审核与离线门禁测试；已证/推断/未知分列。只在直接证据成立时定义参数含义，不能用whole payload头、host map或Java公式制造行。 |
| 停止/恢复 | 身份漂移、非唯一extent、8192B/2048条超界、decode不完整或平台安全/Daybreak门禁立即留原诊断并停止，不换方法或模型重试。常规未闭语义在本窗内继续，成熟规格或准确最小缺输入即交主线并释放max；新窗口/CPU/现场须主线另批。 |

## 当前状态

**本有界静态子出口已完成：三个本地backend及dispatch分支用途分开，不能把两个U32统一命名offset/size。** 已取得唯一1438B/309条完整reader，两fresh八原件逐byte一致；没有子callee/xdata/CPU/live。第一值在不同分支分别交原callee的有符号64位参数或写42005字段+1c；第二值在一分支决定缓冲容量/原调用参数，在另一缓存分支完全不使用。**写+1c不证明有效row span。** 本卡没有获得同key索引值、有效row bytes或原parser执行，不批准资源ready、group/level别名、Java算式、完整Avatar或D-02。

## 原caller、唯一窗口与固定SHA

原caller不是按邻函数名称猜出的：owner80063 `IBABFGJHFGN` /method663546 `CKPLHDMIKBB`、原 `[14d7ad8e0,14d7adb3e)` 606B完整SHA `c090061f8eaef3bce27411310d52b2811eab410fe73f772dbe76424d22fd570d`。它在 `14d7ad969/96c` 取原8B索引out的两个DWORD，在 `14d7ad983..990` 以13B `4889f989da4189f0e820d73afe` 将准确Proud String、A、B交新entry；原E8相对解码独立相符，正常返回地址14d7ad990。

新entry唯一metadata声明为owner71907 `DAJMBJEOHCP` /method595516 `AALPIPFEMPK`，flags96、三声明参数466026..466028，类型分别String/int32/int32。上游75077的field372773/+10、372774/+14仍声明U32，原call不转换其位值；本reader对这些位值的有符号使用必须保留，不能按原U32声明抹掉。

| 原件 | 实际身份 | 完整SHA-256 |
| --- | --- | --- |
| code（reader1/reader2） | VA `[14bb5b0b0,14bb5b64e)`，1438B/309条 | `45f7d9818dbdef1bc86c8438926a359ed63a7cddead83d9a4c8d7954a6d7ae21` |
| pdata | RVA1a93ebf4，12B=`b0b0b50b4eb6b50b1cfe2404`，unwind424fe1c；未取xdata | `6d32d24174457e5e4b7a1bfdb0330dc377017bd801ac9a9f0e4961390423e5a2` |
| AOT cell | VA1422f9950，8B=`d0548a4201000000`，P=1428a54d0 | `68f5f1908ac9f789c3fc0b5e71c5b8af00962605561f7b8ea2cc4718f00a83da` |
| AOT slot | P+595516*8=142d306b0，8B=`b0b0b54b01000000` | `ac909ee30306875ef49583ce04ce6bd9890919461238f69401aed547de4ea139` |
| asm JSON | 各39213B，309条连续原hex完整拼回code | `21579200c4d958f990eeba4aaf2e6c313ac6596ff66adde962c0c56e17de711f` |
| extraction报告 | 各17582B | `1b491512e5a10473619c4c4789cf2e31ba70591209948fecdb76682e08abb514` |

原件根 `local/migration72/d02-resume-20261110b/c-proud-row-reader/`。两次新Python进程分别重新通过 `tools/game_path.ps1` 解析当前GAME_PATH，重核profile五文件大小/完整SHA、原606B caller、13B setup/E8、metadata/signature和两AOT原QWORD；唯一exact与containing记录先保存pdata/pre-body，再检查8192B上界并取code，连续decode低于2048条。没有使用邻method595517/14bb5b650定end，也没有把相邻padding算进body。

五输入仍为EXE `8b014f64b5dc12fbc476e0c362b053abfdd7a1378cd282e4ad81057621dbd6b5`、global metadata `45dc7a44dc9fc187146c52f6c33de57e2d1c1dcfec57f8736431553460f6ef2a`、startup metadata `b314810840ca6f495822c9c44483dde2394b9f09ed3c6b13c2782092f04d5105`、SDK `adecdf0bd5442e321b1b66b0ca9e98bef76d5067326aa4add5b75266055a6c25`、stock Astrolabe `87e0a005ac46515156636af2120d3f824bcd28d69253e7d7235b959b54a0b442`，实际大小与SHA均在两份报告。不沿用历史heap/PID/运行身份；这里只读磁盘，不是目标执行。

## 三条本地分支与dispatch：bytes/start/count分开

入口 `b0ba/b0bd/b0c0` 保存B到EDI、A到R14D、String到RBX。以下flag只按本窗实际RIP地址命名，没有读取自然值或将其业务语义猜成mode。类初始化冷路径仍保留，不据flag分支静态存在认定本轮实际选中。

| 原条件与site | 原始数据关系 | 已证范围与未知 |
| --- | --- | --- |
| byte145847d8c非0，b374..3a5 | 从原dispatch对象取目标，保留String/A/B，恢复栈后尾跳33459.method264414 `__Gen_Wrap_18` /14d88bb10 | 此分支没有本地bytes结果合同；不能忽略dispatch或补false |
| 上flag为0、byte14586d05e为0，b278..2ce | 从global+5d60原pool虚槽取得对象；写其+10=null、+1c=0；调用60257.method500130/14d6f9090，四原参数=`reader,String,A,B`，随后返回同reader | typed参数明确42005；该helper体未取，返回byte[]/native数据、两数的实际读取方式及清理未知 |
| 上flag为0、14586d05e非0，且byte145876e41非0，b108..273 | String查询global+14910，再操作global+14920共享reader；A经MOVSXD交141123190第五参数，B参与缓冲容量和原调用第四参 | 原+14910声明为Dictionary<String,U64>，不是FileStream。两个未审helper只按原地址/参数记录，不命名其完整I/O行为 |
| 上flag为0、14586d05e非0，且145876e41为0，b3ca..4c7 | String查global+14918的42005缓存；hit返回同对象，miss调用500132/14d6e1570(String,0,0)后经原generic缓存；非null写reader+1c=A | B不参与本地内容或边界；+1c写入不证明A有效、row长度或实际span。不得自动替换成截取payload[A:A+B] |

静态类型与实际指令共同限定：field358013/raw4014910为Dictionary<String,U64>，358014/raw4014918为Dictionary<String,class42005>，358015/raw4014920为class42005。运行+14910/+14918/+14920来自原load，不是屏蔽rawOffset高位猜出。42005字段207760/+10是byte[]，207761/+18与207762/+1c为int32，207763/+20为U64，207764/+28为int32。这里只按原存储类型表达，+20不因U64声明自动获得有效指针所有权。

### U64缓存/共享reader分支的准确参数账

- `b16e..17a`：只有原B低32位为0，才以同cache U64调用141123260，并保存其RAX到RDI；后续只使用EDI。该helper体/返回语义未取，不能将它叫“剩余row长度”，也没有A参与这个替换。
- `b1b5..215`：EDI与共享reader.byte[]的array+18作**有符号**比较；不足时按原32位倍增循环选择容量，通过140514ee0分配并写reader+10。这个步骤只证明缓冲容量，不证明有效数据数或所需row已读。
- `b244..260`：写共享reader+1c=0，取+10数组；A经 `movsxd rax,r14d` 保存到第五槽，依次传 `141123190(cacheU64, byteArray, 0, B_effective, sign_extend(A))`。这是原call实参关系，不是本轮执行过复制。原返回值没有检查，随后取global+14920返回；本body不写reader+18/+20/+28。
- 因此已有大缓冲的array长度可能与本次B不同，原读取实际返回/短读、+20/+28前值及有效字节边界均未由本窗证明。不能给parser人工补“长度=B”或把capacity当有效row count。

### reader缓存分支的准确起点账

命中后 `b42f/b43d` 取同42005并写其field207762/+1c=A；未命中在 `b44f..457` 把原String和**两个0**交500132，成功缓存后也只写+1c=A。它复用已有[MiHoYoBinData loader来源](CLIENT_72_RESOURCE_LOCATOR.md)，本轮没有新取/执行这个callee。原B在此路线不消费。

既有ResourceRuntime/NaturalRuntime将42005+1c作为primitive reader cursor模型，本窗只新证明这一原字段写入；实际row bytes与可读边界还依赖所选backend、loader产物、同key实际A和原parser消费。**不能仅凭写+1c称本Proud payload的有效row span已经成立。** 完整2132778B payload的身份与此写入是不同出口，不将整表offset0当row0。

## 零、高位与错误边界

- A=0没有独立“零键/空行”特判：本地分支按原位值传递或写0。B=0只在U64分支触发上述helper替换；reader缓存分支无论B为0或非0都不用B；pool分支把原B交未审helper。不据此批准任何一般空值语义。
- 上游A/B声明U32，本reader签名int32。A的80000000/ffffffff在MOVSXD处分别得到 `ffffffff80000000` /`ffffffffffffffff`；这只是原指令的符号扩展，没有把高位值当有效正offset。B的容量比较是signed，不存在本窗通用 `A+B<=payloadLength` 检查。
- 原dictionary下标负值进入miss；array entry下标越界经jae到1404c2650，所需对象/缓存/返回对象为null的原分支到1404c2be0。没有执行这些helper、不命名具体异常、不把它们换成返回0或成功。
- U64分支的LoadBinDataZipFile miss可返回0并通过共享正常出口返null；loader参数准确为原泛型hash结果、offset0、length0、compressionType0、pageSize100000h，不能由默认参数名或loader成功候选推定实际bytes。其它backend的null/错误合同分开保留。

## 索引producer的有界停止点与下一窗口

只在三个已冻结窗口中定向查field400190使用：原3179B map-producer完整asm、772B查询与606B typed producer。606B的 `14d7ad926` 与冷路径 `14d7adab0` 是global+21f58 load；这些范围没有该field直接store。没有扫描其他方法code、追cache helper或取新body。

field400190 `BKOILGHDKDP` 属class80063，attributes31、raw4021f58、18192实例5071 `<U32,75077,47647,25030>`。同class唯一`.cctor`是method663526/14d7adbc0、零声明参数；它是按本静态字段声明定位的**下一唯一候选**，未证明实际写400190，不伪填一条原E8 caller。建议root若继续，只批此.cctor的AOT/pdata单窗≤2048B/512条，先回答是否真实分配/写该field及唯一index loader/path边；没有direct store就如实返回，不树状扩展。

157ffc4c0的closed-AOT当前仅由field类型约束；未复用另一18192 tuple的旧指针作为本tuple证据。私有 `next-input-spec.zh.md` 另列纯数据机械计划：固定旧28B header/两generic table范围，只筛owner18192/classInstance5071，建议最多32QWORD/256B，复核所有共享row并分列method/class inst；只证明身份，不执行查找或制造map成功。新.cctor/数据包均未执行或获本卡附带许可。

下一真实样本仍须同key索引A/B及backend的有效bytes/范围，或独立原whole reader给出的确切row source span，再与另一mode/factory包汇合。原141123190/141123260/500130未知不在本卡扩窗。group/level别名、81127+10门槛、Java公式、实际121角色恢复仍各自未闭。

## 实际验证、共享更新与交付

私有collector SHA `f39394d36b693b1f95f9fbc2686d7f46c9683262a4dd8128eeccaf2f106e128a`；两新进程各一次同entry提取退出0，八原件逐byte一致。新public CLI只读冻结材料，不再打开当前PE或运行旧locator/parser：

```powershell
python -B -m unittest tools.test_client72_d02_proud_row_reader -v
python -B tools/client72_d02_proud_row_reader.py --output <新私有basename>.json
```

实际6项测试通过，覆盖真实分支/高位/未知边界、首source漂移在metadata前拒绝、原code漂移、邻方法假extent、重复containing pdata与错误AOT。两fresh最终CLI各85项静态断言退出0，报告各31839B、逐byte相同，SHA `90c3920795c14fcb1ef2058eddd8e3692ca189e59703cd08b372fcd675f2aeee`。它们核typed声明、原309条bytes、原caller/AOT/pdata、分支参数账和完整payload身份；不是parser CPU、真实读取或row样本验收。源码16089B/SHA `f16e2ceef910be6ac2ad3e9a7090796618c1932ebb30f5702b6f974083a3fd9a`；测试3205B/SHA `4088f5f142e4a6f78e2916e95de22226448039dad35de1812dee4d38f0509b1c`。

期间root通知三前卡已通过17项/第三并提交adcf68e，仅在public专题追加接受段。本卡已补读新增段；直接门禁不含这三可维护doc，原private/code pin未漂移，没有更改或放宽旧pin。新doc上下文SHA留私有清单。一次Windows wildcard和一处旧asm文件名查看失败已纠正，未影响原取证或补成功；无平台安全/Daybreak门禁，B队列保持未碰。

本卡只新增本页、public工具、同名测试及指定私有根；没有Git提交/分支、共享Project/Harness、Proto/patch/resources/config、客户端/进程/live/JVM/DB/服务/构建操作。仅静态研究无需重启/部署，客户端验收增量0。主线独立第三与提交/共享状态由主线负责；本max交回全部写权，不自行启动下一卡。

主线第三CLI31839B/85断言与两fresh逐byte同SHA `90c3920795c14fcb1ef2058eddd8e3692ca189e59703cd08b372fcd675f2aeee`，6项测试通过，review SHA `0af98c43420bfd60f526c146859cf561a5dc0e8a8fbf52ed7e9a97c64a29dbc1`。只验此原窗静态合同，实际row span/backend/index值、parser及父项未晋级；max槽位转Scene独立后继，不自动执行下一.cctor或closed-AOT计划。
