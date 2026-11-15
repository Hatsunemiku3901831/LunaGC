# D-02 恢复集成活动检查点

任务 `D02-RESUME-INTEGRATION-20261116`，接手 HEAD `420002edaa07bf7050ba51a90dcc734332c36475`。用户已明确解除旧暂停，继续至六项实机通过或所有必要独立项各自形成准确外部阻塞；当前不是收尾或全阻塞结论。D-02 / F-05 / D02-R6仍0/6，R1/R2/R3进行中，R4/R5/R6待开始。

## 接手核对与已验复用

本轮只读核对：`.env` 为portable、HTTP 8088/game 22101，`GAME_PATH`目录、exe与config存在；`.env.local`不存在。根配置v14/HYBRID，两服务回环地址、两Mongo URI均27017，资源及data目录存在。匹配Java/Mongo/客户端/调试器进程0，TCP 8088/22101/27017/27027与UDP 22101监听0。`local/running.json`存在，但无实际运行进程，不能据此声称健康或运行JAR已验。源码 `tools/local_windows.ps1:57–59` 明确HTTP/game默认8088/22101、InstanceRoot Mongo固定27027；patch HTTP/WinHTTP仍固定8088，未声称全端口隔离。

11个既验产物的实际SHA-256与专题一致：121 Avatar base/growth及critical投影、两个Java completion、121 Depot三键、Talent规范报告、phase6、Float断言集成、启动闭环及cold现场/审阅回执。仅核文件完整性，未重跑测试/native/JVM或把最终critical driver当作已native重跑。Float/Pair、启动修复与唯一cold结果继续复用；完整资源及同旧号恢复未因核hash晋级。

私有盘点为 `local/migration72/d02-resume-20261116/integration-resume-20261116/preflight.private.json`，SHA-256 `ac5e0f2b38775c98da5597edabbf2ad4f0c1a4f557d1816d39f5f05e69c0396e`。独立审阅 `debug/d02-resume-independent-20261116/summary.md` 的10个文件hash直接复用，其结论只覆盖既有本地来源。没有客户端启动、computer-use、数据库/实例写入、构建、Proto生成或部署；R1/R2/R3必要合同未齐。

保护范围：用户`AGENTS.md`；patch的`Cargo.toml`及未验`examples/sdk72_service_trigger_proxy.rs`/同名目录；resources的`ScriptSceneData/flat.luas.scenes.full_globals.lua.json`；两份未跟踪E8草稿。11个文件指纹留私有盘点。patch与resources gitlink仍分别为`50f9bf82b3dbc0d4857321417dccf40f78c13ce4`和`dac2a9d43332ef51879522cf39da2831b0ca0543`；不暂存这些差异。

## 必要项与当前动作

| 必要出口 | 准确缺输入 | 已有覆盖 / 本轮动作 |
| --- | --- | --- |
| Skill/energy → Depot实际加载与旧号属性恢复 | 121行costElemVal已闭；specialEnergyMin/MAX cap与START全121覆盖及实际maximum采用仍需准确来源，影响Avatar.setCurrentEnergy/recalcStats | [367本轮+2旧片段](CLIENT_72_D02_SKILL_ELEMENT_HOST_BATCH_20261116.md)和[真实369Skill→121Depot Java](CLIENT_72_D02_OLD121_SKILL_DEPOT_JAVA_20261116.md)已通过，120新正向energy绑定解除；不等raw-id/reader，不建Player或假cost。[121普通cost字段](CLIENT_72_D02_SKILL_ORDINARY_COST_20261116.md)已实际闭合，后继真实cost-aware Java及特殊字段另验；ctor缺边界单窗不加作已闭出口门 |
| Weapon/Material/Relic必要行 | Weapon其他238种/1636行独立值；Material literal数值/row/consumer；Relic rank/set具名来源或同源输入+非零oracle排除标签互换 | 11101既验片段保留；本地审阅未给成熟包。具体公开问题尚待主线分片，未证明来源不存在；已知Weapon A构造返回依赖冻结 |
| Proud实际资源/当前采用 | 目标字段到共享Pair的准确桥、实际row/M、非空cache/key、同epoch及独立值 | 148B共享消费正例、Float条件合同已验；不重复SDK页采样，不创建Default/cache；普通新来源尚未查尽 |
| Scene/Team/Entity必要合同 | M1 producer→同次+5d60；M2同receiver +f0/返回R；M3首次消费前R+18及writer顺序；M4同R release/reacquire/reset | 238B工厂、writer/getter来源复用；本地暂无新输入。World slot114及getter/Recreate准确A返回依赖冻结，普通M1–M4来源问题保留 |
| SDK自然连接与认证 | 新的固定允许来源闭合Init/env→route采用→认证/完整校验及会话交换 | map/HTTP/key局部结果复用；B组合loader及其依赖冻结，未认定全部SDK都依赖B；精确定向公开问题未完成 |
| R4→R5→R6 | 上述必要合同、最终发送/原消费者顺序、同旧号全121角色和实际物品装备postLoad/recalc/save | 无成熟运行包，暂不部署。成熟最小合同回传后由唯一集成者实施、相关验证、本地提交；R4充分后再portable隔离部署与六项实机 |

第5节六项缺口仍分别为：旧号自然本服登录/归属；新号显式选人命名/确认保存；完整初始化与握手；角色可见落地；连续10分钟心跳；两类账号正常退出重登及受控进度保存。没有以SDK页面、局部Java、hash或服务健康代验。

## 准确停止边界与文档处理

A第二allocator返回/构造、热Default/sameepoch、`151841361`及准确tail/caller依赖，B首Cargo类型错误修复及组合loader/owned/装载/自然现场依赖、preview专项冻结均保持。A原平台error已找回；1528字符opaque事件关联不恢复执行正文，原明文验收已撤回，仍缺可读正文与固定技术输入。原提示、停止点和最小候选见[队列](CLIENT_72_D02_DAYBREAK_QUEUE_20261111.md)与[纠错](CLIENT_72_D02_A_PROMPT_PROVENANCE_CORRECTION_20261116.md)，不重试或自动派Daybreak，不扩大成整族/所有独立工作禁止。

Project/Harness/HANDOFF/HANDOFF2已先备份至本批忽略目录`integration-resume-20261116/before/`，四件字节/hash匹配；原HANDOFF2 SHA-256 `cabe165b17c6375a2d9ba3e745588b557bdaf88632aa63a1e7d385d436ea9c2f`。本轮只纠正当前授权和过时首发排期，历史事实、完成标记、拒绝/未知保留，HANDOFF只追加。接手核对与状态纠正子出口完成，集成继续等成熟字段包；本项无需独立测试或重启。
