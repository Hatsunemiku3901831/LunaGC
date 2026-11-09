# E-01/R3-ITEM-ENTRY：String Equals 原入口后继

主线已完成13项只读定向测试及12项独立证据审核，`main-review.private.json` SHA `56ed0eb0ea9e9fb0e865ac2fdc7f6fd929981dddca40f7471e76b682c17978d9`。未再执行CPU，累计仍为两组8call/66完成指令；helper申请尚未采获，不扩大原共同输入批准范围。

本卡完成获批 64B 原入口前缀的两个 fresh 组。每组只执行四个 owned/null case：null/null 和同指针返回 `AL=1`，长度不同返回 `AL=0`，等长不同指针在 `159b6f31e` 的原尾跳执行前停止。没有执行其目标 `159aac320`，没有字符内容比较或自然业务键结论。

10339 入口没有对应 `.pdata` 记录。获批的是最多 64B 前缀，**函数完整 extent 仍未知**；不能命名为已恢复完整 leaf 函数。原记录解码 62B/26 条后留下 `159b6f33e` 的 `418d` 两个不完整字节。本工具只允许从实际 entry 可达的前 48B/19 条原指令，后续字节和不完整尾部一律不执行。三个 ret 证明本次窄返回，不提供完整 extent。

## 来源与既有共同图

全部自然 receiver/selected 函数/MethodInfo 仍来自新联合 raw SHA `1a2e7509f6149f995cc0cc5c4ddc8f0c3f8acd297afcfc27c1eb9d5caee9c63e`。旧共同图边界 SHA `b0475a9154e3bd5a5213c41648dfe0f0a0bc7d8737cbd55a55a2cfcbc763dd13`、主线第三审核 SHA `df49f401b2004d10510cbb3bbc916cfc72d38270d3f9cf5086701fbcf078ca78` 和批准 wrapper SHA `07494634d3d46ee26807e0f96672b3378bd50fa7451786801c988a86b0bea08e` 保持冻结，本卡不扩大许可。

原窗为 `native-successor-windows-main1/string-equals10339/manifest.private.json`，SHA `ba0c1db3122ce7a391bf80fdc67eccef1c2a41ef8369de42a167a22d7b03c6d7`；`code.bin` 64B/SHA `3e7f1a9b1b85839fc21cdcc62450dc10eac165ba2e098fb17da68800cda0bc21`。`pdata.bin` 是空文件，`recordPresent=false`、`functionExtentKnown=false`。主线固定 EXE SHA `8b014f64b5dc12fbc476e0c362b053abfdd7a1378cd282e4ad81057621dbd6b5` 与该轮五 stock 核验属于原窗来源；本代理不读当前 stock 或 GAME_PATH，也不做 PE 新提取。

主线第一次按 `.pdata` 取完整窗的静态失败保留在 `capture-main-successor-windows1.pdata-diagnostic.private.json`，SHA `9cfa253ccd6f7c75bf46c3945a4741deeb58aab275ee48ccd549a54afb611e3b`：该 entry 匹配数0，`codeBytesWritten=false`。之后主线独立批准并提取本前缀；不把首次失败改记为完整函数成功。

固定 metadata 缓存 `local/migration72/d02-resume-20261108/r3-equipment/metadata.private.pkl`，SHA `df52eb70fe8e70dfecd1059e898d15e288cfa98bcefc78bcaabf753ae150349f`，证明：

- ordinary1243 `System.Collections.Generic.InternalStringComparer.Equals` 为 method10339、slot8、注册入口 `159b6f300`。
- String293 的 `m_stringLength/int32` 是 instance `+10`，`m_firstChar/char` 是 `+14`。
- 尾目标为 String293 的 `EqualsHelper` method2908，静态 flags `91`、两个参数、注册入口 `159aac320`。

固定签名缓存 `local/migration72/evidence/client72-signatures.json`，SHA `28377e7a8d3c891cd61be97ddb74f8502a22f2f2f039d6a91dba3b092d7822fe`：10339 参数 `x/y`、2908 参数 `strA/strB` 均是 typeReference33，即 String14/byref0。该缓存明确 return type 尚未恢复；本卡记录原指令实际写出的 AL，不虚构 metadata 返回类型。已有 String hash 原窗 `159a8da20` 在固定 `r3-gadget-config-key/first.json`（SHA `10085ff137a6d918a43adaa2cf27754e4b5c638c9de12c3119705852e65e5abd`）证明字符从 `+14` 按 word/2B 连续读取并检查 NUL；本卡仅静态复用这些布局证据，不执行 hash。

## 原入口静态路径与准确尾 ABI

