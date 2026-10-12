# 副本入口与怪物详情的 7.1 字段

核对 capyb LunaGC 7.1 固定提交 `8be69a16c1410fc4233c4872996581317c876ffe` 的兼容占位字段，
使用本机客户端的序列化／解析代码及真实字段偏移表修正原生字段。

## 原生依据

YuanShen.exe SHA-256：`7f89938da606c1281659607d464702cddb9a09a7d4320a196630f60a811ec38e`。
global-metadata.dat SHA-256：`469eccd43aa48fe7a2df4e1caf66fa1268a17f5a4a03fa209e1427e467cb0161`。
通过 Class::SetupFields `0x1405135b0` 定位类型布局索引、布局记录与字段偏移三张表，
再将元数据字段名 → 对象偏移 → 序列化标签相连，避免直接按名字或字段声明顺序猜标签。

| DungeonEntryInfo | 元数据字段 | 偏移 | 原生字段 |
| --- | --- | --- | --- |
| max_boss_chest_num | LKNNIKGEEOH | +0x20 | 4 |
| boss_chest_num | MIDGFKJJPAE | +0x30 | 10 |
| dungeon_id | MAPEPFNEDEE | +0x24 | 15 |

入口类型 PLOKNNKAHCI（44490）写入代码 `0x150cd44b0`。
进入副本请求类型 LNCHLEMPHNJ（79808）的同名 MAPEPFNEDEE 字段位于 +0x30、标签 14，
提供 dungeon_id 语义交叉检查。两个次数字段还与怪物详情同名字段相连。
副本 ID 保留既有字段 15；只将次数字段从 50000／50001 占位迁到 10／4。

怪物详情 MIJPIIBGGIM（70083）写入代码 `0x14fdc4f10`：
max_boss_chest_num=9，boss_chest_num=6，is_area_locked=1，_is_respawning=15，
refresh_interval=4，next_boss_chest_refresh_time=5。
其余 8 个已知字段保留，Vector、怪物 config 与周本折扣嵌套类型均已检查。

调查中曾按声明顺序推测副本 ID 为 10；偏移表证明该推测错误，已修正样本和实现。
最终基线与验证使用修正后的样本，早期 green.log 不作为当前正确性证据。

## 验证

新增 **11 项**，覆盖独立原生字节样本、字段描述、直接原生标签解码、实际入口回包构造、
周本次数／树脂折扣、普通副本、任务额外副本，以及怪物详情次数、状态、刷新时间和既有嵌套字段。
上一固定包正确样本对照 **9 项失败、2 项通过**。

最终 **263 项／18 套件**关联回归及打包通过，失败、错误、跳过均为 0。
806 处输入与 8 个变化运行类一致，无非预期变化或删除。
JAR SHA-256：`e84c3f1fac883ee1a1b3e1a462bac8309b7a6e762a414d8e01bc097473ec412c`。
记录位于 `debug/aster-mid-20261011/batch134/`，包含布局恢复脚本、原生偏移、汇编与校验哈希。

本批验证静态原生字段和受控回包，没有重启日常服务或验证游戏界面。
首次副本奖励仍在接入：上游标为 `_GetDungeonEntryFirstPassRewardStatusRsp` 的包号 3781
可见两组 uint32 列表和返回码，但其请求与业务调用链仍需确认，不据此注册猜测的领取入口。
