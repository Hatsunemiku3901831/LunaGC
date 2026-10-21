# 7.1 原生深渊入口、切队与继续挑战修复（2026-10-22）

点击「开始挑战」后只有背景和顶部按钮、没有楼层列表。本机复现时，03:23:35 收到 CmdId `27826`，旧服务端没有 Handler；原 `TowerAllDataReq=-87` 被注册器跳过。

修复使用本机既有 7.1 客户端指纹，未修改客户端：

- EXE SHA-256：`7f89938da606c1281659607d464702cddb9a09a7d4320a196630f60a811ec38e`。
- metadata SHA-256：`469eccd43aa48fe7a2df4e1caf66fa1268a17f5a4a03fa209e1427e467cb0161`。
- `CKMPIFJFKDH.SetupView`（`0x15024c570`）调用深渊管理器 `DADDHGKEGGE.EBLCAJANNPA`（`0x148826810`）。后者通过 TypeInfo cell `0x1457d9070` 创建 `KFKHJHAEBLC`，写入交互标记并发送。
- `extract_client_usage_types.py` 将该 cell 对应到 type 12977；getter `0x152c45860` 返回 `27826`，writer `0x152c45730` 写 tag `0x70`，即 `is_interact=14`。首次预取可发空 payload。
- 响应 `OMPOFJHDDKG` getter `0x14c66d3f0` 返回 `21818`。深渊管理器分发函数 `0x1488276e0` 的 `0x553a` 分支调用 `0x148827cd0`，消费该响应。

## 恢复的字段

字段号取自原生解析分支，对象偏移仅用来关联消费路径，不直接当作字段号。

| 消息字段 | 原占位 | 7.1 字段 | 原生证据 |
| --- | ---: | ---: | --- |
| TowerAllDataRsp.tower_schedule_id | 50000 | 4 | reader `0x14c66dd70` tag `0x20` → `+0x50`；管理器保存到排期配置查找键 `+0x100` |
| TowerAllDataRsp.next_schedule_change_time | 50002 | 6 | tag `0x30` → `+0x54` → 管理器时间 `+0xd0` |
| TowerAllDataRsp.schedule_start_time | 50009 | 1330 | tag `0x2990` → `+0x4c` → 管理器时间 `+0xe0` |
| TowerAllDataRsp.floor_open_time_map | 50005 | 14 | tag `0x72` → map `+0x18` → 管理器楼层开放时间字典 `+0x58` |
| TowerCurLevelRecord.cur_floor_id | 50001 | 9 | `IDPGHBEGBBG` reader `0x151e3ff40` tag `0x48` → `+0x30` → 当前楼层 |
| TowerCurLevelRecord.cur_level_index | 50000 | 10 | tag `0x50` → `+0x2c` → 当前间号 |

其余未恢复的 50000 段字段不在本次兼容声明中。

## 回归与动态验证

`TowerEntryProtocol71Test` 最初三个用例全部失败，修复后通过。测试用固定原生 tag 验证字段，并经真实 `GameServerPacketHandler` 和 `GameSession.send` 检查输出帧 CmdId `21818`、排期及楼层列表；仅替换传输 tunnel 和测试楼层数据，不重写发送方法。测试无登录密钥，临时关闭 XOR 后恢复配置。

Windows 便携方式重新构建、正常停止旧 GS、启动新 JAR。实际联调日志已记录：

- 03:32:32：`TowerAllDataReq (27826)` → `TowerAllDataRsp (21818)`。
- 03:33:04：原生客户端发 `TowerTeamSelectReq (20134)`，floor `1044`，两队各 4 人；随后发 `TowerEnterLevelReq (21373)`。
- 服务端开始进入 dungeon `3407` / scene `33737`，发送 `PlayerEnterSceneNotify`、`TowerCurLevelRecordChangeNotify` 和 `TowerEnterLevelRsp`；客户端完成 `EnterSceneReadyReq` 阶段。

