# F-03d3s2 / SCENE-ROW-LIFECYCLE（2026-11-10 批次 B）

本卡仅做固定国服 7.2 证据的离线研究。完整 Scene、resourceReady 与 D-02 保持未验；共享输入、原证据、运行代码、Proto、资源、配置及矩阵只读。

## 重大问题有界计划

| 项目 | 本卡约定 |
| --- | --- |
| ID / 目标 | F-03d3s2 / SCENE-ROW-LIFECYCLE；沿 World factory 的真实整行 consumer `148d6ebf0`，找出入场必经且能转为发送/资源实现规格的最小字段、组件或生命周期合同。 |
| 已有 | `148da0a30` 已证只将 receiver 和原 Gadget row 转交；`146e055f0` 已证为 `IAutoAllocRecycle.slot0 → slot2`，不是实体 ctor；MPLevel/+58 的配置查找键职责、Pool11008/slot4/mode1/type46314 及 TLS 首缺均直接复用。 |
| 准确缺口 | `148d6ebf0` 是否直接消费 row，写入哪些有 metadata 来源的实体字段，调用的先后及返回前置；真实 MPLevel 分组输入/配置能力内容仍缺。现有 World 分支创建/已有实体路径未闭，不能借 Avatar teardown 代验。 |
| 方法 | 先核冻结来源与准确 caller；固定 metadata cache 中普通方法 owner28174/index218981。申请仅一个精确入口完整 body，提取前由主线独立核 ordinary AOT 与唯一 pdata。只解释此 body 中实际字段和边，不追相邻代码，不顺手扩 callee。 |
| 依赖 | 本卡不等 Avatar/SDK 自然轮；沿已冻结 90000004 row 与已验 lookup-key 合同。新窗由主线串行提取；CPU、复制写、现场和后续自然采集须先交独立规格。 |
| 修改 | 仅本专题、`tools/client72_d02_scene_row_lifecycle_20261110b.py` 及确需的同名测试；新原件在忽略的 `local/migration72/d02-resume-20261110b/scene/`。不操作 Git、构建、Java/DB/服务、客户端/调试器/live API/DLL。 |
| 产物 | 固定来源门禁、唯一窗请求、原字段/调用顺序表、有限许可或准确最小缺输入；双 fresh 确定性报告与主线第三命令。静态、模型、自然执行分别标记。 |
| 验收 | caller rel32、普通 AOT 唯一性、实际 pdata 起止、完整 code/asm SHA 连续性均相符；每个字段结论可追到 metadata 声明和真实 store/load。无独立语义不得造 alias、默认省略或 sender 许可。 |
| 停止 / 恢复 | 唯一窗若只转发或落入新未知调用，保留准确首缺并交回，不树状扩窗。得到实现规格即交 Sol xhigh/Terra 并释放 max；新窗/CPU/现场需主线按最小缺输入另定任务。 |

## 唯一新窗的必要性与门禁

原 `148da0ab2` 的 `488b55d0` 从保存的原 row 恢复 RDX，`148da0ab6` 的 `e835e1fcff` 精确指向 **148d6ebf0**；RCX 仍为原 receiver。上一完整 365B 窗不解引用 row 字段，因此它不能回答 `Default_Item/+50`、MPLevel 之外的模板引用或组件初始化职责。目标不是按名字猜到的回收器或相邻函数。

固定 metadata cache（SHA-256 `df52eb70fe8e70dfecd1059e898d15e288cfa98bcefc78bcaabf753ae150349f`）中唯一普通绑定为 owner28174 `GCGEJOPPGAE` / method218981 `ICMKIEEDAPK`，一参数，flags `0x86`，virtualSlot65535。下一全局非零已登记入口为 method218982 `148d6ee40`；两者相差592B。**此间隔仅作为上限，不是函数边界证据。**

主线提取必须先独立核固定五输入、AOT pointer table 与该 slot、上述5B caller，再查 PE exception directory：仅接受一个覆盖目标且 begin 恰为 `148d6ebf0` 的 pdata，实际 end 不超过 `148d6ee40`、body 不超过592B。无记录、多记录、entry 不符、越界或截断均停止；不以 leaf fallback、邻窗、xdata 或反汇编猜 extent。随后只读这一完整 body，保留 code/asm、12B pdata、AOT 两个QWORD和准确 caller；不执行 CPU 或目标。

## Sol max 中文 GitHub 定向补查计划（交主线安排 Terra max）

本卡的新问题是 World row 到本地组件/生命周期的实现出口。先复用既有固定 Scene 来源 commit `d54f6f0d423ded9d055bb740a3e5f3aa8f2667f1`、AstaPS commit `6cfecfb55f889f8e3b7156adb12809608db4f74b` 与仓库外 clone，不重复全网盘点。

