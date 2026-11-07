"""Audit frozen Java resource consumers; no conversion, JVM or runtime side effects."""
import argparse
import hashlib
import json
from pathlib import Path
import re

REPO = Path(__file__).resolve().parents[1]
JAVA = 'src/main/java/emu/grasscutter/'
SOURCE_AUDIT = 'local/migration72/d02-active/resources/source-audit-frozen-2.json'
SOURCE_AUDIT_SHA256 = 'e874ec98e88f651281869ae0b2caf8c45ae953032643fec93366d310042a726d'
COMMIT = 'd54f6f0d423ded9d055bb740a3e5f3aa8f2667f1'
PINNED_INPUTS = {
    "docs/CLIENT_72_AVATAR_DEEP_RESEARCH.md": "8b6648e5a33c3efb50ee08aa8238465a7daadf01fb41f872639a7e6a12d61fa2",
    "docs/CLIENT_72_AVATAR_LIVE_CONTRACT.md": "91f6fb3ab6dc4be912b69328877cbe170e911ee0298cba095492e2531990e6fe",
    "docs/CLIENT_72_AVATAR_SKILL_DEPOT.md": "260d710727d92693b9e09870f8cd49db26d323e36d5745aad8024fa3bbfcdc96",
    "docs/CLIENT_72_D02_CHAPTER_LOAD.md": "471a5c9ec6642842282269273488f78489f36a5a93dea9815bbd9d0dc56081d6",
    "docs/CLIENT_72_D02_MINIMUM_RESOURCES.md": "d6c7a5151bdcd7deac4dbf268cf437fa8fe81adce14d79b20b68ce641c6dae4f",
    "docs/CLIENT_72_D02_QUEST_303_RECOVERY.md": "47bdc171842a709b03036d0433e345d5f7bfda754a8e5fc267587344bcbe97ce",
    "docs/CLIENT_72_D02_QUEST_303_START.md": "cbec688b32a03f2e3fe551f2bf940cd8d85fd2cbbd6cb15ed7344ff0d517833e",
    "docs/CLIENT_72_D02_QUEST_ENTRY.md": "ad777a5946eb857ce33c74a384afe2bb2f42e4c0d704763b49792d00f5b8904b",
    "docs/CLIENT_72_D02_RESOURCE_SOURCES.md": "87bbe5b92db3f154a900939f296c5313d5861853e3a8f1c685577b0e74d42e24",
    "docs/CLIENT_72_D02_SCENE_ACTIVE.md": "c91659feb59e809eba3b675326528bfc6d79a66acb19209d44445e54f49e5001",
    "docs/CLIENT_72_RESOURCE_DEEP_RESEARCH.md": "bc4652dfbc226697458cc77677caecddacb1af6f412f0a812ea7f6d318c3ff24",
    "protocol/cn-7.2/avatar-deep-ornaments-observations.json": "bf385528a5f1bffbd15b63a51eea7127469662f93be8c33861f1cc3a8ade3c3d",
    "protocol/cn-7.2/avatar-skill-depot-observations.json": "a8a7b4ce71869c4af88e3bf8164413ed109c7d6e051478d1e682b19e5d0e1a95",
    "src/main/java/emu/grasscutter/GameConstants.java": "9828532592b92ecf2ed2e4fef002eadada87b3a77670f2927a3ee8adfcf9f617",
    "src/main/java/emu/grasscutter/data/GameData.java": "518585689d8181a31bef0558ab730939e5186ecd2ae98b0b20753db2ee879364",
    "src/main/java/emu/grasscutter/data/GameResource.java": "ae8d59b5b7f6083035b9975cd6e6d1f8ce4e26420d7c643872dba20890b47a12",
    "src/main/java/emu/grasscutter/data/QuestBinFallback.java": "9d896c0c317c880f9d062cb68859397070f97d9f1b3306b5797e073b3f4a121e",
    "src/main/java/emu/grasscutter/data/ResourceLoadDiagnostics.java": "337134954c6f74373f56b9b3e20133325e4ff1e2cb33a6b833a645ed6262c8ee",
    "src/main/java/emu/grasscutter/data/ResourceLoader.java": "fa7b7d26ef234e92f6d7eac5790922d36dc76fb113b5a31cc80c7bad40d2a015",
    "src/main/java/emu/grasscutter/data/ResourceType.java": "8a8711f3ad4319b14f4cb4bd9c9dcc6531dad77eae287d8cdf495b118e7d3b6e",
    "src/main/java/emu/grasscutter/data/binout/MainQuestData.java": "ae768bb1da5a7d1ded553a0ca6f272eff5803c5ded5825c6b9db54f3b1e0bbce",
    "src/main/java/emu/grasscutter/data/common/CurveInfo.java": "096a15f21edbde65b2418ef6f1009b7c7dda15fe5a3e14a96b6a097111befb0d",
    "src/main/java/emu/grasscutter/data/common/PointData.java": "b3906ef372affe4d27217be8618c49203efbd092a3550edcbbd5083bf0a6c253",
    "src/main/java/emu/grasscutter/data/common/PropGrowCurve.java": "fbd61192b99281039240570ddce49e458589ad0b7edffa14bc853f8b363c35ad",
    "src/main/java/emu/grasscutter/data/excels/ChapterData.java": "7d0c865f0138b23eed854b60ac846767ff26610d3831e78384b36d4b05afd677",
    "src/main/java/emu/grasscutter/data/excels/ItemData.java": "ec5ec94f2cf7ed4be3cdc7a0249004115ec149a988e19529bd53315016f02c93",
    "src/main/java/emu/grasscutter/data/excels/ProudSkillData.java": "bacd37a17a85d409b4cc701cc0c0e180fdea8fcf208026a148e73f1afaeadef2",
    "src/main/java/emu/grasscutter/data/excels/avatar/AvatarCurveData.java": "fe5e9437f736b81a1da0f2be7164d25d5a3fd07f6032694c001d5a8bc9617c55",
    "src/main/java/emu/grasscutter/data/excels/avatar/AvatarData.java": "be326428e1c3eee90c1c588c35b8d8133e5e59fa81adaeba4f902b7a4d53a779",
    "src/main/java/emu/grasscutter/data/excels/avatar/AvatarSkillData.java": "d87cf73c7105dd7854f3fc20ccb009bf0d850b703ba45cba0d572b6a66e1b6bc",
    "src/main/java/emu/grasscutter/data/excels/avatar/AvatarSkillDepotData.java": "2aa9dd97dd2d375d430af7053d3e7adab58e91cf3b997fadf5852d33c24d4f52",
    "src/main/java/emu/grasscutter/data/excels/avatar/AvatarTalentData.java": "8bdcc424b2860e11e9e49bea4202e26991bdb7c133361e6e4f129d5ef75cbf8d",
    "src/main/java/emu/grasscutter/data/excels/quest/QuestData.java": "15f4c849f1be0cfb3f4eb2bb5301ae46094db6c4de0387499d0b4a3af8b54264",
    "src/main/java/emu/grasscutter/data/excels/scene/SceneData.java": "61f243cea0e476bfc83f45feef5014205d5a766a9825d92bf200ac3afa1a81b5",
    "src/main/java/emu/grasscutter/data/excels/weapon/WeaponCurveData.java": "6df59a1d5cd7c17903fc330fd504072285f74549c9f508af6409fb8842da40e0",
    "src/main/java/emu/grasscutter/data/excels/weapon/WeaponPromoteData.java": "a3e604cdc2bc3437b5476b746ed7366851c205e8174843e27469339e6054224d",
    "src/main/java/emu/grasscutter/game/avatar/Avatar.java": "9176d33373ee5e663f1cc8f3a9137b65c3def1908fc13035dc5569e4bd4cc31d",
    "src/main/java/emu/grasscutter/game/avatar/AvatarStorage.java": "6d6b07102d5f8b9f95ebbd4e18d1489f9143d502100dcfaadff0235db138e784",
    "src/main/java/emu/grasscutter/game/entity/EntityAvatar.java": "6ea956565c6cb933245a48abb3d4b126c4a2c9b473ec3aaaee1de40a4cb8844c",
    "src/main/java/emu/grasscutter/game/entity/EntityWeapon.java": "67e74b4ef6bc5e0e8f2ef91effa69065a07d6692f55bf6185d49341c400a2d7e",
    "src/main/java/emu/grasscutter/game/inventory/EquipType.java": "4eb558de2784a207702cb3f0d4449f9b6bc96c2a24196c31f4c367e21633e8d4",
    "src/main/java/emu/grasscutter/game/inventory/GameItem.java": "e9de522816d9400c1cd3a3ba3ae9ca9ff6691365ceee1ecb8d534c4a03cef132",
    "src/main/java/emu/grasscutter/game/inventory/ItemType.java": "c62d03cf4c77a9b6711fd5f0756a234fc80be6deedfc1e8fa48b5dfd5a6f3559",
    "src/main/java/emu/grasscutter/game/player/Player.java": "7024c88ea996f3b5864d3badbb90ce742faa382153ee27ac5d0b3dc9ecf3e2c4",
    "src/main/java/emu/grasscutter/game/player/PlayerProgressManager.java": "9026574e73aac4d640a74c71da8577121bd7b797a7d3e05706f86f44ad706568",
    "src/main/java/emu/grasscutter/game/player/TeamManager.java": "e22bf409e5d9f1d9182f0b4094ed72e02e40751347a152e825c6c3e4f9434dfb",
    "src/main/java/emu/grasscutter/game/props/ElementType.java": "7f64fcc73d96a908cf911961557897710224eeb5df190afad8ab9d68824a4d49",
    "src/main/java/emu/grasscutter/game/props/FightProperty.java": "a7abcc59bbaa39476158ab7bb39af544494b17617514d60f687ba4052f88cb57",
    "src/main/java/emu/grasscutter/game/props/SceneType.java": "90300af8cad8f3b96e53660b97a6e8bf7579b1412b69c6adae7577a72dfe5afa",
    "src/main/java/emu/grasscutter/game/props/WeaponType.java": "684f6b65ea8de2fbf1b5efac772ef3476c541b3d5056a5299c8318c7c953f8b0",
    "src/main/java/emu/grasscutter/game/quest/QuestManager.java": "a3400f3cbf3bdbb9f15c2df6de21259c501497024f86b4362bdcb50b861ee49e",
    "src/main/java/emu/grasscutter/game/quest/enums/LogicType.java": "5e9b83a30ee5a8a5eeaeb048000380cc1d64d5758ed720b3d47531615d0a92bd",
    "src/main/java/emu/grasscutter/game/world/World.java": "7194fffbe0cfeb39c7a08b88dff1b52c0745bbc61e98f33987017066fbb967f4",
    "src/main/java/emu/grasscutter/scripts/SceneScriptManager.java": "e595021bd432b865e9c198ccd4b9194fb1769079eaa877da74615bf076367a1f",
    "src/main/java/emu/grasscutter/scripts/ScriptLoader.java": "6f24ea24be4f9a0d7839e88adf7010cec05e86fb987e91cff09b5b26dd6d23e8",
    "src/main/java/emu/grasscutter/scripts/data/SceneConfig.java": "c506772972687b417d4c88c5bff9746920cc9bea024066d156de7ec35a698f59",
    "src/main/java/emu/grasscutter/scripts/data/SceneMeta.java": "6305976fd54971a8ae681e22ee1edec88ccdc12acbe7664cedcfddee14f92773",
    "src/main/java/emu/grasscutter/server/packet/recv/HandlerEnterSceneDoneReq.java": "f0b84bb755cf87dcebb0d0fcd6ca1e901b95873f2730037cce1e367706524bd5",
    "src/main/java/emu/grasscutter/server/packet/recv/HandlerSceneInitFinishReq.java": "49d6dbc2400405d6583a2df0cb0d868476a01737f822554e1330d3b4a0a38c29",
    "src/main/java/emu/grasscutter/server/packet/recv/HandlerSetPlayerBornDataReq.java": "0785dbdd712044eb29b4584122ee37eeb3a5652055186bea167dc0d16fa5af35",
    "src/main/java/emu/grasscutter/server/packet/send/PacketPlayerEnterSceneInfoNotify.java": "07763b4d1e515e1083681209acc11280c78abdfa6190669d7c3ca0b6da1d9201",
    "src/main/java/emu/grasscutter/server/packet/send/PacketSceneTeamUpdateNotify.java": "9d90c0b79a6d46cfee1c8f124ac11265d0dead187179db7d889ed2c86c29975d",
    "src/main/java/emu/grasscutter/utils/JsonAdapters.java": "f810ac639c6e631e05028696d5742db109cea040b95bf24a13e0f7ba8f0eb38c",
    "src/main/java/emu/grasscutter/utils/JsonUtils.java": "4f5051e0bbd4a53d5993ab7b2750bbfd14c1fa41e722a69cf5effd1887453abf"
}
DATA_TREE_SHA256 = 'ba21b9ca80d52e0e25bc93c48867c59173f78d4b02971efdef6e03a2544c7d88'

