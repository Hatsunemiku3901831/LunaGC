# 未归的熄星：菲谢尔特殊奖励服务端领取

## 已接入的入口

本批补齐服务端领取和保存，通过 GM 命令目录或控制台使用：

```text
aster @10001 status
aster @10001 take
```

目标须在线。权限为 `activity.aster`，操作其他玩家另需 `activity.aster.others`。
`status` 检查当前排期的领取资格；`take` 调用同一检查和实际发奖。
命令不能跳过任务、领奖次数、背包容量、排期或未确认发奖记录。
领取成功后使用已有 `ActivityInfoNotify` 同步 `is_special_reward_taken`。
新排期初始化新进度；关闭、重新开启同一排期保留领取状态。

**7.1 客户端活动页的领取按钮尚未接通。**
当前协议提取结果中有多个仅带排期 ID 的匿名请求，无法单凭消息形状确定哪一个是
`TakeAsterSpecialRewardReq`。本批不新增猜测的包号或高位占位字段；此命令只提供已经验证的服务端入口。

## 原始领取条件与角色转化

| 条件 | 服务端检查 |
| --- | --- |
| 排期和等级 | 当前活动 2001、排期匹配、未关闭或禁用、冒险等阶至少 20 |
| 1200130 | 完成原任务 4111309 的条件，不能由 14／7 次领奖代替 |
| 1200131 | 条件完成，并有至少 14 次已确认个人中型领奖 |
| 1200132 | 条件完成，并有至少 7 次已确认个人大型领奖 |
| 奖励 | 原配置 3000000，必须恰为角色卡 1031 × 1 |
| 重复与故障 | 当前排期未领过，且没有未确认的其他活动发奖或购买 |

条件 ID、进度上限、绑定任务／机关和角色资源均核对原配置。
保留活动内容结束后、排期结束前的兑换期领取，不要求玩家再完成战斗。
任务 4111309 的完整剧情恢复仍属后续工作，本批不会自动完成任务或人为增加条件进度。

| 当前角色状态 | 本次实际交付 |
| --- | --- |
| 尚未获得菲谢尔 | 10000031，初始等级 1、初始武器 15101；不自动替换当前队伍 |
| 已有菲谢尔，未获得全部六个命之座 | 1131 × 1、星辉 221 × 2 |
| 已获得六个命之座或对应材料 | 星辉 221 × 5 |

重复转化依据固定历史 `AvatarCardChangeData.txt` 的 10000031 行。
当前项目以已解锁命之座加背包中的 1131 判断六次界限，与现有抽卡判断一致；
没有额外新增原服务端的独立 `avatar_repeat_count`。人为删除命之座材料后，不能据此推断原始获得次数。

角色卡不走现有 `useOnGain` 的布尔返回值：该接口在产生角色后仍可返回 `false`。
本批核对实际角色与初始武器，或每项转化材料的实际数量增量，随后确认数据库写入。
新增角色保留已有 `AvatarAddNotify`；角色卡通知使用活动原因 1062。
重复角色的已有 `AddNoGachaAvatarCardNotify` 同时填入转化标志、物品数量与新物品标志。

## 保存与故障处理

活动进度增加 `pendingSpecialReward`、`specialRewardTaken`，旧文档默认均为 `false`。
未确认特殊奖励与其他领奖、购买预约互斥；既标为已领又标为未确认的冲突文档不能使用。

1. 发奖前检查资源、全部条件和容量，确认保存领取预约。
2. 创建角色与初始武器，或逐项发转化材料；核对实际变化并确认保存相关实体与玩家。
3. 确认保存活动已领取状态，之后返回成功。

确认写入使用 `majority` 和 journal。预约写入失败不会发奖；发奖或最终确认失败，保留未确认预约并记录
UID、排期和异常，阻止在线重试或冷重载再次发奖。此类记录需要根据实际持久化角色、物品和活动文档对账，
本批不自动清除预约或重复补发。

## 来源与验证范围

- [原服务端 AsterActivity::takeSpecialReward](https://github.com/Havesten/hk4e-sources/blob/d12e0ae8dbe6012aefca71128d4c13e48113b68d/data/jenkins/jenkins_hk4e/workspace/hk4e_3.4_dev/gameserver/src/player/activity/aster_activity.cpp)：全部预览条件、领取标志、特殊奖励和动作原因。
- [原服务端角色卡发放](https://github.com/Havesten/hk4e-sources/blob/d12e0ae8dbe6012aefca71128d4c13e48113b68d/data/jenkins/jenkins_hk4e/workspace/hk4e_3.4_dev/gameserver/src/player/item/player_item_comp.cpp)：重复角色转化与非抽卡通知。
- [原始重复转化表](https://github.com/Ahanlei123/2.8_live_data/blob/f25e3155e38f9dc8f647493bf23a935147caf59e/txt/AvatarCardChangeData.txt)：菲谢尔前六次 1131 × 1／221 × 2，第七次起 221 × 5。
- 本地资源 `AsterActivityPerviewExcelConfigData.json`、`NewActivityWatcherConfigData.json`、`RewardExcelConfigData.json`，以及真实角色、技能库、命之座和武器定义。

首先用三个失败的测试锁定详情、预约互斥和冲突状态缺口。
单元测试覆盖原始条件、排期、等级、兑换期、容量、资源、并发领取与保存失败。
隔离 Mongo 测试通过实际命令／领取服务发角色、武器和转化材料，再冷读取数据库核对；
包括预约、武器、角色、玩家、最终活动记录和部分转化已写入但确认丢失的情形。
测试中的任务和已确认次数是夹具设置的前提，不代表实际客户端完成过剧情和全部战斗。

源码、资源哈希、首次失败、隔离 Mongo 结果和最终 JAR 核对记录存于
`debug/aster-mid-20261011/batch91/`。原生按钮、完整剧情、客户端角色领取弹窗仍待实机验证。
