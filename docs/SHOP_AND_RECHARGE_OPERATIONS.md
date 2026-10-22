# 商城与充值：运维、验证与后续工作

配套：[总览](SHOP_AND_RECHARGE_SYSTEM.md) · [数据](SHOP_AND_RECHARGE_DATA.md) · [协议](SHOP_AND_RECHARGE_PROTOCOL.md)。核对日期 2026-10-22，源码基线 `519eb03386d19c4a62ecd74d4a83ac02427e0ba0`。

## 接手与排查顺序

1. 阅读 [AGENTS.md](../AGENTS.md)、[账号存档指南](ACCOUNT_AND_SAVE_SYSTEM.md) 和本组总览；用 `git status`、`git rev-parse HEAD` 确认是否已偏离文档基线。
2. 确定故障入口：游戏普通兑换、RechargeReq、SDK 价格页、GM 免费购买、月卡日奖、纪行奖励、通用活动币或熄星兑换。
3. 记录必要身份：玩家 UID、shopType、goodsId 或 key/productId、购买份数、原 receipt/操作 ID、发生时间。账号 SDK uid 不等于玩家 UID。
4. 区分源码、磁盘配置和运行中的目录。先看已加载 GM 目录，再核对用户 ShopOverrides、Shop.json、Server 覆盖和 Excel；避免改了不生效的文件。
5. 判断是展示错误、资格拒绝、费用错误、发货错误还是保存确认丢失；不要把“客户端转圈”直接归因于数据库。
6. 涉及余额或发奖时，先保留存档、凭证和日志；准备复现测试后再修复。使用独立测试玩家/数据库，不用正式存档做故障注入。

## 常见症状与定位

| 症状 | 先查什么 | 不能据此做什么 |
| --- | --- | --- |
| 月卡一直准备中/价格不出现 | 9913 通知、18 个原生产品是否完整、SDK V1/V2 路由、补丁日志、11 个 tier 的当前启用状态 | 不能靠 GM 发奖成功判断原生 SDK 已通 |
| 价格 HTTP 200 但仍没数据 | body 是否确有 tiers/t_price，是否落到空 data 兜底，tier 名及版本是否匹配 | 不要造 Tier_0 |
| 推荐页空、礼包页有月卡 | 900 自身的 cardProductList，不能只检查 902 或页签列表 | 不要重复创建独立月卡首购档 |
| 全部充值产品消失 | `Unable to load free store products`、五张产品表、配置关联、disabledProducts | 不要删除所有玩家产品次数“试修复” |
| GM 有商品，游戏看不到 | 销售时间、活动分页、轮换解析、普通/千星页签、等级、客户端 schema | 管理目录存在不是玩家资格证明 |
| 充值重复到账 | 是否换了 receipt、记录是否已超 512、别名是否归一；中途异常前是否已发奖 | 不能宣称所有重复请求自动去重 |
| 首购赠送不符合预期 | key 对应次数；结晶与事象凝核的基础/赠送字段不同 | 不要用 productId 字符串次数替代 key |
| 月卡剩余天数异常 | 上海 04:00 日界、startTime、duration、最后领取日；续费以剩余天数叠加 | 不要每次领奖增加 duration 或补齐所有离线日 |
| 纪行入口没出现 | 当前客户端开放状态与资源；历史已核对入口 1301 要求冒险等阶 20 | GM 解锁 paid 不会自动打开客户端入口 |
| 高级纪行无奖励/不可重复买 | 当前 schedule、extraPaidRewardId、extraPaidRewardTaken、库存与返回异常 | 不要仅清标记后重新发所有奖励 |
| 限购无法重置 | hasBoughtInPeriod、nextRefreshTime；0 不触发到期；活动另有排期/账本 | 不要假定 resetshop 命令已清次数 |
| 扣钱但没物品 | 普通退款是否成功、礼包是否在背包、外观是否已解锁、是否跨文档中断 | 不要直接重放同一购买并再次扣款 |
| 活动代币有堆叠却无法买 | 该 ID 是全局币、通用活动币还是 109/110；活动当前账本与归属 | 不要给背包同 ID 堆叠当作账本补偿 |
| 活动币冻结 | pendingCoinOperation、lastCoinOperation、pendingWatcherReward、Guard 状态 | 不要把 APPLIED 当成未扣费而退款 |
| 熄星商店持续服务器错误 | detail.pendingShopPurchase 及其他 pending 奖励、活动次数、实际库存 | 不要直接清 pending、自动退款或盲目补发 |
| GM 改文件后 reload 无效果 | ShopCatalog 是否仍持旧内存 settings；是否实际改了 local/data | 不要以重载命令成功消息证明文件已重新读入 |
| 资源升级后圣遗物串 ID | 自动 goodsId 重新排序、旧 GM 覆盖/限购、artifactRoll 来源 | 不要根据大编号一律走随机圣遗物 |

