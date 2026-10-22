# 商城与充值：数据、资源与持久化

配套：[总览](SHOP_AND_RECHARGE_SYSTEM.md) · [协议](SHOP_AND_RECHARGE_PROTOCOL.md) · [运维与验证](SHOP_AND_RECHARGE_OPERATIONS.md)。核对日期 2026-10-22；源码基线 `519eb03386d19c4a62ecd74d4a83ac02427e0ba0`。表中默认值是源码或资源值，不代表所有本机 GM 覆盖后的实际售价。

## 编号与概念

| 名称 | 例子 | 含义 |
| --- | --- | --- |
| `shopType` / `shopId` | 903、1037 | 商店/目录编号 |
| `goodsId` | 1002008 | 普通兑换行；玩家限购按该 ID 查找，不再加 shopType |
| `goodsItem.id` / `itemId` | 201、104003 | 交付物配置编号，不是某件库存实例的 Mongo ObjectId |
| `goodsItem.count` | 3 | 每份商品交付量；总量 = 每份数量 × buyCount |
| `configId` | 101、1、201 | 充值资源配置关联键 |
| `productId` | ProductId 表中的平台字符串 | 客户端 SKU；多个字符串可对应同一 configId |
| 产品 `key` | `card:101`、`crystals:1` | FreeStore 的规范商品身份，购买次数、上下架按此保存 |
| `receipt` | UUID 字符串 | 某玩家一次管理购买的去重键，不是支付渠道单号 |
| `priceTier` | `Tier_5` | SDK 价格档名称；保留资源原名，价格内容置零 |
| `secondarySheetId` | 活动资源中的分页 ID | 活动页条件映射；不能和 `subTabId` 混用 |
| `scheduleId` | 当前活动复刻编号 | 绑定某一轮活动账本与限购，不是从编号算活动 ID |

主要商城页：900 推荐、902 礼包、903 结晶、1001 尘辉、1052 角色装扮；千星页顺序为 104000、103000、102000、101000、100000、105000。普通城镇商店和活动商店还会使用其他编号，不能仅凭这份页面表过滤全目录。

## 文件来源和加载优先级

### 资源位置

| 文件/表 | 用途 | 加载入口 |
| --- | --- | --- |
| `Shop.json` | 人工整理的商店基础快照 | `DataLoader.loadList` → `ShopTable` |
| `ShopGoodsExcelConfigData` | 普通商品、价格、等级、销售期、轮换、分页 | ResourceLoader → GameData |
| `ShopRotateExcelConfigData` | 按 rotateId / rotateOrder 排列的轮换交付物 | `ShopInfo.resolveGoodsItem` |
| `ShopChest.v2.json` | 礼包可开出的内容，格式为 `itemId:count,...` | `ShopSystem.loadShopChest` |
| `ProductIdConfigData` | configId 到多平台 productId | `FreeStore.load` |
| `ProductCardDetailConfigData` | 月卡天数、即发、日奖、上限和 tier | 同上 |
| `ProductMcoinDetailConfigData` | 结晶各档与额外奖励；同时生成 GM 原石档 | 同上 |
| `ProductBydMcoinDetailConfigData` | 千星事象凝核档位 | 同上 |
| `ProductPlayDetailConfigData` | 五种普通纪行商品 | 同上 |
| `ActivityShopOverallExcelConfigData` / `ActivityShopSheetExcelConfigData` | 活动商店总表/分页条件 | `ActivityShop` |
| `ActivityHistory.json` | 原排期到活动身份的映射 | `ActivityShop.SourceSchedules` |
| `BattlePassScheduleExcelConfigData` / `BattlePassRewardExcelConfigData` / `BattlePassMissionExcelConfigData` / `RewardExcelConfigData` | 纪行当前排期、等级奖励索引、任务、奖励内容 | GameData 与 BattlePassManager |
| 物品、圣遗物、外观、千星服饰资源 | 可发放类型、随机属性池、解锁目标 | 自动目录与 Inventory/外观动作 |
| `ShopOverrides.json` | GM 商品覆盖与充值上下架 | `ShopCatalog` 直接读取用户运行数据路径 |

