# E-01/R3-PROUD-BASE-PREFIX-REFERENCE-TESTS

2026-11-11：对普通文件前缀参考工具进行独立人工字节样本测试。目标工具和规格只读；本包不读取客户端、PE、实际 payload 或 86 个来源，不运行原生代码、模拟器、服务或数据库。

规格 `terra-reference-spec.zh.md`：8283 B，SHA-256 `311bf6644f362d9aa602406b8dfe5b1e72485590924a37eb88437525a47e8a8d`。最终受测工具 `tools/client72_d02_proud_base_prefix_reference.py`：SHA-256 `14063c0ceb94feadfac1ee5dabda6e4f983c64fb5eb3692583dd4c7e8b3f764e`。测试脚本 `tools/test_client72_d02_proud_base_prefix_reference.py`：SHA-256 `b3b340d4c4b4b8f396ac543f31062e591fecedd1352d8c241a923fd926bca9a0`。

执行 `python -X utf8 -B -m unittest -v tools.test_client72_d02_proud_base_prefix_reference`：8 项通过，失败、错误、跳过均为 0。Python 为本机 host Python 3.12。输入和预期是人工固定字节与结果，不调用其他解析器生成期望。

覆盖：prefix 缺省与三个零长度/计数；双元素缺省、各单独 presence 和双 presence，2/6/10 字节及 `a090` 的 32 位 carry；非零高位枚举；NaN、正无穷、负零输入 bits 与始终未知的 stored DWORD；base list 模 2^32 加法和逐项 XOR；字符串 QWORD 独立加法、溢出不跨组、尾余数；物理宽度不足与原 start check 分离、已推进 cursor 和先前枚举值；候选边界只标记不截断；高位 count 明确 partial；隔离临时目录中已有输出排他拒绝且原内容保持，且不触发 preflight。

人工测试开发时曾将 18 字节字符串样本的长度字段误写为 19，首轮 1 项失败；修正夹具字段后重新执行，最终结果如上，受测工具未改。失败属于测试夹具错误，不是被测程序缺陷。

初审工具 `45fec92e330d3d929e6e90b6aa682d4ef64e8f2c36d66c67c0e0dbfe146418d2` 存在输出证据缺口：对 `376c 9819ef7a 6091 cc1c113a 29d8`，结果为 partial、cursor 12、枚举 `fedcba98` 后 float 的原 start 通过但物理全宽不足；存在的 enum token 保留值，却未明确标示条件路径中先前的 receiver+0 直接 DWORD 写入。主线确认并修复后，本测试对最终工具重跑：存在和缺省 enum 均有 PC `14ee1ea9a`、offset 0、width 4、对应 bits、`nativeExecuted=false` 的条件写入记录；反例中该记录保留，float 不推进。它表示规格条件下的原指令事实，不是已实测的 typed 对象或已执行的原 helper。

范围仅为条件文件语法和所列人工夹具；并未完成实际选定样本两次 fresh/主线第三次、自然 backend/mode、typed receiver/allocator、原生 parser、完整行或 63831 后续字段，更不证明资源、Avatar 或 D-02 客户端验收。`next63831InputCursor=142` 只适用于固定样本及规格条件，非本测试观察到的实际游标。主线负责固定样本复核、共享 Project/Harness 更新及提交；本包没有部署或重启。