# These are Java destinations, never proposed target aliases. Types are checked against source.
FIELD_GROUPS = {
    'data/excels/avatar/AvatarData.java': {
        'id': 'int', 'initialWeapon': 'int', 'skillDepotId': 'int',
        'candSkillDepotIds': 'List<Integer>', 'featureTagGroupID': 'int',
        'iconName': 'String', 'avatarPromoteId': 'int', 'weaponType': 'WeaponType',
        'hpBase': 'float', 'attackBase': 'float', 'defenseBase': 'float',
        'critical': 'float', 'criticalHurt': 'float', 'propGrowCurves': 'List<PropGrowCurve>',
        'nameTextMapHash': 'long'},
    'data/excels/avatar/AvatarSkillDepotData.java': {
        'id': 'int', 'energySkill': 'int', 'attackModeSkill': 'int',
        'featureTagGroupID': 'int', 'skills': 'List<Integer>', 'subSkills': 'List<Integer>',
        'extraAbilities': 'List<String>', 'talents': 'List<Integer>',
        'inherentProudSkillOpens': 'List<InherentProudSkillOpens>',
        'specialProudSkillOpens': 'List<SpecialProudSkillOpens>',
        'skillDepotAbilityGroup': 'String', 'needAvatarPromoteLevel': 'int'},
    'data/excels/avatar/AvatarSkillData.java': {
        'id': 'int', 'costElemVal': 'int', 'costElemType': 'ElementType',
        'proudSkillGroupId': 'int', 'abilityName': 'String', 'cdTime': 'float',
        'maxChargeNum': 'int', 'specialEnergyMin': 'float', 'specialEnergyMax': 'float',
        'nameTextMapHash': 'long', 'descTextMapHash': 'long'},
    'data/excels/ItemData.java': {
        'id': 'int', 'itemType': 'ItemType', 'equipType': 'EquipType',
        'gadgetId': 'int', 'weaponPromoteId': 'int', 'skillAffix': 'int[]',
        'weaponProp': 'WeaponProperty[]', 'propType': 'FightProperty',
        'initValue': 'float', 'type': 'String', 'stackLimit': 'int'},
    'data/excels/scene/SceneData.java': {
        'id': 'int', 'sceneType': 'SceneType', 'scriptData': 'String',
        'levelEntityConfig': 'String', 'specifiedAvatarList': 'List<Integer>',
        'maxSpecifiedAvatarNum': 'int'},
    'data/excels/ChapterData.java': {
        'id': 'int', 'beginQuestId': 'int', 'endQuestId': 'int', 'needPlayerLevel': 'int'},
    'data/excels/quest/QuestData.java': {
        'subId': 'int', 'mainId': 'int', 'order': 'int', 'descTextMapHash': 'long',
        'acceptCondComb': 'LogicType', 'finishCondComb': 'LogicType', 'failCondComb': 'LogicType',
        'acceptCond': 'List<QuestAcceptCondition>', 'finishCond': 'List<QuestContentCondition>',
        'failCond': 'List<QuestContentCondition>', 'beginExec': 'List<QuestExecParam>',
        'finishExec': 'List<QuestExecParam>', 'failExec': 'List<QuestExecParam>',
        'paramStr': 'String', 'count': 'int'},
    'data/binout/MainQuestData.java': {
        'subQuests': 'JsonObject[]', 'preloadLuaList': 'BigInteger[]',
        'talks': 'List<TalkData>', 'titleTextMapHash': 'long'},
    'scripts/data/SceneConfig.java': {
        'born_pos': 'Position', 'born_rot': 'Position', 'die_y': 'float'},
    'data/common/PropGrowCurve.java': {'type': 'String', 'growCurve': 'String'},
}

