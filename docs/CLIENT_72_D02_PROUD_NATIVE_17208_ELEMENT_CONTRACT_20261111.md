# E-01 / R3-PROUD-NATIVE-17208-ELEMENT-CONTRACT（2026-11-11）

## 结论与可实施出口

**已闭合固定key22101的17208元素及nested正常返回文件合同：四个元素各2B，条件cursor 322→324→326→328→330；derived随后必读4B到334。** 从A=4累计消费330B，与候选`[4,334)`在完成推进后独立吻合。物理reader end仍是完整payload2132778，候选未用于限长或倒推。足以实现一个key的完整条件文件reference，不再请求影响这条wire/cursor路径的新source。

成熟私有规格`terra-single-key-reference-spec.zh.md`13078B / SHA-256 `f98b1da78d11c921d4fa7def19a8748b7199ba7d754cebfd77275ca18b5294cc`包含前缀复用、derived选定22项presence/default、17208两成员准确输入/条件copy、四轮返回、尾4B及独立host测试要求，交root拆有界实现和独立测试。本项不写生产或现有工具，不扩helper树、其它derived路线或全6371行。

`completeConditionalSingleKeyFileSyntax=true`只覆盖固定样本的普通文件语法；`completeProudReference=false`继续表示完整原生/业务reference未批。自然backend/mode、实际cursor/index/row、typed对象、encoded值、业务alias、完整Avatar、资源ready和D-02均未晋级；新增客户端验收0。

## 准确元素语法与存储边界

唯一既存body `[147e917c0,147e918fd)`317B/86条，SHA-256 `42112e160290bdf777efc9f7d05f8ae3afe9ed3746c4f9356a6ca614950b96e7`。实例17208/m130222仅一个声明reader参数class42005；RCX为隐式unboxed value receiver，RDX为reader，return type仍unknown。metadata/signature完整hash后解析；字段81537/+10与81538/+14都声明58209，对应机器receiver+0/+4。

| 顺序 / 原PC | 精确读取或默认 | 条件推进与copy |
| --- | --- | --- |
| header 1807/1812 | LE16零扩展到EBP，再U32 xor3f87；181f/187c分别TEST BPL的4/80 | 180c/180f先提交cursor+2；不是52802的加法header。 |
| 首成员81538 / receiver+4 | mask4；缺省EDX在181a为0，不读wire；存在185b读LE32，加2061053c mod2^32，无xor | 存在185f提交+4。186f无论presence都调58209 setter，1878条件复制call后encoded DWORD到receiver+4。 |
| 次成员81537 / receiver+0 | mask80；缺省EBX在1818为0；存在18b0读LE32，先加6c483fd7 mod2^32，再18b7 xor4bfd37e4 | 存在18b4先提交+4。18cc无论presence都调同setter，18d5条件复制call后encoded DWORD到receiver+0。 |

表内PC前缀`147e9`。实际处理顺序是81538再81537。setter唯一声明58209/m483726、实例、一个uint32参数，无reader；准确输入U32、局部call前清0和call后实际encoded分别记录，actual encoded保持unknown。元素wire仅`2+(mask4?4:0)+(mask80?4:0)`，可能2/6/10B，无64位wire读取；QWORD receiver/数组存储宽度不是wire宽度。

三个相关reader+20检查的非inline边分别17e8/1834/1891以同reader、EDX2/4/4调14f614760；其行为未知，普通文件条件分别跳过。原三处unsigned start检查不含统一全宽检查；错误入口18e3→1404c2650，reader/array null入口18e8/18ed/18f2/18f7→1404c2be0，均未执行。receiver无null guard。host全宽不可读、原start失败与候选越界独立，不合并成EOF；此前已推进cursor和条件copy事实保留。

## 同key完整条件消费

沿上游分别声明derived+40-null、base+20-null、每个相关reader+20-inline、有效receiver/reader/array及途经原allocator/object/storage/cleanup调用正常返回。文件抽象不为无reader参数的opaque存储call另加消费；allocator0不证明自然分配或typed对象。

前缀既有合同到142；完整7674B derived选定路径到318，均在本host审计中按固定payload复算必要span与值。[derived合同](CLIENT_72_D02_PROUD_DERIVED_FILE_CONTRACT_20261111.md)的rawH=`1b8afee2`、P=`4ce97859`及22项presence保持，43个pre-nested token逐项与已验第三报告核对。

