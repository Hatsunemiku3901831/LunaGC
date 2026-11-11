# F-03d3s2 / CODEC9-UPSTREAM-REUSE：上游解码器检索

2026-11-11，按冻结计划开展定向 GitHub 检索。这里记录上游的声明与源码观察，不表示目标 tag9 已被解码。目标两块预期输出分别为 262144 B、72852 B；它们的原始内部流类型、历史依赖和 checksum 使用情况仍未知。本批没有执行目标原生路径、上游代码或其测试，也没有取得目标输出。

## 结论分层

| 状态 | 结果 | 本任务的意义和限制 |
| --- | --- | --- |
| 已声明支持 | [powzix/ooz](https://github.com/powzix/ooz/tree/05038060aa68f9187ae9923b2388ca8db40e58d1) 和 [oozextract](https://github.com/lvlvllvlvllvlvl/oozextract/tree/8acaeb2bc375dd56c413a83aae4e87156136fdac) 的 README 声明 Kraken、Mermaid、Selkie、Leviathan、LZNA、Bitknit 解码；[koodle](https://github.com/UselessMnemonic/koodle/tree/4d1f2aea56b2a3cca69c91cab4acef08693931e7) 声明为 oozextract 的 Kotlin 移植并提供 JVM 入口。 | 只是项目对其格式的声明，不说明目标块属于其中任何一种格式。 |
| 源码实证 | 三者均有接受压缩流和调用方给出的输出容量的解码入口，具体合同见下文；代码和测试断言均已固定到 blob。 | 静态阅读不等于编译、执行、目标兼容或测试通过。 |
| 仅候选 | koodle 的增量 `OozDecoder.decode` 能返回本次输入消耗、输出写入和状态；oozextract 的 `Extractor.read` 可配合调用方控制的 `Read` 输入位置检查消费量；ooz 的 `Kraken_Decompress` 自行要求输入完全消费。 | 当目标输入变换、块边界、历史关系及可用许可证明确后，才可选适当入口做不同于目标原生调用的对照。三者同一移植谱系，不能把它们之间的相同结果当成多套独立实现。 |
| 不适用 | [pyoodle README](https://github.com/Dualgame/pyoodle) 说明它处理 `COMPRESS` 容器，内层解码仍需自备专有 DLL；[liboodle README](https://github.com/LunaticInAHat/liboodle) 讨论独立的 Oodle1 方案。 | 前者不是独立源码解码器；后者没有证据关联当前目标流，不能仅凭名字认定适用。 |

## 固定来源与分支

三个仓库各以 GitHub branches API `per_page=100&page=1` 查询，响应无后续 `Link`；分别只有下表默认分支，因此本次没有可进一步分析的非默认分支。递归 tree API 均返回 `truncated=false`。外置研究根中的三个浅层、无检出克隆分别固定到下表提交；按项目要求不运行脚本、不安装依赖、不下载专有 DLL。原始 API 响应和文件 blob 校验结果保存在忽略目录。

| 仓库 / 分支 | commit | tree | README blob | 许可证来源 |
| --- | --- | --- | --- | --- |
| powzix/ooz `master` | `05038060aa68f9187ae9923b2388ca8db40e58d1` | `1d8725b181fb9b3946c0949b0fef8a2b17feccc1` | `25259d5bd394d94dce0d3d4ed5c5610886c5eb49` | 固定树没有独立 LICENSE blob；[kraken.cpp 文件头](https://github.com/powzix/ooz/blob/05038060aa68f9187ae9923b2388ca8db40e58d1/kraken.cpp#L1-L17)，blob `a4983d6de308f92c4a7f71f8d0f87bb2bdc3cdd2`，声明 GPL-3.0-or-later。 |
| lvlvllvlvllvlvl/oozextract `dev` | `8acaeb2bc375dd56c413a83aae4e87156136fdac` | `cdb6c6dbb82f97fcd9072fa20ec07def9c88c0d3` | `c1244a74450fda418e829c301dd373e39f10c4d5` | 固定树没有独立 LICENSE blob；[Cargo.toml](https://github.com/lvlvllvlvllvlvl/oozextract/blob/8acaeb2bc375dd56c413a83aae4e87156136fdac/Cargo.toml#L1-L8)，blob `2d056ced514514b6bd53fbb2099c69815427d438`，声明 MIT；仓库描述称其为 ooz 移植。 |
| UselessMnemonic/koodle `main` | `4d1f2aea56b2a3cca69c91cab4acef08693931e7` | `9a5f9e6916019bb28c6db3617e7f8d46508fa877` | `a2a84a9897ac4609ef15d0e8f4c8cf8ddcdde8fb` | [LICENSE](https://github.com/UselessMnemonic/koodle/blob/4d1f2aea56b2a3cca69c91cab4acef08693931e7/LICENSE)，blob `de0d362de846b21b78a21547d3035ad242a35e79`，声明 MIT；README 称其移植自 oozextract，gitlink 指向上表 Rust 提交。 |

不同声明和移植关系的授权边界尚未核清；不能仅凭 Rust 清单或 Kotlin LICENSE 认定整条来源链均可按 MIT 融合。是否复制、链接或分发任何上游源码，应先明确适用授权。此处只记录文件声明，不作法律结论。

## 入口、状态和边界

### ooz C++

[Kraken_Decompress](https://github.com/powzix/ooz/blob/05038060aa68f9187ae9923b2388ca8db40e58d1/kraken.cpp#L4130-L4153) 接收 `src, src_len, dst, dst_len`，创建状态，循环调用 `Kraken_DecodeStep`，完成后销毁状态；成功返回输出字节数，失败返回 `-1`，并要求剩余输入长度为零。名称虽含 Kraken，`DecodeStep` 源码还分派其他已实现类型，具体由输入 header 决定，不能据此推定目标类型。`Kraken_Create` 分配 16 字节对齐的状态及 `0x6c000` 字节 scratch。源文件注明输出可能越过目标末尾，测试驱动分配 `SAFE_SPACE=64`；README 明确称其不 fuzz-safe。分配失败、保护和输入可信性需作为后续实际调用合同处理。

[Kraken_GetCrc](https://github.com/powzix/ooz/blob/05038060aa68f9187ae9923b2388ca8db40e58d1/kraken.cpp#L567-L570) 是 TODO 并返回 0，尽管调用处分支会比较 checksum，不能声称它完成了真实 checksum 校验。仓库含 Windows x64/Win32 Visual Studio 项目，自有解码路径可审；README 中 DLL 是可选外部路径及压缩用途，本批没有构建或验证 Windows 可运行性。

### oozextract Rust

[Extractor](https://github.com/lvlvllvlvllvlvl/oozextract/blob/8acaeb2bc375dd56c413a83aae4e87156136fdac/src/ooz/mod.rs#L79-L135) 维护 header、可选 LZNA/Bitknit 状态、两个 256 KiB 缓冲区和动态输入缓冲；`new()` 创建，Rust 所有权管理回收。`read_from_slice(input, output)` 返回 `Result<usize, OozError>`，数字是写出的输出量；外层循环填满给定输出或停止，不返回 slice 消费量。需要检查全部输入消耗时，候选为 `read(&mut Cursor, output)` 后核对 cursor 位置，而不是把返回值当成输入量。量、历史和错误合同仍需实际测试。源码的 checksum 分支尚未实现校验。

[测试源码](https://github.com/lvlvllvlvllvlvl/oozextract/blob/8acaeb2bc375dd56c413a83aae4e87156136fdac/src/lib.rs#L249-L290) 遍历 `testdata` 并与 `verify` 字节比较；固定树含多种编码样本及 restart 等附加样本。这是测试设计证据，本批没有运行测试。

### koodle Kotlin/JVM

[Decoder.decode](https://github.com/UselessMnemonic/koodle/blob/4d1f2aea56b2a3cca69c91cab4acef08693931e7/src/commonMain/kotlin/com/uselessmnemonic/koodle/Decoder.kt) 返回 `bytesConsumed`、`bytesWritten` 和 `NEEDS_INPUT`、`NEEDS_OUTPUT` 或 `FINISHED`，调用方持有输入/输出数组，错误后丢弃实例；支持以 `endOfInput` 表明最后输入。`OozDecoder` 需要外部给定准确输出大小，维护 block/quantum、历史和特定 codec 状态，默认历史容量 64 KiB，实际距离超过容量时抛出 `UnsupportedOperationException`。源码按需分配两个 quantum 缓冲和工作区；checksum 字段被消费但不验证。

`decompress` / `decompressInto` 便利入口会忽略末尾输入；完整边界对照应使用增量入口并分别核对完成状态、全输入消费和精确输出长度。[ReferenceCorpusTest](https://github.com/UselessMnemonic/koodle/blob/4d1f2aea56b2a3cca69c91cab4acef08693931e7/src/commonTest/kotlin/com/uselessmnemonic/koodle/ReferenceCorpusTest.kt#L110-L149) 的源码按分段输入输出调用，并断言上游样本输出逐字节相等、输入消费及输出写入量；该语料来自其 oozextract gitlink，并非目标两块，本批没有运行。README 声明 JVM/JDK 17 目标，未在本机编译验证。

## 检索覆盖

先检查已有外置 AstaPS-Resource 克隆及其 README、已固定分支：README 无 Oodle/解码器线索，现有克隆不修改。再执行以下四组 GitHub repository/description/README 查询；查询是 AND 条件下的发现手段，不能视为遍历 GitHub 全部实现。仅对 README 显示可能直接处理压缩流、可审源码并可用于便携 Windows/JVM 的候选深入分析。

| GitHub API 查询 | 返回总数 / 已取页 | 说明 |
| --- | --- | --- |
| `Oodle decoder decompression in:name,description,readme` | 213 / 3 页，100+100+13 | `incomplete_results=false`。 |
| `OodleLZDecoder in:name,description,readme` | 0 / 1 页 | 不能据此排除仅在源码中出现该名称的仓库。 |
| `ooz decoder in:name,description,readme` | 105 / 2 页，100+5 | `incomplete_results=false`。 |
| `Unity Blb v5 Oodle in:name,description,readme` | 0 / 1 页 | 不能据此推断该容器或目标内部 codec。 |

GitHub 索引结果与分页是当时快照，并非逐个读取所有命中的 README；还从定向网页检索结果追到原始仓库 README。最初两个完整克隆在较长等待后仍未收到 pack 数据，已停止本次克隆进程，随后三个指定仓库的浅层无检出克隆成功；没有把未完成的完整克隆称为成功。

## 最小后续对照条件

1. 先由主线确定目标真实前缀变换后的输入、每块边界、header/校验和及是否跨块保留历史。任何算法名均从目标来源或真实结果确认，不按上游 README 猜。
2. 待主线确认实施范围和来源授权后，可优先考察 koodle 增量入口；调用方保留实际消费/写入累计、终止状态、错误和输出摘要。若选择 Rust，用 `Read` 输入位置补足消费量；若选择 C++，遵守其额外空间和输入条件。每条都必须先用上游固定样本及错误/边界建立实际行为，再对目标两块执行。
3. 目标原生 wrapper 仍须完成自身状态创建、真实解码和收尾；即便上游返回预期长度或字节一致，也只构成特定输入的独立对照，不代表自然资源 loader、ConfigMeta 身份或客户端验收通过。
