# F-03d3f4d2 / CLEAR48-TRACE-WAIT-EXIT-LOAD-VALIDATOR

## 范围与结论

这是纯 Python artifact 验证器与人工合成测试，不是 native、客户端或现场通过。原验证器全局允许事件类型 6，但 `check_witness` 的固定尾序列只接受退休异常后直接退出；在内存中恢复该旧序列条件后，完整人工 W1、W2 各插入一次迟到 DLL 均以 `debug event sequence` 拒绝。

唯一生产校验逻辑变化：W1/W2 的固定数量退休异常之后、退出事件之前允许零个或多个类型 6；W3 尾序列保持原样。既有事务、TF 恢复、事件身份、成功继续、预算及其他门保留。有效文件句柄的类型 6 仍要求原有 `fileIdentityBOOL` 和 `fileCloseBOOL` 成功；现有门对无效句柄不作上述要求，也不验证 `filePath` 或 `moduleImageBytes`。这不是对其他文件字段增加校验的声明。

## 验证

执行 `C:/AI/Python312/python.exe -X utf8 -B -m unittest tools.test_client72_d02_trace_validate -v`：11 项测试通过。人工 W1/W2 在恢复 TF 并继续之后分别插入 1、2 次类型 6；完整 CLI 合成四类 JSON 输入也分别覆盖两个迟到事件，沿用 EFlags v2、元数据、固定输入和 required 门。负例覆盖有效句柄身份/关闭失败与字段缺失、PID/TID 不符、继续失败/状态错误/cleanupOnly、其他事件类型、额外异常、原事务未退休、未恢复 TF、最后一步前插入 DLL、W3 插入 DLL；128 事件通过、129 事件按预算拒绝。原测试均保留。

基线输入 SHA-256：验证器 `be5f39a3450aea3770b2ce06fbd7e5b68091b9bc5abd9c08eaefae2891740e17`，测试 `f7b6641ebd0ee6e32518d21af1e137639ee1cb1edbc4687bcf3800bf790ab924`。旧两次 W1 仍为失败，未重跑或改写实际 artifact；W2/W3 未因本项执行。旧现场另有 `wait identity` 失败，由主线独立分析，不在此处修改 wait 约定。新 build、独立审核及新的实际运行由主线进行，本项不代表完整 Avatar、自然 GC 或 D-02 客户端验收。