1. **先搜索仓库及README，重点阅读README，再决定是否分析仓库。** 既有固定来源也先核其仓库/分支README与研究摘要，记录README所述版本、功能和资源范围，以及哪一段能帮助World/Level entity、MPLevel或生命周期。README没有相关帮助线索的仓库不进入代码爬取。
2. 对README筛出的有用来源，再读仓库外既有clone的已取分支refs，定位World/Level entity创建、替换、移除顺序及 `defaultMPLevelAbilities`、`MPLevel`、`Default_Item` 的真实资源注册方式；记录repo、branch、commit、文件/行和许可。外部同名只能作候选，不能替代本PE字段语义。
3. 若既有refs无相关实现，仅补查同一仓库远端分支及明确fork，仍先搜索仓库/README再按有用README进入仓库分析，搜索 `EntityWorld` / `SceneInfo` / `MPLevel` / `IAutoAllocRecycle` 组合；最多两个额外仓库、每库最多三个有差异价值的分支。clone必须在当前仓库外，固定commit。
4. 返回可复用代码/资源来源、与本PE consumer的逐项吻合/不符及完整性限制。没有原资源分组或目标版本证据时返回partial，不推导路径、能力组或缺省值；本卡静态取证不等待网络检索。此增补计划尚待主线安排Terra max执行，不重做已验PE研究。

## 已完成的静态合同

**唯一新窗闭合了模板引用的存放位置，没有解锁新的 Java sender 或能力资源实现。** 主线随后授权本卡在独占目录自行完成这一个离线提取；五输入、ordinary AOT、原 caller 与唯一 pdata 均通过。实际范围是 **`148d6ebf0..148d6ee39`，585B/120条**，不是按相邻592B间隔截取。

code SHA-256 为 `8670d03e37b4155990e0f8fa4dc07238dc090d0143378df352abe61a2d583f65`，完整 asm SHA-256 为 `170faa269332449cc6de612e7e79bb4edc4b3538faaeabd9d74d69469051887d`；唯一 pdata RVA `1a7cc34c`，原12B为 `f0ebd60839eed608a42cf103`。提取证明 SHA-256 `7d5a30ee605c52dfab4381331905d6ef2d18c554bf944f57816cccc3b93405a5`。没有读 xdata、第二函数体或邻窗，没有 native CPU、进程读取或客户端调用。

### 原模板引用与类型分支

普通路径先把 RDX 原 row 保存在 RSI、RCX receiver 保存在 R14，最终在 **`148d6ed36`** 执行 `mov [r14+140],rsi`。固定 metadata 的 owner28174.field138373 `IJDEFJLOMKF/+140` 声明为基类27264；真实 World entity46314继承28174，Gadget40472也继承27264。此处保存完整对象引用，不将它展开成 wire 字段。null row 普通路径也会存 null；工厂先前已检查真实 row 非空，因此不能将 setter 的 null 行为作为 World 无模板成功的许可。

row 的直接读取仅有 klass 和两条分支各自的 **`+18` 编码ID**；没有 `+50/Default_Item`、`+58/MPLevel` 或其它 row 字段读取。`148d6ebff` 的 IFix 分支会改走包装调用；本卡没有读取其自然开关，普通路径静态结论不冒称自然执行。

| 路径 | 原数据与先后 | 可证边界 |
| --- | --- | --- |
| row 是52115的派生类型 | `1458a1300` 类型usage；`+18 → 14aef9db0`，检查owner78883的bool，再取GLOBAL+25e30 | 与本卡真实Gadget40472不同，不借它解释World配置 |
| row 是40472的派生类型 | `1458a47c8` 类型usage；`+18 → 14aef9db0`，检查模块RVA`587cb61` bool，再取GLOBAL+237d0 | bool为0直接到模板store；非0需容器非null且共享调用返回后才store。bool的自然取值没有在本卡观察 |
| 两类均不符或row为null | 直接到模板store | 仅此 setter 的静态行为，不批准工厂或handler成功 |

GLOBAL+237d0 是 owner54132.field269426，实际类型为自定义容器13732实例19433 `<uint32,40472>`；GLOBAL+25e30 是 owner78883.field394856，容器实例46664 `<uint32,52115>`。**不是标准 Dictionary 的已证 Remove，也不是已证 retain/recycle。** 本卡只额外解析了此唯一 body 所引用的两条 MethodInfo 数据usage，没有读取 callee代码：

- `145a0b138`：usage749984、methodSpec65309、classInstance19433。
- `145a16a38`：usage751294、methodSpec69081、classInstance46664。

