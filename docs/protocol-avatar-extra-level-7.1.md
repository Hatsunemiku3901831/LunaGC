# 7.1 角色等级突破与无主命星

## 当前实现

满突破、90 级的自有角色，按 `AvatarExtraLevelExcelConfigData.json` 中的两段配置升级：

| 升级 | 消耗 |
| --- | --- |
| 90 → 95 | 无主命星（104300）×1 |
| 95 → 100 | 无主命星（104300）×2 |

升级直接设置目标等级，清零经验，按真实角色成长曲线重算属性并保存。没有额外摩拉或经验书消耗。
重复抽到已满命的五星角色时，额外赠送一个无主命星；原有 25 星辉保留。
四星角色重复和五星武器不赠送无主命星。
此规则与 [HoYoverse 重复角色奖励说明](https://support.hoyoverse.com/hc/en-us/articles/52089402385945-What-rewards-do-I-get-for-obtaining-a-duplicate-character-near-or-at-C6) 一致。

本轮补齐的是祈愿中的材料来源。商店、活动直接发放角色卡的完整重复转换，以及历史重复获取的补发，
仍需另外核对；不能按现有角色数量猜测历史重复次数。

## 协议依据

静态核验使用用户提供的当前客户端目录；不改写客户端文件。
提取器首先核对下面两个 SHA-256，只有匹配的文件才进入类型和方法表解析：

```text
YuanShen.exe
7f89938da606c1281659607d464702cddb9a09a7d4320a196630f60a811ec38e
global-metadata.dat
469eccd43aa48fe7a2df4e1caf66fa1268a17f5a4a03fa209e1427e467cb0161
```

| 消息 | 包号 | 字段 | 当前客户端证据 |
| --- | --- | --- | --- |
| `_AvatarExtraLevelUpgradeReq` | 7915 | require_level=8，avatar_guid=12 | BJFOLACFLPH，包号 getter `0x150885070`；writer `0x150884e20` 写入 tag 0x40／0x60 |
| `_AvatarExtraLevelUpgradeRsp` | 5633 | cur_level=2，old_level=3，retcode=7，avatar_guid=15 | DKHGBMBLDIB，getter `0x147eacf10`；reader `0x147eacbc0` |
| `_AvatarExtraLevelNotify` | 3528 | extra_level=1，avatar_guid=2，entity_id=11 | ELOAIHPIDJP，getter `0x14d430110`；reader `0x14d42ffe0` |

请求第 8 字段是**要求的当前等级**，不是目标等级。
UI 方法 `0x1479e4e00` 从选中的资源对象 ABKGLFKCHCJ 的偏移 `0x28` 读取
`DEJBOJMHHJB`，经 `0x14bb2c5e0` 解码后送给 `0x14ec11310`。
资源中这个字段分别为 90 和 95；目标等级 `BBPEGJLEEEC` 位于偏移 `0x2c`，分别为 95 和 100。
服务端将第 8 字段命名为 `require_level`，保留原字段号和类型，拒绝过期请求。

登录序列化填写已有 `AvatarInfo._extra_level` 字段 29，值从已保存的等级与资源阶梯推导。
不新增重复的存档进度字段；字段 30 的意义尚未确认，保持未填写。
这里的登录字段取值参考 LunaGC 上游，游戏中升级界面、重登显示与联机实体同步仍需实机验收。

## 与参考库的差异

来源：

- [LunaGC 角色突破提交](https://github.com/capyb2222/LunaGC_7.1.0/commit/ce1db10e95e58db6b004b07af1606de6e597c7ea)。
- [AstaPS 角色突破提交](https://github.com/MeChen618/AstaPS/commit/630a147d419b17b79648fb5bf0318319d38cc162)。

本地采用明确的 7915 处理器，不使用未知包中的 GUID 特征猜测升级请求。
请求必须属于该玩家、满突破、非试用角色，并与当前等级和有效资源阶梯一致。
升级前验证材料定义和目标等级成长曲线；缺失或非法配置返回失败，不能变成免费升级。
同一材料的多个成本先合并，溢出、负数和空成本拒绝；库存检查与结算在同一库存锁内。

满级角色的旧、新经验书升级请求在付费前返回等级上限错误，防止误扣经验书或摩拉。
无主命星奖励固定使用已核对的 104300，并要求突破资源确实引用它；
不把任意成本数组的第一个材料当成重复角色奖励。
十连转换在实际发放每一张卡时计算，前面的角色命星会影响后续转换；
祈愿后置事件替换物品时，也按最终物品计算重复奖励。

## 验证与证据

`AvatarExtraLevelProtocolTest` 使用客户端 writer／reader 对应的原始字节测试字段与包号，
并检查实际包注册表；`AvatarExtraLevelTest` 经实际注册表调用处理器和升级结算。
测试使用真实资源中的刻晴、诺艾尔、角色成长曲线、材料与武器定义。
覆盖两段成本、属性与当前生命比例、资源缺失、试用角色、未满突破、错误 GUID、
材料不足、满级、旧请求重放、并发同一请求、经验书保护和 Morphia 保存读回。
抽卡测试调用实际 `doPulls`，核对库存、单卡转换奖励、星辉和十连跨满命的结果。

本机证据目录为 `debug/avatar-20261008/`：
原始类型／成员表、请求发送链、包号 getter 及 writer／reader 的反汇编，
专项测试日志和 XML、全量测试日志、构建产物。
测试数据库是本轮独立的临时 MongoDB，测试不会连接日常玩家数据库。
静态协议、服务端结算和数据库测试通过后，仍需游戏内点选突破、重登和联机验收。
