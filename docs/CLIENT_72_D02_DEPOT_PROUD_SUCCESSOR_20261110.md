# E-01/R3-PROUD-ENTRY：Depot proud 的有界后继

本卡收口为一个准确的部分结论：Depot22284的 **+18/81127数组→元素+14→ProudSkillInherent分支→typed pair80067查询→输出U32行ID** 已有直接原指令链；但该pair两项的“group/level”业务身份、81127+10的提升门槛及当前Java `group*100+1`等价均未闭合。因此现在**不交proud投影/alias实现**，不以出生五个零、Java提前过滤或onLoad规范化空批准未证字段。没有第三窗口请求。

talents已由主线独立静态、投影和实际JDK17验收，两次真实`new Avatar(data)`返回；删talents的实际失败为`getTalentIdList:529`。这项成果直接复用，不把proud/ability一起晋级。本卡针对`Avatar.setSkillDepotData279..301`先遍历的inherent输入，不恢复全部Depot/玩法。

## 计划、来源与实际核验

先写 `local/migration72/d02-resume-20261110/c-depot-proud-successor/plan.zh.md`，2047B，SHA `8ddf31ad73ca33ca4f6462bb77c1983a204339016f86f3b8b017475be20e7efc`。只写本独立目录与本新文档；旧spec/raw/报告、Java/tools、Project/Harness、patch/resources均只读。子任务0当前PE/GAME_PATH、Windows API、native CPU、JVM、DB、网络、Git、构建或现场。

| 固定材料 | 完整 SHA-256 |
| --- | --- |
| `local/migration72/d02-resume-20261108/r3-equipment/metadata.private.pkl`，117201003B | `df52eb70fe8e70dfecd1059e898d15e288cfa98bcefc78bcaabf753ae150349f` |
| `local/migration72/evidence/client72-signatures.json`，78649549B | `28377e7a8d3c891cd61be97ddb74f8502a22f2f2f039d6a91dba3b092d7822fe` |
| `tools/extract_client72_signatures.py` | `ee92527f3607be774c28be694d6f57e9eed3aa40068b7b584979e27b6470c349` |
| `d02-resume-20261108/r3-mapping/main-third.json`，10370640B | `d990c2c469e5a9d3ee40351e4dfe5223ae49afaa855abe5d912427139c66307e` |
| 同目录 `skill-component-inspection5.json`，729627B | `520f54f66f8a09c7a5fd266a4e5cd15193a312406ece1dd14e6c8059a338f716` |
| `d02-active/resource-locator-astra/locator-final-1/AvatarSkillDepot.native-m_bytes.bin`，26024B | `fda5ac5875581bf6c04a269333197d41decea728ecc9f075e4add7780745b14b` |
| `c-depot-enhance-window/code.bin`，原5901B | `a5677fc634752a48abf9486c8c3302654296e267bb272c67ecbf3224f1acb14f` |
| `c-depot-enhance-enum-main/selected-enum.private.json`，既验13299枚举 | `7194c2c23130ff9dd1817888d051525e62e0de980f85ede04fb819a70ae3282b` |
| `c-depot-proud-window/code.bin`，304B | `6bb0acbf70278fdc41faf3b71a678e1c2c7f87e3e2d7d4dae82f0685486bb726` |
| 同目录 `pdata.bin` / `extraction.private.json` | `4e8c333049471066db067aec157fbf585a94ac171f125e72115a98d4167a40ae` / `75f23c2aff91b57ac0ecb2b2c6f7fbf6fa8f79f7f43dcf81921da35cc618a6cd` |
| `c-proud-group-window/code.bin`，351B | `7ae91708b6db0028ae72f2a33c817c27d6a2386259f674bcc083ba6856bf2885` |
| 同目录 `pdata.bin` / `extraction.private.json` | `0cfface172e2db0dec97947926270420b0140fcf64b23a4470d282e46977d7b5` / `e5b6c4e263d65d6ff7a5e91fb15262af21a0611e819eabdfcb89902e32a779fc` |

未写全前缀的`d02-*`位于`local/migration72/`；`c-*`位于本轮`local/migration72/d02-resume-20261110/`。全部20输入和asm完整SHA见独立报告。

新 `audit_static.py`，13605B，SHA `7b91cdbd337168c5b1b23b18e1521967ec9cd437f9b391b54fba30e889ac2127`，实际两个独立Python进程运行通过。`first.private.json`、`repeat.private.json`各20977B，SHA均为 `a0a1f93cb980ddcd39fc46c70c46742f20c8ef154a8690816c2b068c7fdfbc9d`，原bytes相同。核验20pin、5901B/1382条及两新窗304B/78条、351B/92条的全部指令地址/原bytes连续性，16个iterator及18个callee opcode、5个typed声明、唯一callee、全参数索引、3行payload跨度和3个producer签名。没有运行这些机器码；旧135枚举、whole、Java、talents测试均不重计。

## 字段先分清，不能混作一个proud列表

