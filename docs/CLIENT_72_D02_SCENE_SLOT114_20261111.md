# F-03d3s2 / SCENE-SLOT114（2026-11-11）

本卡仅沿模板关联后的第一个未闭调用。完整 Scene、resourceReady、自然生命周期与 D-02 均未验；本卡不改生产、Proto、patch、资源、配置、准入或共享矩阵。

## 中文有界计划

| 项目 | 本卡约定 |
| --- | --- |
| ID / 目标 | F-03d3s2 / SCENE-SLOT114；证明 World factory 在 `15154c07e` 调用的 `klass+460 / slot114` 的准确实现、类来源及直接入场后继，或返回准确缺输入。 |
| 已有 | `148d6ebf0..148d6ee39` 唯一完整585B原窗，code SHA-256 `8670d03e37b4155990e0f8fa4dc07238dc090d0143378df352abe61a2d583f65`；原 row 存入 owner28174.field138373/+140。此前 pool/row wrapper/recycle、MPLevel lookup 与外部固定仓库 partial 均直接复用，不重新研究。 |
| 准确缺口 | frozen metadata 的 type46314.method384166/slot114 指向 `1483df040`，但声明尚未关联到 factory 实际返回对象的 vtable。需区分静态类初始化/绑定和自然动态调用；同名、偏移或声明不作动态命中。 |
| 方法 / 依赖 | 先核冻结 metadata、factory 原码和已有捕获的 raw 区间及 provenance，仅追实体返回对象→klass→slot114 的必要链。复用 `reverse-engineer-anything` 静态/自然证据边界，不打开新的 native 分析或运行时会话。外部只读本轮已固定 GitHub 增量和原 clone 摘要，不新增大检索。 |
| 条件式原文件读取 | 只有已有原 bytes 能给出唯一准确 class/vtable provenance，才从当前 GAME_PATH 的固定五输入核完整 SHA/size 后，按已证明的准确入口和 PE exception directory 唯一 begin/end 读取一个完整小窗。必须先记录 ordinary AOT/caller、唯一 pdata、最大字节/结束及读取预算；声明候选不能解除该前置。若不能从现有证据唯一定位，0新body，交主线最小取证规格。 |
| 修改范围 | 仅本专题、`tools/client72_d02_scene_slot114.py` 及确需的同名测试；原件只写忽略的 `local/migration72/d02-resume-20261110b/scene-slot114/`。旧材料、Project/Harness/AGENTS 全只读。 |
| 禁止项 | 不扩招、不提交；不操作客户端、live API、调试器、DLL、DB、JVM、Cargo、Gradle、部署或共享现场；不做 native CPU、不补对象/槽/未知页、不顺手追 wrapper/recycle/外部 MPLevel 或后继函数树。 |
| 产物 / 验收 | 固定来源SHA和可复现小审计；至少两个 fresh 新进程输出逐字节一致；每个判断区分观测、静态推论及未知。形成最小可实施规格或准确首缺，保留全部自然线程、GC、IFix、组件/通知边界。静态断言不冒称 native 测试或客户端通过。 |
| 停止 / 恢复 | provenance不足即停在缺输入，不为凑任务列无关函数。若唯一新窗仍落入新未闭调用，列准确首缺交主线另定预算；若形成成熟实现规格立即交 Sol xhigh/Terra 并释放 max。无任何共享文件或现场恢复动作。 |

## 执行状态

**结论：准确实现尚不能唯一定位，当前应补来源而不是读取 `1483df040` 候选体。** 选定冻结 snapshot 的全部36条原读记录均已核长度及SHA；目标类 `+460..+468` 的8B没有任何字节落入这些原读区间。既有 allocator 片段也明确未返回对象，所以不能用已采 requested class 头代替 factory 实际对象的类。0新增PE body、native CPU、live读取或运行时会话；父项保持进行中。

## 来源与准确首缺