两者精确指向 **13732.method101367 `DMEFOIHDAIK`**，独立泛型AOT都绑定 `154db6810`；具名 `Remove` 另为 method101368。共享方法只以原名保留，缓存增减、淘汰或所有权效果仍未知。解析证明4,301B，SHA-256 `619d60b5d4bd568f70dabbf7c26c9c86a534896b590e1c53204f67a41550b243`。

### 创建、已有实体、组件与通知的最小未闭点

以下使用既有完整 factory/manager 窗，不新增代码读取，也不把静态边说成已创建组件。

| 阶段 | 当前已证的顺序 | 尚未闭合的最小合同 |
| --- | --- | --- |
| Pool → 模板关联 | Pool slot4 → `146e055f0` 的slot0/slot2 → MPLevel lookup → wrapper先调用`148d8e3f0`再调用本窗 | 自然allocator线程/TLS、实际回调与分配返回未闭；`148d8e3f0` 未读，不能称其已释放旧模板 |
| 模板关联后 | 原factory `15154c020..15154c05b`先处理row+20并存entity+428、config→+2b0、常量→+4f4、第二u32→+42c；然后在 **`15154c07e`** 调实际klass+460 | klass+460是slot114；普通metadata46314 override384166候选为`1483df040`，但fresh只采类头，不包含此槽。不能用metadata声明代替实际vtable或认为构造已返回 |
| 后续factory | slot114返回后，才有`15154c10f → 151539a40`（声明13参），继而`15154c12e → 147c4b850(entity, config)`，再到scene+280写入 | 这两个body/完整参数效果与所建组件未闭；本窗没有组件创建、注册、拆除或通知消费 |
| 已有实体 | manager先查询并保存原实体；`14828bab6 → 148d813d0(secondU32)`；随后按entity+426分支访问组件。非零路径依次`1547052a0 → 147586f70 → 1547052a0 → 14773fbc0` | 没有证据把`148d813d0`称为teardown。组件返回、能力更新与实际替换/移除未闭；第二u32仍不命名host peer |
| 缺旧实体 | `14828bbb1 → 15154bf20`，返回后保存新引用；能力clone和回调是后继 | 不能用缺旧实体夹具代表旧实体清理；SceneTeam/EntityAppear三层及通知先后仍未闭 |

因此，若继续**模板关联之后的入场创建**，下一准确输入是实际46314的slot114绑定与对应consumer，而不是重新审365B wrapper或扩散通用回收器。其代码读取仍须新任务先用准确caller/AOT/pdata另定唯一范围，本卡没有申请或读取第二窗。若要闭合**旧模板替换**，须独立回答原wrapper的`148d8e3f0`；这与slot114、已有实体的`148d813d0`是三个不同问题，不能串成已验生命周期。

MPLevel的资源出口仍是此前准确缺失的 type70954 中包含 `MPLevel` 的完整分组key及原string[]，以及实际配置/能力内容。`Default_Item`没有获得新职责；本卡不拼资源路径、不采旧能力组、不批准空能力或默认省略。

## 冻结审计与交付

正式工具仅用标准库读取16份固定来源，不再读取 PE/GAME_PATH/进程。30项静态语义和完整性断言覆盖：原caller、唯一AOT/pdata、完整连续机器码、字段声明、两类型分支、非标准容器/非Remove身份、原World row两ID分离、创建与已有实体顺序。它们不是30个native测试，也没有新模型或自然执行范围。

双新进程 `fresh-{a,b}.private.json` 均 **25,730B**，逐字节相同，SHA-256 **`fc37cad32bb1bbe2515e42395fc453768af06835e1229336db3bf5262c57f061`**；证明 `determinism.private.json`。主线第三命令：

```powershell
python tools/client72_d02_scene_row_lifecycle_20261110b.py --output local/migration72/d02-resume-20261110b/scene/main-third.private.json
```

原件、提取/metadata脚本和实际日志均在本卡私有根。早期只读展示中字段offset为None、manager指令使用`mnemonic/operands`而非`text`、Windows字面通配及猜测的旧工具名导致显示失败；已按真实容器格式读取，未改原证据或放宽门禁，记录见 `inspection-attempts.private.md`。提取与正式审计首次均通过。

本卡只有离线工具和专题修改，无Java/Proto/配置/patch/资源变更，不需构建或部署；没有Git操作。交主线复核后更新Project/Harness并本地提交。有限模板引用合同可供独立夹具使用；**新增Java实现、正式sender、完整Scene/resourceReady/D-02均未获批准**。准确后继已明确，本卡返回释放max，不扩第二窗。

主线2026-11-11独立第三审核通过：16pin/30断言，25730B/SHA `fc37cad32bb1bbe2515e42395fc453768af06835e1229336db3bf5262c57f061` 与双fresh一致。仅接纳模板关联及准确未知，不批准完整实体构造、能力或通知。