NESTED_OWNERS = {
    'AvatarSkillDepotData.needAvatarPromoteLevel': 'InherentProudSkillOpens',
    'ItemData.propType': 'WeaponProperty', 'ItemData.initValue': 'WeaponProperty',
    'ItemData.type': 'WeaponProperty',
    'QuestData.paramStr': 'QuestCondition', 'QuestData.count': 'QuestCondition',
}

# Ordered anchors are narrow source assertions, not execution claims.
FACTS = [
    ('loadAll_order', 'data/ResourceLoader.java',
     ['loadAbilityEmbryos();', 'loadResources(true);', 'GameDepot.load();',
      'loadQuests();', 'loadScriptSceneData();', 'loadScenePoints();', 'loadedAll = true;'],
     'Ability catalogs precede Excel, then quest BIN, script-scene metadata and points; final flag is not an ERROR-free guarantee.'),
    ('priority_parallel', 'data/ResourceLoader.java',
     ['getResourceDefClassesPrioritySets()', 'classes.stream()', '.parallel()', '.unordered()'],
     'Priority batches are sequential; classes of one priority have no defined relative order.'),
    ('install_after_onLoad', 'data/ResourceLoader.java',
     ['resource.onLoad();', 'int id = resource.getId();', 'map.put(id, resource);'],
     'Row onLoad runs before installation; duplicate IDs overwrite and onLoad side effects are not rolled back.'),
    ('row_rejection', 'data/ResourceLoader.java',
     ['JsonUtils.decode(source.get(i), c)', 'diagnostics.reject("row " + (i + 1), e);'],
     'Typed/onLoad exceptions reject that row and continue; no all-or-nothing table guarantee.'),
    ('avatar_curve_dependency', 'data/excels/avatar/AvatarData.java',
     ['int size = GameData.getAvatarCurveDataMap().size();', 'int level = curveData.getLevel() - 1;',
      'for (PropGrowCurve growCurve : this.propGrowCurves)',
      'this.hpGrowthCurve[level] = curveData.getCurveInfos().get(growCurve.getGrowCurve());'],
     'Nonempty curve map requires propGrowCurves, valid level indexes, known types and referenced float curve values; missing values can reject onLoad.'),
    ('depot_skill_and_abilities', 'data/excels/avatar/AvatarSkillDepotData.java',
     ['GameData.getAvatarSkillDataMap().get(this.energySkill)',
      'this.elementType = ElementType.None;', 'GameDepot.getPlayerAbilities().get(getSkillDepotAbilityGroup())',
      'if (this.inherentProudSkillOpens == null)', 'this.specialProudSkillOpens == null'],
     'Missing energy skill uses None; unknown ability group leaves abilities null; inherent/special absent lists normalize but this does not authorize target omissions.'),
    ('new_character_requires_maps', 'server/packet/recv/HandlerSetPlayerBornDataReq.java',
     ['int baseDepot = avatarId == GameConstants.MAIN_CHARACTER_MALE ? 501 : 701;',
      '!GameData.getAvatarSkillDepotDataMap().containsKey(baseDepot)',
      'Avatar mainCharacter = new Avatar(avatarId);'],
     'Birth validation requires Avatar and base depot even when questing-disabled path subsequently chooses 504/704.'),
    ('avatar_base_depot', 'game/avatar/Avatar.java',
     ['case GameConstants.MAIN_CHARACTER_MALE -> GameData.getAvatarSkillDepotDataMap().get(501);',
      'this.skillDepotId = skillDepot.getId();', 'getSkillsAndEnergySkill()',
      'skillDepot.getInherentProudSkillOpens().stream()'],
     'Constructor uses base depot; null depot fails at dereference; skills+energy+attack positive distinct IDs get level 1 without proving referenced skill rows exist.'),
    ('starting_weapon_before_null_check', 'game/avatar/AvatarStorage.java',
     ['new GameItem(avatar.getAvatarData().getInitialWeapon())', 'if (weapon.getItemData() != null)',
      'avatar.equipItem(weapon, true);'],
     'The nonnull test occurs after GameItem construction; it cannot handle a missing ItemData row.'),
    ('missing_weapon_throws', 'game/inventory/GameItem.java',
     ['this(GameData.getItemDataMap().get(itemId));', 'public GameItem(ItemData data, int count)',
      'this.itemId = data.getId();', 'switch (data.getItemType())'],
     'Absent ItemData dereferences null before AvatarStorage can test it; ITEM_WEAPON establishes weapon level/affixes.'),
    ('weapon_equipment_slot', 'data/excels/ItemData.java',
     ['this.itemType == ItemType.ITEM_WEAPON', 'this.equipType = EquipType.EQUIP_WEAPON;',
      'this.equipType = EquipType.EQUIP_NONE;'],
     'Weapon item type, not a fabricated slot constant, determines equipType during onLoad.'),
    ('weapon_stats', 'game/avatar/Avatar.java',
     ['GameData.getWeaponCurveDataMap().get(weapon.getLevel())',
      'for (WeaponProperty weaponProperty : weapon.getItemData().getWeaponProperties())',
      'GameData.getWeaponPromoteData('],
     'A present weapon curve makes weaponProp iterable mandatory; missing curve/promote rows skip stats and do not prove correctness.'),
    ('scene_team_weapon', 'server/packet/send/PacketSceneTeamUpdateNotify.java',
     ['p.getTeamManager().getActiveTeam(true)', 'getWeaponNotNull().getWeaponEntity()',
      'getWeaponNotNull().getGuid()', 'entityAvatar.getWeaponEntityId()'],
     'Every active avatar in the world needs a nonnull weapon; weapon entity may be null and serializes empty abilities/zero ID, whose target validity remains unproved.'),
    ('saved_energy_protection', 'game/avatar/Avatar.java',
     ['private void captureVitals()',
      '(depot.getEnergySkill() != 0 && depot.getEnergySkillData() == null)',
      'this.currentEnergy = boundedVital('],
     'Missing depot or a nonzero unresolved energy skill preserves stored energy scalars; do not convert unknown pools to zero.'),
    ('quest_missing_accept', 'data/excels/quest/QuestData.java',
     ['if (this.acceptCond == null) this.automaticAcceptance = false;',
      'this.acceptCondComb = LogicType.LOGIC_NONE;', 'if (!this.automaticAcceptance)'],
     'Absent/null acceptance disables automatic indexing; explicit empty acceptance may index NONE. They must remain distinct.'),
    ('quest_nested_types', 'data/excels/quest/QuestData.java',
     ['QuestExec type;', 'String[] param;', 'String count;',
      'private TYPE type;', 'private int[] param;', 'private String paramStr = "";', 'private int count;'],
     'Exec parameters are strings; condition parameters are int32; condition enum generic binds QuestCond/QuestContent. Raw key aliases do not authorize new target aliases.'),
    ('chapter_keys', 'data/excels/ChapterData.java',
     ['beginQuestChapterMap.put(beginQuestId, this);', 'endQuestChapterMap.put(endQuestId, this);'],
     'Chapter notifications use separate begin/end indexes; omitted ints are zero and duplicate keys overwrite.'),
    ('quest_bin_after_excel', 'data/ResourceLoader.java',
     ['GameData.getMainQuestDataMap().put(mainQuest.getId(), mainQuest);', 'mainQuest.onLoad();'],
     'BIN parent enters map before onLoad/fallback; a final parent count alone does not prove children installed.'),
    ('quest_cold_gate', 'game/quest/QuestManager.java',
     ['ResourceLoader.isLoadedAll()'],
     'Positive manager cold rebind requires natural resource loading; existing 303 tests preserve loadedAll=false rejection.'),
    ('scene_row_missing', 'game/world/World.java',
     ['var sceneData = GameData.getSceneDataMap().get(sceneId);', 'if (sceneData != null)',
      'scene = new Scene(this, sceneData);', 'return null;'],
     'Missing scene row makes getSceneById return null, not an automatically constructed scene.'),
    ('scene3_script_conditional', 'scripts/SceneScriptManager.java',
     ['this.getScene().getId() < 10', '!Grasscutter.getConfig().server.game.enableScriptInBigWorld',
      'this.initialization.complete(false);', 'new Thread(this::init).start();'],
     'Scene3 big-world scripts depend on configured script enablement; disabling it is not target resource acceptance.'),
    ('scene_meta_source', 'scripts/data/SceneMeta.java',
     ['ScriptLoader.getScript("Scene/" + sceneId + "/scene" + sceneId + ".lua")',
      'if (cs == null)', 'return null;', 'this.context.get("scene_config")',
      'this.context.get("blocks")', 'this.context.get("block_rects")'],
     'Java scene script metadata comes from Lua config/blocks/rects, not SceneExcel.scriptData or a client stageType alias.'),
]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--repo', type=Path, default=REPO)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    repo, output = args.repo.resolve(), args.output.resolve()
    allowed = repo / 'local/migration72/d02-active/java-resource-contract-sol'
    if not output.is_relative_to(allowed):
        parser.error('Output must stay in the dedicated ignored audit directory')
    report = audit(repo)
    data = (json.dumps(report, ensure_ascii=False, indent=2) + '\n').encode('utf-8')
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_bytes(data)
    print(json.dumps({'javaFields': sum(len(x['fields']) for x in report['javaFields']),
                      'sourceFacts': len(report['sourceFacts']), 'candidateInputs': len(report['candidateInputs']),
                      'sha256': sha256(data), 'resourceReady': False}))