| 固定来源 | SHA-256 | 本卡使用范围 |
| --- | --- | --- |
| `d02-active/main/scene-runtime-context-third.json` | `bf9efd6225cd2836286b3a301bb2ea2f33dab6c6ddfb103d53aeed8f10db81d4` | 完整factory `15154bf20..15154c2f0` 的原字节连续性；body SHA `1d46e69c9304becd4b7efef4671bf31541fb433e6e55e4a58655f5653df08504`。不新增函数读取。 |
| `d02-active/main/entry-context-tool/live-resume-20261108-2.json` | `119f8a0b3c3ecc8829eda9bf7422c3646c643afa34ac041ea69b5607a02a7384` | 17,795B/36条冻结原读，requested-class cell与208B头；历史非原子采样，不操作其历史PID/地址。 |
| `d02-resume-20261108/r3-equipment/metadata.private.pkl` | `df52eb70fe8e70dfecd1059e898d15e288cfa98bcefc78bcaabf753ae150349f` | 只解析纯数据，核46314→28174继承、两者slot114声明和类型usage。 |
| `d02-resume-20261108/scene-pool/first.json` | `3a650294fbd979a775f98e01db480a85bc6784edff157113c98aa0e10ebcc811` | 原有 prefix 的未返回/首缺边界，未重跑CPU。 |
| `d02-resume-20261110b/scene/row-body/code.bin` | `8670d03e37b4155990e0f8fa4dc07238dc090d0143378df352abe61a2d583f65` | 原585B及 `148d6ed36` 的模板引用store，不重审wrapper。 |
| `d02-resume-20261110b/scene/row-body/extraction.private.json` | `7d5a30ee605c52dfab4381331905d6ef2d18c554bf944f57816cccc3b93405a5` | 历史五输入身份与完整原窗大小。 |
| `protocol/cn-7.2/profile.json` | `a3b9eda630942ba8479f713fc1692c7eb7b05198ae382dfe878ecc963051fd5d` | 五份固定输入期望与上述冻结证据一致；本卡未重新核当前GAME_PATH或声称当前现场身份。 |

以上私有相对路径统一以 `local/migration72/` 为根。工具固定这7份输入；公开文档不记录原运行地址、PID或安装路径。

### factory 的真实数据流

1. `15154bfb9` 将 type usage `145897b48`（metadata46314）作为RDX请求参数送入Pool；`15154bfc6` 调实际Pool class的slot4。
2. `15154bfcc` 把实际返回RAX保存在RSI。沿这个原静态路径到slot114前，没有替换RSI的指令；先完成已验模板关联和已有字段store。
3. `15154c06f` 执行 `mov rax,[rsi]`，因此slot114使用的是**返回对象自己的klass**，不是直接使用type usage cell。
4. `15154c072..15154c07e` 准备 `RCX=RSI`、`EDX=R13D`（factory第一个原uint32）、`R8D=R14D`（原静态u32）、`R9D=0`，随后 `call [rax+460]`。这些是静态调用参数，不另给其业务别名。

先前 `15154c062` 的IFix分支及自然开关未采；上述顺序只描述到达普通间接调用的路径。后续 `15154c10f→151539a40`、`15154c12e→147c4b850` 都依赖slot114返回，本卡没有跨过首缺解释后续组件。

### 为什么原件不足以命中实现

冻结 `pool.entityClass46314.cell` 的原8B确实指向所采208B头，原头 `+10` 为46314、`+18` 的kind为18；cell地址也符合该snapshot模块基址加RVA `5897b48`。但它只是**分配请求的类**。所有36条完整原读的区间联合都没有覆盖这个类的 `+460..+468`；本卡另用逐字节成员检查独立确认缺8B。不能从未覆盖的页、零或header后邻接数据补槽。

metadata46314.method384166 `GGDILPOJMCP` / slot114 / 两参数的registeredAddress为 `1483df040`；其父28174.method219197的同slot/同名两参数另指向 `148d85fc0`。本卡只确认声明及继承，**两者均不作动态命中**。没有读取它们的原生body，也未将普通方法登记地址当作实际vtable字节。

