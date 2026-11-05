# B-02e3：正常场景分支的限定原生消费

2026-11-06。本项接续[阶段计时](CLIENT_72_STAGE_TIMING.md)和[通知reader/缓存等待](CLIENT_72_SCENE_WAIT.md)，工具为`tools/trace_client72_scene_normal.py`。固定三输入重新提取元数据后，实际执行原reader及`14CD67260 → stage10 → 14CD5F7B0`，最远停在`153A18E80`入口，**没有执行该查询或伪造返回，也不构成完整入场或D-02验收**。

## 来源与实际路径

每次运行核对原27748/type46311声明、scene入口参数和Stage枚举参数签名；从原始metadata usage分别解码source与destination：

| usage row | source index | destination cell | 类型 |
| --- | --- | --- | --- |
| 701228 | 478317 | 145892570 | 11064 / MoleMole.IAutoAllocRecycle |
| 710071 | 106228 | 1458C3938 | 75453 / NMCKLNDCNKA |

任务的15个实例字段及两个接口方法声明受核对；`task+44`的声明目标是UnityEngine.Vector3/type2329。这只证明消费目的类型，不据此把消息type84343或三个float轴命名为正式业务字段。6个注册方法和3个PE unwind范围的完整代码SHA-256保存在报告中。消息、任务、加载管理器均核对所属类型，不从邻近地址推导身份。

原生`14CD5F7B0`以解码后的+94值查询字典，调用明确的托管pool借用槽；之后实际执行`146E055F0`接口查找、`147B2DDF0 AutoAllocTypeFields`（本体ret）、`147B2F350 OnPoolAllocated`、`147B2F6A0`和`147B2F460`。字典`1578B66F0`查找和`1578B6A20`插入均执行原指令；停止后另作一次原生查找，独立断言插入条目与返回index。pool借用及metadata/vtable、泛型binder、固定容量数组是显式存储模型，并非完整托管运行时ABI恢复。

当前普通样本无born等待对象、无IFix分流、无timer/coroutine上下文，`GLOBAL+9E90=0`；`147B2F6A0`因此走真实early-return，不模拟定时任务成功。借入任务故意设置非零脏字段；原生reset实际把+2C/+30/+34/+38/+3C/+50/+54清零。旧任务的coroutine引用为空，未调用停止协程的系统体。

随后实际观察到以下变换，raw均来自同一CPU上执行的reader对象，业务含义保持未知：

```text
key/task+58 = ((raw94 + 5889) mod 2^32) XOR E011
task+28     = ((rawA0 + 45A5) mod 2^32) XOR 6B98
query edx   = ((raw7C XOR 2D03) + C0E3) mod 2^32
task+10     = message+38 原字符串引用
task+44/+48/+4C = message[+18] 的 +18/+20/+1C 三float
```

独立fixture逆算上述三个raw，手写wire经过原reader；已知输入1.25、3.75、-2.5最终在任务三槽形成bits`3FA00000/C0200000/40700000`。`rsi`仍指向原reader对象，消息完整已验内容在消费后未变。消息+68的`FEDCBA9876543210`、Unicode/emoji/NUL字符串与含uint32高位的列表均保留；本段未消费该64位值或列表，不能宣称已经映射其业务意义。

## 实际样本与限制

14组原生样本中8组达到下一查询入口：普通非零；三个decoded值高位；全uint32最大；decoded零；显式raw零；Stage开关开启；原current任务槽存在；带碰撞旧条目的字典。Stage关闭样本不取时钟；开启样本实际写stage10，bits`42F64000`，显式时钟123.125和数组来自共用StageRuntime72。

首次任务槽为空时原生将新task的+40/+41置为[1,1]。已有current槽时，旧指针移入owner+10，新task进入owner+30，新task这两个flag保持[0,0]；按原生分支差异断言，没有统一改成“成功状态”。字典碰撞旧条目保持原key/hash/value，插入后原生查找能返回新条目。

其余5组分别缺字典、缺pool、pool返回null、缺消息+18三float、缺下一查询owner，均停在对应原始null helper调用点；已有key样本则停在`146E05720`回收入口，未执行该回收体。没有以统一成功谓词替换这些分支。字典扩容、custom comparer/free-list、已有key完整回收、timer存在分支、其余五类nested内容、153A18E80及后续消费者仍未覆盖。

## 复现与验证

```powershell
$sceneClient = & ./tools/game_path.ps1
python tools/trace_client72_scene_normal.py --client $sceneClient --output local/migration72/evidence/scene-normal-final/trace.json --summary-output local/migration72/evidence/scene-normal-summary-draft.json
python tools/trace_client72_scene_normal.py --client $sceneClient --output local/migration72/evidence/scene-normal-final/trace-repeat.json --summary-output local/migration72/evidence/scene-normal-final/summary-repeat.json
python -m unittest tools.test_client72_scene_normal tools.test_client72_scene_wait tools.test_client72_born tools.test_client72_born_continue tools.test_client72_stage_timing -v
```

9项新增测试是纯证据validator测试，不读取尚未接入的共享摘要；实际native样本由CLI执行并逐组验证。拒绝省略原生方法/用model替换、错误decode/wire、float重排、64位截断、错误旧槽/flags、字典碰撞条目覆盖、错误null分支以及Stage或原消息指针替换。与原scene/born/continue/stage共41项聚焦测试通过。14组正式trace和摘要实际执行两遍逐字节一致；原born39、scene-wait32及128发现输入也实际重跑，两份摘要与原共享证据逐字节相同。核对结果见本机`evidence/scene-normal-final/{determinism,regression}.json`，日志`logs/scene-normal-{final,final-repeat,regression-tests,regression-born,regression-wait}.log`。完整报告含逐指令地址摘要、wire/reader存储、任务与字典结果、全部模型和留白；本机证据只写忽略目录，未写新的共享observations文件。

首次独立查找复现`Offline72.run`要求registered base method extent，而此闭合泛型只有PE unwind；修正为使用已核对的精确unwind范围直接执行至返回，不修改共用emulator或放宽全局执行范围。失败日志`logs/scene-normal-final-first.log`保留。本项只有离线工具、测试和文档；没有启动或修改服务、客户端、数据库、资源、配置或补丁，无部署。

下一项可执行工作是核对`153A18E80`声明、原生前置与返回来源，先补明确输入再向后执行；也可独立恢复其余nested内容。仍需完整P0、SDK/补丁/资源与新旧号实机序列，父B-02e、B-02和D-02由主任务独立验收。

主线整合：原draft字节SHA-256 d8a2aaa9…c1914a经固定来源和完整trace validator复核后，原样接入[共享摘要](../protocol/cn-7.2/scene-normal-observations.json)。完整研究327项通过；人工复核研究输入56→58时只新增本摘要与修复后Lua摘要，原矩阵全部三层/源码/Proto/候选/开放边界不变，recover两次一致。B-02e3限定项完成，B-02e4已建卡继续查询；本批未晋级未知业务或实机。