def sha256(data):
    return hashlib.sha256(data).hexdigest()


def read_pinned(path, expected):
    data = path.read_bytes()
    if sha256(data) != expected:
        raise ValueError('Frozen input changed: ' + path.name)
    return data


def unique_object(pairs):
    result = {}
    for key, value in pairs:
        if key in result:
            raise ValueError('Duplicate JSON key: ' + key)
        result[key] = value
    return result


def inspect_field(source, name, java_type):
    # Bounded extractor for explicitly selected declarations, not a general Java parser.
    pattern = (r'(?m)^[ \t]*(?:@Getter(?:\([^\n]*\))?\s+)?'
               r'(?:(?:private|public|protected|transient|static|final)\s+)*' + re.escape(java_type) +
               r'\s+' + re.escape(name) + r'\s*(?:=\s*([^;\n]+))?;')
    matches = list(re.finditer(pattern, source))
    if len(matches) != 1:
        raise ValueError('Expected one typed Java declaration: ' + java_type + ' ' + name)
    match = matches[0]
    prefix = source[source.rfind(';', 0, match.start()) + 1:match.start()]
    annotations = list(re.finditer(r'@SerializedName\((.*?)\)\s*$', prefix, re.S))
    keys = re.findall(r'"([^"\n]+)"', annotations[-1][1]) if annotations else [name]
    initializer = match[1]
    default = initializer if initializer is not None else (
        'false' if java_type == 'boolean' else '0' if java_type in ('int', 'long', 'float', 'double') else 'null')
    return dict(field=name, javaType=java_type, acceptedJavaKeys=keys,
                declarationLine=source.count('\n', 0, match.start()) + 1,
                initializer=initializer, javaOmittedDefault=default,
                targetMappingApproved=False)