用户确认楼层已显示、可以进入战斗。03:33:13 实际启动上半场挑战，03:33:48 完成；随后 Lua 写入公共组 `233737001` 的 `TPL_TIME` 时抛出 `UnsupportedOperationException`，未切换下半场。

## 上下半切换修复

`isPreventEntityError=true` 时，`Grid.getNearbyGroups` 跳过宽度大于 500 的格子。scene 33737 的公共组 `233737001` 和下半组 `233737004` 只落在宽度 1000 的 SUPER 格子，未被实例化；`getVariables` 返回不可写空 Map，导致上半成功回调中断。

`Scene.getPlayerActiveGroups` 对 Tower 副本完整列出本房间的静态脚本组，使 `onLoadGroup` 的第一轮初始化所有共享变量，再运行 suite 和回调。动态组继续由脚本控制，大世界仍使用原加载保护。`TowerGroupVisibilityTest` 采用实际 scene 33737 资源，在修复前复现丢失两个组；修复后验证五个组、565 秒剩余时间和 stage=1 可读写。

下半切队通知恢复为 `28798`：原生空类型 `CFHJLLMBCPO` 的 getter `0x14af7d740` 返回此号；深渊管理器分发 `0x1488276e0` 的 `0x707e` 分支调用 `0x148823030`，执行下半场切换。真实发送通道测试同时验证这个空消息没有被负数编号分支丢弃。

03:42:32 真实客户端收到 `28798`，03:42:41 点击下半机关并启动第二个挑战，03:43:13 下半成功，发出整间结算。用户确认可以切队、出怪并通关。

## 结算误报「少于六星」

第一间通关后，用户反馈不能继续。`TowerLevelEndNotify` 的 `continue_state` / `next_floor_id` 仍使用 50001 / 50000，客户端得到默认继续状态 0。

- `PPFBGNHJIPE` reader `0x14f51da20`：tag `0x40` → offset `+0x28`，tag `0x18` → `+0x30`，分别为 `continue_state=8`、`next_floor_id=3`。
- 深渊管理器 `0x1488253e0` 将 `+0x28` 保存到 `+0x114`；`InLevelTowerEndWinContext.InitPage`（`0x1506a0790`）按该值分支：0 禁止继续、1 下一间、2 下一层。`+0x30` 被保存为下一层选择 ID。
- 客户端在继续本层按钮上对当前间号加一。服务端仍将内部检查点推进一次，但结算前回报刚完成的层／间；实际进入下一间时再发送新当前间号，避免界面错报下一间和提前跳层。

`TowerContinuationProtocolTest` 的三个用例在修复前全部失败，分别覆盖原生结算 tag、第一间三颗星仍可继续第二间，以及连续三间的内部推进与客户端显示编号。修复不伪造星数、不修改已有通关成绩。

258 项深渊及副本回归通过（无失败、错误或跳过）。本次便携部署的运行 JAR SHA-256 为 `8daf00729967f60778d97347b11e5d86d6ee7d3bd329b0e40cf8bfe6c186962d`，健康接口与游戏 UDP 端口已核验。可机读结果见[验证记录](data/tower-entry-validation-2026-10-22.json)。

真实客户端复测已通过（2026-10-22，Australia/Sydney）：

- 03:57:01 第一间结算；03:57:05 原生继续请求进入第二间 dungeon `3507`。
- 03:58:05 第二间结算；03:58:10 继续进入第三间 dungeon `3607`。
- 03:58:44 第三间结算。三间都记录了下半切队通知和整间结算。
- 用户确认「能进入第二间，现在已经打完第9层进入第10层了」。验证范围为第九层连续三间通关及第十层访问；不据此宣称第十至十二层战斗或整期玩法已经验收。

祝福选择请求 `4779` 仍无完整效果处理，选队响应编号仍待确认；整期深渊和全部奖励需继续验收。
