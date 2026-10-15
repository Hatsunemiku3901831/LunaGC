# 小道具放置请求与响应

## 当前客户端字段

本轮继续使用固定哈希校验的当前 7.1 客户端，只读取 executable、global-metadata 和 startup-metadata。
radare2 后续调用返回 `Transport closed`，核对改用已有的 Capstone 提取器。

| 消息 | 原生类型 | 包号 | 字段 |
| --- | --- | --- | --- |
| WidgetDoBagReq | 88144 | 27964 | material_id=2；location_info=623；widget_creator_info=1378 |
| WidgetDoBagRsp | 68223 | 21894 | material_id=13；retcode=15 |
| UseWidgetCreateGadgetReq | 58369 | 20020 | material_id=4；pos=14；rot=15 |
| UseWidgetCreateGadgetRsp | 41419 | 7173 | retcode=5；material_id=11 |
| WidgetCreatorInfo | 63045 | 内嵌消息 | op_type=1；entity_id=13；location_info=15 |
| WidgetCreateLocationInfo | 85756 | 内嵌消息 | pos=2；rot=12 |

保留已经核对正确的背包请求及内嵌字段，替换通用放置请求沿用的旧字段，
补回两个响应的正包号及当前字段。背包响应携带实际 material_id，不再发送空成功消息。

## 配对依据

- 原生 `AEGNNPENLNM` 方法读取上述四个包号；读取／写入方法独立给出字段号及存储偏移。
- 通用放置请求的原生复制器 `0x14a19ae50` 与创建器模块 61038 的发送器
  `0x152d3b1d0` 使用同一类型槽 `0x1457e1760`。
  该模块的返回处理器 `0x152d3b5f0` 接收 41419，读取返回码并处理错误。
- 背包请求复制器 `0x150340ce0` 与小道具模块 84270 的发送器
  `0x1502c17c0` 使用同一类型槽 `0x1457e5190`，同时构造位置／创建器两种分支。
  该模块的 68223 返回处理器 `0x1502bdea0` 检查返回码，并把实际物品传入背包使用计数更新
  `0x1518de6e0`。用途配对依据包括这段请求和返回流程。
- 15666 与 46725 也含有 material_id 和 retcode；前者更新其他小道具模块，
  后者发出 WidgetBackGroundStateChanged 事件，均未替代成背包响应。

原始读取、消息签名、同模块调用、反汇编及断言记录在
`debug/aster-mid-20261011/batch254/widget-protocol-proof.json` 及相邻文件。
源码沿用的 SHA-256 白名单由 `tools/extract_client_type_names.py` 校验。

## 验证

新增测试独立构造原生字段，不从生成描述符取出期望值。修复前 6 项中 5 项失败，
保留已正确的背包内嵌请求作为兼容检查；修复后相关 **59 项／5 套件**全部通过，
零失败、错误或跳过，包含原有装备、随行、资源及隔离 Mongo 保存测试。

JAR 构建、修改源码的宿主机／容器哈希、生成类与实际 JAR 字节核对通过。
修改包号导致 `PacketUseWidgetCreateGadgetRsp` 的内联常量重新编译，核对时单独记录该未修改源码依赖。
JAR SHA-256：`676c1191d17221578421ce3ea8336c72a8230612b60fa933a62903bf05a6dc35`。

本记录覆盖协议适配；原处理器的载具类型、回收操作、归属和冷却仍需后续修复。
日常运行服务未替换，尘歌壶与烟花筒等当前客户端画面和完整交互尚未验收。
