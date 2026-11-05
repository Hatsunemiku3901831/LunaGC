# B-02e5：正常场景两分支的列表消费

2026-11-06，接续[查询与两分支](CLIENT_72_SCENE_QUERY.md)。`trace_client72_scene_list.py`和`client72_scene_list.py`固定三输入重新提取，在明确的托管接口/数组存储输入下，实际执行`153A3CB70`、`15881E210`及`15881F800`。**只闭合限定列表消费，不提升未知场景字段业务，不代表完整入场或D-02。**

## 原生体与来源

`153A3CB70`使用type61485的owner+60字段`CMFGIBMFCCM`，声明是`Dictionary<uint32,List<uint32>>`。它实际调用原生`1578B66F0`查找，缺key时分配List并通过`1578B6A20`插入。随后读取同一List，native将+18 size清零、+1C version加一，再tail-call`15881F800`，传入当前size（零）和原消息+20指针。

`15881F800`的ICollection快路实际取Count、按容量决定复用或扩容数组、调用CopyTo、累加size并再次增加version。`15881E210`则是List构造路径：Count为零时使用空数组，否则分配精确长度数组、调用CopyTo并写size；没有增加version。

类型/接口来源分别从原metadata usage解码source和destination，不按相同数字猜映射：

| row | source | destination | 已核对声明 |
| --- | --- | --- | --- |
| 704087 | 127 | 1458A1EC0 | ICollection<uint32>/type1244 |
| 698791 | 28866 | 1458869F0 | uint32[] |
| 700095 | 5203 | 14588CA20 | List<uint32>/type1255，沿用上项复核 |

ICollection的七个slot全部核对，slot0是Count，slot5是CopyTo。原消息+20的type35584声明具有Count/CopyTo方法；泛型base registeredAddress为零，不能据此声称恢复了真实closed generic ABI。List元数据声明_items/_size/_version，其具体+10/+18/+1C位置由本项原生体和实际store观察绑定。完整两个消费者、InsertRange、assignability及两个字典helper的代码hash/范围记录在报告中。

## 原生执行与模型边界

`14051B7B0`类型关系检查、接口表搜索及后续间接分派均执行原指令。夹具提供明确初始化的class flags、接口表和泛型binder存储，不能把这些合成metadata当作实机ABI已验。

只有managed ICollection.Count和CopyTo是辅助模型：Count读取原reader对象的实际count；CopyTo读取该对象实际uint32数组，并按调用参数复制到目标数组。它们不直接返回整个列表消费成功，也不从预期结果清单直接生成目标内容。uint32数组分配/原始Array.Copy复用既有reader的明确存储模型；两个消费者及InsertRange均不替换成model。

输入经原生scene reader生成，validator另有逐样本显式expectedItems目录，独立核对wire读入值、输出rawArray/逻辑列表/size/version及原消息不变。非空构造产生独立数组；已有缓存保留List对象身份，足容量复用原数组，容量不足由原生代码决定扩容。uint32 Clear只把size清零，旧数组元素不会被归零。实际header stores记录其原始指令来源，包括`153A3CC57`清size、`15881FD1E`增version、`15881E3C6`构造size写入。

已有缓存初始version9，正常完成clear/InsertRange后为11；新缓存完成后为2；构造完成后为0。null source的非零消费者先清已有List，再在InsertRange到原始参数错误：实测size0/version10、旧数组仍保留，未虚构回滚。null cache entry则在清空前停止，旧对象保持不变。

## 同reader路径与停止位置

本工具仅在当前Python进程延长既有normal.fixture的fragment停止点，并改变所生成的明确列表wire输入；没有修改共用reader/normal/query文件。五个连续场景样本仍执行同一原reader、Stage10、任务创建/原生字典、query和列表消费者：

- 零查询：空列表及重复/高位列表，在构造返回位置`14CD675A2`停止。
- 非零查询：新缓存、已有缓存和decoded7C高位80000001，在消费者返回位置`14CD67450`停止。

两个停止位置位于原调用之后；未执行之后的其他场景系统。原列表指针/消息内容、64位输入、重复值顺序、显式零和uint32高位保持独立断言。

ICollection接口明确缺失的输入实际进入IEnumerable fallback，停`158820740`入口；不执行该body或伪造其返回。非零IFix停`14E129150`。null字典、null entries、null cached List、越界条目、null target/source均核对原始helper与确切call/jmp位置。普通支持路径执行完整列表消费者返回，不把这些错误或开放路线计作完成。

## 验证与已修正问题

24组正式样本覆盖空/非空、重复、零/高位/uint32最大值、已有缓存复用/扩容、原始错误、IFix、fallback及同reader双分支。12项新增纯validator测试不依赖共享新增摘要；与原query/normal/reader/born/continue/stage共63项聚焦测试通过。修正后的最终24组trace和摘要实际执行两遍逐字节一致，证据`evidence/scene-list-determinism.json`；原query19、normal14、born39、scene-wait32及128发现输入也实际重跑，四份摘要与既有共享证据逐字节相同，见`evidence/scene-list-regression.json`及`logs/scene-list-regression-{query,normal,born,wait}.log`。上述完成项仅指限定列表路径；24组中14组到消费者返回边界、7组原始错误、1组IFix、2组fallback，后10组不计作完整列表消费。

首轮输入生成器错误使用`endswith('empty')`，使`ctor-nonempty`和`nz-new-nonempty`也生成空输入；不能把首轮“24 passed”当作这两个非空样本的验收。已改显式空样本集合，validator独立逐名列出expectedItems而不复用生成函数，新增直接检查空/非空/重复目录的测试。修正后两项实际输入均为`[1,80000000,FFFFFFFF]`，目标数组/Count/CopyTo路径均重新执行。首轮完整快照保存在`evidence/scene-list-first-{trace,summary}.json`及`logs/scene-list-first.log`，不覆盖该问题证据。

```powershell
$sceneClient = & ./tools/game_path.ps1
python tools/trace_client72_scene_list.py --client $sceneClient --output local/migration72/evidence/scene-list-final-trace.json --summary-output local/migration72/evidence/scene-list-summary-draft.json
python tools/trace_client72_scene_list.py --client $sceneClient --output local/migration72/evidence/scene-list-repeat-trace.json --summary-output local/migration72/evidence/scene-list-repeat-summary.json
python -m unittest tools.test_client72_scene_list tools.test_client72_scene_query tools.test_client72_scene_normal tools.test_client72_scene_wait tools.test_client72_born tools.test_client72_born_continue tools.test_client72_stage_timing -v
```

原始报告/草稿摘要及全部运行日志仅写忽略的`local/migration72/`，使用scene-list前缀。未更改运行代码、Proto、配置、补丁、资源或共用工具，没有启动服务/数据库/客户端或部署。

下一项可沿返回后的场景调用继续，或独立恢复`158820740`枚举路线；完整资源/SDK/网络时序及D-02仍须实机验收。