`FileUtils.getDataPath` 优先选择用户数据目录，再选择内置默认数据；本机用户运行数据约定是 `local/data/`，不是看到仓库根 `data/` 就认定它生效。`getExcelPath` 优先 `resources/Server/`，再 `resources/ExcelBinOutput/`；同名文件格式选择顺序 TSJ → JSON → TSV。**FreeStore 自己用 JSON 列表解析器读取所选路径**，不能据此假设换成任意 TSV 后也可正常加载。

本次资源核对清单见 [机器可读基线](data/shop-recharge-baseline-2026-10-22.json)，包含读取的资源相对路径、SHA-256、行数、规范产品与本次测试结果。它不包含用户覆盖内容、玩家档案或令牌；资源升级后必须重新核对。

### 基础目录合并顺序

1. `ShopSystem.loadShop` 读取 `Shop.json`；将旧 `costItemList` 中 201/203 合入 hcoin/mcoin，防止标量货币在 UI 重复显示。
2. `enableShopItems=true` 时从 Excel 补全缺失商店和缺失 goodsId。普通同 ID 行保留人工快照，但完全没有费用的行会补回 Excel 的标量摩拉/原石/结晶费用；不任意重写已有材料兑换价。
3. 尘辉商店 1001 是例外：启用 Excel 补全时整个基础列表采用资源商品，保留轮换与真实销售期。
4. `ArtifactShop` 添加自动随机圣遗物；`CostumeShop` 重建稀有角色装扮；`SpecialCosmeticShop` 补永久历史外观和千星服饰。
5. `ShopCatalog.replaceBase` 复制基础行，再叠加 GM 覆盖，构造当前可用目录。
6. `FreeStore.load` 独立读取 Product 表；其上下架状态共享 ShopCatalog 的 disabledProducts。

ShopSystem 构造可能早于物品资源加载，因此初始自动目录为空不一定是故障；完整加载路径和后续 load 必须核对。GM 覆盖始终最后应用。

启动主流程在资源加载后还会调用 `gameServer.getShopSystem().load()`，入口见 [Grasscutter.java](../src/main/java/emu/grasscutter/Grasscutter.java)。普通 `reload` 没有完整重读所有 Excel 的逻辑；Product 表由 FreeStore 直接再读，ShopGoods 等多数定义仍来自已加载 GameData。`ActivityHistory` 的商店归属映射采用静态惰性缓存，第一次读取后也不因普通 reload 自动重建。修改资源后应按部署流程重启验证。

### 覆盖文件格式和语义

结构示意，不是直接覆盖正式文件的模板：

```json
{
  "edits": [
    { "shopType": 1001, "goodsId": 150000000, "enabled": true,
      "goods": { "goodsId": 150000000, "goodsItem": { "id": 201, "count": 1 },
        "scoin": 1000, "buyLimit": 1, "minLevel": 1, "maxLevel": 99,
        "beginTime": 0, "endTime": 2147483647, "costItemList": [],
        "refreshType": "SHOP_REFRESH_DAILY", "shopRefreshParam": 1 } }
  ],
  "disabledProducts": ["crystals:1"],
  "nextCustomGoodsId": 150000001
}
```

- 每条 edit 以 `shopType + goodsId` 匹配；goods 非空时是整行覆盖，不是只覆盖 JSON 内出现的少数字段。保存编辑前先取得完整现有行，避免省略字段退回默认值。
- `save` 保存行；`enable/disable` 改可见性；`reset` 删除该覆盖。自定义行 reset 后消失，资源行 reset 后回到基础目录。
- `save` 不自动把原下架行重新上架；enabled 是独立状态。
- 自定义编号从 150000000 开始，自动分配小于 200000000。跨商店重复 goodsId 会在新增校验中拒绝；限购本身仍只看 goodsId。
- 新行每份数量限 1–1000000；价格非负；等级 0–99 且上下界合法；endTime > beginTime。额外材料必须存在且数量正数。日/周/月刷新参数分别校验 1–365 / 1–52 / 1–12。
- 非千星商店不允许设置 beyondMcoin。自动圣遗物商品不能改物品种类或单份数量；随机标记由基础来源决定，GM JSON 不能凭空赋予普通商品随机交付。
- 文件先写同目录临时文件，再原子替换；不支持原子替换时退回普通替换。成功后发布内存快照；这不是跨进程配置协调机制。
- `ShopCatalog` 构造时读取文件；普通 `reload` 重建目录，但不会重新构造 catalog 并重新读外部手工编辑的 ShopOverrides。优先用 GM 接口修改；离线改文件需重启并核对。
- FreeStore 全部表成功解析后才替换 products/aliases；失败记录 `Unable to load free store products` 并保留此前内容，冷启动失败可能是空列表。

