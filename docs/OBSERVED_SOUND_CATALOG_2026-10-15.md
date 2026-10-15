# 已观察音效目录与控制台补全

参考 AstaPS 固定提交 `7cc148d1f752a43d4b9146b3b73f26875902546f` 的 SoundRegistry、
SoundCommand 和 PacketScenePlayerSoundNotify，适配本项目既有 CommandHandler 与 JLine 3.21。
保留来源中的运行期记录设计，没有引入 Picocli 或替换现有命令路由。

## 使用

```text
sound @10001 play_some_cue
audio @10001 play_some_cue 1 -2 3.5
sound list
sound list battle --page 2
sound list --page=2 battle
```

- 名称来自本次进程成功构造的音效通知，涵盖场景 Lua 和 `sound` 命令；忽略空名称，大小写不敏感地去重、搜索和排序。
- 每页 20 条。目录查询不要求在线目标；实际播放仍要求在线玩家、场景和原有 `player.sound`／`player.sound.others` 权限检查。
- 控制台 Tab 补全注册命令、别名及最新记录的音效名称；支持 `sound`／`audio` 和显式 `@UID`，不在坐标或页码位置乱填音效。
- 坐标必须完整提供三个有限数值；NaN、无穷数、缺失参数和无效分页均不会向场景发送音效。
- 构造失败的音效通知不记入目录，修正了直接照搬参考记录时机产生的错误条目。

## 验证

首轮 9 项中 7 项失败；追加检查又复现了失败通知污染目录的问题。
修复后 **20 项／3 套件**通过，覆盖真实命令路由、别名、权限检查、分页、坐标、动态 JLine 补全及并发记录。
有实际播放目标的测试设置真实 ACTIVE 会话，避免通用场景夹具默认未激活导致误判。
JAR 构建、宿主机／容器源码哈希与编译类／JAR 字节校验通过，无意外类删除。
独立 JVM 的 `serverSmokeTest` 启动／HTTP／GM 鉴权检查 **2 项**通过；此任务与普通单元测试分开执行。

证据：`debug/aster-mid-20261011/batch233/sound-red-results/`、`sound-packet-red-results/`、
`sound-final-results/`、`sound-verification.json`、`sound-smoke-results/` 和 `server-smoke.log`。
JAR SHA-256：`4effd04d0f035bf81610fc061060f21b6449d7d4c80e0785c507c17b6ecab895`。

## 范围

这是本次进程的观察目录，重启后重新积累，不是全量音频资源表。
通知成功构造不证明客户端已经收到或能够播放对应事件；当前客户端音效及实际终端 Tab 操作仍需实机验收。
日常运行服务未替换。