相关错误日志：`Unable to load curated shop data`、`Unable to load shop data`、`Unable to load ShopChest data`、`Unable to load free store products`、`GM shop operation failed`、`Aster shop requires reconciliation`、`Activity coin operation ... was not acknowledged`。应记录 UID/商品/操作 ID 和异常类型，避免发布 SDK 查询字符串、账号 token 或 GM accessToken。

## 只读检查

本机运行入口为 `GS-Status.cmd`，服务启停见 [LOCAL-RUN.txt](../LOCAL-RUN.txt)。本次文档工作未重启、部署或修改真实玩家；以下只是日后排查方法。

无需账号数据的价格检查：

```powershell
$gsPrice = Invoke-RestMethod 'http://127.0.0.1:8088/hk4e_cn/mdk/shopwindow/shopwindow/listPriceTierV2'
$gsPrice.retcode
$gsPrice.data.price_tier_version
$gsPrice.data.tiers | Select-Object tier_id, t_price
```

使用现有 `Open-GM.cmd` 登录 GM，查看商城与产品页；接口 GET 可以用于核对运行目录。不要把授权 URL、完整 headers 或 .env 输出粘贴到公开文档。

Mongo 查询前先确认**游戏数据库名和目标实例**。下面在已经选择正确数据库的 mongosh 会话内执行；23001 是虚构示例 UID，不是本机账号。

```javascript
const shopUid = 23001;
db.players.findOne({_id: shopUid}, {
  properties: 1, shopLimit: 1, freeProductPurchases: 1, freePurchaseReceipts: 1,
  moonCard: 1, moonCardStartTime: 1, moonCardDuration: 1,
  moonCardDailyPrimogems: 1, moonCardGetTimes: 1, saveStamp: 1
});
db.battlepass.find({ownerUid: shopUid}, {
  scheduleId: 1, paid: 1, extraPaidRewardTaken: 1, level: 1,
  point: 1, cyclePoints: 1, takenRewards: 1
});
db.items.find({ownerId: shopUid, itemId: 104003}, {
  itemId: 1, count: 1, saveStamp: 1
});
db.activities.find({uid: shopUid}, {
  activityId: 1, scheduleId: 1, activityCoins: 1,
  pendingCoinOperation: 1, lastCoinOperation: 1,
  pendingWatcherReward: 1, detail: 1, saveStamp: 1
});
```

只读投影避免读取账号凭据；返回的玩家信息也应保存在本机受控诊断目录。`detail` 需离线 JSON 解析；在当前 Guard/版本仍有在线对象时，直接 update Mongo 会与内存及后续保存冲突。不要把以上查询变成不经业务恢复入口的修复脚本。

## 未确认交易的处理

### 普通商城、免费充值与纪行

这些路径没有统一订单查询或完整阶段凭证。先比对玩家余额、原库存实例、限购、产品次数、receipt、月卡日期、纪行状态和日志时间，必要时正常停止游戏写入后做一致备份。不要仅凭成功/失败提示补发，也不要仅凭 receipt 存在推断所有独立物品写入都完成。

携带原 receipt 的 FreeStore 重试只对同玩家、同 key、仍保留的记录有效。若原请求在写入 receipt 之前中断，不能依靠重试自动判断应该补发多少。当前 GM 页面再点一次会生成新 receipt，应将它视为新购买。

### 通用活动币

详见[活动币恢复](ACTIVITY_COIN_RECOVERY_2026-10-17.md)。先使用只读 `activitycoins @UID pending` 和 `activitycoins @UID audit <activityId> <operationId>` 核对。`resume` 继续原币账本操作或确认已应用结果；`cancel` 只允许尚未变更余额的计划。操作 ID、归属、排期、版本或余额不符会拒绝。

