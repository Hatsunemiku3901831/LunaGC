# F-03d3s2 / Reader factory 与 Scene 后继（2026-11-11B）

## 本轮最小计划

| 项目 | 范围 |
| --- | --- |
| 目标 | 核实实际 PathHash.bin 对应 reader42005 的 factory/初始 cursor/backend/refill 来源；随后按准确 CALL/data 边区分 World、slot114、Pool+1990 的独立后继与受拒绝依赖。 |
| 已有 | 正式 host reference 主线第三：707 组、15835 子名称、ordinal544 的完整 World key 和 21 原序名称、全文件逆编码一致；已有 parser/字典/文本游标和 Scene 静态原件只读复用。 |
| 缺口 | 文件 offset0 的真实 grammar 已闭；自然 reader 对象初始化、World factory 返回和生命周期尚未由实际对象/完整来源闭合。两个 caller flag 0 的含义未知。 |
| 方法 | 先查已存完整 body/constructor 与固定来源清单，不重抽 parser 或再解析字典；需要新窗时只给唯一已验 CALL/AOT/pdata 和最小完整 budget，交主线协调 Terra。沿每条后继先判依赖，未知保持未知。 |
| 修改/产物 | 仅本新专题、忽略目录 `local/migration72/d02-resume-20261111b/scene/`；成熟 request 立即交回。旧工具、旧专题、Project/Harness/Git/build/live 只读。 |
| 验收与停止 | 已存证据能闭则给可复现合同；否则给准确首缺、已查范围和解除条件。A 第二返回、热 Default 归属、B loader 及依赖不续接；不自行启动/读取客户端，不改变主线 C7/50B 现场范围，不将 host 解析或静态 CALL 升级成自然对象成功。 |

本轮不是新的 codec/框架研究；先复用本机已验来源，不重复已有 GitHub 检索和 clone。

## 已存来源与 reader 的准确下一窗

本轮未重跑 host parser、旧原窗或静态断言。只查已存 Scene 来源及文件名：`astra-resources/`、`d02-active/scene-runtime-context-astra/`、旧 group-source、parser、positive-analysis 材料，以及 `14d6f9210`、`14f614*`、`14f5f9*` 的已存文件名。所查材料保留 factory 声明和原 CALL，没有该 factory 或 reader constructor 的完整 body。此结论仅覆盖上述实际范围，不声称本机任意未审目录均无证据。

固定 metadata 117201003 B / SHA-256 `df52eb70fe8e70dfecd1059e898d15e288cfa98bcefc78bcaabf753ae150349f` 已完整校验后按禁止 global class 的方式解析。本轮核定：owner60257 `HALCDKCLBAO` 的 m500134 `GBPNKANHNCH` 唯一登记到 `14d6f9210`，flags `0x96`、static、slotFFFF、三个参数。已验 signature 的三个显式参数为 UInt64/Int32/Int32；return type 不在旧 extractor 的覆盖内。

| 来源层 | 已有准确证据 |
| --- | --- |
| 原完整 caller | `14aa99a50..14aa99d81`，817 B，SHA `991c1e787c57e6268491098762c259c502ce01923796d47959bfa95ca7091569`；冻结原件在旧 parser-window/fresh2/caller.bin |
| factory CALL | 原 `14aa99c0d..14aa99c1a` 共 13 B 为 `4c89f9 31d2 4531c0 e8f6f5c502`；RCX=bin，EDX=0，R8D=0；`14aa99c15` 的真实 E8 目标为 `14d6f9210` |
| 返回消费 | caller 在 `14aa99c2e` 将同 RAX 交 RDX，`14aa99c31` 调用显式 reader42005 参数的 parser；是静态返回值流，未观察自然返回对象 |
| 普通 AOT 预期 | cell `1422f9950` → P=`1428a54d0`；P+500134*8=`142c76200`，目标应为同 entry；下一包须实际重核两 QWORD |
| PE exception 目录 | RVA `1a4e6a40`、14344080 B、SHA `5895dbeef8fe13458720889e318ba64f3fc9c5d3ca03bf01b0adc70eee16c08d`；新目标的具体 pdata 行尚未取，必须 exact/containing 唯一且相同后才可取 body |

冻结 request 为 `local/migration72/d02-resume-20261111b/scene/reader-factory-window-request.private.json`，11339 B，SHA-256 `41586a3a0e27c90c8e47f1952022330258b51c6b0dfd150e1e08d91294992312`，已立即交主线供 Terra 执行。它固定原五输入、metadata/signature、已验 caller/报告和源工具，复用已验提取算法：入口各完整校验一次；只新核 13 B setup、16 B AOT、exception 目录及唯一 12 B pdata；先保存边界/pre-body，再读一个完整 body，最多 8192 B/2048 条连续指令。0 constructor、子 callee、xdata、数据表、native CPU 或 live。超预算保存真实诊断，由主线决定下一预算，不标为外部阻塞。

本窗只回答原参数如何消费、RAX 各返回路径及是否直接初始化 field207760/+10、207761/+18、207762/+1c、207763/+20、207764/+28。若这些 store 在未知 callee 内，记录准确 CALL/setup 后停止；不因 metadata 有 `.ctor/14f614a80` 就先取它，也不把两个 0 参数命名为 offset/count。若实际 CALL/data 边到 A 第二返回、热 Default 归属或 B loader，只保留原边并停其依赖，不以 Scene 命名续接。

## World、Pool 与组件的独立性结论

