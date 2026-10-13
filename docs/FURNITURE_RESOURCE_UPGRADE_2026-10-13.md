# 家园等级表对齐当前制作优化

代码支持等级减时后，重新核对实际开发资源：10 阶已经有 `MBABBBPOFFM: 14400`，但旧容量列 `HMEPEMLDHII` 为 10，且缺少 `UI_HOMEWORLD_LEVELFUNCTION_REDUCE_TIME`。
上一批文档中“没有新版减时值”的描述已修正。

## 资源改动

开发目录 `D:\Projects\Experiment\LunaGC-Resources` 的 `ExcelBinOutput/HomeworldLevelExcelConfigData.json` 只改 10 阶两处：

- 制作容量从 10 改为 15。
- 插入减时功能标记。

保留现有四小时减时值、奖励、信任经验、家园货币容量等数值。1–9 阶完全保持。
固定依据为[官方制作优化说明](https://genshin.hoyoverse.com/en/news/detail/163454)和[固定 7.1 完整源表](https://github.com/iam-akuzihs/excel/blob/ca385db35654e0278861e791005da7aed32b1a81/v7.1.0-9587d1afbd/ExcelBinOutput/HomeworldLevelExcelConfigData.json)。
归一化旧容量列名、确认旧附加列是零，并归一化省略的零经验后，完整十阶与固定 7.1 表一致。

资源仓库已本地提交；主项目中的 `resources` 从同一开发仓库快进到同一提交。
本地开发路径和 Compose 子模块因此使用同一版本。资源发布到 GitHub 时应先推资源提交，再推主项目引用。
本批只做本地提交，没有执行推送，也没有重启现有服务。

## 验证

新增 5 项资源测试，覆盖实际十阶表、15 槽、已有四小时减时、功能标记和完整源表一致性。
更新现行资源容量断言；旧十槽兼容测试改用完整 6.0 夹具，仍保留旧数据的覆盖。
最初测试源码使用了当前 Gson 不支持的方法，编译错误保存在 `baseline-compile-error.log`；修正测试源码后再执行对照，没有将该错误计为缺陷复现。
旧资源对照为 **3 项失败、2 项通过**，错误和跳过为零。

最终 **1,287 项／57 套件**关联回归与打包通过，失败、错误、跳过均为零。
1,014 处输入全部匹配；本批没有变化运行类。
JAR SHA-256 沿用上一批：`b64ba5d1cf08902caae533aedca4d0209f282bd56550aa32a5f72b05aa53f110`。
资源更新前原始 SHA-256：`404aae1577c359bc66aac49a56e386632b9701c4689446d9f15bca971f916140`。
开发目录更新后原始 SHA-256：`549137fee7195366b0fa31e4f385f80eae2e0ac0ea6053ac010102c8108f55d2`。
子模块检出可能因 Git 换行设置使用 CRLF；完整 JSON 和 Git 对象一致。
证据位于 `debug/aster-mid-20261011/batch180/`。

新版槽位与减时数值已在实际资源读取测试中确认。原生制作查询请求、开始响应配对和界面操作仍待验证。
