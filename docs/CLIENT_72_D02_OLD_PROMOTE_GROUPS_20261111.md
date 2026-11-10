# E-01d1 旧号实际 AvatarPromote 组与 phase

从[固定旧号](CLIENT_72_D02_OLD_ACCOUNT_SNAPSHOT_20261110B.md)取全部 121 个保存角色与阶段。复用同一固定载体、实际 Avatar 原表、原 parser 和 [group consumer](CLIENT_72_D02_PROMOTE_RESOURCE.md)。原表 169 行到 EOF，行跨度连续。121 次准确原切片逐一经过实际 Avatar cache 取行、`+0x178` 及原 getter。121 个不同组均与固定 889 行的 phase 6 唯一行匹配，缺键 0。主角 12/6 的原区间 `[2670,2776)` 与[真实 Java 单行](CLIENT_72_D02_OLD_TRAVELER_PROMOTE_20261111.md)一致。

主线独立重跑 10 项测试与 CLI，报告 639,423 B，SHA-256 `e500009492028183179eed579b7e5f85f6883fe191d5efee546eb914bf584e4c`，同最终两个 fresh。原 parser 与选定读出 665,296 hook，121 切片 15,543，合计 680,839 / 2,000,000。测试覆盖重复、缺键、来源漂移、归属、全跨度和预算。五 stock 前后哈希一致。

```powershell
python -X utf8 -B -m unittest tools.test_client72_d02_old_promote_groups -v
python -X utf8 -B -m tools.client72_d02_old_promote_groups --client $env:GAME_PATH --output <新文件名>.json
```

后续由 121 个确切原跨度选择属性和材料数组。完整字段、Java 全体加载、实际属性重算及旧号恢复保持待验。