def ordered_anchors(source, snippets):
    result = []
    cursor = 0
    for snippet in snippets:
        position = source.find(snippet, cursor)
        if position < 0:
            raise ValueError('Missing/out-of-order Java source anchor: ' + snippet)
        result.append(dict(line=source.count('\n', 0, position) + 1, text=snippet))
        cursor = position + len(snippet)
    return result


def project_row(row, names):
    # No get(..., 0), coercion, aliases or fabricated empty collections.
    return {name: dict(present=name in row, value=row[name]) if name in row else dict(present=False)
            for name in names}


def candidate_projection(row, fields):
    return {field['field']: dict(acceptedJavaKeys=field['acceptedJavaKeys'],
                rawMatches={key: row[key] for key in field['acceptedJavaKeys'] if key in row})
            for field in fields if not field.get('nestedOwner')}


def select_row(rows, key, wanted):
    matches = [row for row in rows if row.get(key) == wanted]
    if len(matches) != 1:
        raise ValueError('Expected one candidate row: ' + key + '=' + str(wanted))
    return matches[0]


def data_inventory(repo):
    rows = []
    annotations = []
    for path in sorted((repo / JAVA / 'data').rglob('*.java')):
        data = path.read_bytes()
        relative = path.relative_to(repo).as_posix()
        rows.append(dict(path=relative, sha256=sha256(data)))
        source = data.decode('utf-8-sig')
        match = re.search(r'@ResourceType\((.*?)\)\s*', source, re.S)
        if match:
            names = re.findall(r'"([^"\n]+\.json)"', match[1])
            priority = re.search(r'loadPriority\s*=\s*LoadPriority\.(\w+)', match[1])
            annotations.append(dict(source=relative, names=names,
                                    priority=priority[1] if priority else 'NORMAL'))
    digest = sha256(json.dumps(rows, separators=(',', ':'), sort_keys=True).encode())
    return rows, annotations, digest


