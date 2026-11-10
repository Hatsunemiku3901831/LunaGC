# E-01 / E-01d1 旧号主角突破行真实 Java 加载

选定旧号的固定存档中，主角 10000007 为 level 100、phase 6。[目标突破证据](CLIENT_72_D02_PROMOTE_RESOURCE.md) 将该角色绑定到原 group 12。固定 889 行报告中唯一 12/6 行含完整 typed 数组。测试重算 87,706 B 原 payload 及此行区间 `[2670, 2776)` 的 SHA-256，按已批准字段构造仅供测试的独立资源输入。

| 验收 | 实际结果 |
| --- | --- |
| Java 复合键 | 真实 `ResourceLoader.loadFromResource` 将唯一行放入独立 map，键 3078 |
| 属性 | 依次解析 BASE_HP、BASE_DEFENSE、BASE_ATTACK、ATTACK_PERCENT，float32 bits 分别为 `454be5f3`、`434cc000`、`427edf70`、`3e75c28f` |
| 材料 | 保留三个正 ID 和原 count 6、60、24；源零 ID 槽的 count 20 保持到 Java `onLoad`，之后由该方法过滤 |
| 隔离 | 便携 JDK17、离线单 worker、独立目录；只走单资源 loader，`loadedAll` 保持原值。XML 1 项，失败、错误、跳过均为 0 |

复用[出生测试的 worker 隔离方式](CLIENT_72_D02_C_RESOURCE_LOAD_20261110.md)，令 worker 的 `d02.oldPromote.source` 指向核对后的 `local/migration72`，选择 `emu.grasscutter.data.D02OldTravelerPromoteResourceLoadTest`。本次 init、Gradle 日志、class-init 和 XML 在 `local/migration72/d02-resume-20261111-asc/c-traveler-promote/java1/`。实际 Gradle 31 秒、exit 0。

`Avatar.recalcStatProperties` 按组和保存 phase 读取 `addProps`。本次确认该行的 Java 加载结果。实际角色属性重算、三个材料的目标资源身份、未映射升级字段、其余 120 角色及完整旧号恢复待验。入场属性重算不会查询这三项升级材料。没有生产资源变更。
