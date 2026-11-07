# D02-R1 自然enum输入与cold后继

2026-11-08，接续冻结的[初始cold合同](CLIENT_72_AVATAR_COLD_CONTRACT.md)。限定成果已由主线第三复验通过。这里只新增工具、测试、本文；不连接客户端、不修改原始样本或生产，不把研究子项当完整D02。

## 小计划与限定结果

目标是用自然登录页的新只读样本替换原来人工enum/underlying/RuntimeType class输入，并在有来源的范围继续cold helper。证据为固定客户端三文件、provider4逐次raw读及metadata/原生指令。未知包括lazy RGCTX、真实defaultComparer、RuntimeType层级/vtable、接口反射及泛型构造。最小实验先复核地址链，再重定位仅已采字段，原生跑canonicalizer/underlying；新发现的TypeCode内部函数允许独立产生真实code，最后与既有selector衔接。遇到未采状态在指令执行前停止，不补0、不给函数返成功。

**自然enum20060→underlying Int32/251→canonical Int32Enum/351，以及TypeCode9→EnumEqualityComparer定义1239，现有原生证据。** 它们是分层片段，不是连续完整helper。自然EqualityComparer getter/ctor槽仍为空，defaultComparer字段未读；完整cold comparer、Avatar constructor/handler和正式g2仍false。9组原生对照、8项独立断言通过。

## 固定输入与自然链

每轮重读验固定EXE `8b014f64b5dc12fbc476e0c362b053abfdd7a1378cd282e4ad81057621dbd6b5`、global `45dc7a44dc9fc187146c52f6c33de57e2d1c1dcfec57f8736431553460f6ef2a`、startup `b314810840ca6f495822c9c44483dde2394b9f09ed3c6b13c2782092f04d5105`。本工具保存原生窗口SHA与完整指令、metadata来源、依赖源码SHA。

样本 `local/migration72/d02-active/plain/initialization-provider-elevated4.json` SHA `5a233bd0048f38f449e890268510e00d74870c770f58a416a82ceffe9faf0b9b`，57项读取的长度/字节SHA/错误全验。执行前采样器v3 SHA `2dfc86a6bd9f99ae72cd380c1315ba6d5f6d3968ff892a5eb7c089f4c1fc65ab`；源自然观察SHA `2643bfe4006acaf3a7a97c1c0e681d5e71ae8a3c27f048cc31d87dc273adcf9f`。现场标识/时刻留原始忽略文件，非原子快照。

以当次模块基址B：`B+5bc8f18`→class cache→第20060项→C。C+10的16字节为`5c4e0000000000000000110000000000`，与独立registered descriptor和RuntimeType cache首candidate key完全相同。C+c8为21，C+ca为6c（enum bit4置位），C+ac为`01017928`。`B+5bc8ad0`→registry→base，base加上述offset得到底层class U；U+10是type251/kind8，U+c8为21。所有读地址逐项验证，不用“看似非空”代替链条。

RuntimeType cache实际对象+10指向C+10，其对象头所指class的+10为type284/kind18，+c8为31。provider4因此补上router3尚未读取的对象类型身份。此前initialization1未取得独立descriptor和首bucket，不能称cache miss；router3证明精确20060首candidate，provider4进一步证明对应class/underlying/RuntimeType身份。三者都未读取真实defaultComparer，不能把None当static field null。

## 原生canonicalizer与TypeCode

自然options record为`0101000007000000`，canonical class cell的type351及initialized字节也来自样本。离线仅重定位指针与storage，保留实际type字节、ca/c8和underlying offset；registry base按同一个offset重新计算。`14052df40`返回U+10；`14052dd70`实际经过14052de91，返回canonical351+10；直接输入Int32251保持原指针。额外byref位1明确标作反事实，对应underlying快捷路径不启用。

关键新发现：RuntimeType284 method2790的metadata virtualSlot21、registeredAddress159b87c70，与helper在159b6ea0c调用class+178一致（d0+21*8）。它检查类cctor后tailjmp **1404e0f30**。目标metadata方法名被清空，因此报告保留index/slot/代码关系，功能按实际代码解释。

内部1404e0f30从RuntimeType+10取descriptor；enum分支调用真实cached-class lookup，检查class+ca，再通过+ac和registry读取underlying type bits（1404e0faf）。将provider4实际enum与底层字段放入后，完整内部函数返回TypeCode9；Int32输入同样9，byref反事实返回1。没有预置eax=9，也没有模型替代TypeCode函数。这里直接调用内部函数，**未执行实际虚表分派或159b87c70的cctor guard**。

将刚产生的9作为明确跨片段输入喂给159b6ea12，原生selector选择registered descriptor offset691330，metadata确认definition1239 `EnumEqualityComparer`，停159b6e9a2前。这里闭合“值如何产生和选择哪个定义”，不闭合RuntimeType反射对象创建、MakeGenericType、实例构造和defaultComparer写回。

