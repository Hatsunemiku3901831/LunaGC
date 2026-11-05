# B-02e3：入场阶段计时纠名与实际执行

2026-11-06。前批B-02e1/B-02e2把`14C8AE9A0(10)`的边界误称为identity request，依据不成立。本批重新复核既有参数声明、85949枚举默认表和函数体，确认10是**OnPlayerEnterSceneNotify阶段**；该函数记录时间，未发送身份/网络请求。已将旧工具/摘要中的identityRequestMode、identityRequestExecuted、identityMode分别改成stageValue、stageBodyExecuted、stageValue，停止地址与原生路径不变。

`trace_client72_stage_timing.py`通过固定三输入重新调用stage_signature并提取85949表，不从文字误名推导业务。原生14C8AE9A0先检查阶段非负及145878E41开关，再读取GLOBAL+191F8的托管float数组；阶段小于数组长度才调用141447A90并写入array+20+stage*4。该包装实际调用原生时钟对象/取值边界后执行double→float转换。时钟对象与double输入是明确夹具，没有恢复真实OS时钟初始化或开关生命周期。

13组实际原生样本覆盖：阶段0/10/15、开关关闭、关闭且空数组、负值且空数组、Int32最小/最大值、长度0、索引等于长度、有效开关下空数组原生错误，以及IFix分流只停在目标入口。仅3组产生写入，原生存储位置固定14C8AEA1E；123.125转为float bits42F64000，16777217.0实际舍入为4B800000。跳过分支不调用时钟；有效索引/空数组到原始null helper，并未强置返回。

IFix真值样本只执行原始分流，停14DD8D150，不执行或模拟补丁成功。所有有效普通样本使用已初始化元数据/类、明确数组/开关/时钟输入；原生控制、边界和double→float包装不替换为“成功”。没有网络、客户端UI、SDK或服务器运行。

```powershell
$env:GAME_PATH = & ./tools/game_path.ps1
python tools/trace_client72_stage_timing.py --client $env:GAME_PATH --output local/migration72/evidence/stage-correction-stage-timing-trace.json --summary-output protocol/cn-7.2/stage-timing-observations.json
python -m unittest tools.test_client72_stage_timing tools.test_client72_born_continue tools.test_client72_scene_wait -v
```

新工具使用`client72_stage_runtime.py`封装明确时钟/数组输入，可用于接下来的正常入场原生分支；没有提前定义未知场景业务。共享[观察摘要](../protocol/cn-7.2/stage-timing-observations.json)和修名后的旧摘要仍runtimeStatus=not-tested，三层字段与ready状态不变。B-02e3继续追踪阶段记录之后的加载任务/数据消费，父任务和D-02未完成。

6项新增/26项聚焦、完整311项研究回归通过；计时13组及修名的旧33/32组均实际运行两次，trace/摘要逐字节一致。逐结构复核确认旧证据只变字段名和范围文字/工具hash，原生路径/地址/输入/结果不变。依赖门禁人工接入第55份研究输入及两个改名hash，全部源码/Proto/矩阵内容与开放边界不变，recover两次一致；研究校验通过，ready仍228缺口。复核证据为stage-correction-{determinism,login-proto-review-check,login-proto-repeat}.json，日志stage-correction-research-regression.log，均在忽略local/migration72。
