# 7.1 头像、头像框与名片适配

## 本轮接入

- 资源加载器接入 `ProfilePictureExcelConfigData.json` 和 `ProfileFrameExcelConfigData.json`。
  当前本机资源含 186 个头像、12 个头像框；头像兼容两组混淆后的解锁类型字段。
- 头像按自有非试用角色、已拥有皮肤、完成的父任务、已消耗的头像券与默认配置解锁。
  单纯查询列表不会赠送物品、完成任务或解锁全部外观。
- 三张真实头像券 320001／320002／320003 的动作 57，持久化解锁
  99999／200010／200011。每次只消耗一张，重复使用、错误来源、非法配置与批量使用拒绝。
- 玩家存档增加 `profileCosmetics`，保存所选头像、头像框与永久解锁集合。
  旧 `headImage` 角色 ID 在读取时映射到资源头像，查询不会改写旧存档。
- 已拥有的头像框可以选择；默认框为 100000，其余框需要奖励调用方明确授予所有权。
  当前尚未接上所有纪行、活动与千星奇域的头像框奖励来源。
- 列表与选择请求使用明确处理器和类型化 proto。响应保留请求序号，不创建延迟重发线程。
- 名片列表请求注册 5683，响应按 ID 排序。好友缓存和在线资料使用同一头像序列化逻辑，
  保留角色、皮肤、头像和头像框的独立字段。

## 原始客户端证据

使用 `tools/extract_client_type_names.py` 的哈希校验后，读取本机客户端的类型、成员、
包号 getter 与 writer／reader；没有修改客户端文件。

```text
YuanShen.exe
7f89938da606c1281659607d464702cddb9a09a7d4320a196630f60a811ec38e
global-metadata.dat
469eccd43aa48fe7a2df4e1caf66fa1268a17f5a4a03fa209e1427e467cb0161
startup-metadata.dat
90848967d3f9432e7a13caffcec999bf457ac3be1f2ae86336443a63acbaadcf
```

| 消息 | 包号 | 原始字段与地址 |
| --- | --- | --- |
| 资料解锁列表请求 | 4179 | EGBGKBEJNKI；getter `0x14ea331f0`；更换头像对话框的请求发送链 `0x1512ae070` |
| 资料解锁列表响应 | 22477 | MFLHNBCGNJP；reader `0x1490cac50`；重复 uint32 字段 4／15，retcode=9 |
| 切换头像请求 | 6969 | MLHHHHIAGOF；getter `0x1521585b0`，writer `0x1521586f0`；picture ID=8 |
| 切换头像响应 | 1191 | KOFOEMBDLMO；getter `0x14b48f300`，reader `0x14b48f3f0`；retcode=6，ProfilePicture=14 |
| 切换头像框请求 | 21517 | IEAEDEPOEHM；getter `0x152aa8ec0`，writer `0x152aa8cb0`；frame ID=9 |
| 切换头像框响应 | 9314 | BGLAIJBKLAJ；getter `0x14974ff10`，reader `0x14974ff20`；ProfilePicture=3，retcode=15 |
| 名片列表请求／响应 | 5683／4400 | OCLEKCLPLAL／JMEBGLMKJAB；getter `0x149353c20`／`0x151f26830` |
| ProfilePicture | 内层消息 | MFDLDKCDGCF；writer `0x150974660`；frame=1，avatar=2，picture=3，costume=4 |

列表第 4／15 字段的头像／头像框语义参考 AstaPS 的界面观察，并核对客户端对话框
JGEEACBLKCE 持有 MFLHNBCGNJP。切换响应的语义结合原有响应定义、字段形状与参考库映射。
字段号、线类型、包号和服务端流程均有静态或测试证据；这些语义与实际界面仍需本机游戏验收。

## 拒绝直接采用的参考改动

参考 [AstaPS 的头像适配提交](https://github.com/MeChen618/AstaPS/commit/ca7198d17b86ced954bd23c0159e9ae8acc4389c)。
本地没有采用其全部解锁、伪造父任务、重复赠送头像券及延迟多次推送的方式。

该库 `BeyondProfilePictureHelper` 把 SocialDetail 第 6 字段解释为图片／框组合。
本机类型 IIBOEFCLJEA 实际是 Birthday：生日请求 27497、响应 29394 和 SocialDetail
都引用它；字段 1／2 分别是月／日。SocialDetail 的头像消息仍是第 25 字段
MFDLDKCDGCF，writer `0x14f880eb5` 写入该内层消息。生日字段保留原样。

该库还将 27126 作为头像解锁通知，原始类型 NLCLNLDGECL 的重复字段
KPIDIHGBNKB 与名片列表共用名称。此通知的最终语义未完整追踪，本轮不用于头像解锁。
`BeyondProfilePictureDataNotify` 6326 中的头像上传／URL 对象不等同于资源表头像，
目前没有用资源头像图标去伪造该链路。

## 验证范围与剩余工作

修复前的 5 项协议测试复现 4 项失败，分别为缺少处理器、头像请求字段错误、
角色／头像框字段混用和头像响应返回码字段错误；生日对照通过。

专项验证覆盖真实资源、3 张头像券的实际库存消耗、重复和错误输入、父任务、皮肤、
试用角色排除、旧存档映射、实际注册的处理器、请求序号、排序和好友缓存。
Morphia 使用独立临时 MongoDB 写入并读回永久解锁和当前选择。

本轮新增 20 项测试，全量 **627 项全部通过，0 项跳过、0 项失败、0 项错误**。
Java 17／Gradle 8.5 的隔离 Linux 环境运行 `gradle test jar` 成功，
并检查 JAR 含新增资料状态、头像框处理器和列表协议类。
产物为 `debug/profile-20261008/LunaGC-7.1.0.jar`，SHA-256：

```text
4ac570bbec2af0cc24e0430513aed68349a89346c9027c81e1d300d6d24dcdf6
```

证据保存在本机 `debug/profile-20261008/`：原始类型、调用链、反汇编、修复前后日志、
测试 XML 和格式化目标清单。格式化仅对本轮 21 个 Java 文件执行。

仍需接入／验收：

- 游戏内更换头像、框和名片，重登与好友／联机显示。
- 27037／9219 独立资料请求及两个重复字段的完整语义。
- 千星奇域自定义头像上传、URL、审核状态及 6326 登录通知。
- 纪行、活动等所有非默认头像框奖励来源，以及外观管理界面。

本轮不代表上述链路全部完成，也尚未部署到日常运行容器。