def candidate_inputs(repo, manifest):
    local = repo / 'local/migration72'
    bases = {'audit_cache': local / 'd02-active/resources/candidate',
             'fixed_candidate': local / 'sources/AnimeGameData' / COMMIT,
             'r3_mixed_fixture': local / 'd02-r3-candidate',
             'chapter_fixed_copy': local / 'offline-sol-terra/chapter-audit'}
    values, inputs = {}, []
    for entry in manifest['files']:
        origin = next(row['origin'] for row in entry['origins'] if row['accepted'])
        relative = ('ChapterExcelConfigData.json' if origin == 'chapter_fixed_copy'
                    else entry['path'])
        data = read_pinned(bases[origin] / relative, entry['sha256'])
        blob = hashlib.sha1(b'blob ' + str(len(data)).encode() + b'\0' + data).hexdigest()
        if len(data) != entry['size'] or blob != entry['gitBlob']:
            raise ValueError('Candidate Git blob/size mismatch: ' + entry['path'])
        inputs.append(dict(path=entry['path'], origin=origin, size=len(data),
                           gitBlob=blob, sha256=sha256(data)))
        if entry['path'].endswith('.json'):
            values[entry['path']] = json.loads(data, object_pairs_hook=unique_object)
    return values, inputs


def audit(repo):
    sources = {path: read_pinned(repo / path, digest).decode('utf-8-sig')
               for path, digest in PINNED_INPUTS.items()}
    inventory, annotations, inventory_hash = data_inventory(repo)
    if inventory_hash != DATA_TREE_SHA256:
        raise ValueError('Frozen Java data tree changed')
    manifest = json.loads(read_pinned(repo / SOURCE_AUDIT, SOURCE_AUDIT_SHA256),
                          object_pairs_hook=unique_object)
    values, candidates = candidate_inputs(repo, manifest)
    fields = []
    for path, selected in FIELD_GROUPS.items():
        declarations = []
        for name, java_type in selected.items():
            field = inspect_field(sources[JAVA + path], name, java_type)
            field['nestedOwner'] = NESTED_OWNERS.get(Path(path).stem + '.' + name)
            declarations.append(field)
        fields.append(dict(source=JAVA + path, fields=declarations))
    by_table = {Path(group['source']).stem: group['fields'] for group in fields}
    facts = [dict(id=name, source=JAVA + path, evidence=ordered_anchors(sources[JAVA + path], anchors),
                  conclusion=conclusion) for name, path, anchors, conclusion in FACTS]
    selected = []
    selection = [
        ('AvatarExcelConfigData', 'AvatarData', 'id', [10000005, 10000007]),
        ('AvatarSkillDepotExcelConfigData', 'AvatarSkillDepotData', 'id', [501, 701]),
        ('AvatarSkillExcelConfigData', 'AvatarSkillData', 'id', [100540, 100550, 11101]),
        ('WeaponExcelConfigData', 'ItemData', 'id', [11101]),
        ('SceneExcelConfigData', 'SceneData', 'id', [3]),
        ('QuestExcelConfigData', 'QuestData', 'subId', [30302])]
    for table, owner, key, ids in selection:
        rows = values['ExcelBinOutput/' + table + '.json']
        for identifier in ids:
            row = select_row(rows, key, identifier)
            selected.append(dict(table=table, key=key, id=identifier, rawKeys=sorted(row),
                                 javaDestinationProjection=candidate_projection(row, by_table[owner])))
    depots = values['ExcelBinOutput/AvatarSkillDepotExcelConfigData.json']
    skills = values['ExcelBinOutput/AvatarSkillExcelConfigData.json']
    references = []
    for depot_id in (501, 701):
        depot = select_row(depots, 'id', depot_id)
        for key in ('skills', 'subSkills', 'energySkill', 'attackModeSkill'):
            if key not in depot:
                references.append(dict(depotId=depot_id, field=key, present=False))
                continue
            ids = depot[key] if isinstance(depot[key], list) else [depot[key]]
            references.append(dict(depotId=depot_id, field=key, present=True, rawValue=depot[key],
                                   referencedRows=[dict(id=i, count=sum(row.get('id') == i for row in skills))
                                                   for i in ids]))
    chapter = values['BinOutput/Quest/303.json']
    chapters = values['ExcelBinOutput/ChapterExcelConfigData.json']
    ornaments = {table: values['ExcelBinOutput/' + table + '.json'] for table in (
        'AvatarOrnamentGroupExcelConfigData', 'AvatarOrnamentExcelConfigData')}
    return dict(schemaVersion=1, task='D02-R3-Java-resource-consumer-contract',
                scope='Frozen source contract and candidate raw projection only; no target-to-Java mapping or runtime proof.',
                inputSHA256=PINNED_INPUTS, sourceAuditSHA256=SOURCE_AUDIT_SHA256,
                toolSHA256=sha256(Path(__file__).read_bytes()),
                javaDataTree=dict(sha256=inventory_hash, files=inventory, resourceAnnotations=annotations),
                javaFields=fields, sourceFacts=facts, candidateInputs=candidates,
                selectedCandidateRows=selected, depotSkillReferences=references,
                ornaments=dict(rawCandidateRows=ornaments,
                    javaResourceDefinitions=[row for row in annotations if 'ornament' in str(row).lower()],
                    defaultOrEmptyTargetPermission=False, targetTag38SourceMappingApproved=False),
                quest303=dict(binId=chapter['id'], binChildren=len(chapter['subQuests']),
                    firstChild=project_row(select_row(chapter['subQuests'], 'subId', 30302),
                        ['subId', 'mainId', 'acceptCond', 'acceptCondComb', 'finishCond', 'beginExec', 'finishExec']),
                    chapterExcelBegin30302=[row['id'] for row in chapters if row.get('beginQuestId') == 30302],
                    chapterExcelEnd30302=[row['id'] for row in chapters if row.get('endQuestId') == 30302],
                    existingExecutedContractsReusedNotRerun=True),
                targetToJavaMappingApproved=False, naturalRevisionSelected=False,
                javaResourcesLoaded=False, positiveQuestManagerRebindExecuted=False,
                resourceReady=False, d02Complete=False,
                limitations=['Java omitted defaults are not approved target defaults.',
                    'No Gson/JVM/native parser/loader, DB, service, client or debugger executes.',
                    'No complete P0 resource closure or loadAll first-failure claim.',
                    'Two real 0000006f payloads exist; literal-to-table/parser and exporter bridges remain separate work.',
                    'Unknown target aliases, acceptance rules, enum alignment and Ornament persistence stay unresolved.'])


if __name__ == '__main__':
    main()
