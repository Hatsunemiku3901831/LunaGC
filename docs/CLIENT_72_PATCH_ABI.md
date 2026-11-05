# 国服7.2连接补丁入口研究

输入固定为extract_client72.HASHES的三文件。本页属于D-01的离线前置，**未安装补丁、未加载目标客户端、未完成连接验收**。patch子模块保持原提交，不能因为以下参数前缀成立就放开play/patch版本守卫。

## D-01a 四个命名入口的参数前缀

`trace_client72_patch_abi`从目标元数据重新提取方法flags、参数名字/类型和注册入口，并执行对应原生前缀。以下地址只对固定输入有效；没有按旧版RVA推算新地址。

| 目标方法 | 目标声明 / 入口 | 原生观察 |
| --- | --- | --- |
| UnityEngineInternal.WebRequestUtils.MakeInitialUrl | 类型2910/方法23459，static(string targetUrl,string localUrl)，15A3464C0 | RCX targetUrl存至RDI；RDX localUrl存至RSI，再原样作为Uri.CreateThis的RDX；停止于15A1103F0入口，不执行URI构造或HTTP路由 |
| ZenFulcrum.EmbeddedBrowser.Browser.LoadURL | 类型8135/方法60229，instance(string url,bool force)，15A366300 | RCX receiver/RDX url/R8 force分别写捕获对象+10/+18/+20；目标8146/`<>c__DisplayClass179_0`字段声明独立确认同一布局；停止15A366342 |
| MiHoYo.SDK.SDKUtil.RSAEncrypt | 类型4389/方法33239，static(string publickey,string content)，15997C290 | RCX key存至RSI，RDX content存至R14，停止首次RSA对象分配1405336C0之前；不执行加解密，不使用真实密钥或密码 |
| System.Runtime.InteropServices.Marshal.PtrToStringAnsi | 类型1140/方法9392，static(IntPtr ptr)，159C21C90 | 原生141CB67E0扫描NUL，159C21CAC将原指针RCX与字节长度EDX交给14052FBF0字符串工厂；工厂内部转换/托管分配未执行；null输入完整返回null |

12组原生样本通过：HTTP的绝对/相对URL2组、Browser force真假2组、RSA两组不同哨兵2组、Marshal5组字节缓冲及null1组。Marshal覆盖空串、ASCII URL、8字节边界、首NUL截断、UTF-8多字节输入与非对齐指针；这里只确认**字节计数**，不把它当作Unicode转码完成。Browser中文URL仅验证同一指针被保存，也不声称浏览器实际加载。

显式模型仅为托管string/receiver存储、指定Uri/捕获对象分配和Browser已初始化的metadata-cache标记。Native strlen执行审查范围141CB67E0–141CB6888，不使用Python长度模型替代。未知helper不返回默认成功；观察到停止边界即结束。

143项研究/映射/盘点测试通过，其中本项5个声明守卫测试覆盖static/instance互换、RSA参数交换、Browser错用RCX、Marshal重载/入口/类型错误。trace与摘要重复逐字节一致。此处测试总数不包含旧完整Java测试集，也没有消除其历史失败。

## 尚待处理的连接前置

- `marshal.rs`调用的字符串工厂仍涉及UTF-8转换、TLS和GC分配；本项不证明真实托管分配、GC寿命或钩子可重入性。
- `security.rs`现有原生RSA替换依赖R12/R13/R8及268字节长度，尚未用7.2实际密钥材料/错误签名路径闭合。旧`after_key_sign_check`直接将RAX置1，不能作为目标验签正确的证据或验收依据。
- HTTP/Browser回调仍需核对URL重写结果、查询参数隐私、调用栈重定位、实际模块加载、启动与IFix覆盖。方法前缀参数成立不能替代这些检查。
- `native_sdk.rs`仅对商城价格URL生效；SDK Get/Post、价格缓存、充值等可选钩子不应混同P0登录前置。现有7.1特征零命中/静态命中记录只作定位线索。
- WinHTTP/security-file、代理加载、dispatch与区域密钥、Token完整密文/验签以及另一游戏传输实现仍未闭合。B-02生成闸门也未打开，因此没有7.2可连接部署或D-02结果。

初始静态扫描可在`evidence/client-audit.json`复查。当前本机只有目标客户端与本checkout；公开源查询未找到两个目标混淆请求名的独立协议资料，未采纳任何网上旧包号或字段作为7.2映射。后续优先继续目标字符串工厂、原生RSA数据流或已定位封包入口。

```powershell
python tools/trace_client72_patch_abi.py --client $env:GAME_PATH --output local/migration72/evidence/patch-abi-trace.json --summary-output protocol/cn-7.2/patch-abi-observations.json
python -m unittest tools.test_client72_patch_abi -v
```

证据：`local/migration72/evidence/patch-abi-{trace,determinism}.json`、`logs/patch-abi-tests.log`；共享摘要`protocol/cn-7.2/patch-abi-observations.json`。仅离线工具、测试和文档变更，无需构建/重启现有服务或补丁。
