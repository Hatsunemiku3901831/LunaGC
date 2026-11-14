# 上游分支固定清单（2026-11-14）

配套 [研究报告](CLIENT_72_UPSTREAM_SURVEY_20261114.md)。GitHub REST 分支列表分页到末页；以下以随后仓外 clone 的远端跟踪引用为准。每行读取该提交根树的 README 文件并按 blob 去重；相同 README 不代表相同源码树。tip 提交主题用于初筛，深审范围以报告为准。未检出工作树，未运行上游代码；blobless clone 按需取得已读文件，LFS 和子模块未自动取得。

每行的提交、树、README blob 均为完整 Git 对象 ID；`Δ` 为相对该仓库默认分支的仅默认 / 仅该分支提交数，表示历史差异而不是功能差异。以下源码树是根 tree ID，不声称已遍历所有子树。

## [2406450951/AstaPS](https://github.com/2406450951/AstaPS)

默认：`main`；抓取时间（UTC）：`2026-11-14T04:29:01.268089+00:00` 至 `2026-11-14T04:29:02.156130+00:00`。

| 分支 | tip commit | 根 tree | README blob | Δ 默认/本支 | tip 主题 |
| --- | --- | --- | --- | --- | --- |
| `dev` | `94f9759c1e91995cafd545f7205d1ff0036cc72d` | `7ca47f0814b0048114e315cd78863b76dc6fbf86` | `c370306ab9eae1fe31f0a838d08481f28cb6f4b2` | 818 / 44 | 1.9.5: 移动加速开关挪到玩家页顶部并修正过期文案; c=sots 自动定位最近七天神像 |
| `main` | `a49f51aa6f2787ffbc25cee46801b70dee860d23` | `9940bf7756ce5ff8be8a836a4485a645b490c2e1` | `3b3f5d3b59d66845dfa69cbe7736cf2cbf0dffa4` | 0 / 0 | update to 7.2 |

## [962666231a-max/AstaPS](https://github.com/962666231a-max/AstaPS)

默认：`main`；抓取时间（UTC）：`2026-11-14T04:25:21.569556+00:00` 至 `2026-11-14T04:25:23.566412+00:00`。

| 分支 | tip commit | 根 tree | README blob | Δ 默认/本支 | tip 主题 |
| --- | --- | --- | --- | --- | --- |
| `feat/quest-skill-fixes` | `930aadd0e8cade73380f54b54fa775fd924cebf8` | `73ecec2de63c0f8764673b8ad93dffea4f856fcd` | `3b3f5d3b59d66845dfa69cbe7736cf2cbf0dffa4` | 0 / 1 | feat(quest): make story/quests playable on 7.2 CN clients |
| `fix/7.1-fresh-born-intro` | `bbe0fe4ee3e118bf9b4c8bfc811865dc970fb192` | `64f561742d8e571e3bd0125543af82c5ac3804e3` | `39d0f41a805baf0aa265eb7445e6d168b434fdfd` | 678 / 0 | Thanks to qweczh11 for fixing the multiplayer function |
| `main` | `a49f51aa6f2787ffbc25cee46801b70dee860d23` | `9940bf7756ce5ff8be8a836a4485a645b490c2e1` | `3b3f5d3b59d66845dfa69cbe7736cf2cbf0dffa4` | 0 / 0 | update to 7.2 |
| `quest-content-fixes-7.2.0` | `2ed264ae4594c1c566297b63828b041ccbe4dbcc` | `09b4ba651b660a217975f064367c730f95b8989c` | `3b3f5d3b59d66845dfa69cbe7736cf2cbf0dffa4` | 0 / 2 | data: add 7.2.0 data patch (avatar / chapter / TextMap hash fixes) |

## [AzureXuanVerse/HunkyMeow](https://github.com/AzureXuanVerse/HunkyMeow)

默认：`development`；抓取时间（UTC）：`2026-11-14T04:28:56.762376+00:00` 至 `2026-11-14T04:28:56.762376+00:00`。

| 分支 | tip commit | 根 tree | README blob | Δ 默认/本支 | tip 主题 |
| --- | --- | --- | --- | --- | --- |
| `development` | `029f0a63d1b9eb786825110fc4fd14debf83e70a` | `bb20adac9f4b7f65ae882850f7a5f2e0ff4224ea` | `9b7b31e1212bd3db9ab83a9fd59aad03435a8acf` | 0 / 0 | 重构一些内容 |

## [Grasscutters/Grasscutter](https://github.com/Grasscutters/Grasscutter)

默认：`development`；抓取时间（UTC）：`2026-11-14T04:28:49.941884+00:00` 至 `2026-11-14T04:28:56.221867+00:00`。

