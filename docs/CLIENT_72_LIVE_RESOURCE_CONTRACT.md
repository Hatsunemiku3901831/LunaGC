# D02-R1 自然资源缓存入口与解码状态

2026-11-08，独立后继合同。限定结果已由主线第三复验通过；本卡不修改生产、资源、配置或已冻结研究工具，不连接客户端进程。主线提供自然登录页的只读导出，本工具只验证文件和执行离线机器码。

## 小计划与结论

目标是在自然 UI 阶段寻找已创建的 typed 资源缓存，为 Avatar 5/7、初始武器11101、depot501/701及Ornament提供真实输入。固定 EXE/metadata 的 path getter、static field、查询指令和独立数值解码器是证据；候选 revision、JSON alias、真实行是否存在起初未知。最小实验先读静态根和七个指针，非空才进入有上限的字典读取；不调用 getter 或 loader。另执行非零、高位、碰撞、错误和解码对照。失败出口分别为未创建、未知 comparer、查询预算耗尽和不可读，不将这些等同缺行。

**实际结果：静态根已创建，七个表指针均为0；自然解码表及seed已完整读取到。** 这证明该次观察尚无这些缓存，不能证明表缺行、资源坏或应发送空/default。已独立确认 `S+34900` 是 WeaponExcelConfigData。17组原生对照和9项断言通过；没有自然资源行、实际blob revision或完整Avatar handler证据。

## 固定输入

三客户端输入每次重读验 SHA-256：EXE `8b014f64b5dc12fbc476e0c362b053abfdd7a1378cd282e4ad81057621dbd6b5`；global `45dc7a44dc9fc187146c52f6c33de57e2d1c1dcfec57f8736431553460f6ef2a`；startup `b314810840ca6f495822c9c44483dde2394b9f09ed3c6b13c2782092f04d5105`。

自然文件 `local/migration72/d02-active/plain/initialization-resources-elevated5.json` SHA `c5e6b05778f6326891065f120b966f4a8135b8601f097c30f6477724066a765d`，93项读取的长度/raw SHA/错误均核对。执行前采样器v4 SHA `4578606f875197a44abd7cbce1f895ffcfc4a72a02031f0cb13a5c5086da5e09`，源自然观察 SHA `2643bfe4006acaf3a7a97c1c0e681d5e71ae8a3c27f048cc31d87dc273adcf9f`。原件保留当次基址、进程创建时间和逐次读取来源；不是原子快照。本卡只解释 resourceStorage/resourceDecode，router/new-manager部分由资源专题另审。

## 真正的静态根与表身份

静态地址均以固定 image base `140000000` 表示。现场必须使用当次模块基址 `B`：`B+586b600` 读8字节得到 `S`。离线runtime中的 `GLOBAL` 是人工地址，不能用于现场。

| 表 | S内偏移 | owner / row type | 原生查询 | 最多查询的候选ID |
| --- | --- | --- | --- | --- |
| Avatar | 30870 | 65789 / 58272 | 14f3382e0 | 10000005、10000007 |
| AvatarSkillDepot | 212b8 | 63488 / 22284 | 14d6d70f0 | 501、701 |
| AvatarSkill | 16c70 | 11866 / 88602 | 14eb02d30 | 待同源depot行给ID |
| AvatarOrnamentGroup primary | 13b70 | 28604 / 61286 | 15437acb0 | 不猜key |
| AvatarOrnamentGroup byAvatar | 13b78 | 28604 / List&lt;61286&gt; | 15437ada0 | 10000005、10000007 |
| AvatarOrnament | 1cd98 | 39027 / 44799 | 1473673b0 | 101、102仅候选 |
| Weapon | 34900 | 71245 / 87097 | 154177600 | 11101仅新号候选 |

工具重新提取各 owner 的 `LJNPMLJAIAO` path getter，验证其8字节 `mov/ret` leaf与独立metadata literal usage。Weapon：method589823/154177230读取cell145b66f10；usageRow783974/source80414为 `Data/_ExcelBinOutput/WeaponExcelConfigData`。同owner method589828/154177600在154177605读静态根、15417760c读+34900，调用1578b66f0。独立必经refresh151843490在1518434f1也读该根和+34900。因此能给此前未知refresh依赖准确资源名；不能据此填充该行未知字段。

本次七个指针读取地址分别与 `S+offset` 完全一致，值均0。Ornament byAvatar函数可能先检查primary并构建派生缓存；被动读取禁止触发该重建。主角不在候选Group表的事实也不改变此边界。

## 有上限的字典读取

只有非空字典才读取前0x48字节。真实 `1578b66f0` 使用 +18 entries、+20 buckets、+28 comparer；managed array +18为长度、+20为payload。comparer为0时：`hash=ID & 7fffffff`；桶下标为hash对实际正长度取余；bucket值减1为entry index。entry宽24，+0 hash、+4 next、+8完整uint32 key、+10为value指针。hash匹配后仍比较完整key，高位不能截掉。对应指令1578b6794..6808及调用者返回value的154177634..63a。

每表最多2个既定ID、每ID最多8个链节点；记录实际数组长度、每次节点原字节和来源。预算耗尽是unknown，空桶/next=-1才是本条实际查找未命中；无效索引记录结构异常，不扩读。非空comparer须先取得并验证实际接口方法，当前合同只记录其对象头和class身份，停止ID解释；不替它假设默认hash。其接口分派在1578b671a..6842与68a7..6932。

