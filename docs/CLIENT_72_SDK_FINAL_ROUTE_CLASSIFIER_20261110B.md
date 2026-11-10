# R2-ROUTE-CONFIG-SELECT / FINAL-URL-CLASSIFIER（2026-11-10B）

## 实施计划与固定合同

本项仅实现独立 Python 纯分类器、来源门禁及人工离线验证；不实现 hook、观察器、现场采集或网络请求。固定既有 managed 白名单源码、私有规格/来源快照/两份审核、40108 原 328B 和问号 literal 原证据的完整 SHA-256；每次创建分类器先核全部来源，任何缺失或漂移拒绝。读取已核源码的 AST 中原 ENVS、HOSTS、scheme、path，并与已核私有规格一致性比对；从已核 literal 读取唯一后缀 `?`。不改旧源码/旧 pin/profile/预算，不把共享文档作为运行门禁。来源只证明这些固定输入的绑定及已审范围，不声称重新执行目标代码。

外部入口接受 UTF-16LE 原 bytes，非 bytes 作为 unknown；只比较八个完整 base 加唯一 `?` 的原字节，输出固定 `managed-final-request-cn-env-k` 或 `unknown`。无前缀匹配、query 删除、规范化、解码或回退。unknown 不输出其原 bytes、长度、hash、body、token 或 headers；异常不包含输入。验证人工数据覆盖八类、缺少问号、双问号、额外 query、内嵌 NUL、未知 UTF16、64/65/70/71 units 和每个来源漂移。结果只证明纯分类，不构造目标托管对象，也不证明 PostRequest 进入/同步返回、HTTP 完成、认证或 D-02。33107 原 body/pdata、hook 生命周期与自然触发均交后续研究。

## 验证结果

实际运行：`python -B -m unittest tools.test_client72_sdk_final_route_classifier tools.test_client72_sdk_managed_route_inputs -v`，新 6 项及旧 12 项共 18 项通过；新测试含八个独立完整 fixture、64/65/70/71 units、缺单问号、双问号、附加 query、大小写、内嵌 NUL、未知 surrogate、奇数 byte、非 bytes、未知隐私及七个来源各自内容漂移/缺失。源漂移以测试桩模拟，原文件未改。

独立纯 Python 人工驱动：`python -B local/migration72/d02-resume-20261110b/b-final-route/independent_driver.py`，实际退出 0；八个独立完整 fixture、1090 次单字节扰动、附加 query 和七个来源逐项漂移拒绝通过。这不是第三方审核或原客户端样本。另可执行 `python -B tools/client72_sdk_final_route_classifier.py --verify-sources` 核固定来源门禁，输出固定状态，不接受或输出 URL 参数。

该门禁直接固定旧白名单源码、其私有 implementation spec、冻结清单、两份原审核结果、40108 原 328B 和原问号 literal 证据，共七份；固定完整 SHA 见新工具的 `PINS`。它验证这些直接输入，并不声称重新审核冻结清单中的全部历史下游输入，也不重开客户端 PE、执行目标 CPU 或构造托管对象。读取已核源码的 AST，不导入或执行旧工具；已核 literal/spec 只作为纯 JSON 数据读取。

本项未启动现场、服务或客户端，未修改运行代码、配置、资源、旧 profile 或预算，无需部署。原 PostRequest 的 33107 body/pdata、入口身份及 hook 生命周期、自然请求/HTTP/认证和 D-02 仍未验收；客户端验收增量为零。主线负责独立审核、Project/Harness 和提交。

主线2026-11-11独立执行18项测试、固定来源CLI及人工driver（8正例、1090逐字节扰动、7漂移）全部通过，接受纯分类/直接来源绑定子出口。未据此证明真实消费者或网络/认证。