| 分支 | tip commit | 根 tree | README blob | Δ 默认/本支 | tip 主题 |
| --- | --- | --- | --- | --- | --- |
| `LintRatchet` | `beeaad0034035fad415357220137d58df1149a0b` | `1ec89fc979b988fafee6278ecf2c6b4a9b459b21` | `a17abef630105e316ebe69330c02bbfeed4a2d36` | 408 / 4 | Version 1.4.6-dev [skip actions] |
| `development` | `f373827a83d3e688fd3c6afb1e1c3759ca1d61c9` | `0cf84ac857019ed235c537c7f596071e9f8bab7f` | `4867a4934808a5ef67ad2bd31611e1954f3aeae9` | 0 / 0 | Update README.md for clarity (#2543) |
| `hyper-optimization` | `dad7821e26d1f31628754e026fe2b4ecc269ad34` | `c80a875c96cf17b88f6f3591f99cdadf235650cb` | `67cd2e116395b77197d75ba204726b6fa5ca44d6` | 66 / 9 | Give thread pools time to terminate & Save all data before shutting down |
| `old-dev` | `f46fd372d2dee8d3befe9f521a7a2d36c5a850ad` | `d2fe6c21407ea229a502d9c94a5f738529c488b7` | `560e73b9a0fe3f42c222a680477015a152f214e8` | 362 / 0 | Remove re-open condition from 'protect_files' |
| `stable` | `2b2c612f1e72f9402d4fa2d954e3561dc112a407` | `c314c46fda8be33a1c1ca89f697ea2601fd7fe08` | `d7cd3d6c40ab5430449d7dc999eab666df5cd8ae` | 542 / 3 | Merge pull request #1704 from Grasscutters/development |
| `unstable` | `e6895ca628405240c1c5c21e90f7971262442886` | `3e41641ed6860d533cd5b8390cd8ffd7ceba1b48` | `544d0aca7595ebaf188b9c0679ae2e1f78dfcbe1` | 4 / 35 | Merge remote-tracking branch 'origin/unstable' into unstable |
| `unstable-old` | `031f3bcd25ab1d71e3903031fee251072d6eff87` | `09badbf38aebd9394bb419e9d329ab2b9b59b231` | `cd56b9837b4e59af28177293fe822ad3a56dac8a` | 363 / 537 | Revert "Fix issues with regions (#2177)" (#2178) |
| `version/4.0` | `f373827a83d3e688fd3c6afb1e1c3759ca1d61c9` | `0cf84ac857019ed235c537c7f596071e9f8bab7f` | `4867a4934808a5ef67ad2bd31611e1954f3aeae9` | 0 / 0 | Update README.md for clarity (#2543) |

## [HertaA11/AstaPS](https://github.com/HertaA11/AstaPS)

默认：`main`；抓取时间（UTC）：`2026-11-14T04:25:23.885891+00:00` 至 `2026-11-14T04:25:31.633788+00:00`。

| 分支 | tip commit | 根 tree | README blob | Δ 默认/本支 | tip 主题 |
| --- | --- | --- | --- | --- | --- |
| `7.1.0-Watermark` | `034ba6c97de25986bf56765255bba196804cfeca` | `c56da568b62eab6ce0562cc21ff12edd9e7755b6` | `aa212dbfa38e8e7c1d5bed104f3a2a9256aef588` | 15 / 4 | Merge branch 'MeChen618:main' into feature/custom-watermark |
| `ccr-53468249-3vfc4l` | `45bddcae6b40510945bc186299e9e23db913d9e4` | `d4afc2f219244334e854ea2219ed10e23694900a` | `39d0f41a805baf0aa265eb7445e6d168b434fdfd` | 649 / 1 | Make TPS weapon hits take 2% of a monster's max HP |
| `feat/proto` | `873331f2257cdfdf81bc822993736c35a45f4558` | `d6a60e9c5d9b75414c61df596e9318a1b052b72c` | `aa212dbfa38e8e7c1d5bed104f3a2a9256aef588` | 72 / 0 | Merge branch 'main' into feat/proto |
| `feat/snezhnaya-frigid` | `3df7d0440a70f342da6f47f12082bf999457ade0` | `215b278f59f76bb43a0cb2f360ac299454caceb9` | `39d0f41a805baf0aa265eb7445e6d168b434fdfd` | 646 / 1 | Attach Snezhnaya's cold meter in its world areas |
| `feature/custom-watermark` | `1f3859ab852f556a9b3dbbbf600df082d91374d5` | `f1d14ebbd6182055b3b8171d5e7602f61dca30e3` | `3b3f5d3b59d66845dfa69cbe7736cf2cbf0dffa4` | 0 / 5 | Merge branch 'MeChen618:main' into feature/custom-watermark |
| `fix/7.1-fresh-born-intro` | `bbe0fe4ee3e118bf9b4c8bfc811865dc970fb192` | `64f561742d8e571e3bd0125543af82c5ac3804e3` | `39d0f41a805baf0aa265eb7445e6d168b434fdfd` | 678 / 0 | Thanks to qweczh11 for fixing the multiplayer function |
| `fix/fresh-intro-reconnect` | `6faa58276c828466325b68c650cf2075e9605475` | `1b4964c34df076821e25899a64448679c0177edf` | `74a4c6c733b783bebd54e763ebd626dc34103191` | 630 / 0 | Merge remote-tracking branch 'origin/main' into fix/fresh-intro-reconnect |
| `fix/login-before-ready` | `6b447de76909c7318c47f29aed5b4b8a78d1ae0e` | `f22401099a188db661712122124ec1dc78cd7864` | `74a4c6c733b783bebd54e763ebd626dc34103191` | 637 / 1 | Merge pull request #53 from MeChen618/perf/async-login |
| `fix/proto-unknown-packet-logging-71` | `54ccae61a3fbcb83bb878abcd7ae2e88ee2078f0` | `00bfc807c6317ee86118bbb9b247e2e3061a5456` | `aa212dbfa38e8e7c1d5bed104f3a2a9256aef588` | 71 / 1 | fix(proto): log unknown incoming packets and preserve raw payload fallback |
| `fix/shrine-interact-delete-204` | `8b683474954803fe8e29e8748ee7a6d617d1c4dc` | `d7ba46b34d5ab005930d28d9f9b708ba6f23bcc8` | `630a37b063f35bf35478ddeb0a4f656ddae6a7be` | 86 / 0 | fix(gadget): leave shrine gate removal to group script |
| `fix/special-energy` | `521f90cb4364e1dd1ee2b53a6b01a1b8a40fa220` | `e7e429858763a53be4b3613bd5298be383ff38aa` | `39d0f41a805baf0aa265eb7445e6d168b434fdfd` | 666 / 2 | Read 7.1 special energy fields from the skill excel |
| `main` | `a49f51aa6f2787ffbc25cee46801b70dee860d23` | `9940bf7756ce5ff8be8a836a4485a645b490c2e1` | `3b3f5d3b59d66845dfa69cbe7736cf2cbf0dffa4` | 0 / 0 | update to 7.2 |
| `mechen/gifted-ride-3l2mwn` | `b5223ce8779c6b34c88376858db07a7c3667a455` | `a5aceaf48bd071dfc6cdb65b638b2dcf9175022c` | `630a37b063f35bf35478ddeb0a4f656ddae6a7be` | 94 / 0 | Keep a fetter reward claimable when the bag refuses it |
| `mechen/kitkat-opcodes` | `3b719637c97f041ed53af3ec0fb719abf5f82294` | `fb98cd0e10c0590b78d1ed14ce4b4956de8e5711` | `24dc51883abddf2483360869420d1139c3871ff3` | 239 / 1 | fix(net): give 60 handled packets their 7.1 CmdIds from the kitkat dump |
| `perf/async-login` | `bf42286cd57651692ead0072fd27223f853f37f1` | `f22401099a188db661712122124ec1dc78cd7864` | `74a4c6c733b783bebd54e763ebd626dc34103191` | 637 / 0 | Run the database half of logins off the logic thread |
| `测试中文` | `f3cacb65bb66c400c31c82db4a0d1abd2d60070c` | `9ed213160d8f393699753c3500797f78e8d149c1` | `aa212dbfa38e8e7c1d5bed104f3a2a9256aef588` | 50 / 4 | fix(i18n): normalize Chinese language locale codes |

## [KitsukiDev/hk4e-patch-universal](https://github.com/KitsukiDev/hk4e-patch-universal)

默认：`main`；抓取时间（UTC）：`2026-11-14T04:29:16.917634+00:00` 至 `2026-11-14T04:29:16.917634+00:00`。

| 分支 | tip commit | 根 tree | README blob | Δ 默认/本支 | tip 主题 |
| --- | --- | --- | --- | --- | --- |
| `main` | `2b589125874b0b96f8694bfad31e34811eba8573` | `822e0b73e04a6ec63461e0d7ce87c923f7e5e964` | `ed9483283b503080cb217806bb447443b4beeb62` | 0 / 0 | Rewrite README: architecture, modules, configuration, credits |

## [MeChen618/AstaPS-Resource](https://github.com/MeChen618/AstaPS-Resource)

默认：`main`；抓取时间（UTC）：`2026-11-14T04:25:34.264051+00:00` 至 `2026-11-14T04:25:35.400325+00:00`。

| 分支 | tip commit | 根 tree | README blob | Δ 默认/本支 | tip 主题 |
| --- | --- | --- | --- | --- | --- |
| `main` | `6cfecfb55f889f8e3b7156adb12809608db4f74b` | `3cf83488a94b47a018934e5f91c1bf7e93357aae` | `42c45a8f83fffbed61edb6122b96c051b8a3ae22` | 0 / 0 | Update 7.2 |
| `mechen/resources-7.2-runtime` | `9f1ae21ea6b4610fca4fde8484ae4316cb3860b1` | `3cf83488a94b47a018934e5f91c1bf7e93357aae` | `42c45a8f83fffbed61edb6122b96c051b8a3ae22` | 1 / 0 | Update 7.2 runtime resources and dependencies |
| `update-quest-excel` | `9fe14a67667e3bca4db026d9c98e452342e2b989` | `3e5a05b61b2ea4139ee4989a10bb72f05cafe091` | `ddd37d661112d617fd14a314b8ed0f792e45d393` | 79 / 1 | Merge BinOutput/Quest sub-quests into QuestExcelConfigData |

## [MeChen618/AstaPS](https://github.com/MeChen618/AstaPS)

默认：`main`；抓取时间（UTC）：`2026-11-14T04:25:19.252554+00:00` 至 `2026-11-14T04:25:20.556177+00:00`。

| 分支 | tip commit | 根 tree | README blob | Δ 默认/本支 | tip 主题 |
| --- | --- | --- | --- | --- | --- |
| `fix/7.1-fresh-born-intro` | `bbe0fe4ee3e118bf9b4c8bfc811865dc970fb192` | `64f561742d8e571e3bd0125543af82c5ac3804e3` | `39d0f41a805baf0aa265eb7445e6d168b434fdfd` | 678 / 0 | Thanks to qweczh11 for fixing the multiplayer function |
| `main` | `a49f51aa6f2787ffbc25cee46801b70dee860d23` | `9940bf7756ce5ff8be8a836a4485a645b490c2e1` | `3b3f5d3b59d66845dfa69cbe7736cf2cbf0dffa4` | 0 / 0 | update to 7.2 |

## [Neona-Xi/LunaGC_7.2.0](https://github.com/Neona-Xi/LunaGC_7.2.0)

默认：`7.2.0`；抓取时间（UTC）：`2026-11-14T04:29:09.992424+00:00` 至 `2026-11-14T04:29:10.957445+00:00`。

| 分支 | tip commit | 根 tree | README blob | Δ 默认/本支 | tip 主题 |
| --- | --- | --- | --- | --- | --- |
| `7.2.0` | `acc7a0b48f44f3d51666c91f1125e8e1ee923ca4` | `5964c9d0099effd55ea8a891cc2b3dc6d1fb0512` | `14022d39e2b49a16c70aae39998597143866782e` | 0 / 0 | omg i always mess up submodules 😭 |
| `Hard-7.2.0` | `9d14f34ab4e57cdf3241761ad224d041405871c8` | `b70de349deb4a96221131958ae495c60a8796d2f` | `4d4e447e5626461857e3045d8cb48ee19ae4ef4d` | 0 / 1 | Update README.md |

## [Peruere-dev/LunaGC](https://github.com/Peruere-dev/LunaGC)

默认：`7.0.0`；抓取时间（UTC）：`2026-11-14T04:29:03.008829+00:00` 至 `2026-11-14T04:29:04.971712+00:00`。

| 分支 | tip commit | 根 tree | README blob | Δ 默认/本支 | tip 主题 |
| --- | --- | --- | --- | --- | --- |
| `6.6.0` | `49cd58f275925260a90966268b985ab020f78ace` | `302519e3db6ea670b61d555cd0425b935bfe81bb` | `14b9f1dcb5dd930e603140b03bae4cabab6f1f0a` | 22 / 0 | fix spiral abyss (tower) protos and dungeon flow |
| `6.7.0` | `1b4de97e3d01a6c952675522e5ae21a05cc7bd92` | `160285521814e4456702e910ef1b53573198c0cf` | `8b032461dd35eda864a021842d16d0cd8ae5a8e0` | 19 / 0 | fix /talent npe when talent name missing from textmap |
| `7.0.0` | `30279ccafd9a3693798f468df0e900f4c50f5e67` | `5e395f33355b2ad6c4ebade955c0eb66a75e1e72` | `71d24926b6ba09cccc403c48099ec215a4a6053a` | 0 / 0 | 更新 README.md |

## [RinoPaw/AstaPS](https://github.com/RinoPaw/AstaPS)

默认：`main`；抓取时间（UTC）：`2026-11-14T04:28:59.667572+00:00` 至 `2026-11-14T04:29:00.812081+00:00`。

| 分支 | tip commit | 根 tree | README blob | Δ 默认/本支 | tip 主题 |
| --- | --- | --- | --- | --- | --- |
| `main` | `a49f51aa6f2787ffbc25cee46801b70dee860d23` | `9940bf7756ce5ff8be8a836a4485a645b490c2e1` | `3b3f5d3b59d66845dfa69cbe7736cf2cbf0dffa4` | 0 / 0 | update to 7.2 |
| `rino/refactor/jline4-shell` | `d9e34adca24faad166d5064741658234def51dc6` | `ff7901667127ff2299342998796e20fd6ee6a7b7` | `3b3f5d3b59d66845dfa69cbe7736cf2cbf0dffa4` | 0 / 188 | ci(shell): include help and ANSI regression tests |

## [Ruiwang66/AstaPS](https://github.com/Ruiwang66/AstaPS)

默认：`main`；抓取时间（UTC）：`2026-11-14T04:25:33.018456+00:00` 至 `2026-11-14T04:25:33.872051+00:00`。

| 分支 | tip commit | 根 tree | README blob | Δ 默认/本支 | tip 主题 |
| --- | --- | --- | --- | --- | --- |
| `fix/stygian-onslaught` | `f3c5dfd639c2bb3b388015dd1bc35d81cc8e9140` | `1b22cdc2aabf4f2d3b8a0c03100c2e5b152de2fb` | `aa212dbfa38e8e7c1d5bed104f3a2a9256aef588` | 2 / 1 | feat(activity): add initial Stygian Onslaught flow |
| `main` | `ac9a7e6cec900e4ced28ec5dc16ceda82cf7538d` | `8d58872c647573c9f2ada2e566d234b3d6f66da4` | `aa212dbfa38e8e7c1d5bed104f3a2a9256aef588` | 0 / 0 | Merge pull request #240 from MeChen618/rino/feat/blossom-native-lua-71 |

## [capybr2222/LunaGC-Resources](https://github.com/capybr2222/LunaGC-Resources)

默认：`master`；抓取时间（UTC）：`2026-11-14T04:29:08.935487+00:00` 至 `2026-11-14T04:29:08.935487+00:00`。

| 分支 | tip commit | 根 tree | README blob | Δ 默认/本支 | tip 主题 |
| --- | --- | --- | --- | --- | --- |
| `master` | `11d971bec4ab8fbe4598c6d8f63bbdfdb49b7010` | `9882c05118c1fdfa425b615a9ce9e08d007f8462` | `4171039024e227838b16fba009577d9def6f5630` | 0 / 0 | feat: update to 7.2 |

## [capybr2222/LunaGC_7.2.0](https://github.com/capybr2222/LunaGC_7.2.0)

默认：`7.2.0`；抓取时间（UTC）：`2026-11-14T04:29:05.915283+00:00` 至 `2026-11-14T04:29:08.086955+00:00`。

| 分支 | tip commit | 根 tree | README blob | Δ 默认/本支 | tip 主题 |
| --- | --- | --- | --- | --- | --- |
| `6.7.0` | `6e96b91e4286da2e7eb2c95ed25da14267800d5e` | `c39fa6369b146c94681ab6a71d23ae52c79ecc4a` | `02b0d877c7c49008f364a0426fa248e42fd4b481` | 171 / 3 | Merge pull request #4 from capyb2222/test |
| `7.0.0` | `8e5db4997d1967397c531b693352251babd9a0e4` | `0b6955c2007ca8f6902ebdeb201cd60ad772e57a` | `1d5a60ea53795b20d65417f7a678d216d8d4be5f` | 44 / 0 | oops |
| `7.1.0` | `a0b32dba9d7d07721c160ea37ff3d2d02d2854ee` | `2a7377e2fbd12dd0d00422b91e0da2be919e75cc` | `0d09b766ed1393b8260d1d349ae0a44d2b9213bf` | 4 / 0 | fix: abyss stuff (a lot of fields are guessed so many stuff is broken or not accurate) |
| `7.2.0` | `acc7a0b48f44f3d51666c91f1125e8e1ee923ca4` | `5964c9d0099effd55ea8a891cc2b3dc6d1fb0512` | `14022d39e2b49a16c70aae39998597143866782e` | 0 / 0 | omg i always mess up submodules 😭 |
| `main` | `d90b737f1ae82c715c4a680fdf3c62a630f37a9c` | `b1fe2122093b3a84912a27e39c23634328b8fb01` | `d4b96f11e346a0771ee834aa12185af8bb467dfd` | 363 / 5 | Update |

## [girluh/LunaGC](https://github.com/girluh/LunaGC)

默认：`7.1.0`；抓取时间（UTC）：`2026-11-14T04:25:36.430034+00:00` 至 `2026-11-14T04:25:39.216669+00:00`。

| 分支 | tip commit | 根 tree | README blob | Δ 默认/本支 | tip 主题 |
| --- | --- | --- | --- | --- | --- |
| `6.6.0` | `49cd58f275925260a90966268b985ab020f78ace` | `302519e3db6ea670b61d555cd0425b935bfe81bb` | `14b9f1dcb5dd930e603140b03bae4cabab6f1f0a` | 17 / 0 | fix spiral abyss (tower) protos and dungeon flow |
| `6.7.0` | `1b4de97e3d01a6c952675522e5ae21a05cc7bd92` | `160285521814e4456702e910ef1b53573198c0cf` | `8b032461dd35eda864a021842d16d0cd8ae5a8e0` | 14 / 0 | fix /talent npe when talent name missing from textmap |
| `7.0.0` | `b3c29518b69d1dde6859d6ab43696ab6bdff7973` | `5e395f33355b2ad6c4ebade955c0eb66a75e1e72` | `71d24926b6ba09cccc403c48099ec215a4a6053a` | 1 / 9 | Merge pull request #4 from Peruere-dev/7.0.0 |
| `7.1.0` | `c2e541dab83296a8292b1e4191031fcb33f8fbc5` | `2ff3c87bc3bde1581f5d39dca92d3aeb0060aea1` | `4c8e854292cd266a37c1f9b9cd2cb9288f599394` | 0 / 0 | 7.1 migration |

## [invoker-bot/LunaGC-Resources](https://github.com/invoker-bot/LunaGC-Resources)

默认：`main`；抓取时间（UTC）：`2026-11-14T04:29:15.942125+00:00` 至 `2026-11-14T04:29:15.942125+00:00`。

| 分支 | tip commit | 根 tree | README blob | Δ 默认/本支 | tip 主题 |
| --- | --- | --- | --- | --- | --- |
| `main` | `dac2a9d43332ef51879522cf39da2831b0ca0543` | `180c4afe8519c11c3fc5db7b6f60bb71bdbc32c7` | `4a84cc85f997d937a79a058b562b7f45298dce7c` | 0 / 0 | fix: register existing blocks for twenty repeatable domains |

## [tangyanliang1/AstaPS](https://github.com/tangyanliang1/AstaPS)

默认：`main`；抓取时间（UTC）：`2026-11-14T04:28:57.933503+00:00` 至 `2026-11-14T04:28:58.731558+00:00`。

| 分支 | tip commit | 根 tree | README blob | Δ 默认/本支 | tip 主题 |
| --- | --- | --- | --- | --- | --- |
| `develop` | `503a33a9d558ae533e083b20956ebf478bf0c525` | `f109e045f1b3351731325a98e875f0623256b1b2` | `aa212dbfa38e8e7c1d5bed104f3a2a9256aef588` | 17 / 13 | feat(dispatch): add built-in plaintext resource server |
| `main` | `a49f51aa6f2787ffbc25cee46801b70dee860d23` | `9940bf7756ce5ff8be8a836a4485a645b490c2e1` | `3b3f5d3b59d66845dfa69cbe7736cf2cbf0dffa4` | 0 / 0 | update to 7.2 |

## [zhujiangheng64-alt/LunaGC_7.2.0](https://github.com/zhujiangheng64-alt/LunaGC_7.2.0)

默认：`7.2.0`；抓取时间（UTC）：`2026-11-14T04:29:11.802957+00:00` 至 `2026-11-14T04:29:15.092608+00:00`。

| 分支 | tip commit | 根 tree | README blob | Δ 默认/本支 | tip 主题 |
| --- | --- | --- | --- | --- | --- |
| `6.7.0` | `6e96b91e4286da2e7eb2c95ed25da14267800d5e` | `c39fa6369b146c94681ab6a71d23ae52c79ecc4a` | `02b0d877c7c49008f364a0426fa248e42fd4b481` | 169 / 3 | Merge pull request #4 from capyb2222/test |
| `7.0.0` | `8e5db4997d1967397c531b693352251babd9a0e4` | `0b6955c2007ca8f6902ebdeb201cd60ad772e57a` | `1d5a60ea53795b20d65417f7a678d216d8d4be5f` | 42 / 0 | oops |
| `7.1.0` | `a0b32dba9d7d07721c160ea37ff3d2d02d2854ee` | `2a7377e2fbd12dd0d00422b91e0da2be919e75cc` | `0d09b766ed1393b8260d1d349ae0a44d2b9213bf` | 2 / 0 | fix: abyss stuff (a lot of fields are guessed so many stuff is broken or not accurate) |
| `7.2.0` | `4062d6353dc6c9d2fa546a5e18ee78850f3446f8` | `0484bdaacb7ba8c110d3e2d1aad2bbb0de3ae80e` | `14022d39e2b49a16c70aae39998597143866782e` | 0 / 0 | chore: update patch submodule |
| `main` | `d90b737f1ae82c715c4a680fdf3c62a630f37a9c` | `b1fe2122093b3a84912a27e39c23634328b8fb01` | `d4b96f11e346a0771ee834aa12185af8bb467dfd` | 361 / 5 | Update |

## [zornicza/AstaPS](https://github.com/zornicza/AstaPS)

默认：`main`；抓取时间（UTC）：`2026-11-14T04:25:32.126790+00:00` 至 `2026-11-14T04:25:32.614787+00:00`。

| 分支 | tip commit | 根 tree | README blob | Δ 默认/本支 | tip 主题 |
| --- | --- | --- | --- | --- | --- |
| `development` | `63d1e1c42bd767253cf4aeefcf4dc6f879e300fc` | `a8471ed51e8073aef3a15176ce510944994c1389` | `fe32d607a70a9f1efa0bbb6624f537331e72d7f3` | 0 / 30 | Drop source_row from static game data output |
| `fix/protocol-generated-out-of-src` | `453dc078daca6c02818a6244ef608de2c5db352c` | `78e6821cdda7028c8eaa7c8c0837e3277bdd4a1d` | `3b3f5d3b59d66845dfa69cbe7736cf2cbf0dffa4` | 0 / 1 | Generate protocol Java into build/ instead of checking it in |
| `main` | `a49f51aa6f2787ffbc25cee46801b70dee860d23` | `9940bf7756ce5ff8be8a836a4485a645b490c2e1` | `3b3f5d3b59d66845dfa69cbe7736cf2cbf0dffa4` | 0 / 0 | update to 7.2 |

## README 内容去重

同一 blob 对应同一 SHA-256；每个分支的根目录 README 路径按各行实际检出，缺失单列。

| Git blob | SHA-256 | 根目录文件名 |
| --- | --- | --- |
| `02b0d877c7c49008f364a0426fa248e42fd4b481` | `cdf5ae48579453d6e0c84def31af0e6e85a466829bdd1be7f53abdd33e24142f` | `README.md` |
| `0d09b766ed1393b8260d1d349ae0a44d2b9213bf` | `2a1ed9ce3d3a67aa506219771c59e84c58864c6350c38438b18e2f8d015ef5d2` | `README.md` |
| `14022d39e2b49a16c70aae39998597143866782e` | `3cec9c108b9ce89882f1b89b38409b20c99c2ca304ccd5d5e59557421906c7cd` | `README.md` |
| `14b9f1dcb5dd930e603140b03bae4cabab6f1f0a` | `7cf8d354f90971ef63f40db80315bfa54d1ae11837923e25096baf1f01097e9f` | `README.md` |
| `1d5a60ea53795b20d65417f7a678d216d8d4be5f` | `96f55c23a4e766cd1444fa47f073fade268711ae7a936d5030b1de6aadeec9be` | `README.md` |
| `24dc51883abddf2483360869420d1139c3871ff3` | `48dec7017d8df3fe290a7d676b93c5d9381a884a76a989a49855451cf5bd68d5` | `README.md` |
| `39d0f41a805baf0aa265eb7445e6d168b434fdfd` | `a7773e6df92017c5df5d06d6ab8f317e4a6bc6bdfbf072179438d63517636f39` | `README.md` |
| `3b3f5d3b59d66845dfa69cbe7736cf2cbf0dffa4` | `15b98e9580a6b2a595ca0d2c75254c9e9376fa1d4e09bf107359856126fe95fb` | `README.md` |
| `4171039024e227838b16fba009577d9def6f5630` | `ff05c125c7cd45c5553e949a735ec6665c2a119448ae860ebb97aefd28328f20` | `README.md` |
| `42c45a8f83fffbed61edb6122b96c051b8a3ae22` | `ee8ae32510108af279496a5328ab90d277fb33d5e622434573aee08a8f5b4013` | `README.md` |
| `4867a4934808a5ef67ad2bd31611e1954f3aeae9` | `b686d919cc06c32ca1f6a41373348bc159c55ff8830fc6dde458744c22edf4e2` | `README.md` |
| `4a84cc85f997d937a79a058b562b7f45298dce7c` | `7583bc82da843110787d125be4a551d7aebc45691169529c2d4ba062631f6508` | `README.md` |
| `4c8e854292cd266a37c1f9b9cd2cb9288f599394` | `400882df573f3d724ebf786e27ca023843c6e29271cabb5872d84743ae7320a8` | `README.md` |
| `4d4e447e5626461857e3045d8cb48ee19ae4ef4d` | `98443f3fb2150dc296a27eb471fe683fb1c470658778323c1bfb2ba9f209835b` | `README.md` |
| `544d0aca7595ebaf188b9c0679ae2e1f78dfcbe1` | `18930a872ba2c7ac988396e040b4707fc4d90cbe8c08ca8e946dfdfe7003ad1a` | `README.md` |
| `560e73b9a0fe3f42c222a680477015a152f214e8` | `26ffaa5d83ff8196ac3820ac3eb223ed69c2c98a9bdb1c55511a01b958a1c4c5` | `README.md` |
| `630a37b063f35bf35478ddeb0a4f656ddae6a7be` | `432f69649beb9aad02003d6037cc6b9c23a7a761b57ef59afce8c34f5b895ecb` | `README.md` |
| `67cd2e116395b77197d75ba204726b6fa5ca44d6` | `55d9d7ebbcd3372b147db2253e2f7259e66bff644d38d2b322435f162fd3e4b0` | `README.md` |
| `71d24926b6ba09cccc403c48099ec215a4a6053a` | `f6130c2c899141cf94fa781108adf234e626e718b05500ba98cbc93f96836a92` | `README.md` |
| `74a4c6c733b783bebd54e763ebd626dc34103191` | `5ce5bd7aab10e02e512452cd9bdd1f53da4092c9b425d1e6048d7998c4176a68` | `README.md` |
| `8b032461dd35eda864a021842d16d0cd8ae5a8e0` | `7c69e1d523a0f5deb609497cec15277d5652bb9c94cba69e3654d0ce4562515d` | `README.md` |
| `9b7b31e1212bd3db9ab83a9fd59aad03435a8acf` | `d76426ce34e83ee742778c2b555a23e325ea99df37e5cd488dc7e452abcc6ce8` | `README.md` |
| `a17abef630105e316ebe69330c02bbfeed4a2d36` | `1ad271071fb0c809f90308c9b6cda8f6caeef4ccdda568a5dbd42a6f13fde521` | `README.md` |
| `aa212dbfa38e8e7c1d5bed104f3a2a9256aef588` | `d69220128d7015802d8f03cb09b805cc71ac5a56527cb17df888f724398301fa` | `README.md` |
| `c370306ab9eae1fe31f0a838d08481f28cb6f4b2` | `ac28439d4fcd0b37f2a6ad8d0fe339a57c599fb610dcbf0467e72f9e134242dd` | `README.md` |
| `cd56b9837b4e59af28177293fe822ad3a56dac8a` | `0d322787021ec549c76d58ec324c9a9f82428594610ba091e1c65c2bc4c6228f` | `README.md` |
| `d4b96f11e346a0771ee834aa12185af8bb467dfd` | `fb37dafa2186d089248dc918e7b9bc01ca1f22a01a36ea8fd0c93f829fe2b9a9` | `README.md` |
| `d7cd3d6c40ab5430449d7dc999eab666df5cd8ae` | `9443bff8792401e26add7683fd8ca9d612e128b8baa49249e30ef43ab50a34a5` | `README.md` |
| `ddd37d661112d617fd14a314b8ed0f792e45d393` | `afac0beda2c8844ad0fe4cda9804a0d21489bc68c3158d563598644c32b5f28f` | `README.md` |
| `ed9483283b503080cb217806bb447443b4beeb62` | `edefe3a742510c923d24d12a1224d6f75b741eb3aa71b850a5209657e3f9f9fa` | `README.md` |
| `fe32d607a70a9f1efa0bbb6624f537331e72d7f3` | `91c060a545117ac221703bb403fb57f15f2bb2674851e342f122216d40f8bc3b` | `README.md` |

上述每行完成 tip 主题、根树与 README 摘要初筛；只有报告列出的文件完成深入静态对照。对其他差异树、标签对应历史实现或资源子树，尚不能称为完整源码审计。
