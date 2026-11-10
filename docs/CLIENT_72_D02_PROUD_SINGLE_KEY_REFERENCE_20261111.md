# E-01 / R3-PROUD-SINGLE-KEY-REFERENCE

2026-11-11。固定 key 22101 的条件文件语法现已从完整 payload 连续读到终点。此结果依赖 [base 前缀](CLIENT_72_D02_PROUD_BASE_PREFIX_REFERENCE_20261111.md)、[derived 合同](CLIENT_72_D02_PROUD_DERIVED_FILE_CONTRACT_20261111.md)、[17208 数组合约](CLIENT_72_D02_PROUD_NATIVE_17208_ARRAY_CONTRACT_20261111.md)及[元素合同](CLIENT_72_D02_PROUD_NATIVE_17208_ELEMENT_CONTRACT_20261111.md)的明确条件；它不证明自然 backend/mode、对象分配或客户端执行。

固定 payload 2132778B，SHA-256 `82a8017c6c2868e41e3ce8fc02f8bc04437cb641ec653ed3383c5cc109e25835`。从 A=4 开始，base 到142，选定 derived 到318，数组 header 到322，四个元素各2B到330，尾4B到334，共330B；81个物理 span 连续。候选 B=330 给出终点334，但仅在解析后对照，物理 end 始终为完整文件。尾原 `cefc0c44` 的 LE32 加 `bbf30334` 宽和 `100000002`，低32位为2。

元素依次处理81538/+4、81537/+0；四个 `873f` header 均解出0，两 setter 各条件调用、输入均为0，实际 encoded 值未知。工具每次读取分开记录原 unsigned start、MOVSXD 地址、host 全宽条件和候选越界；失败保留之前推进与条件写，不执行原错误 helper。选定 derived 替代 presence、非零字符串项及未批准计数明确 partial。块使用独立 LE64 组，float 保留原始 IEEE bits；前缀原 partial 与默认边界复用原工具。

`completeConditionalSingleKeyFileSyntax=true`只用于固定 key/固定 payload 的条件文件语法。`completeProudReference`、自然 backend/mode、actual cursor/index/row、typed object、资源 ready、完整 Avatar 与 D02 均为 false；encoded 值与业务 alias 为 unknown。没有为无 reader 的 opaque 存储调用添加文件消费，也没有建立实际对象、批准 row span 或证明客户端验收。

## 测试与复现

源码 SHA-256 `a92e5b45a62befa6de9e2ea2c623c5c0c61cf0c77eb38d16b4fe0876eea2dadb`；测试 SHA-256 `253216a117c32ebc2b0bafae4048f92ef992572a96bcfe297e4b0d668aed2036`。实际运行本项8个和上游前缀8个测试，16个均通过：人工四种 presence、32位顺序与溢出、header/成员短读和先前 copy、数值边界、候选越界、float/LE64、源漂移/排他输出、完整81 span 与替代路径 partial。并未重跑客户端或其它业务测试。

两个新 Python 进程 `fresh2.private.json` 与 `fresh3.private.json` 各82835B，逐 byte 相同，SHA-256 `b7cc56653e2ca1c41f7b8fdcc3495c7568e7e25efe848706628450bceb14f0f2`。报告及计划位于忽略目录 `local/migration72/d02-resume-20261110b/c-proud-single-key-reference/`。使用此前不存在的 basename，报告排他创建；root 独立第三入口如下：

```powershell
python -X utf8 -B -m unittest tools.test_client72_d02_proud_single_key_reference tools.test_client72_d02_proud_base_prefix_reference -v
python -X utf8 -B tools/client72_d02_proud_single_key_reference.py --output main-third.private.json --audit fresh2.private.json
```

初次直接脚本启动发现 `tools` 包导入路径问题，在任何报告创建之前退出；修正后 `fresh1` 属于中间源码 epoch，保留未覆盖，最终报告为 fresh2/3。审查原指令后将四个 derived setter 的 copy PC 精确到实际存储指令，而不是使用后继测试 PC。没有新增 PE/code/AOT/pdata/xdata/literal 来源或 CPU/native/emulator/live/进程/网络/Git/构建/DB/部署，纯文件工具无需重启。本卡交回主线统一第三复核、Project/Harness 与提交；完整原生/业务 reference 和 D02 客户端验收仍未完成。

## 主线最终验收与位宽更正

主线首次第三82835B与旧双fresh相同，但独立核查发现base元素header的`transformWidthBits`被按2B wire填成16，与原`U32(rawU16+6f60)`及`00010000` carry矛盾。先加入当前固定样本的两header非零carry断言，真实红测失败并保存`root-width-red.private.txt`；再仅更正该运算标记为32，不改原base工具或数值算法。旧fresh2/3/main-third原件和旧SHA继续保留。

最终新source SHA `89604b1f56fbc986f4e47b69205d06eeaf2f5d9253c9fe30388e26ea41b5bf9b`，test SHA `a893cb7377bec19a0145c7b70066e87042a94ffc773523c73d54a4e835a88431`；16项再验全部通过。fresh6/fresh7/main-third-v3各82835B，逐byte相同，SHA `30b99d804468f9572a54f60b1e3dfc0838d38e55c52e8ae11e285f7fcd1d9a2c`；root独立逐项把81个raw span与完整payload对齐，连续4→334/330B及所有自然/业务false边界通过。冻结前另规范EOF空行，AST未变且旧源码/三原件保留。最终摘要`root-final-review-v3.private.json`留私有目录。本验收只关闭本条件文件reference，不授权任何新资源/语义后继；用户要求当前工作收尾后暂停。
