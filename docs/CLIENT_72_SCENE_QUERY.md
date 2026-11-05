# B-02e4：场景查询来源与零/非零分支

2026-11-06，接续[正常场景消费](CLIENT_72_SCENE_NORMAL.md)。`tools/trace_client72_scene_query.py`固定三输入哈希后重新提取，实际执行`153A18E80 → 153A357D0 → 156632D10`。它只恢复查询的存储来源和原始分支，**未知字段业务不晋级，不代表完整入场或D-02**。

## 声明与查询存储

查询、getter和非零消费者都属于type61485/IAAIJCMLCGO。查询的独立元数据参数为uint32；`153A3CB70`两个参数是uint32及type35584的uint32泛型实例，与原消息+20类型对应。方法return声明尚未恢复，不能把eax及JS/Python显示形式当作正式int返回声明。

owner+90的字段`OBNPMJHMHAF`声明为`Dictionary<uint32,uint32>`；getter有缓存时原样返回此指针。泛型类型来源分别核对metadata usage source和destination：

| row | source index | destination | 已核对类型 |
| --- | --- | --- | --- |
| 702224 | 32774 | 1458975F0 | Dictionary<uint32,uint32>/type1222 |
| 700095 | 5203 | 14588CA20 | List<uint32>/type1255 |

查询字典的布局与上项任务字典不同：+10为bucket数组，+18为entries数组，+20为comparer。普通样本明确comparer为null，执行原始uint32查找分支；hash为key&7FFFFFFF，bucket保存index+1，entry步长16，依次为hash/next/key/value。查询第一次lookup负值则返回零；存在时重新getter/lookup并校验entries长度，再读取`entries+32+index*16+12`。实际返回高位`80000001`及`FFFFFFFF`，仅据其非零控制分支，不猜测业务含义。

完整查询、getter、泛型lookup及冷来源enumerator代码范围/hash记录在报告；注册方法和PE unwind范围分别核对。字典数组、managed allocation和泛型static槽是明确存储输入；query/getter/lookup/enumerator没有用返回model替换。

## 冷getter的来源与边界

缓存为空时getter读取`GLOBAL+34730`。该槽对应type66294/OJJHCIFFFCD的字段index327426、rawOffset4034730，声明为`Dictionary<uint32,type70859>`：

- 来源为空：实际进入`147BB1B10`初始化调用入口停止，未伪造其返回。
- 显式空来源：原生分配新cache、写owner+90，实际运行`1593859B0`枚举至结束；新字典无bucket，查询实际返回零。
- 单条非空来源：实际枚举出原key和type70859记录指针，把记录+80的raw四字节复制到临时槽，再到`14AEF9DB0`解码入口停止。独立断言枚举key及raw`12345678`；没有执行decoder或cache插入尾段。

记录+80是字段index349818/PGEALOPDPOF，声明为valuetype58209/COJIPHBKKGB。getter后续代码只在其解码结果非零时把枚举key/结果传给`156633000`；本项把这段记作代码来源，未声称非空来源已完整填充cache。真实数据初始化、编码表和来源生命周期仍未知，未把静态资源ID套进查询。

## 同一reader接回正常分支

本工具只在当前Python进程包装既有normal.fixture的fragment边界，保留原生reader、Stage10、任务allocation/reset/字典写入；不修改共用工具文件。在其实际执行前，给GLOBAL+A400对象的+90提供明确query输入，再连续运行至两个入口：

- 非零：`153A3CB70(global+A400, decoded7C, 原消息+20指针)`。普通17及高位80000001都实际观察原参数。
- 零或无key：native分配一个List<uint32>对象后，到`15881E210(newList, 原消息+20指针, 0)`入口停止。此时newList+10仍为null，不能宣称列表构造/复制已完成。

四个连续场景样本分别为零、非零、高位key及无key；全部核对消息raw7C的独立算术、原repeated指针、含高位列表及64位输入。原任务路径与原reader对象内容保留；两个消费者body都未执行。

## 验证与复现

19组原生样本包括8组正常直接返回（无bucket、无key、存零、存7、高位key/value、最大key/value、碰撞链、冷空来源）；null entries、越界bucket/entry和循环链分别停在原始null/bounds/cycle helper；query/getter IFix分别止`14D85C250`/`14E2A5D60`；冷无源、冷非空记录和四个同reader场景分支保持上述边界。碰撞链的全部显式entries在查询后逐字节不变。

```powershell
$sceneClient = & ./tools/game_path.ps1
python tools/trace_client72_scene_query.py --client $sceneClient --output local/migration72/evidence/scene-query-final-trace.json --summary-output local/migration72/evidence/scene-query-summary-draft.json
python tools/trace_client72_scene_query.py --client $sceneClient --output local/migration72/evidence/scene-query-repeat-trace.json --summary-output local/migration72/evidence/scene-query-repeat-summary.json
python -m unittest tools.test_client72_scene_query tools.test_client72_scene_normal tools.test_client72_scene_wait tools.test_client72_born tools.test_client72_born_continue tools.test_client72_stage_timing -v
```

新增10项为纯证据validator测试，不依赖尚未接入的共享query摘要；与原选集共51项通过。实际CLI逐组核对原生结果；最终19组trace/摘要执行两遍逐字节一致，核对在`evidence/scene-query-determinism.json`。旧normal14、born39、scene-wait32及128发现输入均实际重新执行，三份摘要与既有共享证据逐字节一致，见`evidence/scene-query-regression.json`及`logs/scene-query-regression-{normal,born,wait}.log`。首轮错误地预期新List的items已初始化，原生观测证明构造入口尚未执行，已改为明确的分配对象/null items边界；`logs/scene-query-first.log`保留。测试文件首次括号语法错误日志也保留，没有删除失败后伪称首轮通过。

本项只有离线工具、测试与专题；原始报告、草稿摘要、重复/回归结果和失败日志均在忽略的`local/migration72/`，使用scene-query前缀。未更改Java/Proto/配置/补丁/资源，未启动服务、数据库或客户端，没有部署。下一项可独立恢复`15881E210`列表构造/复制或`153A3CB70`非零分支；真实初始化/decoder及完整P0/实机仍须另验。

主线复核：af573b9三个文件已审阅；19组完整trace通过validator及来源hash复查，原draft SHA-256 b34c936d8cf95e3e948d0d6bdbb504728893c860d0e6617482e1f5e9d998ea8e原样接入[共享摘要](../protocol/cn-7.2/scene-query-observations.json)。研究输入58→59时全部源码/Proto、三层矩阵、计数、候选/描述符及开放边界逐结构不变，recover两次一致；337项完整研究回归通过，研究结构0错误/ready仍228缺口。B-02e4限定完成，B-02e5已建卡追踪两个消费者；只新增离线内容，无需重建/部署。