## 商品字段与刷新

| ShopInfo 字段 | 含义与默认 |
| --- | --- |
| `goodsId` / `goodsItem` | 商品行身份 / 每份交付物 |
| `scoin` / `hcoin` / `mcoin` / `beyondMcoin` | 每份摩拉 / 原石 / 结晶 / 事象凝核费用，默认 0 |
| `costItemList` | 每份额外物品或活动代币费用；扣款时与标量费用合并 |
| `buyLimit` | 本期上限，0 不限 |
| `beginTime` / `endTime` | Unix 秒；含开始、不含结束；普通默认结束为 1924992000 |
| `minLevel` / `maxLevel` | 普通冒险等阶或千星等级范围；普通对象默认上界 61，资源 99 可保留 |
| `refreshType` / `shopRefreshParam` | 无/日/周/月与间隔；刷新按上海 04:00 |
| `rotateId` | 0 固定交付，否则从轮换表按月解析；当前月计算先减 4 小时 |
| `secondarySheetId` | 活动分页归属，服务端访问检查使用 |
| `artifactRoll` | 自动圣遗物交付策略来源标记 |
| `boughtNum`、`preGoodsIdList`、`disableType` | 存在于模型，但当前购买入口没有据此实现完整前置商品/禁用类型规则；不能只改这些字段就认为已生效 |

`ShopLimit` 嵌入 Player：`shopGoodId`、`hasBought`（总份数）、`hasBoughtInPeriod`（本期份数）、`nextRefreshTime`（Unix 秒）、`activityScheduleId`。刷新判断要求 nextRefreshTime > 0 且已到期；0 是无定时刷新，不是“立即重置”。

活动首次绑定旧限购仅补 scheduleId，保留次数；从非零旧排期切到新排期才清本期次数和刷新时间。熄星的本期次数另有活动账本权威副本，展示时取较大值修复落后的 Player 镜像。

## 当前免费产品资源

本次读取 ProductId 表 228 行；支持的明细为月卡 1、结晶 6、千星货币 6、纪行 5。生成 24 个规范产品，其中 6 个是无 productId 的 GM 原石档，原生产品共 18 个；所有启用时共有 11 个不同 priceTier。这是当前资源基线，不是永远固定的目录总数。

同一 configId 优先选择 `isInternal=true` 且 productId 不以 `cloud` 开头的行作为展示主 ID，否则取第一行；全部匹配行仍加入 aliases。故“未提供独立云产品玩法”不表示所有 cloud 字符串都一定被拒绝：支持 configId 的云别名仍可映射到同一产品。

### 月卡、结晶与原石

月卡 `card:101`：30 天，立即 300 结晶，每日 90 原石，累计剩余最多 180 天，`Tier_5`。资源 `replaceMcoinNum=330` 当前未被 FreeStore 用于超上限替代发放；超过上限是拒绝。

| key | 基础结晶 | 首次额外 | 后续额外 | 首次实际 / 后续实际 | tier |
| --- | ---: | ---: | ---: | --- | --- |
| `crystals:1` | 60 | 60 | 0 | 120 / 60 | Tier_1 |
| `crystals:2` | 300 | 300 | 30 | 600 / 330 | Tier_5 |
| `crystals:3` | 980 | 980 | 110 | 1960 / 1090 | Tier_15 |
| `crystals:4` | 1980 | 1980 | 260 | 3960 / 2240 | Tier_30 |
| `crystals:5` | 3280 | 3280 | 600 | 6560 / 3880 | Tier_50 |
| `crystals:6` | 6480 | 6480 | 1600 | 12960 / 8080 | Tier_60 |

对应 `primogems:1`–`:6` 仅发基础列数量，不带首购额外，不占用 crystals 的首购次数。购买次数不会因商店刷新自动重置；代码没有年度首充双倍重置机制。

六档原生贴图来自客户端自己的 `UI_Mall_Purchase_Primogem1ststall` 至 `UI_Mall_Purchase_Primogem6thstall`，由 ProductMcoinDetailConfigData 的 `icon` 与 productId/configId 对应；服务端无需发送或重画图片。2026-10-23 的[点击免费充值](NATIVE_FREE_RECHARGE.md)使用同一套档位、资源和持久化次数，结晶价格仍显示 0。

