# F-03d3s2 / SCENE-GITHUB-INCREMENT：有界外部对照

2026-11-11；结论 **partial，有外部同名配置候选，但没有目标注册或生命周期实现许可**。固定 AnimeGameData 和 AstaPS 资源的 Scene World 文件都含顶层 `MPLevel` 对象；这比只有同名字符串更具体，但文件路径和顶层名称不能证明目标 type70954 的实际分组 key、原 `string[]`、配置命中或组件行为。

## README 优先及来源边界

| 来源与筛选 | 固定版本及许可 | 与本问题的关系 |
| --- | --- | --- |
| [AstaPS-Resource README](https://github.com/MeChen618/AstaPS-Resource/blob/6cfecfb55f889f8e3b7156adb12809608db4f74b/README.md) 声称 7.1 基线增量更新角色、怪物、场景点和能力，允许继续检查资源，不代表完整目标版本 | main `6cfecfb55f889f8e3b7156adb12809608db4f74b`，根 tree `3cf83488a94b47a018934e5f91c1bf7e93357aae`，README blob `42c45a8f83fffbed61edb6122b96c051b8a3ae22`。旧固定 LICENSE blob `337856c369eb467b1bbf5810ca61cbb8975c6c1f` 为 MIT；游戏数据权利链与目标版本仍须另证。 | 外部 clone 已有该 main；旧分支 commit `9f1ae21ea6b4610fca4fde8484ae4316cb3860b1` 的根 tree 相同，故这两分支不形成不同资源样本。分支名称不能证明目标身份。 |
| [AnimeGameData 固定 README](https://github.com/DimbreathBot/AnimeGameData/blob/d54f6f0d423ded9d055bb740a3e5f3aa8f2667f1/README.md) 声称发布 ExcelBinOutput、BinOutput、TextMap 数据 | 既有固定 commit `d54f6f0d423ded9d055bb740a3e5f3aa8f2667f1`、根 tree `5b334e711319726c504da95dbfe6ebb26baaa7d3`，README blob `c00840742e1979ca9c3739d4f3560d84ae55aaa7`。提交文字带 CN 7.2 标签，但既有研究已指出标签 revision 与本机内置 revision 不同，运行时 revision 未闭。所审根 tree 没有 LICENSE 文件；未确认数据许可。 | 这是已有固定 Scene 候选，允许检查目标相关数据，不因标签或文件存在就认定与本客户端同源。 |
| [hk4e-patch-universal README](https://github.com/KitsukiDev/hk4e-patch-universal/blob/2b589125874b0b96f8694bfad31e34811eba8573/README.md) 描述 HTTP、安全和定位钩子，没有 World/Scene 组件生命周期线索 | main `2b589125874b0b96f8694bfad31e34811eba8573`，根 tree `822e0b73e04a6ec63461e0d7ce87c923f7e5e964`，README blob `ed9483283b503080cb217806bb447443b4beeb62`，LICENSE blob `07d7288fe61f7bd217ecd661b13dd5324fbe1b48` 为 GPLv3；only-or-later 未核定。 | README 筛除，本轮不扫描代码、不移植 patch 或改变验签。 |
| [Genshin-Reverse README](https://github.com/RinoPaw/Genshin-Reverse) 提到 scene-handler slots 和研究工件 | 可变 main README 自述维护 7.1.0-global 精确样本、Apache-2.0；本轮未固定 commit、未审代码/分支。 | 有方法背景，但 README 没给出 7.2 目标 World 组件生命周期或 MPLevel 注册数据，因此不列为可融合实现。 |

## 固定文件和逐项对照

以下文件均是 `BinOutput/Gadget/ConfigGadget_Scene_World.json`，只按实际枚举的固定树路径获取一个必要文件；不是对目标资源路径的推导。表中行号对应文件原文。

| 来源 | Git tree / blob 与 SHA-256 | 实际结果 |
| --- | --- | --- |
| [Anime 固定文件](https://github.com/DimbreathBot/AnimeGameData/blob/d54f6f0d423ded9d055bb740a3e5f3aa8f2667f1/BinOutput/Gadget/ConfigGadget_Scene_World.json#L2) | BinOutput tree `c3bbf06db3dcff9d81549031a62a4d9c462f5163`；Gadget tree `dfe31760191a96d0846e2a06ab57c954d180bcaa`；blob `14197a3147d49a8b2e574c2118dc0c1ae7c11b04`；18,542 B，SHA-256 `eaf0249642e3af55193e13d4ed97fba70f7331b9f44c30cb2890c580d1262b32`。 | 第 2–14 行顶层 `MPLevel` 只有 `combat` 和 `combatTemplate` 两个直接成员。该内容是候选资源，不是目标解析结果。 |
| [Asta 固定文件](https://github.com/MeChen618/AstaPS-Resource/blob/6cfecfb55f889f8e3b7156adb12809608db4f74b/BinOutput/Gadget/ConfigGadget_Scene_World.json#L5657) | BinOutput tree `48c4d1b7a1b202bc206c7779a3ac051544f59a20`；Gadget tree `b41b8ca8990812ba7ce95e52cf2d6de0d011a754`；blob `8ec5c4e976d281864185361bb6fe7b4c39fd7dbc`；211,193 B，SHA-256 `bce778297f0a0aad030b754870f260a0278cb01e294dd7205bdefb2369d7a3b4`。 | 第 5657–6063 行顶层 `MPLevel` 有 32 个直接成员，含大量混淆名称。不能凭相似值给它们追加别名。 |
| 本地现有同名文件，只读 | 243,474 B，SHA-256 `7f4e9c25160703aaad7c4e2426c1eff094c82a49fe1007a83e508e8c5290e43f`。 | `MPLevel` 有 37 个直接成员；三份 `MPLevel` JSON 对象两两不相等，不能覆盖或视为等价。 |

检查拒绝重复 JSON key，并分别按原始字节 SHA-256 与 Git blob SHA-1 固定文件。Anime 根、BinOutput、Gadget 三层 tree 从非截断条目重算并与旧固定 commit→根的来源记录对齐；Asta 树路径由已有外部 clone 的本地对象与旧 tree 快照核对。三份对象递归遍历均未出现字面含 `abilit` 的 JSON key，**这不证明能力为空**：混淆成员、其他数据和自然消费均未闭。文件中的 `MPLevel`、目标 row `+58` 的 `MPLevel` 只是名称一致，不证明目标会加载这些文件或把任何对象作为相同类型。

## 可复用与缺口

- **候选证据**：两份固定原文件可作为后续比对样本，供独立目标消费者或真实配置输入出现后逐字段核验；未复制到运行资源。
- **仅背景/不适用**：旧 7.1 的研究方法、Asta 与本地的已实现同名能力、patch 的网络功能均不证明 7.2 客户端的 World 创建、已有实体替换/移除或 slot114 消费；没有可直接合并的代码。
- **仍未知**：目标 type70954 中包含 `MPLevel` 的完整分组 key 和原 `string[]`、实际配置对象及其 revision、能力内容、组件的创建/替换/拆除及通知时序。`Default_Item` 职责仍未知。不得用外部路径、相同 key、空能力或缺省推断补齐。

本轮逐查询/URL 保守计 **12 次网络请求**，详细请求、原响应和失败在忽略目录 `local/migration72/d02-resume-20261110b/scene-github/`。首批四词结果因本地序列化错误未保留；Asta 分支响应已保存但 Link header 读取错误导致 HTTP 状态/分页未记录，两处均未重试，不声称完整覆盖。只核 main 与相同树的 7.2 名分支，未审其他分支及 fork，没有新 clone 或执行上游代码。

私有 `inspect_scene_sources.py`（SHA-256 `396502e4bc113ccf1dcb3e2d173f0ff61857037a17351ba910654a2a2c6377c9`）离线重算三层 Anime tree、两个原文件 blob/hash，核本地文件 hash 并比较三个 JSON 对象；两次运行的 `inspection.json` 均为 SHA-256 `330be5e22b8acf306e30b8f06b372a33bdbab96f23b16f1dfadeac09733765c1`。仅文档和私有离线研究，无构建、部署、服务、客户端、原生执行或新增客户端验收。本卡不提交；主线可在复核后按独立目标证据继续确认实际分组输入及 slot114 对应 consumer。

主线2026-11-11确认原审计脚本SHA后，仅将输出文件名改为新第三原件运行，`inspection-main-third.json` SHA `330be5e22b8acf306e30b8f06b372a33bdbab96f23b16f1dfadeac09733765c1` 与双跑一致；旧脚本/原inspection未改。仅接纳候选对照与已列有限覆盖，不新增目标语义、hook或sender许可。
