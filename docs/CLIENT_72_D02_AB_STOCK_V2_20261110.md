# AB-STOCK-STARTUP-299-V2：私有collector实施（2026-11-10）

状态：离线实现已完成，待主线独立审核；尚无新现场结果。来源是[已审核299最小规格](CLIENT_72_D02_STOCK_STARTUP_299_20261110.md)，机器规格SHA-256为`39056b10c869fb679636a3bf05327abc1489b225e11528e2b32f54fdf4b16795`。本页不修改旧stock规格、collector文档、旧pin或原失败证据。

私有实现只在`local/migration72/d02-resume-20261110/ab-stock-v2/`。新`stock-flow-v2.private.py`显式复用冻结原helpers/constructor，只独立实现原`run_flow`的两处变化：首次有效EXE模块前，仅299/items=[]标为unavailable且保留原raw与公用sleep；45次窗口末仍无有效module时，先于epoch identity和A/B显式拒绝。首次有效及后续模块仍走原严格规则；不增加snapshot、retry、时间或RPM。

新coordinator显式选择v2，A五tool和B两tool epoch分开，A图partial但身份/cleanup真仍允许独立B一次；身份/cleanup坏则B0。晚失败统一脱敏、原句柄各关闭一次、source/stock/outeralive保持。预算仍为A72读1852B+B26读452B=98读2304B，单读208B；primary API、2次TBI/96B结构、GC与各自final另账不变。

新plan保持unsealed，v2ReviewAccepted=false。新来源审核门禁要求主线独立JSON绑定v2 flow、coordinator和spec SHA，旧A helper proof只证明旧源；必需来源清单保留原63并集，连同新材料/待建主线review共74路径。新版本仍须主线封全source并集、实际CheckOnly、新实时ready和新run。旧ready和失败run不复用。

人工验证覆盖原111项、新22反例及3项新source/AST检查，共136项。原四A profile12场景JSON逐byte/请求序列保持；新旧完整AB正常/首错/final错3场景同样相等。只在新目录同深度原字节snapshot执行旧helper测试，所有临时fixture留在新目录。新source gate只hash来源/读JSON，以新目录人工临时review验证完整74路径及错误绑定拒绝；不创建主线审核，不冒充实际CheckOnly。两fresh/四PS Parser及最终SHA清单由私有delivery记录实际输出；Parser通过不代表CIM/UAC/现场门禁通过。

原63来源及追加4个固定来源、原A18源/模板和B两tool均前后保持；仅新flow函数中两处差异，AST还原这两处后与原函数完全相等。旧失败/当前raw保持。本批未调用v2 backend、目标Windows API、GAME_PATH/当前进程端口、真实模块或托管对象、自然页面/业务、PE/native CPU、DLL安装、构建、JVM/DB、Git、网络或部署；无ready。299发生于Create还是First及真实根因仍未知。D-02、R2与其他父项不晋级；仅离线工具/测试/文档不需重启GS。Project/Harness与本地Git由主线维护。

下一步：主线审本目录实际diff/AST、22反例/晚失败隐私与新来源清单，独立第三人工验证后填写新review并封新plan，再走新实际CheckOnly与实时ready。完整复现命令与正式门禁CLI位于私有README；本批没有本地Git提交。

## 主线独立离线验收

主线逐项核最终源、真实差异和全部旧63源保持；用原run_flow完整文本独立重建后验证新函数恰只有两处批准执行改动。主线实际136人工项及四PS Parser均通过；另设前17次真实形状299空结果、L101完整head和已知env/route不一致的第三人工图，原45时点/90秒/50个sample组、实际98读2078B/最大208和一次cleanup成立，B原字节可独立重验。全45次unavailable没有第46救场或A/B读取。

主线证明 `local/migration72/d02-resume-20261110/ab-stock-v2/main-review/main-review.private.json` SHA `9bd1a1536e0df4939c5ee9d88309ac64644b996fda7fd7c51703a89b31019fde`，显式绑定本v2 flow/coordinator/spec，旧A helper证明仅作原源来源。新root-third/parser原始stdout/stderr及invocation保持，不将这些人工项称作实际CheckOnly或自然成功。

此处只关闭离线实现子项。下一步由主线另封新plan/source、实际CheckOnly、现stock/配置/进程端口核对和新的实时ready；旧失败run/ready始终保留且不能复用。原299子阶段/根因、页面表现、自然托管值、完整Avatar/Scene/资源/客户端和D-02未因v2人工通过而晋级。

## 本轮实际输入采集、原件审核与退出

主线在本轮新ready后正常创建stock客户端，严格原45次/90秒窗口：首次299/空items原样记unavailable，随后44次唯一EXE身份保持；没有第46救场、加时或额外RPM。A和B各执行一次、各自final，实际 **A62读1388B、B26读432B，合计88读1820B，最大单读208B**，均为稳定候选。正常方法组数保持status51/sample50/image6/creation6；不是逐native调用账。

主线9项独立审核将A全部原始请求/图/预算/末次复读重新构造，B用实际whitelist bytes重验类别；Win64实际24B PROCESS_INFORMATION、两次48B TBI及原PID/TID/TEB相符，primary/process各关闭一次。B两个完整module快照各151项，额外native计数316只按已冻源码派生，不冒称逐调用记录或固定上界。A五tool和B两tool epoch分开，同原进程但并非联合原子快照。

本epoch受控primary候选图取得TLS/R1、完整32B非零head、items非零opaque值及HashSet<UInt32>身份/实际U16=56、bin4；没有读取第二record/block、Default或items数组body。原head实际oldFlags=8，故已有原指令表明若推进原pop会走141fdf9c0，仍是未闭helper；原生执行、captured-copy-write、GC所有权及自然allocator caller均未由采样批准。此前fresh原回放停TLS的记录保留，不能把新输入直接写成已执行完整构造。

B当前ConfigModel.env=0，URLConfig.comboLogin原UTF16LE完整base归 `managed-current-base-cn-env-0`，两者本次候选一致。它不证明SetEnv/GetURL或40108/PostRequest执行、实际route采用或认证；也不能与前轮SDK native map拼成同一epoch。

用户本轮反馈“已退出，无异常”；随后独立提升核验stock五/载体/13保护/portable14及缺席、相关进程和固定端口为空。没有安装DLL或修改客户端文件。原件在 `local/migration72/d02-resume-20261110/ab-stock-v2/` 的 `ab-stock-v2-live1-20261110T052505Z-2d3fe291-*`；主线证明 `live1-main-review/main-live-acceptance.private.json` SHA `d6ad7d00f2656c017e74fbf828683fe8d2be98c54800db87e28a3558009338fb`。

追加本节前已把当轮全部83项source实际bytes及manifest逐一存入 `live1-main-review/source-snapshot/`，旧plan/document hash只能通过该历史快照追溯，不改写旧pin，也不允许复用旧ready或地址。仅关闭本次输入采集子项；完整Avatar/Scene/资源/R2业务/D-02仍未完成。按用户最新指令，收尾既有装备恢复验收后暂停，不开启GC helper、第二pop、route采用或其他后继任务。