以下直接复用已验 [slot114](CLIENT_72_D02_SCENE_SLOT114_20261111.md)、[Pool 条件合同](CLIENT_72_D02_POOL_TLS_CONTRACT_20261110.md)、[条件 collector](CLIENT_72_D02_TLS_POOL_COLLECTOR_20261110.md)、[row 生命周期](CLIENT_72_D02_SCENE_ROW_LIFECYCLE_20261110B.md)和[旧引用清理](CLIENT_72_D02_SCENE_TEMPLATE_PREDECESSOR_20261111.md)，没有重跑各旧审计。

| 出口 | 当前能独立复用的范围 | 首缺与本轮边界 |
| --- | --- | --- |
| 实际 World 返回 → slot114 | 原 factory 在 Pool 返回后保留 RSI，`15154c06f` 从 `[RSI]` 取其自身 klass，`15154c07e` 调 `[klass+460]`；不是 requested-class cell 的直接调用 | 已验 snapshot 没有自然返回实体来源及该实际槽值；复用原 3 读/224 B 输入规格，不重做旧 36 读覆盖证明，不读候选 `1483df040` |
| Pool+1990 分支门 | moduleTLS+1990 与 Avatar+19d0 分开；已有纯条件 collector/分支表可复用，最多独立首遍 8 读49 B、含统一 final 共16读98 B | 需要新同 epoch 模块/线程/TLS 输入及合法采样入口；launch-primary 的状态不能称自然 allocator caller；不读 head-array/node、不执行 pop/写/外部 call |
| Pool fallback | 保存的原 site `14050ab6e`、`14050ac0d` 确实 CALL `1405336c0`，与 A 已验 allocator 的入口相同 | 这里只复用 callee 身份。未授权通过 Pool 验证该 allocator 返回、热 Default 或所有权；当前诊断停在 call 前，共用入口本身不证明自然调用条件已闭 |
| 模板关联前清理 | `148d8e3f0` 普通路径只可称有条件 cache call 后清旧 row 引用 +140，随后 setter 关联新 row | 不称旧对象销毁、组件拆除或 Pool recycle；不为此重取 wrapper/cache/helper |
| 新组件 | 原 factory 在 slot114 返回后才有 13 次 `15154c10f→151539a40` 一类调用，随后 `15154c12e→147c4b850` 和 scene+280 store | 可保留条件静态顺序；没有同次 slot114 正常返回，不可升级为组件创建/注册/通知。新取组件 body 也不能替代这个首缺，本轮不抢先开窗 |
| 已有实体替换 | manager 原 `14828bab6→148d813d0(secondU32)` 与缺实体 `14828bbb1→15154bf20` 是不同分支 | 不称前者 teardown，不把第二 U32 命名为 host peer；无自然对象/分支输入时，不用缺实体路径代验替换或清理 |

Pool 1216 B 的旧保存来源只有指令文本和代码 SHA，没有完整机器码 hex；条件 collector 通过只证明分类/预算，不授予新 native 执行。即便以后取得 TLS 诊断，也不能以此跳过真实 Pool body/调用输入及写域前置。

## 后续现场的最小解除条件

本轮不启动或读取客户端，也不扩已通知的 C7/50 B 轮次。仅给主线判断未来新 ready 的必要条件：

1. 必须先有已授权且能到达普通 `15154c07e` 的合法自然场景触发，并保存同次调用的 frame/线程/模块身份、RSI/RAX 和原参数寄存器。SDK 登录页、scene ID 3、requested46314 头、owned 对象或不同时刻输入都不能代供。既有 Scene 合同要求真实 stageType=World/对应 44512 消费；不能仅以服务端枚举推定客户端路径到达。
2. 取得动态来源所需内存仍只复用 `[RSI]/8`、实际 klass `[0,d0)/208`、该 klass `[+460,+468)/8`。224 B 只计这三项内存，不含尚未确定的 frame 获取/安装/转发/返回/EH/恢复和前后身份门禁；总预算与支持机制必须另定。没有合法同次 frame，现阶段没有可执行 Scene 现场包。
3. Pool 现成 adapter 仅支持同次正常 CreateProcessW 原 primary handle 尚保留的集成来源，不能从旧 PID/TEB 复原同 epoch，也不能在完成的旧报告后追加 +1990。若主线未来选择该诊断，须另审完整新 source epoch/预算和实时 ready；它最多回答该 primary 当时的 Pool TLS 状态。
4. 新观察机制必须与 B 被拒组合 loader、A 第二返回/热 Default 依赖剥离，并沿准确 CALL/data 边证明独立；现有 C7 采样许可不自动提供 Scene frame。支持条件不足时准确返回缺口，不换观察器、模型或间接执行绕行。

真实 World 分组 key 已恢复，但配置正文并未由此出现。已有 `GetPathHash/1471361f0` 计算合同可只读复用；注册原 `14aa9a14c..15c` 传的是包含原分组 key 和另一类型值的结构地址。若后继定位配置，须先准确绑定该类型/后缀来源，再用完整 key；不得按 `MPLevel` 或已知资源命中猜后缀。本轮不为增加研究项而先展开此新树。

## 本轮交付与未验项

实际完成固定 metadata 纯数据核验、已存来源定向清点、原 CALL/AOT/pdata 的单窗规格与依赖分界；新 native body/现场读取/原 CPU 执行均为 0。新 window collector 的门禁测试、双 fresh、独立纯 audit 和主线第三在 request 中明确，尚待机械执行。旧 host reference 第三及其 6 tests 只读复用，不重复运行。

本页及单窗 request 已交主线。没有新增客户端验收、运行代码/Proto/资源/patch/配置或部署；World 自然返回、slot114、Pool/组件生命周期、Scene/resourceReady/D-02 均未验。只有技术首缺或等待新来源，不因尚未取新窗/单次预算停止而标外部阻塞。A/B 拒绝及依赖继续保持原阻塞状态。
