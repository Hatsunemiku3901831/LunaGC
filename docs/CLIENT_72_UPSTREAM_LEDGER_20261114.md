# 上游检索账本（UP-72-20261114）

配套[研究报告](CLIENT_72_UPSTREAM_SURVEY_20261114.md)、[分支表](CLIENT_72_UPSTREAM_BRANCHES_20261114.md)、[文件清单](CLIENT_72_UPSTREAM_SOURCES_20261114.md)。时间均为 UTC；本机原始响应与完整缓存保留在外置研究根，仓库仅留脱敏摘要。

## 规则和限制

GitHub REST 仓库搜索限定 `is:public fork:true in:name,description,readme`，每页 100；分别采用默认相关性与 `sort=updated`。归档另以 `archived:true` 查询。GitHub 文档说明[分页使用 Link](https://docs.github.com/en/rest/using-the-rest-api/using-pagination-in-the-rest-api) 和[搜索最多返回 1000 项、可能 incomplete](https://docs.github.com/en/rest/search/search#search-repositories)。列表实际按页取到末页；搜索按返回 total 计算页数并核实实际取得页，结果超过 1000 时按创建日期拆分。搜索结果可能动态变化，本表是当时快照，不宣称全网穷尽。

下表“缺页”是当时返回 total 对应的页；`>1000` 的父项仅作拆分入口，不将其第一页计作完整搜索。创建日期跨 2008–2026 的初次拆分仍可能超过 1000，须继续递归拆分；`incomplete_results=true` 的条目也必须重新缩小。前期一次未加 `is:public` 的探索数据未用于候选或结论。

## GitHub 仓库搜索实际账本

| 查询（均追加公开、Fork、名称/描述/README 限定） | 排序 | UTC 首次 | 总数 | 已成功页 | 状态 / 继续入口 |
| --- | --- | --- | ---: | --- | --- |
| `AstaPS 7.2` | relevance | `2026-11-14T04:23:20.812571+00:00` | 30 | 1 | 已取完当时页数 |
| `AstaPS 7.2` | updated | `2026-11-14T04:23:24.229837+00:00` | 30 | 1 | 已取完当时页数 |
| `AstaPS` | relevance | `2026-11-14T04:35:45.949053+00:00` | 595 | 1,2,3,4,5,6 | 已取完当时页数 |
| `AstaPS` | updated | `2026-11-14T04:36:30.033479+00:00` | 595 | 1,2,3,4,5,6 | 已取完当时页数 |
| `AvatarDataNotify 7.2` | relevance | `2026-11-14T04:32:35.114583+00:00` | 0 | 1 | 已取完当时页数 |
| `AvatarDataNotify 7.2` | updated | `2026-11-14T04:32:37.853057+00:00` | 0 | 1 | 已取完当时页数 |
| `ExcelBin 7.2` | relevance | `2026-11-14T04:32:45.931883+00:00` | 0 | 1 | 已取完当时页数 |
| `ExcelBin 7.2` | updated | `2026-11-14T04:32:48.557702+00:00` | 0 | 1 | 已取完当时页数 |
| `Genshin server 7.2` | relevance | `2026-11-14T04:23:27.429913+00:00` | 3130 | 1 | 超过上限；继续按日期/条件拆分 |
| `Genshin server 7.2` | updated | `2026-11-14T04:25:59.679859+00:00` | 3130 | 1 | 超过上限；继续按日期/条件拆分 |
| `Genshin server 7.2 created:2008-01-01..2023-12-31` | relevance | `2026-11-14T04:23:32.620520+00:00` | 1128 | 1 | 超过上限；继续按日期/条件拆分 |
| `Genshin server 7.2 created:2008-01-01..2023-12-31` | updated | `2026-11-14T04:26:04.655729+00:00` | 1128 | 1 | 超过上限；继续按日期/条件拆分 |
| `Genshin server 7.2 created:2024-01-01..2024-12-31` | relevance | `2026-11-14T04:23:38.276877+00:00` | 714 | 1,2,3,4,5,6,7,8 | 已取完当时页数 |
| `Genshin server 7.2 created:2024-01-01..2024-12-31` | updated | `2026-11-14T04:26:11.578056+00:00` | 714 | 1,2,3,4,5,6,7,8 | 已取完当时页数 |
| `Genshin server 7.2 created:2025-01-01..2025-12-31` | relevance | `2026-11-14T04:24:29.632943+00:00` | 785 | 1,2,3,4,5,6,7,8 | 已取完当时页数 |
| `Genshin server 7.2 created:2025-01-01..2025-12-31` | updated | `2026-11-14T04:26:51.075950+00:00` | 785 | 1,2,3,4,5,6,7,8 | 已取完当时页数 |
| `Genshin server 7.2 created:2026-01-01..2026-06-30` | relevance | `2026-11-14T04:25:24.002408+00:00` | 339 | 1,2,3,4 | 已取完当时页数 |
| `Genshin server 7.2 created:2026-01-01..2026-06-30` | updated | `2026-11-14T04:27:31.583625+00:00` | 339 | 1,2,3,4 | 已取完当时页数 |
| `Genshin server 7.2 created:2026-07-01..2026-12-31` | relevance | `2026-11-14T04:25:49.534624+00:00` | 164 | 1,2 | 已取完当时页数 |
| `Genshin server 7.2 created:2026-07-01..2026-12-31` | updated | `2026-11-14T04:27:51.051542+00:00` | 164 | 1,2 | 已取完当时页数 |
| `Grasscutter 7.2` | relevance | `2026-11-14T04:22:44.925854+00:00` | 265 | 1,2,3 | 已取完当时页数 |
| `Grasscutter 7.2` | updated | `2026-11-14T04:22:59.331531+00:00` | 265 | 1,2,3 | 已取完当时页数 |
| `Grasscutter` | relevance | `2026-11-14T04:32:51.254136+00:00` | 8333 | 1 | 超过上限；继续按日期/条件拆分 |
| `Grasscutter` | updated | `2026-11-14T04:35:03.563678+00:00` | 8333 | 1 | 超过上限；继续按日期/条件拆分 |
| `Grasscutter created:2008-01-01..2023-12-31` | relevance | `2026-11-14T04:32:57.395491+00:00` | 6627 | 1 | 超过上限；继续按日期/条件拆分 |
| `Grasscutter created:2008-01-01..2023-12-31` | updated | `2026-11-14T04:35:08.724473+00:00` | 6627 | 1 | 超过上限；继续按日期/条件拆分 |
| `Grasscutter created:2024-01-01..2024-12-31` | relevance | `2026-11-14T04:33:02.475930+00:00` | 744 | 1,2,3,4,5,6,7,8 | 已取完当时页数 |
| `Grasscutter created:2024-01-01..2024-12-31` | updated | `2026-11-14T04:35:14.830366+00:00` | 744 | 1,2,3,4,5,6,7,8 | 已取完当时页数 |
| `Grasscutter created:2025-01-01..2025-12-31` | relevance | `2026-11-14T04:33:55.529536+00:00` | 342 | 1,2,3,4 | 已取完当时页数 |
| `Grasscutter created:2025-01-01..2025-12-31` | updated | `2026-11-14T04:35:55.135305+00:00` | 342 | 1,2,3,4 | 已取完当时页数 |
| `Grasscutter created:2026-01-01..2026-06-30` | relevance | `2026-11-14T04:34:19.679544+00:00` | 207 | 1,2,3 | 已取完当时页数 |
| `Grasscutter created:2026-01-01..2026-06-30` | updated | `2026-11-14T04:36:14.510245+00:00` | 207 | 1,2,3 | 已取完当时页数 |
| `Grasscutter created:2026-07-01..2026-12-31` | relevance | `2026-11-14T04:34:35.103758+00:00` | 413 | 1,2,3,4,5 | 已取完当时页数 |
| `Grasscutter created:2026-07-01..2026-12-31` | updated | `2026-11-14T04:36:27.322335+00:00` | 413 | 1,2,3,4,5 | 已取完当时页数 |
| `HunkyMeow` | relevance | `2026-11-14T04:37:04.067920+00:00` | 57 | 1 | 已取完当时页数 |
| `HunkyMeow` | updated | `2026-11-14T04:37:08.649324+00:00` | 57 | 1 | 已取完当时页数 |
| `LunaGC 7.2` | relevance | `2026-11-14T04:23:13.763294+00:00` | 36 | 1 | 已取完当时页数 |
| `LunaGC 7.2` | updated | `2026-11-14T04:23:17.478006+00:00` | 36 | 1 | 已取完当时页数 |
| `LunaGC` | relevance | `2026-11-14T04:34:48.499276+00:00` | 480 | 1,2,3,4,5 | 已取完当时页数 |
| `LunaGC` | updated | `2026-11-14T04:35:18.741559+00:00` | 480 | 1,2,3,4,5 | 已取完当时页数 |
| `PlayerEnterSceneNotify 7.2` | relevance | `2026-11-14T04:32:29.715241+00:00` | 0 | 1 | 已取完当时页数 |
| `PlayerEnterSceneNotify 7.2` | updated | `2026-11-14T04:32:32.341680+00:00` | 0 | 1 | 已取完当时页数 |
| `ProudSkill server` | relevance | `2026-11-14T04:32:40.566537+00:00` | 1 | 1 | 已取完当时页数 |
| `ProudSkill server` | updated | `2026-11-14T04:32:43.266076+00:00` | 1 | 1 | 已取完当时页数 |
| `YuanShen server` | relevance | `2026-11-14T04:37:12.810255+00:00` | 1518 | 1 | 超过上限；继续按日期/条件拆分 |
| `YuanShen server` | updated | `2026-11-14T04:39:24.013488+00:00` | 1518 | 1 | 超过上限；继续按日期/条件拆分 |
| `YuanShen server created:2008-01-01..2023-12-31` | relevance | `2026-11-14T04:37:19.442059+00:00` | 596 | 1,2,3,4,5,6 | 已取完当时页数；历史失败已留原件 |
| `YuanShen server created:2008-01-01..2023-12-31` | updated | `2026-11-14T04:39:29.343419+00:00` | 596 | 1,2,3,4 | 缺页 5,6 |
| `YuanShen server created:2024-01-01..2024-12-31` | relevance | `2026-11-14T04:38:11.534509+00:00` | 212 | 1,2,3 | 已取完当时页数 |
| `YuanShen server created:2025-01-01..2025-12-31` | relevance | `2026-11-14T04:38:28.451620+00:00` | 140 | 1,2 | 已取完当时页数 |
| `YuanShen server created:2026-01-01..2026-06-30` | relevance | `2026-11-14T04:38:40.514121+00:00` | 168 | 1,2 | 已取完当时页数 |
| `YuanShen server created:2026-07-01..2026-12-31` | relevance | `2026-11-14T04:38:52.553668+00:00` | 402 | 1,2,3,4,5 | 已取完当时页数 |
| `hk4e server` | relevance | `2026-11-14T04:28:00.409973+00:00` | 913 | 1,2,3,4,5,6,7,8,9,10 | 已取完当时页数 |
| `hk4e server` | updated | `2026-11-14T04:29:05.348217+00:00` | 913 | 1,2,3,4,5,6,7,8,9,10 | 已取完当时页数 |
| `原神 7.2 服务端` | relevance | `2026-11-14T04:29:54.915439+00:00` | 1100 | 1 | 超过上限；继续按日期/条件拆分 |
| `原神 7.2 服务端` | updated | `2026-11-14T04:31:21.660199+00:00` | 1100 | 1 | 超过上限；继续按日期/条件拆分 |
| `原神 7.2 服务端 created:2008-01-01..2023-12-31` | relevance | `2026-11-14T04:30:01.836781+00:00` | 865 | 1,2,3,4,5,6,7,8,9 | 已取完当时页数 |
| `原神 7.2 服务端 created:2008-01-01..2023-12-31` | updated | `2026-11-14T04:31:26.426632+00:00` | 865 | 1,2,3,4,5,6,7,8,9 | 已取完当时页数 |
| `原神 7.2 服务端 created:2024-01-01..2024-12-31` | relevance | `2026-11-14T04:31:02.560948+00:00` | 25 | 1 | 已取完当时页数 |
| `原神 7.2 服务端 created:2024-01-01..2024-12-31` | updated | `2026-11-14T04:32:12.701924+00:00` | 25 | 1 | 已取完当时页数 |
| `原神 7.2 服务端 created:2025-01-01..2025-12-31` | relevance | `2026-11-14T04:31:06.025782+00:00` | 82 | 1 | 已取完当时页数 |
| `原神 7.2 服务端 created:2025-01-01..2025-12-31` | updated | `2026-11-14T04:32:16.075260+00:00` | 82 | 1 | 已取完当时页数 |
| `原神 7.2 服务端 created:2026-01-01..2026-06-30` | relevance | `2026-11-14T04:31:11.271729+00:00` | 52 | 1 | 已取完当时页数 |
| `原神 7.2 服务端 created:2026-01-01..2026-06-30` | updated | `2026-11-14T04:32:21.035173+00:00` | 52 | 1 | 已取完当时页数 |
| `原神 7.2 服务端 created:2026-07-01..2026-12-31` | relevance | `2026-11-14T04:31:16.347027+00:00` | 76 | 1 | 已取完当时页数 |
| `原神 7.2 服务端 created:2026-07-01..2026-12-31` | updated | `2026-11-14T04:32:25.011037+00:00` | 76 | 1 | 已取完当时页数 |

查询中 `YuanShen server`、`created:2008-01-01..2023-12-31`、相关性第 6 页首次返回 `gh: Server Error (HTTP 502)`；原失败保留，随后同页重试成功，返回 96 条。此前一次研究脚本的搜索不带公开限定，已停止且未作为本报告候选来源。

## 归档项目搜索

均使用公开、Fork、名称/描述/README 和 `archived:true`。
- `archived Grasscutter 7.2 relevance`：第 1 页，返回 3 / total 3，incomplete=false；`2026-11-14T04:37:39.305692+00:00`。
- `archived Grasscutter 7.2 updated`：第 1 页，返回 3 / total 3，incomplete=false；`2026-11-14T04:37:42.952256+00:00`。
- `archived LunaGC 7.2 relevance`：第 1 页，返回 0 / total 0，incomplete=false；`2026-11-14T04:37:46.530781+00:00`。
- `archived LunaGC 7.2 updated`：第 1 页，返回 0 / total 0，incomplete=false；`2026-11-14T04:37:50.065849+00:00`。
- `archived AstaPS relevance`：第 1 页，返回 2 / total 2，incomplete=false；`2026-11-14T04:37:53.620776+00:00`。
- `archived AstaPS updated`：第 1 页，返回 2 / total 2，incomplete=false；`2026-11-14T04:37:57.126517+00:00`。
- `archived HunkyMeow relevance`：第 1 页，返回 0 / total 0，incomplete=false；`2026-11-14T04:38:00.612025+00:00`。
- `archived HunkyMeow updated`：第 1 页，返回 0 / total 0，incomplete=false；`2026-11-14T04:38:04.143896+00:00`。
- `archived hk4e protocol relevance`：第 1 页，返回 0 / total 0，incomplete=false；`2026-11-14T04:38:07.551432+00:00`。
- `archived hk4e protocol updated`：第 1 页，返回 0 / total 0，incomplete=false；`2026-11-14T04:38:10.981302+00:00`。

## 种子仓库枚举

| 仓库 | 分支 / Fork / 全部状态 PR / 标签 / Releases | 结果与继续条件 |
| --- | --- | --- |
| Grasscutters/Grasscutter | 8 / 4292 / 1236 / 22 / 20 | 分别分页到末页；Fork 名称、描述、默认分支做初筛，未逐个读取全部 Fork 的所有 README 和分支 |
| girluh/LunaGC | 4 / 11 / 3 / 0 / 0 | 到末页；Fork 筛选及已选候选分支见分支表 |
| MeChen618/AstaPS | 2 / 29 / 199 / 148 / 148 | 到末页；两个开放 PR 已登记，标签/发布清单已枚举但未逐项深审 |
| AzureXuanVerse/HunkyMeow | 1 / 0 / 0 / 0 / 0 | 到末页 |
| MeChen618/AstaPS-Resource | 3 / 12 / 20 / 0 / 0 | 到末页；资源 Fork 未全部逐分支审计 |
| capybr2222/LunaGC_7.2.0 | 5 / 2 / 0 / 1 / 1 | 到末页；两个服务端 Fork 均检查分支；`latest` 标签当时指向 `acc7a0b48f44f3d51666c91f1125e8e1ee923ca4`，release 只算声明 |
| capybr2222/LunaGC-Resources | 1 / 1 / 0 / 0 / 0 | 到末页；唯一资源 Fork 尚未分支深审 |
| invoker-bot/LunaGC-Resources | 1 / 0 / 0 / 0 / 0 | 到末页，本地 resources 锁定同一提交；保留工作树差异 |
| KitsukiDev/hk4e-patch-universal | 1 / 7 / 0 / 0 / 0 | 到末页，复用既有审计；不实施 patch |
| capyb2222/LunaGC_7.1.0 | — | `gh: Not Found (HTTP 404)`；无法确认公开状态或枚举分支 |

各 GitHub 列表的 Fork 数为 API 实际返回行数，可能与仓库页面缓存计数不一致；公开可见性和排序随时间变化。19 个已纳入检查的 GitHub 仓库共 67 个分支的 tip、树、README、历史差异和抓取时间见分支表；其中仅报告列出的若干文件做了深入静态对照。仓库源码树未作完整递归读取，也没有把部分文件下载称作完整交付。

Grasscutter 的五个开放 PR 在本次清单中均未合并；按标题和公开摘要初筛，均未提供直接的国服 7.2 入场依据，未深入审计完整差异：

| PR | head 仓库及分支 | 完整 head commit | 初筛 |
| --- | --- | --- | --- |
| [#2563](https://github.com/Grasscutters/Grasscutter/pull/2563) | `tiennm99/Grasscutter:docs/add-client-download-mirror` | `a1b223ceaf3909cc3d865059d7c0907de3e5cade` | 4.0 下载说明 |
| [#2561](https://github.com/Grasscutters/Grasscutter/pull/2561) | `kobihikri/Grasscutter:ci/image-provenance-sbom` | `da7e6823f575f079bb400eef6bcf345925d0677f` | 构建产物来源/清单 |
| [#2549](https://github.com/Grasscutters/Grasscutter/pull/2549) | `sitxovski/Grasscutter:feature/traveler-birthday-gift-system` | `39d95ba9f58b02ac2f1f4423a0f9b8631dcfcb26` | 后续生日奖励玩法 |
| [#2519](https://github.com/Grasscutters/Grasscutter/pull/2519) | `brenoepics/Grasscutter:development` | `0e7e165dffe938cba03ac80dee4e0f9b5c5d1fd6` | 代码整理 |
| [#2286](https://github.com/Grasscutters/Grasscutter/pull/2286) | `Phong940253/Grasscutter:combine-patch` | `0b8f9ad8514db62f8ef7bdb30537b44dcc20b193` | 角色组合效果，后续玩法 |

## Web 和其他平台

搜索引擎查询过中文、英文的 `Grasscutter 7.2`、`Genshin server 7.2`、`hk4e server protocol`、`原神 7.2 服务端`、`PlayerEnterSceneNotify 7.2`、`AvatarDataNotify 7.2`、`ProudSkill server`、`ExcelBin 7.2`、`Genshin ... dispatch/login`、`Avatar/PlayerStore/SceneInitFinish/team/entity`、`ExcelBin/BinOutput/ability/Lua`、`combat/quest/shop/abyss/battlepass/Miliastra/UGC`、`anime game server emulator 7.2`，以及中英官方版本名。网页搜索用于发现，不是可分页的完整 GitHub 枚举。

- 国服与国际公告分别为[官方国服 7.2](https://ys.mihoyo.com/main/news/detail/167205) 和[官方英文 7.2](https://genshin.hoyoverse.com/en/news/detail/167187)。
- [GitLab 协议线索](https://gitlab.com/CarolBicsi/genshin-protocol)：网页列出 6.x；本轮未分页枚举分支或审计源码。
- [Forgejo / Gitea mavuika-rs](https://git.xeondev.com/mavuika-rs/mavuika-rs)：页面显示 Rust 服务器、一个分支、5.2.51 历史；源码和其他版本未核。
- Gitee 搜索取得历史 Grasscutter Wiki 线索，但具体仓库网页工具返回不可访问；未将它记为无结果。
- Codeberg 两个 README 关联地址读取返回 `Failed to fetch restricted URL`，停止该地址读取；未另换入口尝试。
- [hk4e-go](https://github.com/Kei-Luna/hk4e-go) README 描述 3.2、Go 多服务架构；[ProtoDumper](https://github.com/66hh/ProtoDumper) 需要非混淆程序集；[Irminsul](https://github.com/konkers/irminsul) 为玩家数据导出工具，其 [#163](https://github.com/konkers/irminsul/issues/163) 记录 7.1 包处理问题。这些均为待进一步筛选的线索，未枚举全部分支/PR，也未执行。
- [Miliastra 文件格式](https://github.com/script-1024/genshin-miliastra-file-format)、[节点编辑器](https://github.com/Columbina-Dev/WebMiliastraNodesEditor) 及[既有上游研究](UPSTREAM_RESEARCH_2026-10-03.md)提供旧玩法线索；本轮未重新完成其分支与源码审计。

## 本批未覆盖与可继续位置

- `Grasscutter` 不带版本号的 2008–2023 分片仍超过 1000；`Genshin server 7.2` 的同一区间也超过 1000；两个排序分别继续按年、月或其他不重叠条件拆分后去重。不要把父查询第 1 页当作全量。
- `YuanShen server` 更新时间排序的 2008–2023 分片从第 5 页继续；2024、2025、2026 上半年及下半年四个更新时间分片尚未执行，从各自第 1 页开始。
- 本批按执行预算停止长尾搜索；未在上表出现的预定 GitHub 查询（`hk4e protocol`、`Genshin protobuf/opcode/dispatch/resource parser/ExcelBin/ability/quest server/abyss/Miliastra/battlepass`、`Hour of Ruin server`、`坏灭肇始之时 服务端`、`原神 私服/资源解析`、`Genshin private server` 等）从各关键词的相关性与更新时间第 1 页开始；若上表已有部分分片，则从表内缺页继续。搜索引擎的命中不能替代这些 API 清单。
- 草率以星标或更新时间排除并不可取；Grasscutter 的 4292 个 Fork 已拉清单和做名称/描述/默认分支筛查，但未逐个读 README、逐个查隐藏在非默认分支的 7.2。对其他进入候选池的 Fork 仍须重复完整分支、README、tree 和 PR 检查。
- GitLab、Gitee、Codeberg、Forgejo 和论坛未做到完整分支/Fork/PR 覆盖，权限/网络限制与未执行工作如上；二手页面不作为源码和实机验收。标签和发布列表不等于逐个固定历史源码。
- 研究根中 `survey.py`、`search.py`、`search_remaining.py`、`archived.py`、原始分页 JSON、`branch_manifest*.json`、`file_manifest.json` 和 `offline-review.json` 是恢复入口；外置目录以本机实际配置为准，不提交机器路径。
