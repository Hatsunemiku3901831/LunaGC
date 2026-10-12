# 客户端字段偏移提取

`tools/extract_client_type_names.py` 新增 `--include-field-offsets`，与 `--include-fields` 一起使用。
按当前客户端 Class::SetupFields `0x1405135b0` 的三张表读取类型布局和字段偏移，
供后续字段名 → 对象偏移 → 序列化标签的协议核对使用。

```powershell
python tools/extract_client_type_names.py 'C:\Users\InvokerBot\AppData\Local\hoyo\hk4e\versions\current' --include-fields --include-field-offsets --include-parents --output local/activity-research/client-field-layouts.json

python -m unittest discover -s tools -p 'test_extract_client*.py' -v
```

## 数据含义

每个类型添加 `fieldLayout`；每个字段添加 `rawOffset`、`offset` 和 `offsetKind`。
`instance`／`static` 分别描述声明类型和静态存储中的布局偏移。
值类型需结合装箱及具体运行时访问方式使用；偏移不是绝对地址，也不是 protobuf 字段号。
带运行时标记的值和没有布局的类型明确保留为 `runtime`，`offset=null`，不推测物理地址。

入口回包 PLOKNNKAHCI 的 MAPEPFNEDEE 位于 +0x24，写入标签 15；
同名字段在进入副本请求中位于 +0x30，写入标签 14。
只有将这些偏移与写入代码相连，才可核实各消息自己的标签，不能直接用字段顺序或另一个消息的标签。

客户端路径由参数传入。提取仍要求可执行文件和元数据匹配已核实的分析配置，
结果目录不得位于客户端目录内部。所有表与字段跨度先检查，失败不留下部分成功的内存记录。

## 验证

新增 16 项检查：乱序布局、静态字段、运行时标记、缺失布局、非法索引、三张表截断、
字段信息与数量、失败后的完整保留、空类型、既有成员保留、CLI 依赖、调用与导出、客户端目录保护。
功能接入前 16 项均失败（15 个 API／参数缺失错误，1 个行为断言），这是新增能力对照。
最终连同原有继承关系回归共 **30 项通过**。

当前客户端实际导出 **88,904 个类型、440,172 个字段**：

| 分类 | 字段数 |
| --- | ---: |
| 声明类型布局 | 330,958 |
| 静态存储布局 | 75,874 |
| 运行时布局待解析 | 33,340 |

75,277 个类型读取到布局记录。全量 CLI 导出在本机此次运行约 9 秒。
对原有完整导出逐条核对：88,904 个类型中的原有字段与 733,442 个方法、继承及其他属性全部保留，
差异为 0；与上一批独立恢复的 6 个消息／嵌套类型布局逐字段一致。

证据位于 `debug/aster-mid-20261011/batch135/`，包含固定旧工具、单元测试、实际导出与原有数据完整性校验。
本批改动为提取工具；服务端使用上一批已经通过 263 项回归的包，SHA-256：`e84c3f1fac883ee1a1b3e1a462bac8309b7a6e762a414d8e01bc097473ec412c`。
这项能力用于继续核对首奖请求与剩余协议，尚不构成完整 protobuf 自动恢复或游戏内功能验收。