原代码先检查 RDX/null，再比 RDX 与 R8，检查 R8/null，读取两个 `+10/dword` 长度。长度不同到 `159b6f32a xor eax,eax; ret`。两地址相同到 `159b6f32d mov al,1; ret`。RDX/null 分支原 `test r8,r8; sete al; ret`。

等长不同地址时，原 `159b6f318 mov rcx,rdx`、`159b6f31b mov rdx,r8` 已实际执行；随后 `159b6f31e e9fdcff3ff` 尾跳 `159aac320` 尚未执行。准确寄存器边界为：RCX=原 x，RDX=原 y，R8=原 y 未变，R9=本轮实际10339伴随 MethodInfo 未变，RSP和原 caller return 未变。没有新 call frame，也没有为 helper 安装另一个 MI；不能把尚未改变的 R8 擅称 helper MethodInfo。helper 是否消费这些额外 carrier，需其原代码回答。

每个 case 的 RCX 初始为本轮实际 Default object，R9 为本轮实际伴随 MI；原10339前缀没有读取它们。函数按本轮 ASLR 地址执行，所有 captured 指针原样保留。没有借1243充当 String class，没有写 captured slot/flags。

## Owned case 与实际结果

两个 owned 地址仅提供已证的长度和 UTF-16 字符字段存储，未提供 String class 或对象 header。页填充即使物理为零，也不成为有效输入；若原指令读取未提供 header/字段，hook 立即拒绝。实际只读四个长度 dword，无 class/header/字符读；因此这些 fixture 不能称自然字符串对象或完整托管分配。

| 每组 case | 原指令完成数 | 实际结果 |
| --- | --- | --- |
| null/null | 5 | ret，AL=1；保留上层 RAX sentinel，按低字节记录 |
| same-owned-pointer | 6 | ret，AL=1；不读取任何 owned 字段 |
| owned 长度1/4 | 11 | 读取两个真实 fixture 长度，ret，AL=0 |
| owned 等长、不同地址 | 11 | 两个长度读后，原参数搬运完成；在尾跳前停，无 bool 结果 |

每个报告恰4 calls/33次观察/33条完成；每个连续 call 硬限2M。host账本278请求/10800B，包含原 raw 审核69/2704、完整 collector/时钟重放69/2704、4×34精确终读/5376B及四个 owned length读/16B；caller4读/32B。限额仍为4096/65536/单读208、caller16/128。没有旧 getter/allocator call，没有 host bool/锁/分配成功模型。

两份 `string-equals-successor/first.private.json` / `repeat.private.json` 均 **113056B/SHA `32fd295fc85a82165def82fb990b0eba915fce80767e6c39768ebd9e49701fcc`**，逐 byte 相同。累计恰8 native calls、66次观察/66条完成、6次原 ret、2次未审尾跳边界停止，0 emulator error；理论指令上限合计16M。host556/21600、caller8/64。没有开发 native preview、丢弃 case 或重置计数，两个正式组所有 argv/UTC/退出码/stdout/stderr/source 副本均保留。

## 验证、最小后继与交付

新增 `tools/client72_string_equals_successor.py` 11876B/SHA `f48f428c1d0d59310331eddb1f100c07a1e329f819211ad17dc2a6efb65f0059`，对应测试8930B/SHA `b83474b13db63f925282d199f3bc9514c570725c582db6bca749c9df7e2b8151`。13项定向检查分别针对两报告通过，测试只读报告/来源并做 host hook 负例，0额外 native call：

```powershell
python -m unittest tools.test_client72_string_equals_successor -v
```

覆盖64B/pdata未知、完整 source hash、String布局/签名、AL与高位返回区别、三个原 ret、实际尾参数与尾跳前停、只读长度、不提供header的拒绝、未审相邻字节/不完整尾拒绝、136精确终读、累计预算和父项 false。`source-closure-audit.private.json` 核46个完整来源 pin全部匹配，不读当前stock。

最小后继是 `string-equals-successor/minimum-helper-window-request.private.json`，SHA `d2b9b894e3c781d1fb58949d95690e3a882c8cf500de8cd1ef7feff3a28370a0`：只申请覆盖 `159aac320` 的一个准确原窗及真实 `.pdata`，建议最多8192B+12B；end 当前未知，若无记录先准确返回，由主线单独决定 prefix，不自动fallback、不追callee、不扩 captured 图。批准并取窗后，才可从等长 owned 路径继续原尾跳并暴露 helper 首缺。

完整字符相等/不等、FindEntry、自然 caller/业务键、配置/ability、Default 创建、Avatar/g2/D-02 均未晋级；旧共同输入范围保持。只新增本 tool/test、本页及独占忽略证据，旧工具/文档/合同/报告只读。未执行 Git、当前stock、现场/进程、REA/网络、构建/JVM/DB或部署；本地提交、Project/Harness与交接由主线处理，离线工具无需 GS 重启。
