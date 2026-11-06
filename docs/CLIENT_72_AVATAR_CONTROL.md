# F-03d3f2a：Avatar 根 GUID 的 Hero 消费与本地角色来源

本卡继续 [f1](CLIENT_72_AVATAR_CONTRACT.md) 的 root tag3 → Player 模型 +0x128，取得两个实际具名消费者的独立执行证据。结果要求继续保留该字段的当前控制角色语义为未知：具名 Hero 消费者和本地角色消费者使用不同的输入链，在明确的受控存储夹具中可以选中不同实体。没有修改三层映射、生产 Proto、发送包或存档；`currentControlGuidBindingApproved=false`、完整场景 Handler/runtime 未验。

每个正式进程重读并核对 `extract_client72.HASHES` 的 EXE/global/startup 三输入，值见 [f1 固定输入](CLIENT_72_AVATAR_CONTRACT.md)。另固定 f1 摘要 SHA-256 `a54f62de9a803e2070c515ecc854488827ea6c62db4468c633190d6733f2b2f3`，重新提取 metadata、参数/AOT/class usage；不使用 Asta 或旧名证明字段含义。具体类 usage 为 `14589BC70 → 18163`（Avatar 模型）、`145898D98 → 54061`（场景实体）与 `145897948 → 58088 ActorUtils`。

## 实际原生链

1. 目标 root reader 从 tag3 解码完整 uint64；实际分派 `1507CC240 → 1507CEE50` 在 `1507CEEB5` 将它写入 Player 模型 +0x128。角色列表按 f1 的真实泛型工厂及 UInt64 字典建立，ID 继续经过原生 codec。
2. `1517B8730` 从 `GLOBAL+9E80 → network+180 → Player+128` 读取 GUID，经原生 `14E9B2C60 → 156F6F510` 查 Avatar 模型；读模型 +0x218 的场景实体 ID，再执行 `1517ABD80`，检查实体 class、+0x4FA 和相关列表。它另有 +0x290 的替代 GUID 路径；本卡未批准此替代值的完整业务语义。
3. metadata 具名静态 `ActorUtils.SetHeroMoveRatio`（`153E4D900`）实际调用上述 getter，并将所得实体及 float 1.25 交给 `153E6D660` 效果入口。本卡在入口前停止，不执行移动效果。这里的 Hero 用途来自真实具名调用者，不是猜测字段英文名。
4. metadata 具名静态 `ActorUtils.GetLocalAvatarConfigId`（`153E46950`）从 `GLOBAL+A3A0` 的场景管理器读取 +0x284 的 uint32 实体 ID，经实际 `1517ABD80` 返回实体，再读 +0x3F4 的配置 ID。它在该路径不读取 root tag3 或 Player+0x128。

场景实体行、Avatar 模型到实体 ID、管理器活动实体 ID、class/分配及 UInt32 字典索引查询均为明确夹具。实际根 reader/分派、完整 uint64 查找、两个具名消费者及实体查询体执行原生代码；没有以 Python 返回最终选中实体。夹具中两个 ID/实体的连接用于隔离消费者的数据来源，**不证明真实入场、切人或服务消息已经正确设置这些存储**。后继必须继续找 +0x284 的实际场景消息写入来源及当前队伍关联。

## 样本、测试和局限

7 个独立图覆盖 Hero A / 本地 B、Hero B / 本地 A、两者一致、uint64 最大值、缺失 GUID、零 GUID和实体禁用边界。`2^53+1`、`2^63+1`、max uint64 经 root reader、存储和真正 UInt64 字典保持完整；缺失/零/禁用 Hero 在本夹具中不交付移动效果，本地消费者仍返回受控 B 的配置 ID。零/缺失不是合法入场参数的批准。

5 项新增证据守卫及原 f1 的10项共15项通过，覆盖高位 wire、不同消费者、缺失边界、声明及关键原生入口不得被模型替代；没有把原 f1 的拒绝/业务边界删除或重计为本卡新增样本。两次全新进程完整/摘要逐字节一致：

| 产物 | SHA-256 |
| --- | --- |
| 完整轨迹 | `cff07f692abd11b2d01a889707ba2992fd6949385970745a882cd7638ece1604` |
| 脱敏摘要 | `8e2433b3d13ec17414cc26f21461f1cc62f64b16c413b327610ce0b7e7480ec0` |

最初本机原型漏掉 ActorUtils 的 class-init 存储，在具名 Hero 调用读 class 时发生未映射读取；补入 metadata 指明的 class usage 后通过，原失败保存在 `local/migration72/logs/d02-r1f2-probe.log`。更早的宽范围 +0x128 扫描和线性寄存器传播只产生候选：跨分支传播会混入无关类型，未作为语义证据，记录留在忽略 analysis 目录。没有把同偏移命中批准为 Player 消费。

```powershell
python tools/client72_avatar_control.py --client $env:GAME_PATH --output local/migration72/evidence/avatar-control-native.json --observations local/migration72/evidence/avatar-control-summary.json
python -m unittest tools.test_client72_avatar_control tools.test_client72_avatar_contract -v
```

正式共享摘要为 [avatar-control-observations.json](../protocol/cn-7.2/avatar-control-observations.json)；第二次使用 `native2/summary2`，原始日志 `local/migration72/logs/avatar-control-run{1,2}.log`。本卡仅离线工具、测试与文档，不运行游戏/数据库/服务，不需重启。F-03d3f2、完整初始化及 D-02 均未完成，继续场景控制实体与队伍合同。
