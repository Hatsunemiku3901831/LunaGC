# E-01 / R3-PROUD-BASE-PARSER-WINDOW（2026-11-11）

## 范围与结果

本项按 root 批准的唯一 source 规格执行，取得 owner59500.method493918 在 `14d5e2680..14d5e2eb1` 的完整 2097B 静态原 body。两次独立 Python 进程分别解析 GAME_PATH、完成五输入门禁、固定 caller 与声明门禁、新取 AOT cell 和按所得 P 算出的唯一槽；各八原件逐 byte 相同。完整 body SHA-256 `bfdb81e46bcd151507ccc2ae8d2c58e71c1922551be67b19f67a73b785958785`，连续解码 483 条。此结果仅建立静态来源与边界，不解释 reader 消费、allocator、dispatch、字段或返回类型，不证明自然模式、native parser、resourceReady 或 D-02。下一步由 Sol 对已存 body 和既有上下文解释准确消费；若有未闭必要边，只提交该单一边的来源请求。

执行前的中文计划保存在私有任务目录 `plan.zh.md`（1053B，SHA-256 `668e13417442669c4cce139aaea794eed28c3b1e1d570442eea19b436a387af1`）。唯一规格 `next-base-parser-source-request.zh.md` 4888B / SHA-256 `cbd756cc50607ec2f9d2ff2a48c1319000181f6ad54a258d8e57649765f36886`；root 第三审核 `main-third.private.json` 6382B / SHA-256 `c49582289209087627a9af3b7ca23097c81aa1f8d89e20cddd162471e18b31a1`。固定 caller 两份各7674B / SHA-256 `949868da171fd252ad9f8486d1a20bf5c211dbdc71065106db7775b6b18e682b`，又逐 byte 对照本次 PE 同完整 caller；核 `149f0c219 488b4940`、`149f0c23d 4c89e9`、`149f0c240 4889fa`、`149f0c243 e838646d03` 和 E8 目标。metadata 唯一声明 flags `0x3c3`、slot7，参数 class42005、class11476 `MoleMole.NativeConfig.INativeAllocator`；返回类型保持未知。

## 输入及目录选择账

每次新进程完整哈希五文件并对照固定 profile，profile SHA-256 `a3b9eda630942ba8479f713fc1692c7eb7b05198ae382dfe878ecc963051fd5d`：

| 输入 | 字节 | SHA-256 |
| --- | ---: | --- |
| YuanShen.exe | 450927000 | `8b014f64b5dc12fbc476e0c362b053abfdd7a1378cd282e4ad81057621dbd6b5` |
| global-metadata.dat | 83644188 | `45dc7a44dc9fc187146c52f6c33de57e2d1c1dcfec57f8736431553460f6ef2a` |
| startup-metadata.dat | 4116148 | `b314810840ca6f495822c9c44483dde2394b9f09ed3c6b13c2782092f04d5105` |
| AccountPlatNative.dll | 9360280 | `adecdf0bd5442e321b1b66b0ca9e98bef76d5067326aa4add5b75266055a6c25` |
| Astrolabe.dll | 2614168 | `87e0a005ac46515156636af2120d3f824bcd28d69253e7d7235b959b54a0b442` |

为完整哈希，五文件均整文件读取；PE header 由固定 `extract_client72.PE` 解析，DOS `e_lfanew` 定位 NT header，取异常目录并按 PE 节区转换 VA。header 原 bytes 未另行落盘。新取 cell `1422f9950` 得 P `1428a54d0`，只再取 P+493918×8 = `142c69fc0`，槽原 QWORD 指向 entry；旧 P 不是输入。异常目录经 header 定位并完整扫描 `RVA 1a4e6a40`、14344080B、1195340 条，完整目录 SHA-256 `5895dbeef8fe13458720889e318ba64f3fc9c5d3ca03bf01b0adc70eee16c08d`。唯一 exact 与 containing 为同条，记录 RVA `1aa13108`、extent `[14d5e2680,14d5e2eb1)`；保存该 12B pdata 与 pre-body 后才读取 body。这里明确发生完整目录扫描，不声称只读了12B。unwind RVA 为目录记录值，未读取 xdata。

## 原件与复现

私有 `local/migration72/d02-resume-20261110b/c-proud-base-parser-window/fresh1/` 和 `fresh2/` 八原件同名同内容：

| 文件 | 字节 | SHA-256 |
| --- | ---: | --- |
| boundary.private.json | 5005 | `95fbb3fda09db2d6686dafa447c62ef2f789b1a3094e8baac32922250497ef5d` |
| cell.bin | 8 | `68f5f1908ac9f789c3fc0b5e71c5b8af00962605561f7b8ea2cc4718f00a83da` |
| slot.bin | 8 | `b7589d1c5074c4ea2b4cb535cabfa6d2957c04304c79e5106f8692f83f2701d0` |
| pdata.bin | 12 | `5461a6b90614c54d0414b90ded2a68d45f3d87319dbd88be8401f6942ff0e8d3` |
| pre-body.private.json | 5212 | `0bd7e0fb4494daf55189a51bf8d55da6eb4d42eea021a1e510ec79d6e65011be` |
| code.bin | 2097 | `bfdb81e46bcd151507ccc2ae8d2c58e71c1922551be67b19f67a73b785958785` |
| code.asm.private.json | 57867 | `392c0c2af0697311193605c753c4e273a3110771ea5632b2862e04b4b607c82c` |
| extraction.private.json | 73567 | `cb75c6b898d94354ad59fadbda5fe0009c72f357d5207439a9980e52059ad689` |

离线 `audit.private.json` 71756B / SHA-256 `8bb6e07bd43a266e9d8ecd6e320d624d549120fa93eb3c41d49a2d313ffe9ba8` 重解码并核对全部原件、来源、pre-body、目录选择、双 QWORD 和 pdata。完整清单包括 48 call、84 branch、29 RIP 引用、168 显式内存操作数、73 隐式栈指令；额外从已存 asm 离线筛选隐式栈指令与显式 rsp/esp/rbp/ebp 引用共105条，`stack-inventory.private.json` 13810B / SHA-256 `fbd414abf7468eef4b45e7c081fe797f155ba07b65eb4dd2d3f758f101512458`，不将寄存器使用解释为语义。

实际命令，均 exit 0：

```powershell
python -B -m unittest tools.test_client72_d02_proud_base_parser_window -v
python -B tools/client72_d02_proud_base_parser_window.py --output fresh1
python -B tools/client72_d02_proud_base_parser_window.py --output fresh2
python -B tools/client72_d02_proud_base_parser_window.py --audit fresh1 fresh2 --output audit.private.json
python -B -m unittest tools.test_client72_d02_proud_base_parser_window -v
```

最终7项测试覆盖来源身份、pdata缺失/重复/重叠拒绝、4096B上限、槽绑定、连续解码及1024条上限、清单、两副本同时篡改 pre-body 的拒绝。采集工具 SHA-256 `3c468f29ef2fffd64e7114bf3161566b9995ef2f3d2aa673cca1131fe047642c`；测试 SHA-256 `77cdac1a9d69b62b5292a315207184b93688796f63f8256465fe18267084be43`。未发生来源漂移或安全门禁事件。没有执行目标代码、读取新 payload、xdata/邻 body/literal/tuple、现场/进程/REA/网络、构建、数据库或 Git；客户端验收增量0。Project/Harness 和本地提交由 root 统一处理。
