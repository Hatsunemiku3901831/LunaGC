# F-03d3e7：多份 message-list 中层

限定 63008/22404 两类，29 个 metadata 存储槽经 256 次 tag1..32/wire0/1/2/5 原生扫描确认 29 wire；完整字段/tag/offset/写点见[脱敏摘要](../protocol/cn-7.2/avatar-multi-lists-observations.json)。复用 [e6 大节点](CLIENT_72_AVATAR_LARGE_NODES.md)及 [e3](CLIENT_72_AVATAR_PRIMITIVE_LEAVES.md)/[e4](CLIENT_72_AVATAR_SIGNED_TREES.md)叶子和 signed-list，不重建公共模型。51050 非空仍拒绝，完整复杂图/Avatar 与业务名、runtime 未晋级。

两个正式全新进程均重新核 `extract_client72.HASHES` 三输入，与[核心](CLIENT_72_AVATAR_CORE.md)相同，并重新提取 metadata、有界 usage cells、两类 reader/cctor 的全部类型引用及选定 AOT。外部消息依赖恰为 58639/64949/68918/38218/67498/31113，无 Object。12 条 AOT 为六个 message-list 具体实例的 ctor/reader：6112→68918、18455→58639、18456→64949、18458→67498、9680→31113、18457→63008；实际 reader 均 `0x158dc41f0`。两消息 cctor 执行到返回，codec/class 检查保持。

## 非空列表彼此独立

22404 的 tag3 `HIKFHOJMFCA`/+18、tag4 `DPPEDFMHBAI`/+30、tag18 `NNIPFIBDCEN`/+50 都是 list→58639。每份第一元素包含 e6 完整18字段，分别将其 tag1 设成101/202/303，第二个独立非空元素设102/203/304。另用交错输入只向第二份追加204、第一份追加空对象，第三份保持303，证明共享具体类型的三个容器不会串用。

22404 tag15 `JNIMENFGFHC`/+40、tag16 `GNKIDBAOAKI`/+48 同为 list→67498，分别使用 (15,16)/(17,18) 和 (25,26)/(27,28)；额外交错追加只改变目标列表。tag5→64949、tag13→63008、tag17→31113、tag22 singular→38218 都用完整非空已验向量。重复 singular 子对象合并时只清掉指定第一标量，其余不同值保留。

63008 的 tag3 `MKFKJIAMCKJ`/+28 为 list→58639，tag6 `AOMDFCPMDOP`/+20 为 list→68918，都含两份不同非空对象；tag7 `MLKKEOCOAKN`/+18 为 int32-list。22404 tag21 `DELNNOKFLAP`/+68 同为 int32-list，均独立执行 packed/unpacked 的 -1/min/max/0、empty packed 保留旧值，保留具体 Add `0x1568b8b10`/四字节 store `0x1568b8bc4`。22404 tag19 u32-list 同时覆盖0/1/max。

22404 tag1 `HOPABANFAJI`/+88、tag2 `NIHNNHMGENL`/+98 为两个 uint64，2^53+1/max 与 max/2^63 两组非零交换验证完整高位。所有新标量采用不同非零值，另验重复清零、bool 规范化和两个 empty。

14 个正向图与9个拒绝实际运行。拒绝分为1个51050研究范围拒绝、4个输入模型错误（非法tag、signed overflow、packed截断、uint64 overflow）、4个真实ReadMessage错误入口（负长度、截断、两层不同递归限额），到入口即停，不称完整throw。managed metadata/allocation、primitive codec/delegate/input、boxing和固定64数组仍为模型；两reader、子reader、list append、ReadMessage/limits实际执行，不称IFix/live ABI/Handler/实机或部署通过。

8项新增、146项关联研究回归全过；两轮完整轨迹及摘要逐字节一致。证据 `local/migration72/evidence/avatar-multi-lists-{native,native2,summary,summary2,determinism}.json`，日志 `local/migration72/logs/avatar-multi-lists-{run1,run2,tests}.log`。仅离线工具/测试/摘要/本文，不需重启；共享台账和提交由主线维护。

```powershell
python tools/trace_client72_avatar_multi_lists.py --client $env:GAME_PATH --output local/migration72/evidence/avatar-multi-lists-native.json --observations local/migration72/evidence/avatar-multi-lists-summary.json
python -m unittest tools.test_client72_avatar_multi_lists -v
```

下一 e8：51050 双Object、boxed-u32/tag100、扫描至128及完整53824/AvatarInfo/Avatar全部非空组合，不能用空壳代替依赖。
