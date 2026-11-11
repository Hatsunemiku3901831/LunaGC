# F-03d3s2 / CONFIGMETA-M177845-ONE-WINDOW

已按单窗请求完成静态提取与两份新鲜原件比较；语义解释及主线第三复核待办。目标仅为 owner23071.method177845 的完整 `[14d1dad60,14d1daded)`，141 B / 32 条。原码 SHA-256 `f62abe553b1ce6225660e55d5b8eb40ad239f5b36c94f225ebfc1468abbee14f`。这不是自然调用或客户端验收。

## 来源与读取前门禁

固定请求 `local/migration72/d02-resume-20261111b/scene/configmeta-m177845-one-window-request.private.json` 6043 B，SHA `913283b2c80f7e9cc9bbbce79aafb467c949320a57f61d0ac4aa69c002755e1a`。八个指定来源及五个 stock 文件均核完整大小和 SHA；metadata 使用禁止全局类加载的 Unpickler。metadata 独立核 owner23071 的 field108683 为 type77121、rawOffset `400c8e0`，method177845 flags `96`、slot `ffff`、零显式参数与登记地址。选择依据是已验消费字段的同一 owner 和最短完整范围，不依赖方法名。

原扫描 `raw-candidate-limit` 停止仍有效；新请求仅允许这个范围。本轮复用原唯一目录索引，在相同完整 PE SHA 下定点重读 12 B pdata `60ad1d0dedad1d0d6c2b3d04`，没有重扫异常目录。fresh 读取 CodeRegistration `1422f98e0+70` 原8 B 得 `P=1428a54d0`，再从 `P+177845*8=142a00a78` 读取原8 B `60ad1d4d01000000`，指向登记入口。正文前单读 site 原7 B 并保存 AOT cell、slot、pdata、site 和 `pre-body.private.json`；该快照中 site 仍标记 raw-only、window 数为零。

## 窗口内可直接观察的指令

完整字节连续反汇编后，`14d1dad92` 确为指令起点：`4c8b05af4088f8`，将 RIP 单元 `145a5ee48` 内容载入 R8；该单元在既有冻结 usage 证据中对应 ConfigMetaConfig 的 MethodInfo。原始指令与来源关联不证明调用在运行时发生。

普通分支按地址记录：`14d1dad7e` 把 `145871708` 的地址放入 RSI，`dad85` 从同处取 dword 到 ECX，`dad8b` 将 EDX 清零，`dad8d` 调 `146eb81e0`；随后 `dad92` 取 MethodInfo 单元，`dad99` 从 `145b51ee0` 取 RCX，`dada0` 将 RSI 送 RDX，`dada3` 调 `1547b09a0`。窗口只给出该参数准备；RCX 单元所指实际 filePath 内容及该 call 的完整被调实现尚未解析，不能据此编造路径或成功行为。

`dada8` 从 `14586b600` 取基址，`dadaf` 把前一 call 的 RAX 写入 `[RCX+0xc8e0]`；结合已固定的同一 GLOBAL/field108683 消费来源，可关联该写入位置，返回值对象类型和运行时成功仍未证。`dadbb` 为唯一 `ret`。另一路由 `dad65` 的字节条件进入 `dadbc`，经 `+232a0`、`+15b5c8` 加载并判断后在 `daddb` 尾跳 `14d9638a0`，空值到 `dade7→1404c2be0`，后随 `int3`；`dad7c` 零值支路 `dade0→140525e00` 后跳回 `dad7e`。这里只记录条件与指令，IFix/错误具体含义留语义审计，不取任何 callee 或 xdata。

四个直接 call 为 `14d1dad8d→146eb81e0`、`14d1dada3→1547b09a0`、`14d1dade0→140525e00`、`14d1dade7→1404c2be0`。尾跳单独列出，不计为 call。

## 复现与交付

两个独立 fresh 的八份原件逐字节一致：完整 asm 5649 B / SHA `0865d5a8f142c92527439953bc24588bcbd46f5b65d121d27c43407498deed35`；完整提取报告 5101 B / SHA `49fa229f5130f5a0c8b6a5ca541b891b782eb20e29d4acd25862da9aaa5fab65`。原件在 `local/migration72/d02-resume-20261111b/scene-holder-window/fresh1/` 和 `fresh2/`。

独立审计直接检查两组原件哈希、全部指令逐字节连接、精确 site/RIP、所列准备指令、四次 call、ret、AOT 算术和 pdata。两次报告均为 6190 B / SHA `0d436a0509a077f0726df679787699d0258eb10743a9514c0bb554e20dc08221`；审计脚本 SHA `6eeb795206b17700e502bf93eab1d066eaea74e1f2d9b186b86ed064b2e9b479`。该审计复用两份原件，不是第三次 PE 提取。可供主线用新文件名复核：

```powershell
python -B -m unittest tools.migration72.test_scene_mplevel_actual_holder_window -v
python -B tools/migration72/scene_mplevel_actual_holder_window.py --output <新的私有运行名>
python -B local/migration72/d02-resume-20261111b/scene-holder-window/audit.private.py --output local/migration72/d02-resume-20261111b/scene-holder-window/audit-main-third.private.json
```

三项合成门禁测试通过，覆盖输入大小/SHA 漂移、受限 pickle、真实边界与 RIP 目标及不完整解码拒绝。[提取器](../tools/migration72/scene_mplevel_actual_holder_window.py) 和 [测试](../tools/migration72/test_scene_mplevel_actual_holder_window.py) 为独有离线文件。本轮未重新扫描 376 MB 可执行区，未读取其他候选 body、callee 或 xdata，未执行目标 CPU、客户端、JVM 或数据库，也未部署。完整文件 SHA 读取另计，不能与定向证据字节混淆。

实际 filePath 值、相应 payload/reader、MPLevel 分组、自然调用、World 生命周期及完整 Scene/D-02 均未验。下一步是 Scene 语义负责人只读原件并指出精确缺口，主线执行第三复核；任何额外原窗需另定范围。
