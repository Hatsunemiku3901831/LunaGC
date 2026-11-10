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
