# D02-R1 Ornament parser 与候选同名字段合同

任务 `F-03d3f4d2-ornament-parser`，2026-11-07。本项接续已验两表literal/loader和tag38选择消费者，使用资源线新取得的固定候选原文件；不修改已冻结warm/cold工具、生产、Proto、配置、resources或实时进程。

**主线已审核的限定结果：Group完整四标量parser、Ornament标量/字符串路径及五个cache-builder key片段，23组原生记录、10项独立断言通过，双新进程一致。** 非空条件数组、完整cache-builder、运行revision和Java业务来源仍未闭，正式tag38/g2未获许可。

## 先规划的范围

目标是把“没有资源文件”进一步分成已取得候选原文件、准确同名字段、binary parser和真实cache构建。已有证据为目标三固定输入、两张具名表的loader、61286/44799类型及tag38完整受控消费者。未知包括候选英文key到目标字段的独立桥梁、非空unlockCond、实际revision和默认记录完整性。

最小实验先重提metadata/函数，再用独立非零、uint32高位、缺flag及错误输入运行原生parser。仅对候选与metadata**逐字同名**的混淆key做partial projection；没有来源的英文alias不猜。cache-builder先核实际读取哪个资源字段作为查询键。未知数组/线程局部临时分配前停止，完整资源和g2不晋级。

## 固定来源

每个正式进程重新验证EXE `8b014f64b5dc12fbc476e0c362b053abfdd7a1378cd282e4ad81057621dbd6b5`、global `45dc7a44dc9fc187146c52f6c33de57e2d1c1dcfec57f8736431553460f6ef2a`、startup `b314810840ca6f495822c9c44483dde2394b9f09ed3c6b13c2782092f04d5105`。

候选原字节位于`local/migration72/d02-active/resources/candidate/ExcelBinOutput/`，新工具独立核SHA-256及带Git blob header的SHA-1。资源线提供的来源commit为`d54f6f0d423ded9d055bb740a3e5f3aa8f2667f1`，本工具不把blob匹配扩张为自然客户端revision匹配。

| 文件 | bytes / Git blob | SHA-256 |
| --- | --- | --- |
| AvatarOrnamentGroupExcelConfigData.json | 114 / `b0e8dde4176a8f4fdd2dcd49ca4587c6676da470` | `f1a4885c2fdff06021263e4d6627feca4ff135547820e85a814eecb6495122da` |
| AvatarOrnamentExcelConfigData.json | 1630 / `4418e8163af74ca1ea06e76386e41bc1aca8cd70` | `aad42c01df12aaa4956374226e26db11cdbd3d72a5586e4c9bf2e4415ae2287a` |

Group完整候选只有一行，显式avatarId10000136、groupID1、DLGBPJPJMHP101；Ornament有101/102两行及五项unlockCond。没有出生5/7行不能转成“主角允许无资源/空tag38”。未跟踪原始资源或二进制，报告保存有限候选内容和原字节hash。

## parser字段与独立非零结果

`61286` slot4 parser为`152fe0bc0`。它读取16位header并xor56bc，在如下顺序读取选定scalar；所有缺flag分支仍调用实际58209编码器生成逻辑零，覆盖预置777。

| mask | 对象偏移 / 原字段 | 读取后的逻辑变换 |
| --- | --- | --- |
| 40 | +10 CIEENBEDEPL，encoded58209 | raw xor05b6233b |
| 04 | +18 HFLEJLBCMBL，encoded58209 | (raw+5a8044f2) xor32942559 |
| 10 | +14 EGJDNOFCPOA，普通uint32 | raw+2b06fc55 |
| 01 | +1c DLGBPJPJMHP，encoded58209 | raw+34259ba1 |

运算按uint32。缺flag与显式全零分别消费2/18字节，均清旧逻辑值；独立非零11/22/33/44和`ffffffff/80000001/fffffffe/ffffffff`分别到各自字段，真实encode/decode保留高位。候选DL同名值101独立投影的原始fixture为`bd56c464dacb`，只有+1c逻辑101，其他字段的零只是该**partial binary fixture**的结果。

`44799` slot4 parser为`14a6fe2f0`，header为`(u16 xor727c)-e4a`，低位flags选择以下内容：

| mask | 对象偏移 / 原字段 | 读取后的逻辑变换 |
| --- | --- | --- |
| 10 | +2c PBMHHJCEJPJ，encoded58209 | raw+c93c8186 |
| 08 | +18 DENCEMCMLHM，string | 长度u16 xor eead；每8字节块加e923d598fc68eead，再UTF8转换 |
| 02 | +10 PLODDDMLPLF，array58937 | 调用14e7e6570，非空路径未实现为成功 |
| 20 | +24 CIEENBEDEPL，encoded58209 | raw+795aae9b |
| 04 | +28 OBMKDJCGHPJ，普通uint32 | raw xor55127efa |
| 80 | +20 EFFKPNKJHBP，enum73624 | (raw+846eb441) xor a94fd967 |

实际运行缺flag、显式零、独立非零、高位和Unicode样本；每次预填逻辑scalar777、旧字符串`old`和count3数组。缺flag真实写逻辑零、共享空字符串和共享空数组，不是保留旧值。不能据此把候选JSON缺键等同目标二进制缺flag。

runtime byte-reader、数组/字符串存储、memcpy/memset和UTF8转换复用已审`ResourceRuntime`模型；各scalar解码变换、buffer循环、branch和对象store均由原生执行。UTF8转换模型只处理机器码已解密的buffer并推进cursor，不替代加密字符串变换。

## 四个逐字同名key与实际候选有限投影

