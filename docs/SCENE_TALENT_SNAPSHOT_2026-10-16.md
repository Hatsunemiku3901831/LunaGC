# 场景初始化的天赋参数保护

本地客户端进入角色时，服务端在 `HandlerSceneInitFinishReq` 中抛出
`ArrayIndexOutOfBoundsException: Index -1 out of bounds for length 20`。
调用链为 `PacketPlayerEnterSceneInfoNotify.buildAvatarAbilityVars → applyProudSkillVars`，
导致后续场景初始化通知及应答未发送。客户端同时停留在错误弹窗；
弹窗是否随修复消失，仍需新会话验收。

## 原因与修复

`ModifyAbility` 已支持数值增量、参数引用和比例运算。
数值增量或缺省增量的 `paramIndex` 为 -1；旧场景快照仍直接用它访问 `paramList`，
也会把参数引用的增量误写成绝对值，并漏掉绝对引用的负号。

- 场景快照只写入绝对赋值，使用现有 `AbilityVarSetter.resolve` 解析引用和符号。
- `ModifyAbility` 继续由现有天赋计算流程处理，增量／比例不作为绝对场景变量发送。
- 缺少参数、越界引用和非有限数值不覆盖客户端默认值；有效零值继续发送。
- 天赋解锁标记继续发送，单个数值增量不会阻止整包构造。
- GM 的 `debug dynamicmap` 使用同一个安全解析入口，修复相同的负下标访问。

## 验证

新增 14 项测试，覆盖真实场景包、内存数据与 Mongo 冷读、数字增量、
参数增量、负号、零值、越界引用、NaN、解锁标记及实际调试命令。
另按本次队伍的艾尔海森、阿蕾奇诺、芙宁娜和纳西妲，
加载当前资源中的实际技能、固有天赋及 OpenConfig 构造场景包。

补齐测试夹具的聊天反馈和协议 oneof 断言后，仅在构建容器回放原始两个源文件：
**14 项中 8 项失败**，其中真实芙宁娜资源复现了同样的 `Index -1 / length 20`。
宿主修复源码始终保留，回放后恢复容器输入。

最终场景包、天赋参数运算、天赋应用、旧存档技能、队伍共鸣、技能加成和技能等级
**144 项／7 套件通过，零失败、错误及跳过**；`test`／`jar` 成功。
3 个源输入核对宿主／容器 SHA-256，2 个变化类核对编译目录／JAR 字节。
候选 JAR SHA-256：
`20f1d797715c9259856d87026404c4ec1afcc23d6d78d8895d6493f6c6865fab`。

原始异常、修复前回放、各轮测试及产物核对保存在
`debug/aster-mid-20261011/batch275/`。服务端通过测试不代表客户端已经进入可操作场景。