原Pool prefix在 `14050a8ae` 的缺失loader TLS索引读取前停止，原报告 `allocatorReturned=false`、`worldFactoryCompleted=false`、`naturalCallerObserved=false`；snapshot也明确 `worldInitializationVerified=false`、`atomicSnapshot=false`。因此现有原件既缺slot内容，也缺返回实体→klass的运行来源。这是所选证据集的准确缺口，不声称所有未来或未审文件均不存在证据。

## 交主线的下一种有界方法

最小新问题是“实际 `15154c07e` 调用的返回实体是什么类、该槽指向哪里”。不能只重采requested-class cell，也不能再看一次SDK页面来推定World路径已运行。

若主线以后确认已有合法自然触发和同阶段观察入口，可另立任务，在**同次实际factory普通调用**绑定frame/线程/模块身份与原调用点，保存RSI/RAX及原参数寄存器；仅就动态来源所需内存为：

| 有界输入 | 大小 | 验收用途 |
| --- | --- | --- |
| 原 `[RSI]` | 8B | 实际返回实体→klass，并与调用前原RAX匹配。不能由requested-class头或owned对象代供。 |
| 该实际klass的完整原头 `[0,d0)` | 208B | 以原ID/kind及已核布局识别实际类型，保留不同于46314的结果，不强行套候选。 |
| 该实际klass的 `[+460,+468)` | 8B | 得到原间接目标并按当次模块归属归一化。null/越界/身份不符保留真实失败，不fallback为metadata地址。 |

这是 **3读/224B的输入规格**，不是已获准或可直接执行的采样器；事件/frame来源、源文件/模块门禁、观察器安装/原转发/返回/EH与恢复生命周期、总预算须由主线另定。没有到达准确调用点时，不扩大时长、选别的对象或拼不同epoch。

若已有独立的精确class/vtable初始化原bytes，可先静态核其来源以替代新运行采样；仅有virtualSlot、同名或相邻候选仍不够。取得唯一原间接目标后，下一卡才重核当前GAME_PATH五完整输入、实际AOT/caller和唯一PE exception begin/end，先定一个完整body的最大范围/预算，再读取。当前没有实际目标，因此本卡**没有申请新body或猜测pdata extent**。

没有新Java实现、正式sender、能力/组件/拆除/通知合同。自然allocator线程、GC/对象所有权、IFix、MPLevel分组与能力内容、完整Scene及D-02继续未知/未验；外部GitHub partial直接复用，没有新增网络检索。

## 可复现审计与交付

两新进程 `fresh-{a,b}.private.json` 各 **5,325B**，逐字节一致，SHA-256 **`0d423a506b46ac30eb911b9da95a1f501a3bfabf84bfcd2b911acb1dc66f6e25`**；7pin/17静态与冻结完整性断言通过。这不是17个native测试。独立逐字节覆盖证明 `determinism.private.json` SHA-256 `4cb9657c7d58f79418a9e3d9ce2781a568288810caa8aa316bf3a5db57251f2f`，再次确认目标8B全缺。

主线可用新输出第三复核：

```powershell
python tools/client72_d02_scene_slot114.py --output local/migration72/d02-resume-20261110b/scene-slot114/main-third.private.json
```

检查前读取旧工具名时有若干只读“文件不存在/Windows字面通配”显示错误；已改用 `rg --files` 定位及实际文件，未改旧原件、门禁或证据。正式小审计首次即通过。只有离线工具/文档，无构建或重启需要，无Git操作。主线统一验收、更新Project/Harness及提交；本卡返回准确缺输入并释放max，不扩无关函数。

主线第三独立CLI复核7pin/17断言通过，仍5325B/SHA `0d423a506b46ac30eb911b9da95a1f501a3bfabf84bfcd2b911acb1dc66f6e25`。只关闭冻结材料缺输入审计，slot实际目标、factory调用与组件生命周期保持未验；不新增实机要求或候选body读取权限。