metadata独立确认Group `DLGBPJPJMHP/+1c` 和Ornament `PBMHHJCEJPJ/+2c`、`DENCEMCMLHM/+18`、`EFFKPNKJHBP/+20` 与候选key逐字一致。无需把候选英文名称猜成混淆字段。

- DL101及PBM101/102分别经真实parser与58209回读，PBM独立binary literals为`267cdf7ec336`、`267ce07ec336`。
- 两份DENC路径分别为63/64个UTF8字节，均实际走完整原生栈缓冲路径返回，内容/消费cursor独立核对。最初以为它们会触发大buffer，实际长度和完整返回纠正该预想；另加真正65字节样本，停在`14a6fe614`线程局部临时分配前，没有任选有利runtime flag。
- 目标enum73624的独立metadata default records恢复 `LOGIC_NONE0/AND1/OR2/NOT3` 及其余目标符号；第二候选的显式`EFFKPNKJHBP="LOGIC_AND"`按同名目标符号1投影并实际回读。第一候选没有该key，未声称它的实际binary逻辑一定为0。

两份组合样本只投影已明确存在的PBM、DENC及显式EFF。候选真实unlockCond没有被删除或替成空来宣称完整行；其余字段零和empty-array仅为partial fixture。工具内`id/group/default`是已有消费者或fixture标签，不批准新英文协议/资源alias；+14/+28普通uint32保持物理字段名，未恢复成titleTextMapHash/nameTextMapHash。

## cache-builder的准确下一来源

`15437af90`完整静态函数先清旧派生cache，再枚举primary Group行，按解码的**row+18**查询GLOBAL+13b78，创建或追加该角色的Group列表。新实验从`15437b12e`到`15437b14c` dictionary lookup call前，显式提供row/rsi dictionary/rbp/r15 scratch。

五个parser产物真实交付得到key0、0、22、80000001、0，dictionary参数保持；对非零行，+10为11、+1c为44而key为22，独立排除混用group/default。结合旧tag38以模型Avatar ID查询该派生cache的证据，+18是此按角色分组键的来源。

没有执行全部cache重建、ValueCollection初始化/枚举、generic字典插入、List增长或重复更新。本片段不能把候选唯一avatarId直接注入生产cache，也不能把候选缺主角行当空列表许可。

## 错误、验收与复现

非空条件数组准确到`14e7e6570`之前；65字节string准确到`14a6fe614`之前。Group header声明必需scalar却截断时到真实`1404c2650`；null reader到`1404c2be0`。这些stop不在models中，没有成功桩。错误输入只覆盖列明边界，不泛称parser已验证任意畸形长度。

产物在`local/migration72/d02-active/avatar/ornament/`：

| 产物 | SHA-256 / bytes |
| --- | --- |
| first.json / repeat.json | `870353377318c69c4f1d4c985bc4a67072e214f000b445a33a8a09438bde87dd` / 202744 |
| first-summary.json / repeat-summary.json | `01c46b2decc8c20e090a85a68d795b1e85b4569eba148c46ca12a46b24bf12de` / 36986 |
| 工具源 | `e810efee76f619b1b911c5f0aec77cb3fd654afdfbf34b4a6e4aecb6da8c3f89` |

`determinism.json`逐字节一致，`tests-first.log`10项通过；原始probe1/probe2保留，最终不沿用旧“候选字符串需要大buffer”的预想。初轮输出还有尚未收紧的fixture标签，最终报告将普通uint32明确叫plainField14/plainField28，未因此改字段行为或语义准入。

```powershell
$avatarOrnamentClient = & powershell.exe -NoProfile -ExecutionPolicy Bypass -File tools/game_path.ps1
python tools/client72_avatar_live_contract_ornament.py --client $avatarOrnamentClient --output local/migration72/d02-active/avatar/ornament/first.json --observations local/migration72/d02-active/avatar/ornament/first-summary.json
python tools/client72_avatar_live_contract_ornament.py --client $avatarOrnamentClient --output local/migration72/d02-active/avatar/ornament/repeat.json --observations local/migration72/d02-active/avatar/ornament/repeat-summary.json
python -m unittest tools.test_client72_avatar_live_contract_ornament -v
```

主线第三回放用独立路径，并设置`CLIENT72_ORNAMENT_REPORT`选择该raw报告。没有运行原生报告不skip。纯离线工具/测试/文档，无重启或部署要求，本代理未Git暂存/提交。

Java仍没有已审核的tag38 getter；`Avatar.getCostume()`、技能map或旧toProto近名字段不能作Ornament来源。本项只增加候选key→目标原字段/编码的限定规格，不新增正式sender实现许可。下一可执行任务为58937非空条件数组parser、完整cache-builder/List更新，以及真实客户端选中的资源blob/revision；随后才能连接当前Ornament选择/默认来源与真实Java存储，再验完整handler与D-02。

## 2026-11-08 主线审核

主线审查真实工具、模型/停止边界及原始证明，以独立新进程第三回放和10项定向检查接收本限定子合同。完整raw `ornament-third.json` SHA-256 `870353377318c69c4f1d4c985bc4a67072e214f000b445a33a8a09438bde87dd`，与独立双跑逐字节一致；证明/日志位于`local/migration72/d02-active/main/`的`pending-contracts-main-proof.json`和`ornament-main-tests.log`。23组parser/字符串/Group-key原生记录已验；候选alias/revision/完整cache-builder和g2未批准。 本项仅离线研究工具/测试/脱敏文档，无运行代码、Proto、配置、补丁或资源变更，因此无需服务重启；不改变ready/required/runtime或D-02客户端验收状态。
