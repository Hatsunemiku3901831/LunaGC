# F-03d3s2 / CONFIGMETA-FIELDS-BIN-HEADER

2026-11-11：在已验真实 serialized 对象和脚本身份上，按 metadata 原声明序读取该实例的 182 个字段；以已验 consumer 对应的字段 index 连接第二个 UInt64，到固定资源索引和物理源，再限定读取其完整 header。本项未执行客户端、资源 reader 42005、payload 变换或 codec，未解码物理文件的 payload；整文件只为核对身份顺序流式计算 SHA-256/MD5，原始落盘读取范围仅前 13,737 字节。结果不代表自然 loader、Scene 或 D-02 通过。

## 字段与 source 连接

接受主线第三份目录身份结果及其独立审计和全部固定 pin；固定 ConfigMeta 对象位于 content offset 4384，长 330612，标准前缀后游标 44。该 Unity 文件没有 type tree；本项对这个已绑定实例采用公开、非 static、非 NonSerialized 字段及数组/可序列化值结构的桥，不能自动推广至其他脚本。metadata 的自定义父类 78929 无字段；77121 声明序 182 字段 index 386045..386226 全部 public PathHash77122 数组，77122 依声明序是 public UInt64 origin、bin。每数组按 little-endian `i32 count` 和 `count` 对 16 字节 `(u64,u64)` 读取，不使用 runtime offset，不加 UInt64 对齐或 boxed 头。结果为 20,615 对、2 个空数组，精确到 object EOF：`44 + 182*4 + 20615*16 = 330612`。逐字段 count、开始/结束、完整 SHA 和所有原序 pair 存于私有结果。

按已验 consumer 的 metadata index 386052 选择 ordinal 7，名称只交叉核对。实际数组 object `[72248,72268)`，count 1，完整数组 SHA-256 `8ce2e7973c43e9dd4e2b787abf76a680844ac1a04eb6cb5417638a9537ae3a4d`；首 UInt64 origin `644cf04bac35cb7f`，第二 UInt64 bin `ce23884493554189`，后者实际 content offset 76644。沿原 source chain 中第二 QWORD 的调用方关联 reader，不能据此声称已恢复 reader 返回声明类型或自然执行。

以第二 UInt64 查询两个固定完整资产索引 section，复用已验 locator，完整走到各自 EOF 并保留 variants：只有 `31049741` 一条 `[206928,206942)`，记录 SHA-256 `551d1828e00afdc6d998b0aa48349bc9c378e3257deaff7c3df18292648bb109`，variants 仅 `(0,1)`。reference packing 得 bundle 1、entry `44935541`、packed `4941386953816801281`；两份 location 全量查找仅 `31049741` ordinal 0 一条 `(24230448,1,0,0,0,7)`，同 block 无其他 location。这是固定索引的唯一选择，不是自然 selector 的运行观测；未以猜测路径或名称计算 hash。

## 物理文件和 header

`pkg_version` 固定行与唯一 location 关联的物理文件为 139,134,667 字节；完整顺序 SHA-256 `cb1a68de51f3baeba7afc771284d46410a7b6cb993aa3d085a0c8a7813e14768`，MD5 `edc854321f0e9d8c64fd6e80426fe657`。原 12 字节签名指示 v5、header 13709；index 的 A/B 为零不视为空资源或长度。前 13737 字节的 SHA-256 `c74880c474ed41ae719f438ea2c805bf9a4e8a443faa8c0ebf623a132176bca0`。

五 stock、旧 header 工具与依赖、snapshot 和原 archive table 均按固定 pin 验证，复用已有 `BlobProbe` 允许范围与原 header 变换，不增加 callee 或 model。实际 header 输出 13709 字节，SHA-256 `5f430aa919b19dac878a66f8da5b36b5b7fadf5bb97ff5256b250367654e1b21`；AL、guard、IV、128 字节后未变换尾部及 cookie 合同成立，0 modeledCalls / memoryModels。独立旧 `parse_header` 以真实文件长度读取，14 次原 scalar/block/content getter 与 parser 一致。

实际 header 给出 compressionTag 3、blockBytes 131072、blockCount 3399、lastBlockBytes 38524、uncompressedBytes 445421180、logicalBlobBytes 139134667、fileTailBytes 0。一个 content 条目 offset 0、size 445421180、flag true，header 内名称 `CAB-53126bc4d34ebc277960cf0c743cffbc`；这仅是 header 内容，不把 bin key 解释成某名称路径。完整 block ends 及 getter 原件保留在忽略目录。此处到完整 header 停止，未提取 payload 前缀或执行解码。

## 实际验证与下一步

- A/B/C 纯数据 `fresh3`、`fresh4` 四份结果逐字节一致，原件审计通过；主线第三份及审计亦通过。`fresh1` 因探索文件仅部分字段含 `firstPairs` 导致工具 KeyError，已保留；修正为仅对存在的对照项比较后 `fresh2` 成功，最终使用增加审计后的 fresh3/fresh4。
- D `fresh2`、`fresh3` 四份结果逐字节一致，纯原件审计通过；主线已实际完成第三 `python tools/migration72/scene_mplevel_actual_configmeta_fields_header.py --output main-third`，四件同fresh2；`--audit fresh2 main-third --output audit-third.private.json` 通过，660B/SHA `3968f8a91f5940a41839292c0ecae56174d8fe88f40c4c64d7c130b4e0ccf07e`，审计不再次执行native。`fresh1` 为串接主线第三验收前开发记录，不用于最终对照。
- `python -m unittest tools.migration72.test_scene_mplevel_actual_configmeta_fields tools.migration72.test_scene_mplevel_actual_configmeta_fields_header -v`：6 项通过，覆盖数组 stride/EOF/高 64 位、负数/截断/余量、声明顺序及选字段、错误 origin、索引重复/多 variant/location 歧义、pin 与物理源签名/哈希，以及 header parser 实际文件尾和界限。

主线独立重跑上述6项测试通过；A/B/C第三四件与fresh3一致，纯审计663B/SHA `8ade21b82a641b02ca4cfc0067ce4781f057b53e4b2692d7dbab4acd153347c5`。两张卡只覆盖本页列出的实际读取，不包含后续内容解码。

私有材料在 `local/migration72/d02-resume-20261111b/scene-configmeta-fields/` 与 `scene-gadget-bin-header/`。下一步须由主线及 Scene 来源研究按实际 content、serialized 目录及 entry 选择最小压缩块；不能盲解整份大文件。`reader42005Executed=false`、`MPLevelRecovered=false`、`naturalLoaderReady=false`、`SceneReady=false`、`D02Ready=false`；无客户端验收或部署。
