# F-03d3s2 / CONFIGMETA-CODEC9-OOZ-PAYLOAD：固定外置研究准备

2026-11-11：按私有冻结规格准备固定外置研究 CLI 和忽略目录编排。此项是独立 GPL 研究，不接入产品。主线以固定 exe 实际执行公开向量、明确错误边界及两个固定目标块；限定 payload 的解码结果见下文。该结果不证明目标原生生命周期、自然 loader、Scene 或 D-02 ready。

固定来源为 powzix/ooz commit `05038060aa68f9187ae9923b2388ca8db40e58d1`。按规格核对 Git blob、长度及 SHA-256，将四份原始源码和唯一所需 `targetver.h` 原样保存于仓库外研究根；未将 GPL 算法源码复制进仓库。固定 `xml.kraken` 与 `verify/xml` 分别以指定 blob 获取并核验，其 SHA-256 分别为 `af3b00ad19f8d649fce56027c01abc19c4021fb763aefa2d6ce4be51e1bf0cf8`、`0e82e54e695c1938e4193448022543845b33020c8be6bf3bf3ead2224903e08c`。公开向量仅 4 字节 LE 长度匹配参考明文 5,345,280 字节且后继头符合指定格式；不是通过尝试解码选择偏移。完整文件 pin 及 blob 记录在忽略目录 `scene-configmeta-ooz/source-manifest.json`。

外置驱动以预处理改名上游 `main`，独立调用 `Kraken_Decompress` 和新状态的 `Kraken_DecodeStep`，保留每步长度、头和总量，并检查输入保持、16 字节双 guard、64 字节上游允许的尾部区域和双输出相等。未调用上游 main、LoadLib 或目标原生 codec。初次构建因驱动辅助函数名 `boolean` 与 Windows SDK typedef 冲突失败；主线保留日志和原驱动快照，授权仅将该标识改为 `json_bool`。主线以 MSVC 19.44.35229.0 / x64 完成第二次构建，exe SHA-256 `bad19756ff3672b4d4482bb643d0f3e352764974ba9dbb0416296cb83d40986a`，驱动 SHA-256 `330cefc8dfc64331e220ccb3dbf38093d5acd012d6628b650c1d83233ff07c03`。构建日志保留在忽略目录；构建本身不是执行验收。

唯一入口 Python 编排在调用前一次核对请求、全部源码、驱动、该 exe、两个固定目标块和公开向量的 pin，限定 fresh 目录及已批准 expected；随后依次公开向量、原始目标块追加一字节及截去一字节、目标 0 和目标 1，遇失败保存现场即停止。C++ 端提供参数与基本尺寸边界，固定文件和尺寸组合由 Python 唯一入口负责，不能绕过该入口将其当成通用服务。测试运行 `python -m unittest discover -s local/migration72/d02-resume-20261111b/scene-configmeta-ooz -p test_driver.py -v`，四项准备阶段门禁测试通过，未调用 decoder；两 fresh 原件审计入口同目录 `test_driver.py --audit fresh1 fresh2 audit.private.json`；执行结果由主线记录。

主线实际执行 `fresh1`、`fresh2` 与 `main-third`，13 份对应产物逐字节一致；4项离线门禁测试通过。准备代理未运行 decoder；主线独立原件审计已通过，1843B / SHA `97491a0b2dce66bfb070c4534de66182d36729ac0991307849ba72669b41ea90`，当前 decoder 调用数0，重算输出、步进、边界和拼接。目标 native 生命周期、自然 loader、Scene 和 D-02 均仍未验。本项不修改运行代码、Proto、资源、数据库或部署。

## 主线实际执行结果

公开固定向量原始流完整消费并产出 5,345,280 字节，逐字节等于固定 `verify/xml`；输出 SHA-256 `0e82e54e695c1938e4193448022543845b33020c8be6bf3bf3ead2224903e08c`。两个固定目标分别创建新 decoder，均以单步消费全部输入；`Kraken_Decompress` 返回目标宽度，独立 `Create→DecodeStep→Destroy` 输出与其逐字节相同。两条路径输入保持，前后 guard 完整；本次各自 64 字节尾部区域均无变化，不能推断其必须保持不变。

| 块 | 输入 bytes | 输出 bytes | 输出 SHA-256 |
| --- | ---: | ---: | --- |
| 0 | 212058 | 262144 | `1cb94ea1611a2bd4457d3feb33180e9a0f8f6aa8fa332c89e8aa1b139f2a74c3` |
| 1 | 72360 | 72852 | `e0dc86f7c8ff75b8577521fba3067526c5189a7091f34990fb24c43802f239f9` |

追加 1 字节样本原流完整解码后留 1 字节，`Decompress` 实际返回 -1；截去最后 1 字节样本的 `DecodeStep` 返回 true 但输入/输出均零推进，`Decompress` 返回 -1。两样本输入不变、guard 完整；不是随机或模糊测试。

两块按顺序拼接，不含头和 padding，`content.bin` 为 334,996 字节，SHA-256 `5dbd48d5856e05ee877932dc7282a51ce30e03e4208174fd43c327f3a68f5a5e`。首个 serialized 文件头只读结果为 metadata size 193、file size 334996、version 17、data offset 4096、endian 0；不能由此认定任何对象就是 ConfigMeta。完整 schema 与目录首缺交主线后续核查。

主线随后原样调用两份既有parser：`serialized17`止于原class114/script-specific hash门禁，内置assets目录parser止于engine门禁。实际engine CString为`2017.4.30f1\n2`、platform19、tree0；旧工具均未修改，失败原件留`main-third/`。准确catalog/脚本身份与字段后继交Sol max，不能把这两个预期范围外结果写成payload解码失败或放宽旧parser。

每轮 13 份对应产物为公开向量流、输出及报告，追加和截短各自输入及报告，两目标各自输出及报告、拼接内容和确定性 manifest。它们保留于忽略目录 `local/migration72/d02-resume-20261111b/scene-configmeta-ooz/{fresh1,fresh2,main-third}/`。Python 私有编排、测试及来源清单同处该目录，外置 CLI 源码与构建产物只留研究根；未把 GPL 源码或原始 payload 提交到主仓库。`externalDecoderExecuted=true`，`targetNativeCodecExecuted=false`、`targetNativeLifecycleExecuted=false`、`naturalLoaderReady=false`、`sceneReady=false`、`D02Ready=false`。
