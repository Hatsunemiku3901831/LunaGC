# E-01d1 / 准确Depot query的静态值流与门槛后继

795 B新原窗已连接**同一typed Avatar的Depot查询输入、regular/energy skill选择及该Avatar已有Proud集合的消费**；没有连接81127同元素`+10`或突破比较。不把本体未读升级为整调用链排除。下一窗已改选与旧Promote配置上下文相交的2034 B函数，未继续扩Proud getter或UI helper树。

## 795 B可直接验收的关系

`14a6218cb`返回对象经`8d5`保存RSI；`8d8/8db`将其klass与固定usage `14589bc70/type18163`比较，相等才继续。`8e4..8f3`复制同RSI Avatar `+2b8`的typed32758并调用原getter，`8f8`将返回EAX交ECX，`8fa`准确E8调用`14d6d70f0`。这是一条原指令条件路径，不是本轮已执行的自然对象观察。

query返回RAX随后按原配置selector 0/1/2分别选择`+30`数组第0/1项、`+6c`标量，经58209 getter得到ID，再调用`14eb02d30`。两字段的regular skills/energySkill职责复用[既有桥梁](CLIENT_72_D02_RESOURCE_BRIDGE.md)，没有重新给未知叶命名。

后继读取Skill形状`+60`解码值，遍历同RSI Avatar `+50`的typed34054集合，调用63831.m529234/`149f0c1b0`并比较。匹配后以同Proud receiver调用m529239/`149f0e170`，把低U32零扩展后转float交`14a5e86f0`；该sink声明为`string,float,bool,bool`。既有组/等级用途只读复用，不重做研究。没有匹配时原路径置float0，这是该consumer的原分支，不是允许给目标Proud或门槛补默认值。

本体返回值消费的是已批regular/energy字段，并没有从Depot`+18`取81127引用元素。它也没有对同元素`+10`与同Avatar突破值比较。callee、hotfix及完整自然场景未执行，仍保持未知。

## 已有输入的typed缩窄

固定metadata全部field递归类型（含数组/泛型）只有Depot22284.field105355/`+18`直接含81127。完整参数报告仅typeReference664602/byref81127[]，唯一方法为81127.m672119/`14f777160` parser，与旧20977 B第三报告一致；type-usage仅81127和81127[]两项。没有新的显式81127参数/泛型consumer可据此取窗。

66个原global-load候选与旧完整来源的join保留真实范围：四个Avatar候选均有同起点旧文本；已有`151853dc0`确实读Depot`+18`，但ordinal元素只取`+14`。旧5901 B iterator也复用其`+14`消费结论。其余Avatar候选分别沿已纠正skill路径、row`+68`、默认Avatar→Depot`+6c`→Skill`+54`。旧49组件报告只完整覆盖四个component候选中的`15345bf80`；另三项不在该49源中，不能报成全覆盖。这些都是有界来源/排除，未证明全客户端没有门槛consumer。

## Promote交集与group/phase纠正

主线新找到的旧41行`avatar-promote-getter-callers.private.json`实际是`14af8f5c0`配置查询的局部上下文，不能称全部Avatar getter xref。按真实function start（旧RVA加modulebase）与73候选join，独立重算为4个context/2函数：`14cbdd560..14cbddd52`2034 B，和`151da35a0..151da46eb`4427 B。前者另有typed18163字段`+230`和U32字段`+25c`，后者对应`+248/+278`。

**`15184adf0`返回Promote group，并非实际突破phase。** 旧189 B/53条从Avatar`+250`的ID查询Avatar定义，读定义`+178` encoded58209返回；它没有直接调用突破属性getter。两个上下文将该返回值交配置查询EDX，ECX分别来自owner`+25c/+278`。这些U32是否来自同Avatar实际phase，需要完整窗继续证明。本次纠正不改主线交集原件，其数据只记录原contexts。

优先2034 B的source-bound请求已冻结：准确owner25486/m198316、普通AOT槽`142a28a30`、原pdata `60d5bd0c52ddbd0ccc263704`，candidate `14cbdda27`。完整旧源未覆盖该body；此前门槛排除也没有验此函数。机械预算为单窗2034 B、cap4096 B/1024条，先保存三slot、原pdata、candidate7 B与三个context E8共15 B再读body；0callee/xdata/live。请求SHA `35cc87447d738bddecd9f2c314b5038cd77034f90f85c5e718dc5f1bde1fb838`。该窗尚未由本分析包读取或执行，不预称phase或81127关系成立；4427 B项保持unknown。

## 离线复核

795 B原code SHA-256 `2c45365f85d4b3a63bbccb0494292411211f08b39c316343fc6b2f9ee485fb26`，201条。私有`query-semantics-audit.private.py`两个fresh均38653 B、逐byte同SHA `10b6901a892fbaf5fa325dcb0a76cfcbd9b8548ebd91c268826d493322be1673`：25源、九原件对、50原锚点、七typed字段及上述metadata/source joins。

交集旧caller来源SHA `c9a513e5fa639f8e22e3f1ccffaf0cda97b2d56efa2e83d8e84299b4ad649c40`，critical来源SHA `0c2e81b338fb2352faef061dc56469420d7a2bbdb1b7f458afa3a869de198eca`。`promote-context-source-audit.private.py`两个fresh及主线第三均14264 B，同SHA `9a2487a70ecad812ba55c76c79cec9f2096a8550ce1f45493d79915e4ba54f16`：11源、41/73交集、五typed字段及旧getter完整listing/六锚点。listing没有raw hex，未冒称独立重建原189 B。795 B query纯审计的主线第三亦38653B/SHA `10b6901a892fbaf5fa325dcb0a76cfcbd9b8548ebd91c268826d493322be1673`，同两fresh。复现均用新basename：

```powershell
python -X utf8 -B local/migration72/d02-resume-20261111b/proud-gate/query-semantics-audit.private.py --output <new>.json
python -X utf8 -B local/migration72/d02-resume-20261111b/proud-gate/promote-context-source-audit.private.py --output <new>.json
```

本包新增PE/code读取、CPU/native/live、客户端验收、构建和部署均0。移除A第二返回/B组合loader拒绝动作及产物后，所有C来源与审计仍独立成立。真实Proud行/map hit、81127门槛、Java比较/算式、resourceReady、完整Avatar与D-02均未批准。共享状态、机械取窗和本地提交由主线处理。