## 完整helper准确停止点

把自然RuntimeType对象已采字段重定位后，从159b6e720入口执行，在 **159b6e72e之前**停止；下一条将读取未采的cell1458869b8。独立metadata usageRow698789/source419确认此cell期待System.RuntimeType284，但不能因此假定其现场值等于已采object class。后续cast还读class+c4深度和+48 hierarchy数组；type284身份与initialized bit不提供这些字节。

继续完整cold所需最小顺序：先取得该cell与准确类型、expected/actual class的c4和+48及仅被索引的ancestor；再核helper所查Byte/String RuntimeType对象及HasIEquatable/GetInterfaces实际来源；接generic/enum parent判断、底层RuntimeType缓存、真实TypeCode虚分派；最后完整泛型实例创建、构造和cache写回。TypeCode内部实现已闭，不再重复旧的人工TypeCode selector实验。遇到自然getter槽仍0只说明尚未inflate该上下文，不是失败或comparer null。

主线若进行新只读批次，可读取B+58869b8的8字节、所指class+10的16字节及+c4的1字节；实际RuntimeType对象class的+c4、+48各1/8字节。仅在深度满足时读取hierarchy+(expectedDepth-1)*8的一项，按159b6e738..754判断cast，禁止扫描整个hierarchy或初始化class。此规格只解除首个guard，不能预称整个helperready；本工具不执行任何现场读取。

## Java与正式实现规格

此卡没有新增Java业务字段。现有准确来源仍为 `Avatar.getAvatarId()/getGuid()`、`getExp()/getPromoteLevel()/getLevel()`、`getEquips()`内同一`GameItem.getGuid()`、`getSkillDepotId()`；技能集合getter仅是待独立语义比对的来源，Ornament没有已审核getter，不能复用`getCostume()`。恢复必须完成 `Inventory.loadFromDatabase → AvatarStorage.postLoad → recalcStats → completeLoadingVitals → restoreLoadingVitals(true)`再读取最终生命/战斗值。

正式g2仍要同源Store/item/装备和depot/技能资源、全部必要后缀、池/scene生命周期及独立Java wire→目标原生完整消费→新旧号实机反核。自然类型初始化只能替换本卡列出的runtime输入，不能授权零值、空表或发送器改动。详细字段边界沿用[warm卡Java规格](CLIENT_72_AVATAR_LIVE_CONTRACT.md#java来源与实现规格)。

## 实际验证与复现

原始目录 `local/migration72/d02-active/avatar/cold-completion/`。早期first.json只含5组canonicalizer/首guard验证，保留；最终deep-first/repeat.json增加原生TypeCode与selector共9组，全部无函数成功模型。这里只创建离线存储；原生Windows锁模型虽由既有runtime注册，本次路径没有调用它们。17项联合测试中本卡8项、资源卡9项，各自可选择第三方输出。

```powershell
$coldCompletionClient = & powershell.exe -NoProfile -ExecutionPolicy Bypass -File tools/game_path.ps1
python tools/client72_avatar_cold_completion.py --client $coldCompletionClient --output local/migration72/d02-active/avatar/cold-completion/deep-first.json
python -m unittest tools.test_client72_avatar_cold_completion -v
```

主线第三验设置 `CLIENT72_COLD_COMPLETION_REPORT`。未执行Git提交、构建、部署或实机；共享索引由主线审核后更新。下一独立工作可沿上述具体未采字段和反射入口继续，不能把本卡子结果标作D02完成。

最终双新进程报告逐字节相同：`deep-first.json` / `deep-repeat.json` SHA `d94b7990f7089772dc591369b9de6e3869f0981225c0006782fec3aad31ad094`，82682字节。工具SHA `b2fc20f0606e802485b9071c37561c0d152bafa60197101ccaa3a3ad5dd9f46b`；测试SHA `cd792234e85ebdcd237e16e38f07d85515daec4c5cbd5547b2c8dfc6c22993c8`。`joint-tests.log`记录联合17项通过，`determinism.json`记录两卡逐字节相同。最终工具/测试/报告冻结供主线复核。

## 2026-11-08 主线审核

主线审查真实工具、模型/停止边界及原始证明，以独立新进程第三回放和8项定向检查接收本限定子合同。完整raw `cold-completion-third.json` SHA-256 `d94b7990f7089772dc591369b9de6e3869f0981225c0006782fec3aad31ad094`，与独立双跑逐字节一致；证明/日志位于`local/migration72/d02-active/main/`的`pending-contracts-main-proof.json`和`cold-completion-main-tests.log`。57跨度自然链驱动9组原生enum/underlying/TypeCode9→1239；完整helper/接口/泛型构造仍未知。 本项仅离线研究工具/测试/脱敏文档，无运行代码、Proto、配置、补丁或资源变更，因此无需服务重启；不改变ready/required/runtime或D-02客户端验收状态。