### 千星事象凝核

当前映射：`JIANADINMNE` → 基础，`PLELAKODHMN` → 首购额外，`HJGDAEMDFJO` → 后续额外。不能把 `AJEELGGGILF` 的展示总额当基础后再次加赠。

| key | 基础 | 首次额外 / 后续额外 | 实际发放（首次与后续相同） |
| --- | ---: | --- | ---: |
| `beyond_crystals:10001` | 60 | 0 / 0 | 60 |
| `beyond_crystals:10002` | 270 | 30 / 30 | 300 |
| `beyond_crystals:10003` | 870 | 110 / 110 | 980 |
| `beyond_crystals:10004` | 1720 | 260 / 260 | 1980 |
| `beyond_crystals:10005` | 2680 | 600 / 600 | 3280 |
| `beyond_crystals:10006` | 4880 | 1600 / 1600 | 6480 |

### 纪行商品

| key | playType | 业务结果 | tier |
| --- | ---: | --- | --- |
| `pass:201` | 1 | 普通付费轨 | Tier_10 |
| `pass:202` | 2 | 高级档，含排期额外奖励 | Tier_20 |
| `pass:203` | 3 | 已有普通轨后升级高级档 | Tier_12 |
| `pass:204` | 4 | 普通轨折扣款，业务同 1 | Tier_bp_5 |
| `pass:205` | 5 | 高级档折扣款，业务同 2 | Tier_bp_15 |

“折扣”在本项目价格仍为 0，仅保留资源产品身份。不同 key 共享当前纪行 paid/extra 状态，不能通过换折扣款再次领取高级额外奖励。

## 货币与存档位置

| 资产 | 物品编号/属性 | 权威位置 |
| --- | --- | --- |
| 原石 | 201 → PlayerProperty 10015 | `players.properties` |
| 摩拉 | 202 → 10016 | 同上 |
| 创世结晶 | 203 → 10025 | 同上 |
| 事象凝核 | 231 → 10063 | 同上 |
| 其他千星虚拟币 | 232、233、234、236、241、242、244 | 按 `BeyondCurrency.propertyForItem`，不能按物品中文名推断 |
| 普通材料、武器、圣遗物等 | 物品配置 ID + 库存实例 ObjectId | `items`，`ownerId` 为整数玩家 UID |
| 通用活动货币 | 资源声明及历史商店费用建立归属 | `activities.activityCoins`，按 uid/activityId/scheduleId |
| 熄星 109 | 探索所得 + midCredit − spentCredit | `activities.detail` 中 AsterProgress；探索所得为 collected 集合数量 |
| 熄星 110 | bigCredit − spentBigCredit | 同上，独立累计与花费 |

全局币和千星币不会被注册为通用活动币；109/110 也不走通用 ActivityCurrencies。通用币支付要求属于同一活动文档，排期有效、归属一致且无未确认收支/冲突领奖。对同一 ID 造一个背包堆叠并不等于给对应账本充值。

### Player 中的商城与月卡字段

| 字段 | 内容 / 注意点 |
| --- | --- |
| `shopLimit` | 上述普通/活动限购镜像 |
| `freeProductPurchases` | key → int 次数；首购判断是值为 0，缺省 0 |
| `freePurchaseReceipts` | LinkedHashMap receipt → key；超 512 删除迭代顺序最早项；无完整订单时间、金额、奖励快照 |
| `moonCard` | 月卡开关；需结合剩余天数判断有效 |
| `moonCardStartTime` | 上海日历日起点 Date；业务日先经 04:00 换日计算 |
| `moonCardDuration` | 从起点算的时长；续费改为当前剩余 + 新天数 |
| `moonCardDailyPrimogems` | 每日日奖，旧档非正值回退 90 |
| `moonCardGetTimes` | 日奖日期集合；正常领取后只保留最近日期，不是全部领取历史 |
| `costumeList` / `flyCloakList` / `nameCardList` / `traceEffectList` / `weaponSkinList` / `beyondCloset` | 外观所有权，实际 ID 含义按对应资源；不是统一的 itemId 列表 |

### 独立纪行与活动档案

`battlepass` 使用 ObjectId 主键及 `ownerUid` 索引；字段包括 `scheduleId`、`paid`、`extraPaidRewardTaken`、`level`、`point`、`cyclePoints`、`missions`、`takenRewards`、`lastMissionRefreshDay`、`viewed`。Player 的 battlePassManager 是 transient，单独恢复 players 不会恢复纪行。

