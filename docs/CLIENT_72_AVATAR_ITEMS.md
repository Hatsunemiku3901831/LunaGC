# F-03d3d：Avatar的双Item列表raw闭包

限定新16352/BALMANFHCBI与71717/KNDMDIDNBMC两类，加已验Item及其13个依赖，共16类。53824继续仅空envelope、非空明确拒绝。身份及业务语义仍`unresolved`，`fullAvatarRawClosure=false`；`fullSelectedItemRawClosure=true`只指本卡16类raw图。不修改公共GraphReader/MapRuntime、生产Proto、sender或包号，不启动服务、数据库、客户端或Gradle。

每个完整运行进程重新核验`extract_client72.HASHES`中的三份CN7.2输入，与[AvatarInfo核心](CLIENT_72_AVATAR_CORE.md)一致；然后重新提取metadata、全部有界usage cells和选定AOT。复用`inventory-graph-observations.json`严格固定SHA-256 `ca45b0d9b038cd46fc35943924ca77ae46c8fbae82c06e131245e03f43d95b18`，不按旧版本字段或Asta英文名升级语义。

数量须区分：原库存15类47wire包含StoreNotify候选根68537的3字段；本卡只复用其余14类44wire。新2类先从6个metadata存储槽开始，经原生扫描才独立确认6wire，因此本卡16类50wire。沿16352实际field及所有有界reader/`.cctor` class/generic usage引用审计为16类45存储、46条usage引用；Object owner仍为28471/44169/65333，展开oneof后才成为50wire，不能把45存储直接当wire。完整Avatar仍不闭合。

## 新6字段与AOT

偏移为十六进制。标量wire0，message及message-list wire2。

| 类型 | tag | clientField / offset | raw |
| --- | --- | --- | --- |
| 16352 | 1 | MNIBICBHICK/+30 | u32 |
| 16352 | 2 | BAKDLDCFCIF/+28 | message-list→65333 Item |
| 16352 | 3 | BBMFJFKAECB/+18 | message→71717 |
| 16352 | 4 | CLFDBPJOFLI/+20 | message-list→65333 Item |
| 71717 | 1 | JEDCIENABEB/+1c | u32 |
| 71717 | 2 | JCPAOHDANBO/+18 | u32 |

两个reader分别`0x14c9c0360`与`0x152eea4d0`。16352实际`.cctor 0x14c9bfea0`执行到返回并分别建立两个Item-list codec，未按两个同形字段的metadata顺序配对。25060/classInstance10366的实际参数为65333，FNNADLCNGFO独立AOT绑定到`0x158dc41f0`；ctor也保留静态记录，不称全部method已执行。原始Item的44wire及三个oneof组见[库存完整图](CLIENT_72_INVENTORY_GRAPH.md)与固定摘要，本轮全部重新执行tag/wire扫描及30个原始非空/merge向量。

## 专用组合上下文

复用[Avatar两棵小树](CLIENT_72_AVATAR_TREES.md)的原生图上下文，重新注册14个Item类及新2类、所有实际class cells，并执行这16类中实际存在的7个`.cctor`。六个库存容器的u32 map/list原生initializer沿用已有primitive model，codec指针按实际static值注册；Item的CopyFrom/clone相关原生函数只按metadata地址允许执行。

库存的托管enumerator/array-clear模型假设所有map都是u32/u32；AvatarInfo同时含u32/message及u32/float map，它们共用dictionary insertion helper `0x1578b6a20`。本卡专用copy上下文将库存native map clear/copy的存储指向专用integer map runtime，插入仍走既有实际dictionary归属路由，只对该整数map集合维护复制所需count。message/float map继续走原codec上下文。没有修改公共runtime，没有把最终反序列化结果手工塞入模型。

## 非空样本与实际覆盖

2,048次16类/tag1..32/wire0/1/2/5原生扫描，将新增6wire和复用44wire全部与metadata/既有raw oneof断言核对。packed空分支记录真实native helper的container→owner/字段偏移及原始key，再由非空列表确认。

44个独立图样本含14个本卡新组合/空/边界样本及30个既有Item非空/合并样本。tag4列表按Item分支5..10依次用id105..110、GUID9007199254740998..9007199254741003；tag2列表用id205..210、GUID18446744073709551610..18446744073709551605。各列表六种分支均含非空内容，独立id和高u64可直接识别串列表或被覆盖，不能用两个相同集合证明同形字段绑定。

新71717两字段用11/22区分，另验u32上界、重复清零、错误wire；重复singular message的部分字段合并和清零、两个Item list交错追加、每个list的独立空Item元素也有固定断言。Item复用图继续覆盖三个oneof组的切换/同分支保留与覆盖、map清零/复制、list追加/复制、bool清零、64位及嵌套。具体message clone、map clear/copy及list copy helper实际执行。

完整AvatarInfo组合保持37个外字段、已验两个小树、负float和高u64，新增16352的两个列表各有9个非空Item：六种外层分支加TPS两个替代子分支及Equip另一分支，实际进入本卡全部16个reader。53824仅保留空标记。另有只包含非空16352/高u64/负float的较小组合，帮助区分本卡与既有外层内容。

10项拒绝分为1个53824非空范围拒绝、4个显式输入模型拒绝（非法tag、Item GUID溢出、packed截断、新叶子varint截断）及5个真实ReadMessage错误入口（negative-length、truncated-message、两个list/新叶子/Item下层相关recursion-limit）。到入口即停，未执行throw，不声称完整客户端异常合同。managed allocation/class/generic context、primitive input/delegates、固定容量64数组/map字典/枚举/clear回调仍是明确模型；native reader/ReadMessage/list append/clone/map及list copy实际执行。没有IFix/live ABI、命名Handler、业务用途、实机或部署验收。

8项新增与85项关联研究回归通过；两个全新进程各自核三hash、提取、扫描和独立样本，完整证据及摘要逐字节一致。共享Project/Harness/矩阵由主线维护。

```powershell
python tools/trace_client72_avatar_items.py --client $env:GAME_PATH --output local/migration72/evidence/avatar-items-native.json --observations local/migration72/evidence/avatar-items-summary.json
python -m unittest tools.test_client72_avatar_items -v
```

后续只剩53824复杂子树尚未raw闭合，原静态34类238存储仍只是下界且与已有图有重叠。应先审其三个直接nested（64132、86812 list、34045）和Object/float依赖，再由主线拆限定子卡；本卡不自动展开、不拿现有组合替代完整Avatar业务合同。
