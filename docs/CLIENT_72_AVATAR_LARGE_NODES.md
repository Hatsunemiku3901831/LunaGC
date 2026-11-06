# F-03d3e6：大标量节点与 signed list

限定 58639、64949 两类，各 18 个存储字段，经 256 次 tag1..32/wire0/1/2/5 原生扫描确认为 36 wire。完整字段/tag/offset/写点见[脱敏摘要](../protocol/cn-7.2/avatar-large-nodes-observations.json)。依赖直接复用 [e3](CLIENT_72_AVATAR_PRIMITIVE_LEAVES.md) 的 51614/48088/68918/21518 与 [e4](CLIENT_72_AVATAR_SIGNED_TREES.md) signed-list 原生路径，不扩张公共 runtime。51050 非空仍拒绝，完整复杂图/Avatar 与未知业务语义未晋级。

每次全新进程核验 `extract_client72.HASHES` 的三份固定输入，hash 与[核心](CLIENT_72_AVATAR_CORE.md)相同；重新提取 metadata、有界 usage cells、两类 reader/cctor 全部类型引用和选定 AOT，确认四个外部消息依赖且无 Object。库存摘要固定 hash 守卫保持。六条 AOT 记录是三个 message-list 具体实例的 ctor/reader：38014→51614、6112→68918、17573→21518；reader 均为 `0x158dc41f0`，实际 codec/class 检查保持。58639 cctor 实际执行到返回，signed factory 参数 146 对应 tag18/wire2；没有声称所有 ctor 均原生执行。

## 关键字段与独立非空图

58639 的 tag4 `HHAFHFEPLPD`/+30、tag5 `GFKJKLIHGNK`/+28 都是 48088。tag11 `ELLMCOFKKPA`/+20 是 list→51614，tag13 `BGGPFKNPLGL`/+40 是 list→21518，tag17 `AOMDFCPMDOP`/+38 是 list→68918，tag18 `MLKKEOCOAKN`/+18 为 int32-list/wire0,2。其余 12 个标量均有不同非零独立值，signed 使用不同负值。

64949 的 tag7 `HHAFHFEPLPD`/+28、tag8 `GFKJKLIHGNK`/+18、tag15 `EDKKGCIAMOA`/+20 是三份 48088，分别输入 (-1,2)、(-3,4)、(-5,6)。重复 tag7 只覆盖该对象第二值为22，重复 tag8 只把第一值清零，tag15 空 envelope 保留此前两个值；另验三份显式空对象，未串字段。

64949 tag2 `HOPABANFAJI`/+50、tag3 `NIHNNHMGENL`/+68、tag18 `OOKFBIPCHBO`/+48 为三个 uint64，先分别输入 2^53+1、2^63、max，再换位为 max、2^53+1、2^63；八字节结果精确保留。tag1 enum64951 仅按 metadata int32 批准 raw -1，不批准业务枚举名。

13 个正向图覆盖两类全部 36 字段非零、每个 message-list 两份不同非空元素、重复追加与独立空元素、mixed packed/unpacked signed -1/min/max/0、empty packed 保留旧值、错误 fixed32 wire 跳过、empty 与重复零。signed 元素均经过真实 Add `0x1568b8b10`，实际四字节写点 `0x1568b8bc4` 和原始位保存在摘要；未由模型直接塞最终值。

9 个拒绝明确区分：1 个 51050 范围拒绝；4 个模型输入错误（非法 tag、signed overflow、packed 截断、uint64 overflow）；4 个真实 ReadMessage 错误入口（负列表长度、截断子对象、list/singular 递归限制），只到入口而非完整 throw。managed metadata/allocation、原语 codec/delegate/input、boxing 和容量64数组仍为模型；两类 reader、所有已用子 reader/list append/nested limits 为原生执行。不声称 IFix/live ABI、Handler、实机、部署或业务接入。

8 项新增、138 项关联研究回归实际全过。双全新进程各核三 hash/重提取/独立样本，完整轨迹及摘要逐字节一致；证据 `local/migration72/evidence/avatar-large-nodes-{native,native2,summary,summary2,determinism}.json`，日志 `local/migration72/logs/avatar-large-nodes-{run1,run2,tests}.log`。仅工具/测试/摘要/本文，不需部署或重启，台账和本地提交由主线审核。

```powershell
python tools/trace_client72_avatar_large_nodes.py --client $env:GAME_PATH --output local/migration72/evidence/avatar-large-nodes-native.json --observations local/migration72/evidence/avatar-large-nodes-summary.json
python -m unittest tools.test_client72_avatar_large_nodes -v
```

下一 e7 恢复 63008/22404 的多份 message-list 与独立合并；e8 完整图仍待依赖汇合。