| typed来源 | 已读叶 | 原分支 | 尚未知 |
| --- | --- | --- | --- |
| Depot22284 field105354 `LHFOOPMJHBN/+10`，SZARRAY<class40302> | 40302 field198737 `ELMCBLCOEEK/+1c`，encoded58209 | 5901B的`150b29c8a..29d7f`读取/匹配，写EnhanceDetail13298 enum+30=`ProudSkillSpecial=7` | 不能当inherent提升门槛；40302还含+10参数数组/+18条件enum85159，不能丢条件后直接映射Java special列表 |
| Depot22284 field105355 `FJNOKIIOHAJ/+18`，SZARRAY<class81127> | 81127 field404854 `ELMCBLCOEEK/+14`，encoded58209 | `150b29b2d..29c0f`读取/匹配，写同typed enum=`ProudSkillInherent=6` | 81127另一叶field404853 `IICBGCPMAHL/+10`同为encoded58209，但本窗没有该叶的门槛读取 |

两个元素类型不同；相同混淆名`ELMCBLCOEEK`或相同偏移不能替代typed来源。角色6/7来自旧13299原常量报告，本卡没有重新恢复枚举。

原真实selected行及源跨度已复核：501为426..577/SHA `943f15501042ae657b27bf424e8bae2c0fbd225a70e80622fa453dada40037cf`；701为2196..2348/`c8150bc63a7055b7a1aa466c8683ff1035c0aec58f68f1616d30f33f19103914`；704为2770..2988/`6e6aeaac927901eec5b35009d2538bdaabff9d52179cc35d0e1ca31d667d05f6`。每行都有真实2个40302、5个81127元素；501/701的81127叶`(+10,+14)`为五个`(0,0)`，704为`(1,721)/(4,722)/(0,0)/(0,0)/(0,0)`。这些只作准确源值；不据0、751、721/722或升序形状命名字段。

## 304B后继的准确负结论

原组件初始化`15345c2d0`于`15345c78a`调用已存`153461980`。后者`1534619b4`把同一新Depot写入组件53763的typed field267596/+290，并在`1534619c7`原E9尾跳`153459190`。据此主线实际验证唯一owner53763/method446776/AOT槽，并只取真实exception完整窗：**153459190..1534592c0 exclusive**，pdata=`90914513c0924513c8cea604`，unwind4a6cec8，未读xdata。

该窗真实流程是RDX Depot→R8，`1534591c0 418b4054`仅取Depot+54 ID，58209解码后调用1531e9460；随后设置组件+2f1状态、构造delegate并尾跳148d6ee90。整个窗没有Depot+18/81127、元素+14或+10门槛循环。故它不是本卡要找的proud字段consumer。**停止其map getter、queue、callback和hotfix调用树**，不因可达而继续提窗。

## 81127读值到351B callee的准确链

下一请求只来自已读叶的实际参数流，冻结于 `group-callee-window-request.private.json`，7117B，SHA `b09a4507b7fa9b445d9d44160a740a837b9c245164f4a28288b200006bab5d3d`。

- `150b29b20`取typed iterator13300+20 Depot；`150b29b2d 488b7018`取typed81127数组；`150b29b7d`按真实长度/边界取引用元素。
- `150b29b87 8b4014`取元素+14，经`150b29b91`的已审58209 getter解码；`150b29bbb`仅在与当前candidate解码ID相等时进入该分支。null有效位另用bit32保存，不把它并入ID。
- `150b29bca`再次解码同一candidate，`29be5/29be7/29bec`明确置ECX=命中ID、EDX=1、R8D=0。
- **`150b29bef e80c4dc8fc`→14d7ae900**。metadata唯一owner80063/method663552；完整签名为U32/U32/bool，非恢复返回类型。固定AOT公式P+510000，派生槽142db54d0；主线重新核原两QWORD和5B caller，未拿邻方法当end。

主线取得唯一真实exception窗 **14d7ae900..14d7aea5f exclusive**，pdata=`00e97a0d5fea7a0d30984304`，unwind4439830，未读xdata。本次主线累计两个新code窗655B、两pdata24B及两AOT核对；本子任务仅消费封存副本，0新提取/CPU/live。

| callee原site / bytes | 能直接得出的结论 |
| --- | --- |
| `14d7ae908 4489c5`、`e90b 89d3`、`e90d 89cf` | 分别保存第三bool、第二U32、第一U32；第三参数不进入key。 |
| `14d7ae938 e803faffff` | 从14d7ae340取得map对象；该callee体未审，不host返回对象。 |
| `14d7ae949 48c1e320`、`e94d 89ff`、`e94f 4809df` | 准确构造 `(uint64(secondU32)<<32) | firstU32`；这里是8B值类型key，**不是pointer**，不是`group*100+level`。 |
| `14d7ae952 4c8b05bfbe13f8` | RIP目标1458ea818，冻结usage明确接口20037<valuetype80067,U32>；80067两字段400202/+10与400203/+14均U32。 |
| `14d7ae9d2 4c8d442424`、`e9d7 4889f1`、`e9da 4889fa`、`e9dd ffd3` | R8是栈上4B输出地址，RCX=map对象，RDX=上述key；按真实接口/slot间接调用，目标和实际map内容未执行。 |
| `14d7ae9df 84c0`、`e9e1 741d`、`14d7aea00 31c0` | 查询AL=false时原函数返回RAX=0。这是实际指令出口，不是允许补零或允许省略map。 |
| `14d7ae9f3 8b4c2424`、`e9f7 89ea`、`e9f9 e872f5ffff` | 查询成功后以**输出U32**和原bool调用14d7adf70。不是直接拿第一U32当该调用的rowID。其体未审；不替换成host字典命中。 |