| 阶段 | 原bytes / 算术 | 条件cursor |
| --- | --- | --- |
| 17208数组header | [318,322)`002c0708`；LE32先加2bedd918 mod2^32，再xor33f5051c，count4 | 318→322 |
| 四个元素 | [322,324)、[324,326)、[326,328)、[328,330)各`873f`，xor3f87皆0；两成员均缺省输入0，实际encoded未知 | 322→324→326→328→330 |
| 数组正常返回 | 四轮分别返回147e91de4后交原15846f530存储；signed比较true/true/true/false，e05→e27 RET，无额外inline消费 | nested返回330 |
| derived末标量 | [330,334)`cefc0c44`；`440cfcce+bbf30334=100000002`，U32输入2；db34 setter / db40复制encoded未知 | 330→334 |
| 末数组/清理 | rawH&20000000未置位，原Empty来源后正常清理/dbc7 RET | 保持334 |

最终81个物理span从4连续到334；共330B原span SHA-256 `cfe85822a6606b824d731b5bb7ba442c8d0d4113187294de5457e3a1e04c8a6b`。尾输入2的33位carry保留，不以候选终点、旧hash或8B存储反推四元素消费。

## 固定证据、复核与交付

仅用两fresh既存八原件、完整payload、上游数组/derived原码和已存合同/清单，以及完整hash后解析的metadata117201003B SHA `df52eb70fe8e70dfecd1059e898d15e288cfa98bcefc78bcaabf753ae150349f`、signature78649549B SHA `28377e7a8d3c891cd61be97ddb74f8502a22f2f2f039d6a91dba3b092d7822fe`。root已验机械15137B SHA `31c96d915eeee7cf87e9e658e8aca49603c17510cfb50d1ec292912b406ff775`与独立第三1719B SHA `e5c8c08132ccbef1eb083039d93ad004e44610e3aa30da44a46b202cdff560d1`原身份保持，未冒称重跑其5门禁。

本纯文件脚本重解码并逐byte拼回元素86、数组90及derived1778条；元素10call/12branch/28显式memory/0RIP及全部栈指令与既存机械清单吻合。保留41个元素和53个跨窗准确锚点，LEA明确为地址计算。73源/208638713B及11份说明上下文冻结；清单19491B / SHA-256 `45e38484b794d62727d169e2f96460b61c0ec0742e07bb7b66eef1d6132f2152`，脚本40437B / SHA-256 `4816481dcfb7f890060c2f91c9a91b0dc6d98dbf5f767ff8424507e7685d0d47`。共享文档后续变化不放宽pin。

独立两个新host Python进程实际退出0，报告各228055B，逐byte相同，SHA-256 `3cb8f15c510dc01b317dbff3b0963ba229cccb56a4a7939fea51c64e55858e0c`。4个presence组合、7个物理/候选/partial见证、6个cursor数值边界、7个U32算术及尾carry见证均通过，属于host断言而非原CPU或新单元测试套数。

```powershell
python -X utf8 -B local/migration72/d02-resume-20261110b/c-proud-native-17208-element-contract/verify-contract.private.py audit fresh1.private.json
python -X utf8 -B local/migration72/d02-resume-20261110b/c-proud-native-17208-element-contract/verify-contract.private.py audit fresh2.private.json
# root独立第三：必须使用该目录此前不存在的新basename
python -X utf8 -B local/migration72/d02-resume-20261110b/c-proud-native-17208-element-contract/verify-contract.private.py audit main-third.private.json
```

报告排他创建；已存在的fresh不可覆盖，复现需另选新basename。中文计划先于审计，准备时一次PowerShell清单管道语法错误及过量只读显示已记read-notes；最终审计无失败，无安全/Daybreak拒绝。既有固定GitHub成果直接复用，无新检索或招募。

无新PE/GAME_PATH/code/AOT/pdata/xdata/literal/helper采集、目标CPU/native/parser/emulator/live/REA、进程/网络/Git/构建/DB或部署。仅本文和独有私有目录写入，无需重启；root负责第三、Project/Harness、本地提交和交接。本max交回冻结规格及全部写权，下一项是single-key条件文件工具与独立人工测试，浮点/58209 encoded未知不阻断此出口。

## 主线验收增量

root第三实际通过，228055B / SHA `3cb8f15c510dc01b317dbff3b0963ba229cccb56a4a7939fea51c64e55858e0c`与两fresh一致。只验条件单key语法；完整文件reference已交独立Terra实现，不增加自然/业务状态。