当前纪行协议日程仍使用 `beginTime=0`、`endTime=2059483200` 和固定 `cycleIdx=3`，周时间根据上海周一 04:00 计算。不要将其解释为完整运营排期实现。

`activities` 的 `detail` 是序列化 JSON 字符串，不是可用 Mongo 点路径随意修改的内嵌文档。熄星字段 `shopPurchases` 保存本期每商品次数；`pendingShopPurchase` 记录 goodsId、购买份数 count、总费用 cost、itemId、总交付数 itemCount、currencyId。老记录 currencyId 缺失回退 109。

通用活动币另用 `pendingCoinOperation` / `lastCoinOperation`：保存操作 UUID、前序 ID、身份、时间、收支和前后余额；PREPARED=1、APPLIED=2、CANCELLED=3。最多保留当前未决与最近完成记录，不是全部商店历史。

## 自动圣遗物与外观目录

`ArtifactShop` 默认开启，商店 1004，每件 20000 摩拉，原石与材料附加价默认 0，不限购；默认交付 +20，暴击权重 8、攻击/精通/伤害权重 3、高档数值倾向 3。配置位于 `server.game.gameOptions.artifactShop`。这是自定义掉落倾向，不是官方概率复刻。

筛选当前资源的五星、初始四词条、append depot 501、符合部位主词条库且具有套装效果的圣遗物；按套装/部位排序生成从 200000000 开始的行。批量逐件 roll，实际数值取资源，等级内部为显示等级 +1。资源新增可能改变自动 goodsId 顺序，既有按 goodsId 的 GM 覆盖与限购需要检查。

`CostumeShop` 为质量 ≥4、非默认、具有有效解锁动作的角色装扮建立永久免费限购 1 商品；优先沿用同物品旧最小 goodsId，否则 140000000 + costumeId。`SpecialCosmeticShop` 将有效风之翼、拖尾、武器外观、名片放入 902，千星服饰优先保留原分类，否则 101000；缺旧行时 ID 为 142000000 + itemId。最后仍可被 GM 改价/下架。

## 保存与故障边界

| 路径 | 内存串行 | 持久化方式 | 失败后能依据什么 |
| --- | --- | --- | --- |
| 普通 BuyGoods | 每玩家 Inventory 锁 | 库存自身保存 + Player 异步保存；无整单预留 | 库存、余额、shopLimit 和日志；无法仅凭限购还原整笔交易 |
| 通用活动币支付后的商品购买 | Inventory → 活动锁 | 币账本按阶段同步确认；商品和次数另行保存 | 货币操作凭证只能证明收支；还需核对发货 |
| 熄星兑换 | Inventory → 活动锁 | 活动预留同步 → 物品/Player 同步 → 完成同步 | pendingShopPurchase；未决时阻塞，不能自动重复发放 |
| FreeStore | Inventory；短暂 Player 元数据锁 | 奖励先执行，次数/receipt 后设置；同步 battlepass 再 Player | 最近 receipt 和产品次数；无奖励预留、无跨文档原子性 |
| 月卡日奖 | Inventory；短暂 Player 锁 | 原石、日期变化后 Player 异步保存 | 最近领取日期与余额；没有日奖专用交易日志 |
| 纪行解锁/领奖/买等级 | Inventory → BattlePass | 管理器普通保存、奖励自身保存；FreeStore 调用后额外同步 | paid、extra 标记、takenRewards 与库存；无全流程发货凭证 |
| GM 免费外观 | Inventory | 动作解锁、shopLimit 与 Player 异步保存 | 永久所有权避免正常重复领取；不等于保存已确认 |
| 结晶兑换原石 | 处理器未包整笔 Inventory 锁 | 两次属性修改后异步 Player.save | 无兑换 receipt，需另查并发和异常边界 |

Player/Item/Activity 专用 SaveGuard 防止旧副本覆盖和未确认写入误用；battlepass 没有同等专用 Guard。`saveGameSync` 同步确认不是跨集合事务，也不会自动把先前发出的客户端通知撤回。详细保存基础设施见[账号与存档数据模型](ACCOUNT_AND_SAVE_DATA_MODEL.md)及[运维手册](ACCOUNT_AND_SAVE_OPERATIONS.md)。