hotfix标志、class C7初始化、map null异常及间接slot均未在本卡执行，不能把正常边静态可达解释为自然成功。当前准确地把81127+14收敛为pair低U32，把原callsite常量1收敛为pair高U32；“高U32就是level”“低U32就是Java proudSkillGroupId”仍需producer/consumer身份来源。

下游使用确有typed约束：iterator的`150b29c37`调用149f0e310，metadata为class63831/method529244；该wrapper field316249/+40声明NativeConfig41105，后续读取41105+90 field203376 encoded58209。它约束调用者期待的对象形状，**不恢复14d7ae900的返回类型**，也不证明原map的ID等于Java算式。

## 最后允许的producer索引及停止点

metadata80063 field400193确实声明泛型18192 `GFLGKMKNBCC<80067,U32,80065,47647>`；field400191另为`Dictionary<U32,class63831>`。不能只按第三泛参位置把80065命名为“从63831生产pair的selector”。

本卡用完整固定参数索引核全部实际声明，结果如下：

| 声明owner / method / 注册址 | 真实声明参数 | 结论 |
| --- | --- | --- |
| 80065 / 663561 `CDMPEFBPCLP` / 1508ae890，flags1e6/slot4 | class42005（同既有parser的reader type）、byref valuetype80067 | 是reader→out pair签名；**没有typed47647或63831输入**，不能作为所需row→pair直接生产者。 |
| 47647 / 395408 / 14a119e20，slot4 | 同class42005、byref U32 | 不能把47647本身当NativeConfig输入行。 |
| 47647 / 395409 / 14a119e50，slot5 | 同class42005、byref U32[] | 同上；不是所需63831→pair关系。 |
| 20037 / 154095 / 无独立注册地址 | generic position0值、byref generic position1 | 支持本窗key/out-value接口形状；不提供key字段的业务名。 |

冻结metadata cache没有恢复implemented-interface表。这里只证明上述实际method/slot/签名，不声称这些struct“没有实现接口”。没有真实接口证据时不猜接口身份；1508ae890虽然地址已知，**本卡不把它提交为第三窗**。

完整参数索引中，81127（含数组/泛型嵌套）只匹配唯一byref数组参数：owner81127/method672119 parser14f777160。它不是提升门槛consumer。既有49个组件原窗没有所需81127+10比较；有界129份旧Avatar/skill材料14,647,563B只找到14d7ae900的调用，未找到其body。两个旧大报告的同地址查询也没有body；这些有限排除不代表全客户端不存在该consumer。

## 精确缺输入，暂不交投影

下一有效外部输入必须是下列**具体字段关系的源码/原指令来源**，目前没有可据此选择的新唯一entry：

1. pair80067两个U32与实际proud group/level的独立生产或消费身份，且其查出的U32与真实Proud行身份相接；仅reader签名、相同数字或候选英文键不足。若需要Java正group引用，还须实证当前Java`group*100+1`等于原map对应ID，不能把原tuple key改写成这条算式。
2. 同typed Depot+18的81127元素field404853/+10实际读取，经过已知codec后，与有来源的Avatar promote值作准确比较及分支的原site。它不是40302的条件enum/参数，也不是Proud wrapper41105+90的同混淆名字段。

本卡没有这两个关系，故`proudSkillGroupIdAliasApproved=false`、`secondU32LevelIdentityApproved=false`、`promoteThresholdApproved=false`、`inherentProjectionApproved=false`。不输出五个空对象、零group过滤片段或假的needAvatarPromoteLevel；不修改生产alias。40302的quest条件和Java special投影亦保持未批，不将它们合并来“补齐”inherent。

若下一卡取得上述直接来源，才按同一原payload/source、原五项次序与零项、U32到Java int边界、非零提升门槛前后两侧、错误typed来源/缺字段/来源冲突做投影正反，再交Terra准备真实ResourceLoader测试及主线运行。本卡不提前造测试正常返回。

已消费原GitHub中文固定branch/source及bounded-partial范围；没有发现能替代当前字段producer的新复用目标，本次0网络/clone，不重查talents、135枚举、SDK页、Pool/String、资源解包或整个component树。`completeAvatar=false`、`resourceReady=false`、`d02Passed=false`保持；Git、共享状态与真实现场归主线。准确缺输入已交回，本模型释放。
