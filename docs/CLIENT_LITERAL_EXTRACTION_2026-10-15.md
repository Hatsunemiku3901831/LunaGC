# 客户端字符串与燃素热修复入口

`tools/extract_client_literals.py` 可以从当前固定客户端恢复字符串字面量，
用于追踪资源名、界面入口和原生调用。支持按编号提取、文本筛选和有界扫描。
源文件通过 SHA-256 校验后才会解析；输出路径不能替换输入或其硬链接。

## 使用

使用 Python 3.10 或更新版本，在项目目录执行：

```powershell
$clientDir = 'C:\Users\InvokerBot\AppData\Local\hoyo\hk4e\versions\current'
python tools/extract_client_literals.py `
  --binary "$clientDir\YuanShen.exe" `
  --metadata "$clientDir\YuanShen_Data\Native\Data\Metadata\global-metadata.dat" `
  --all --contains InjectFix --output debug/injectfix-literals.json
```

按编号查询使用 `--index 24585 --index 24592` 代替 `--all`。
扫描可以通过 `--limit` 设置记录上限，达到上限时输出 `record-limit`。
默认扫描当前文件连续有效的记录，遇到无效范围后明确报告停止编号和原因；
**扫描数量不代表已恢复原生注册总数**。
无效 UTF-8 会标记 `utf8Valid=false` 并保留解密后的 `rawHex`，不会静默丢失字节。
生成文件应保存在已忽略的 `debug` 目录。

## 原生依据

实现按当前客户端的以下函数独立编写：

- `0x140524547`：解码相邻前缀偏移；表头通过 `0x88` 和 `0x13c` 两个字段定位。
- `0x140526bd0`：按字符串编号计算密钥，再按 8 字节块递增密钥并还原原始字节。
- 当前表偏移为 `0x1ebb8ec`，数据基址为 `0x1c6c828`。

两个输入沿用现有类型提取器的固定 SHA-256：

| 文件 | SHA-256 |
| --- | --- |
| YuanShen.exe | `7f89938da606c1281659607d464702cddb9a09a7d4320a196630f60a811ec38e` |
| global-metadata.dat | `469eccd43aa48fe7a2df4e1caf66fa1268a17f5a4a03fa209e1427e467cb0161` |

本轮调研使用 [gi-stringliteral](https://github.com/kuma-dayo/gi-stringliteral)，
固定提交 `1008fd7db28dcbc55729d5bb6b3a585dab86cf8b`，参考 checkout 保存在 `debug/gi-stringliteral`。
其通用扫描结果用于独立交叉核对；项目工具使用上述当前客户端的明确原生算法。

## 燃素热修复的新证据

从当前可执行代码中追踪到 `PatchManager.Load` 的三个直接调用，
分属 `0x14c9bf3c0` 和 `0x14c9c01f0` 两个加载入口。
加载器通过资源提供器取得数据，然后将内存流传给 `PatchManager.Load`。

原生加载入口引用的槽位与以下字符串编号对应：

| 编号 | 资源名 |
| --- | --- |
| 24585 | `Data/InjectFix/Assembly-CSharp.patch` |
| 24586 | `Data/InjectFix/Assembly-CSharp-patchAtBegin.patch` |
| 24592 | `Data/InjectFix/Assembly-CSharp-firstpass.patch` |
| 24593 | `Data/InjectFix/Assembly-CSharp-firstpass-patchAtBegin.patch` |

Windows 普通分支先尝试追加 `.StandaloneWindows64`，读取失败后再使用无后缀资源名。
编号 19397 还提供了 `injectfix.asb`，可继续用于追踪资源包索引。
`PatchManager.Load` 的普通分支检查 64 位标识 `0x66da7b857c633d07`。
这为后续从资源索引与包中定位实际字节码提供了具体路径和内容校验依据。

本次尚未取得这些包的完整字节码。持续 Set 与普通 Add／Set 的完整热修复计算仍待配对，
没有因此修改燃素执行规则或宣称这些玩法已完成。

## 验证

- 保留实现前模块缺失的红测试，新增 12 项测试，连同已有类型／字段提取共 **42 项通过**。
- 使用 7 个实际密文片段覆盖短文本、跨块长文本、空文本及合法的 U+FFFD 字符；
  另验证无效 UTF-8、截断、坏范围、非法编号、扫描停止、哈希拒绝与输入文件保护。
- 对当前客户端实际扫描恢复 **86,108 条**，每条编号与文本均与独立参考工具一致。
  本次扫描零无效 UTF-8；编号 86108 处因无效前缀范围停止，该原因保留在导出中。
- 按编号查询与 `InjectFix` 筛选均返回上述实际资源名。

本地证据位于 `debug/aster-mid-20261011/batch261/`：提取输出、测试日志、原生反汇编、
加载调用者、客户端文件清单及验证报告。客户端与生成数据保留本地，Git 提交包含工具、少量测试片段和说明。