恢复扣币不会补发商品；必须继续核对具体购买交付和次数。通用币预留与熄星预留不是同一机制，不能将 activitycoins 用作熄星商城的通用修复入口。

### 熄星 1037

保留 `pendingShopPurchase`、spentCredit/spentBigCredit、shopPurchases 和当前实际库存。写入失败可能发生在数据库已经接受之后；内存回退到 reserved detail 也不代表 Mongo 没完成。该路径没有对所有不确定状态安全自动重放的商店恢复命令，应在保留原证据后进行人工对账并为拟采取的修复写测试。

### 备份范围

保留完整游戏库（至少核对 players/items/battlepass/activities 及相关解锁实体）、账号/UID 归属元数据、`local/data/`、实际生效资源版本和配置对应关系。仅备份 ShopOverrides 不包含玩家交易；仅复制 players 不包含库存和纪行。`Stop-GS.cmd` 不会停止 MongoDB，停游戏后普通复制 mongodata 不能当成已经验证的冷备。具体操作沿用[备份与恢复](ACCOUNT_AND_SAVE_OPERATIONS.md)。

## 测试索引

| 范围 | 测试类 | 本次执行 |
| --- | --- | --- |
| 覆盖、编号、校验与来源标记 | ShopCatalogTest | 8 项 |
| 轮换、等级、千星货币和展示 | ShopPresentationTest | 6 项 |
| 充值、首购、receipt、月卡、纪行兼容 | FreeStoreTest | 14 项 |
| 协议字节与页签 | StoreProtocolTest、CosmeticProtocolTest | 13 + 3 项 |
| 价格初始化通知 | StoreProductInitializationTest | 4 项 |
| SDK V1/V2、两地区、GET/POST | StorePriceRoutesTest | 1 项，真实临时 HTTP 端口 |
| 外观目录、解锁和所有权 | CostumeShopTest、SpecialCosmeticShopTest | 8 + 8 项 |
| 纪行领奖、日周刷新 | BattlePassFlowTest | 8 项 |
| 纪行/充值/外观锁序 | BattlePassInventoryLockTest、FreeStoreInventoryLockTest | 3 + 2 项 |
| 限购并发、目录快照、圣遗物身份 | ShopTransactionConcurrencyTest | 5 项 |
| 批量展示只保存一次 | ShopBatchSaveTest | 1 项 |
| 活动开放、分页和复刻限购 | ActivityShopTest | 20 项 |
| 熄星两页价格、币隔离、限购和真实处理器 | AsterShopTest、AsterBigShopTest | 22 + 25 项 |
| 熄星 Mongo 确认丢失与冷读 | AsterShopPersistenceTest | 本次未执行 |
| 通用活动币与阶段恢复 | ActivityCurrencyTest、ActivityCoinTransactionTest | 本次未执行 |

2026-10-22 本次运行结果：**17 个套件、151 项，0 失败、0 错误、0 跳过**。Gradle `BUILD SUCCESSFUL`。命令输出位于忽略提交的 `debug/shop-recharge-doc-tests-20261022.log`；JUnit XML 位于 `build/test-results/test/`，HTML 位于 `build/reports/tests/test/`。结构化结果保存在[本次基线](data/shop-recharge-baseline-2026-10-22.json)。未来运行 test 会覆盖 build 报告。

该选集没有执行真实 Mongo 故障注入，也没有进行真实客户端购买。本次部分测试使用内存/替代保存夹具；通过不能证明充值或普通商城具有崩溃恢复保证。日志含活动缺条件等夹具/负向路径输出，不能把测试日志当作当前运行服务的健康日志。

### 重现本次选集

在项目根 PowerShell 中运行，不需要启动游戏：

```powershell
$gsShopJdk = Get-ChildItem -LiteralPath .\local\runtime -Directory |
    Where-Object Name -Like 'jdk-17*' | Select-Object -First 1
if (-not $gsShopJdk) { throw 'Portable JDK 17 is missing.' }
$env:JAVA_HOME = $gsShopJdk.FullName
$env:PATH = (Join-Path $gsShopJdk.FullName 'bin') + ';' + $env:PATH
$env:GRADLE_USER_HOME = Join-Path (Get-Location).Path 'local\gradle'
& .\gradlew.bat --no-daemon test -PskipHandbook=1 `
    --tests 'emu.grasscutter.game.shop.*' `
    --tests '*StorePriceRoutesTest' `
    --tests '*ShopTransactionConcurrencyTest' --tests '*ShopBatchSaveTest' `
    --tests '*ActivityShopTest' --tests '*AsterShopTest' --tests '*AsterBigShopTest'
if ($LASTEXITCODE -ne 0) { throw 'Shop regression failed.' }
```

