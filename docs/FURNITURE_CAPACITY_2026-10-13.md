# 家具制作容量的资源字段兼容

此前原始资源缺少明文 `furnitureMakeSlotCount`，启动和状态查询返回配置错误。
本批依据同一发布源的完整版本表恢复字段含义，再接入已确认的启动与状态路径。

## 字段依据

固定数据来源为 [iam-akuzihs/excel](https://github.com/iam-akuzihs/excel/tree/ca385db35654e0278861e791005da7aed32b1a81)，提交 `ca385db35654e0278861e791005da7aed32b1a81`。

- [5.4 完整表](https://github.com/iam-akuzihs/excel/blob/ca385db35654e0278861e791005da7aed32b1a81/v5.4.0-dee14cc45e/ExcelBinOutput/HomeworldLevelExcelConfigData.json)使用明文容量，1–10 阶依次为 1–10。
- [6.0 完整表](https://github.com/iam-akuzihs/excel/blob/ca385db35654e0278861e791005da7aed32b1a81/v6.0.0-4f9f22c884/ExcelBinOutput/HomeworldLevelExcelConfigData.json)只把该列改为 `HMEPEMLDHII`；排除新增零值列 `HKPNMEHOIGF`，归一化末阶省略的零经验后，十行其余字段全部一致。
- [7.1 完整表](https://github.com/iam-akuzihs/excel/blob/ca385db35654e0278861e791005da7aed32b1a81/v7.1.0-9587d1afbd/ExcelBinOutput/HomeworldLevelExcelConfigData.json)对应列为 `HALLBIKHGGJ`，1–9 阶依次为 1–9，10 阶变为 15。
  当前原生类型 25119 同时包含 `HALLBIKHGGJ` 和该表的其他混淆字段，与资源结构对应。

已将三份完整源表与原始 SHA-256、固定 URL 保存为测试夹具。完整相邻表的对应关系支持字段还原，依据不再只是数值与等级相同。
`HKPNMEHOIGF` 没有作为容量使用。

## 读取与验证

容量依次选用明确的明文字段、7.1 别名、旧别名。分别保存字段是否出现，结果不受 JSON 顺序影响。
明文零值或负值保持，启动和查询仍按原规则拒绝无效容量，不偷偷回退到别名。
没有任何字段时仍为未知容量。

新增 23 项，涵盖当前实际资源的全部十阶、完整历史表逐行对应、7.1 十阶数据、别名冲突顺序、显式零值和真实队列容量。
当前原始资源确实能建立 1 阶的一槽和 10 阶的十槽队列；7.1 夹具建立十五槽队列，下一次启动均返回已满。
没有覆盖原始资源数值；当前资源 10 阶仍为 10，使用 7.1 表时为 15。

固定上一包对照 **17 项失败、6 项通过**，错误和跳过均为 0。
最终 **1,124 项／51 套件**关联回归与打包通过，失败、错误、跳过均为 0。
995 处输入与 1 个变化运行类一致，无新增、删除或非预期变化。
JAR SHA-256：`0bbb7bcbccebe57a863c147651985e100617f2352c287bf870beaad64bcaa540`。
证据位于 `debug/aster-mid-20261011/batch174/`。

查询请求映射、开始响应候选配对、7.1 制作时间缩短及原生界面仍待适配。
本批没有重启日常服务或验证游戏界面。