7组字典原生对照包括非零11101、最高bit键、同hash不同完整key、真实碰撞后第二项、空桶、循环链拒绝、未知comparer前停；成功值保留完整 `fedcba9876543210`。均无函数成功模型。碰撞比较实际两次；循环停止159ca53c0，不伪装miss。

## 自然解码表

`14aef9db0`在db6读静态根、dbd取 `S+e970` 的byte[]对象指针A；de0读 `A+18` 的u32长度。dcd直接读取 `B+5872d30` 的4字节seed，**不是指针**。输入raw4先与seed异或得到x，四次索引依次为 `400|(x>>24)`、`500|((x>>16)&ff)`、`600|((x>>8)&ff)`、`700|(x&ff)`，加对象payload偏移20。读取的四字节按高至低拼成uint32。

自然样本数组长度2048，seed `3b0a002e`；只读取A+420起连续1024字节，按四段各256字节保留，SHA `8717ded77f717cf44988dee2720b99bbd60c751426b130f6166a2ffc379a57f6`。每段均为0..255的排列。空指针或不足长度必须停止，不能初始化或补表。单个真实字段还可只读上述4个byte位置；读完整1024仅为精确覆盖所有解码器可访问项。

4组独立象限公式对照之后，再把这份自然1024字节和seed原样放入离线storage。raw `0/12345678/80000000/ffffffff`由真实机器码得到 `3565698174/2454901053/3716693118/3482244539`，与独立索引算法一致。raw输入仍为受控字段，**不是读取到的资源行**。另有截断和null两个反例分别停1404c2650、1404c2be0。原生decoder无任何成功模型；人工storage布局与自然字节来源分别保留。

## Java与正式实现边界

准确源码来源是 `GameData.getAvatarDataMap().get(avatar.getAvatarId())`、`avatar.getAvatarData().getInitialWeapon()`、`GameData.getItemDataMap()`和 `GameData.getAvatarSkillDepotDataMap().get(avatar.getSkillDepotId())`。`AvatarStorage.addStartingWeapon`创建同一GameItem后还要求itemData存在，加入库存再装备；不能把candidate11101存在当成这条流程已完成。装备GUID来自 `Avatar.getEquips()`中同一 `GameItem.getGuid()`；技能当前depot来自 `getSkillDepotId()`，不能被资源默认值覆盖。

下一正式实现需要实际同源资源行→该客户端typed getter/字段→相同Java业务对象→完整装备/技能后缀，保持保存恢复顺序。Ornament尚无已审业务getter；`getCostume()`不能替代，候选alias和tag38空/default未批准。UI标签R49008628/S48722240/D49008628只证明屏幕显示，不绑定每blob。所有正式g2/handler/资源revision ready仍false。

解除七个缓存未知的条件是自然资源loader实际执行后的新状态或可验证实际资源blob；同一登录页重复读空槽没有增益。不通过调用getter制造初始化。旧号装备也不能直接使用新号11101。

## 验证与复现

原始目录 `local/migration72/d02-active/avatar/live-resources/`。首次静态脚本在Capstone迭代中改变detail选项引发NULL pointer异常，随后把配置移至迭代前；旧工具未改。首次断言把executedAddresses计数map当list调用count，纠正测试读取计数键；原生结果未受影响。早期固定storage报告first.json独立保留，最终自然输入报告为natural-first/repeat.json。

```powershell
$liveResourceClient = & powershell.exe -NoProfile -ExecutionPolicy Bypass -File tools/game_path.ps1
python tools/client72_live_resource_contract.py --client $liveResourceClient --output local/migration72/d02-active/avatar/live-resources/natural-first.json
python -m unittest tools.test_client72_live_resource_contract -v
```

主线第三验设置 `CLIENT72_LIVE_RESOURCE_REPORT` 指向其独立输出。只完成离线限定合同，不替代客户端验收或自动提交；共享Project/Harness状态由主线审核后更新。

最终双新进程报告逐字节相同：`natural-first.json` / `natural-repeat.json` SHA `655465fd6d5ab747cd0d492c3b8c4efcf8b212e46692e7e6cdd527e2ab00232f`，150776字节。工具SHA `85ac4f3db075a89df11291bbf57dda16fe5483d4fb5a9e6cb75879606446865d`；测试SHA `9f5950477c9e6408653283ba9a21ba32c78ce076bbc098f9e7c3c55f6ca4b42a`。联合17断言日志和逐字节核对在相邻cold-completion目录的`joint-tests.log`、`determinism.json`。

## 2026-11-08 主线审核

主线审查真实工具、模型/停止边界及原始证明，以独立新进程第三回放和9项定向检查接收本限定子合同。完整raw `live-resource-third.json` SHA-256 `655465fd6d5ab747cd0d492c3b8c4efcf8b212e46692e7e6cdd527e2ab00232f`，与独立双跑逐字节一致；证明/日志位于`local/migration72/d02-active/main/`的`pending-contracts-main-proof.json`和`live-resource-main-tests.log`。93跨度证明七缓存未创建、Weapon准确getter和实际1024B decode窗口；17原生控制不等于实际资源row/revision。 本项仅离线研究工具/测试/脱敏文档，无运行代码、Proto、配置、补丁或资源变更，因此无需服务重启；不改变ready/required/runtime或D-02客户端验收状态。