需要 Mongo 回归时，先准备独立便携 Mongo 实例和独立 dbpath，再设置 `LUNAGC_TEST_MONGO_URI`，例如已隔离准备的 27027。不要连接正式 27017 做故障注入；不要用 Docker 绕过本机部署约定。AsterShopPersistenceTest 在缺 URI 时会跳过；必须看 skipped 数。完整测试准备见[账号与存档运维](ACCOUNT_AND_SAVE_OPERATIONS.md#开发与回归测试)。

## 已确认边界与建议优先级

下表是分析结论，不是本次修复清单。P1 表示下一轮经济可靠性工作优先处理；P2 表示维护、兼容或功能完整性工作。静态代码能确认“缺少某项保护”，但实际损失场景仍应先写可失败测试，不能写成已复现事故。

| 优先级 | 当前证据 | 影响与下一步 |
| --- | --- | --- |
| P1 | 普通 BuyGoods、FreeStore、纪行缺统一持久交易计划；发奖与标记/限购存在多次或跨集合保存 | 对各保存阶段做失败前/实际成功后丢确认测试；设计原成本、交付清单、阶段与幂等身份，不能只加锁 |
| P1 | Recharge 每次生成新 UUID；GM 网页每次点击同样生成新 UUID；receipt 有 512 条保留界限 | 超时重试可能成为新购买；若需要可靠重试，先定义请求身份与查询/恢复协议 |
| P1 | `HandlerMcoinExchangeHcoinReq` 校验比例和余额后两次属性更新，没有整笔 Inventory 锁和目标余额溢出预检 | 先加兑换与商城/充值并发交错、原石接近 int 上限测试；再统一锁与算术保护 |
| P1 | 通用 ActivityCoinTransaction 只保证币文档，不覆盖后续普通商品发货 | 不能恢复扣款就结束故障；需要把交付、限购与币操作关联起来 |
| P1 | 纪行高级奖和等级领奖调用 addItems 后更新标记，没有完整批量容量预检与逐项交付确认 | 补满包、部分交付、物品保存失败和重连冷读测试；确认可否出现标记已领但奖励不全 |
| P2 | `resetShopLimit` 仅置刷新时间 0，而读取/购买只在刷新时间 >0 时重置次数 | 名字与效果不符，可能阻止后续自动刷新；修复前写涵盖普通/活动限购的测试并定义重置范围 |
| P2 | 获得即使用部分成功时按整数单价退款，最终仍记原份数；退款自身无独立确认 | 非整除比例或退款失败需复现；优先预检全部效果或引入可对账交付计划 |
| P2 | `preGoodsIdList`、`disableType` 等未进入完整购买资格判断；部分 proto 仍占位 | 对依赖这些规则的资源商品单列未支持状态；按客户端证据接入，避免“字段存在即支持” |
| P2 | 产品次数与普通累计限购部分地方用 int 普通相加；crystals 基础+赠送未使用 addExact | 当前正常资源远低于边界；测试损坏数据/极端计数与资源变更，再确定拒绝或迁移策略 |
| P2 | 自动圣遗物 ID 随资源排序生成，覆盖和限购按 goodsId 保留 | 资源升级先对比 goodsId→itemId 映射，制定迁移规则，避免旧设置落到新物品 |
| P2 | ShopOverrides 构造时读取；FreeStore products/aliases 分别发布；目录整体不是所有配置一次原子替换 | 明确受支持重载路径；需支持频繁热更新时补并发一致性测试 |
| P2 | SDK 路由固定本地 8088；月卡通知固定国服 ID；分离 Dispatch 缺本地 game 时价格 503 | 改端口/地区/部署模式前逐条验证，不能只改服务监听配置 |
| P2 | 纪行排期按版本选择，显示时间/周期索引部分固定 | 若要完整赛季，需补明确排期、跨期权益与迁移测试 |
| P2 | 2026-10-22 文档基线未验证原生 checkout；2026-10-23 结晶补丁与验收另见增量记录，熄星原生余额槽/分页及全部历史外观不在该增量范围 | 以[原生免费充值记录](NATIVE_FREE_RECHARGE.md)中的实际结果为准，不将结晶验证推广到月卡/纪行/其他页面 |

真实支付不属于现有系统能力。未来若明确立项，应先建立独立订单与支付确认模型，并保留免费本机测试模式的明确入口。webstatic 中的“支付成功/信用卡”翻译和 `_OrderInfo.proto` 类型本身不能作为已接支付的证据。

## 游戏内验收清单

使用专门测试玩家，在已备份且已确认新构建运行后验证；本次文档任务没有执行这些操作。

1. 登录后打开推荐、礼包、结晶、尘辉和千星页；确认产品数量、货币图标、零价格和原有游戏货币兑换价。
2. 确认 SDK 目录通知、实际 V1/V2 价格请求及客户端购买时是否发 RechargeReq；出现真实平台付费页不视为本地购买通过。
3. 每个结晶档首购/第二次到账正确；不同别名共享次数。千星货币按独立表，不使用结晶双倍假设。
4. 月卡立即奖励、同日续费不重领、04:00 跨日、重登录、过期及 180 天上限正确；用受控测试时间，不修改正式系统时钟。
5. 普通商店材料购买、余额不足、最后一件限购、满包、轮换和退款；礼包购买后留包，打开走对应物品系统。
6. 自动圣遗物多件各自随机；衣装、翼、名片、拖尾、武器和千星套装永久所有权、重登及穿戴检查。
7. 纪行普通→升级、高级直接购、重复拒绝、自选奖励、原石购买等级、上限与重登保存。
8. 通用活动币和熄星两币分别兑换；提前预览不可买、兑换期可买、旧排期不能沿用、新复刻限购重置。
9. 需要可靠性验收时，在隔离环境注入保存失败/确认丢失，再冷读检查余额、实际物品、标记和凭证；不能只看客户端弹窗。

## 修改、部署与交付

纯文档不重启服务。本机默认 `LUNAGC_RUNTIME=portable`，运行入口是 Start-GS/Stop-GS/GS-Status/Play-GS；游戏目录固定 `C:\GAME\Genshi`。修改业务源码、运行配置或内置资源后，按项目约定重新构建、正常停旧进程、启动新构建，核对健康、监听端口和运行 JAR SHA-256。`task.cmd serve` 必须确认仍走便携分支。保留 mongodata 与 local/data，不启用 Docker/WSL/虚拟化。

GM 运行时目录编辑自身会持久化并发布；若将其改动转成仓库内置资源交付，仍要执行构建更新要求。修改 patch 时另构建补丁，游戏退出后才安装，再重新启动验证。

提交只包含预期源码/文档；不提交配置凭据、数据库、日志和本机依赖。完成后说明本地提交、测试范围、是否更新运行构建、仍需客户端确认的项目。按当前 AGENTS.md 无需联网或推送，只有用户明确要求时才推送。历史文档里的 Docker 容器和旧 JAR 哈希不是当前部署证明。

## 给下一个 AI 的接手模板

```text
先读 AGENTS.md、docs/SHOP_AND_RECHARGE_SYSTEM.md 及它链接的数据/协议/运维文档，
涉及玩家数据再读账号与存档三份指南。基线为 519eb03386d19c4a62ecd74d4a83ac02427e0ba0，
先检查当前差异，以现有代码为准。

明确问题属于 BuyGoods 普通兑换、FreeStore 免费充值、SDK 显示、月卡、纪行、
通用活动币还是熄星专用账本。保留 key/goodsId/shopType/UID/receipt 或操作 ID。
当前没有真实支付网关；不要把 priceTier 或 receipt 当作支付凭证。

先用现有测试加可失败复现，再修复；涉及保存必须覆盖实际写入后丢确认与冷读。
不要清 pending、重置首购、改正式余额或使用正式数据库做测试。
不要承诺普通商城/免费充值跨崩溃恰好一次；区分锁、单文档确认和跨文档交易。

本机使用 Windows 便携方式。代码完成后测试、构建、正常更新服务、核对运行哈希，
及时提交推送并同步文档；纯文档不重启。最后列出真实客户端仍需确认的步骤。
```
