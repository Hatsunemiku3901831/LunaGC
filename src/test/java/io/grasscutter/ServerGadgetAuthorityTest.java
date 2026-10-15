package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import emu.grasscutter.data.GameData;
import emu.grasscutter.data.ResourceLoader.AbilityConfigData;
import emu.grasscutter.data.binout.AbilityData;
import emu.grasscutter.data.binout.AbilityModifier.AbilityModifierAction;
import emu.grasscutter.data.excels.GadgetData;
import emu.grasscutter.data.excels.monster.MonsterData;
import emu.grasscutter.game.ability.Ability;
import emu.grasscutter.game.ability.actions.ActionCreateGadget;
import emu.grasscutter.game.avatar.Avatar;
import emu.grasscutter.game.entity.*;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.props.FightProperty;
import emu.grasscutter.game.world.Position;
import emu.grasscutter.net.proto.AbilityActionCreateGadgetOuterClass.AbilityActionCreateGadget;
import emu.grasscutter.net.proto.EvtCreateGadgetNotifyOuterClass.EvtCreateGadgetNotify;
import emu.grasscutter.net.proto.SceneEntityInfoOuterClass.SceneEntityInfo;
import emu.grasscutter.net.proto.VectorOuterClass.Vector;
import emu.grasscutter.server.game.GameSession;
import emu.grasscutter.server.packet.recv.HandlerEvtCreateGadgetNotify;
import emu.grasscutter.utils.JsonUtils;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.Path;
import java.util.*;

class ServerGadgetAuthorityTest {
    private static List<AbilityConfigData> configs;
    private static List<GadgetData> gadgets;
    private final Map<Integer, GadgetData> oldGadgets = new HashMap<>();
    private AbilityTestFixture fixture;
    private EntityMonster boss;

    @BeforeAll
    static void resources() throws Exception {
        var root = Path.of("resources");
        configs =
                JsonUtils.loadToList(
                        root.resolve(
                                "BinOutput/Ability/Temp/MonsterAbilities/ConfigAbility_Monster_Effigy_Electric.json"),
                        AbilityConfigData.class);
        var ids =
                Set.of(
                        42004003, 42004006, 42004010, 42004012, 42004016, 42004017, 42004018,
                        41022001);
        gadgets =
                JsonUtils.loadToList(
                                root.resolve("ExcelBinOutput/GadgetExcelConfigData.json"),
                                GadgetData.class)
                        .stream()
                        .filter(g -> ids.contains(g.getId()))
                        .toList();
        assertEquals(ids.size(), gadgets.size());
    }

    @BeforeEach
    void scene() throws Exception {
        fixture = new AbilityTestFixture();
        fixture.player.setUid(10001);
        fixture.player.setWorld(fixture.scene.getWorld());
        for (var data : gadgets)
            oldGadgets.put(data.getId(), GameData.getGadgetDataMap().put(data.getId(), data));
        boss = monster(20040101);
    }

    @AfterEach
    void close() {
        oldGadgets.forEach(
                (id, value) -> {
                    if (value == null) GameData.getGadgetDataMap().remove(id);
                    else GameData.getGadgetDataMap().put(id, value);
                });
        fixture.close();
    }

    private EntityMonster monster(int id) {
        var data =
                JsonUtils.decode(
                        "{id:" + id + ",type:'MONSTER_BOSS',equips:[],affix:[],hpDrops:[]}",
                        MonsterData.class);
        var entity =
                new EntityMonster(fixture.scene, data, new Position(), new Position(), 1) {
                    @Override
                    public void recalcStats() {}

                    @Override
                    public void initAbilities() {}

                    @Override
                    public SceneEntityInfo toProto() {
                        return SceneEntityInfo.getDefaultInstance();
                    }
                };
        entity.setFightProperty(FightProperty.FIGHT_PROP_MAX_HP, 100);
        entity.setFightProperty(FightProperty.FIGHT_PROP_CUR_HP, 100);
        fixture.scene.getEntities().put(entity.getId(), entity);
        return entity;
    }

    private EntityAvatar venti() {
        var avatar =
                new Avatar() {
                    @Override
                    public Player getPlayer() {
                        return fixture.player;
                    }

                    @Override
                    public int getAvatarId() {
                        return 10000022;
                    }

                    @Override
                    public void save() {}
                };
        avatar.setFightProperty(FightProperty.FIGHT_PROP_MAX_HP, 100);
        avatar.setFightProperty(FightProperty.FIGHT_PROP_CUR_HP, 100);
        var entity = new EntityAvatar(fixture.scene, avatar);
        fixture.scene.getEntities().put(entity.getId(), entity);
        fixture.player.getTeamManager().getActiveTeam().add(entity);
        return entity;
    }

    private Ability ability(GameEntity owner) {
        return new Ability(
                JsonUtils.decode("{abilityName:'Gadget_Authority'}", AbilityData.class),
                owner,
                fixture.player);
    }

    private AbilityModifierAction action(int id, boolean server) {
        return JsonUtils.decode(
                "{$type:'CreateGadget',gadgetID:" + id + ",byServer:" + server + "}",
                AbilityModifierAction.class);
    }

    private void create(Ability ability, AbilityModifierAction action, int position) {
        var payload =
                AbilityActionCreateGadget.newBuilder()
                        .setPos(Vector.newBuilder().setX(position).setY(2).setZ(3))
                        .build();
        assertTrue(
                new ActionCreateGadget()
                        .execute(ability, action, payload.toByteString(), ability.getOwner()));
    }

    private List<EntityGadget> owned(GameEntity owner, int kind) {
        return fixture.scene.getEntities().values().stream()
                .filter(
                        e ->
                                e instanceof EntityGadget g
                                        && g.getOwner() == owner
                                        && g.getGadgetId() == kind)
                .map(e -> (EntityGadget) e)
                .toList();
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3})
    void actual_rebirth_configs_create_the_requested_number_of_prisms(int parts) {
        var data =
                configs.stream()
                        .map(row -> row.Default)
                        .filter(
                                a ->
                                        a.abilityName.equals(
                                                "Monster_Effigy_Electric_CreateRebornPart" + parts))
                        .findFirst()
                        .orElseThrow();
        var ability = new Ability(data, boss, fixture.player);
        assertEquals(parts, data.onAbilityStart.length);
        for (int i = 0; i < parts; i++) {
            assertTrue(data.onAbilityStart[i].byServer);
            create(ability, data.onAbilityStart[i], i + 10);
        }
        var prisms = owned(boss, 42004010);
        assertEquals(parts, prisms.size());
        assertEquals(parts, prisms.stream().map(g -> g.getPosition().getX()).distinct().count());
    }

    @ParameterizedTest
    @ValueSource(ints = {42004010, 42004016, 42004017, 42004018})
    void explicit_server_creation_keeps_multiple_same_kind_objects(int kind) {
        var ability = ability(boss);
        for (int i = 0; i < 3; i++) create(ability, action(kind, true), i + 10);
        assertEquals(3, owned(boss, kind).size());
    }

    @ParameterizedTest
    @ValueSource(ints = {20040101, 20040201, 20040401, 20040501, 20040601, 20040701})
    void current_client_position_markers_do_not_get_a_server_copy(int id) {
        var owner = monster(id);
        create(ability(owner), action(42004012, false), 10);
        assertTrue(owned(owner, 42004012).isEmpty());
    }

    @Test
    void electric_emitters_without_server_authority_stay_client_created() {
        create(ability(boss), action(42004003, false), 10);
        assertTrue(owned(boss, 42004003).isEmpty());
    }

    @Test
    void an_explicit_server_flag_takes_priority_over_an_avatar_client_object_policy()
            throws Exception {
        var owner = venti();
        create(ability(owner), action(41022001, true), 10);
        var server = owned(owner, 41022001);
        assertEquals(1, server.size());
        var session =
                new GameSession(null) {
                    @Override
                    public Player getPlayer() {
                        return fixture.player;
                    }
                };
        new HandlerEvtCreateGadgetNotify()
                .handle(
                        session,
                        new byte[0],
                        EvtCreateGadgetNotify.newBuilder()
                                .setEntityId(501)
                                .setConfigId(41022001)
                                .setOwnerEntityId(owner.getId())
                                .build()
                                .toByteArray());
        assertSame(server.get(0), fixture.scene.getEntityById(server.get(0).getId()));
        assertNotNull(fixture.scene.getEntityById(501));
    }

    @Test
    void explicit_server_creation_also_works_from_a_client_gadget_chain() {
        var owner =
                new EntityClientGadget(
                        fixture.scene,
                        fixture.player,
                        EvtCreateGadgetNotify.newBuilder()
                                .setEntityId(501)
                                .setOwnerEntityId(boss.getId())
                                .build());
        fixture.scene.getEntities().put(owner.getId(), owner);
        create(ability(owner), action(42004010, true), 10);
        assertEquals(1, owned(owner, 42004010).size());
    }

    @Test
    void the_client_chain_default_still_avoids_server_duplication() {
        var owner =
                new EntityClientGadget(
                        fixture.scene,
                        fixture.player,
                        EvtCreateGadgetNotify.newBuilder()
                                .setEntityId(501)
                                .setOwnerEntityId(boss.getId())
                                .build());
        fixture.scene.getEntities().put(owner.getId(), owner);
        create(ability(owner), action(42004010, false), 10);
        assertTrue(owned(owner, 42004010).isEmpty());
    }

    @Test
    void legacy_replacement_does_not_remove_explicitly_server_owned_objects() {
        var owner = monster(20010101);
        var ability = ability(owner);
        create(ability, action(42004010, true), 10);
        var server = owned(owner, 42004010).get(0);
        create(ability, action(42004010, false), 20);
        create(ability, action(42004010, false), 30);
        assertSame(server, fixture.scene.getEntityById(server.getId()));
        assertEquals(2, owned(owner, 42004010).size());
    }

    @Test
    void unrelated_non_server_gadgets_keep_the_existing_replacement_policy() {
        var owner = monster(20010101);
        var ability = ability(owner);
        create(ability, action(42004010, false), 10);
        create(ability, action(42004010, false), 20);
        var current = owned(owner, 42004010);
        assertEquals(1, current.size());
        assertEquals(20, current.get(0).getPosition().getX());
    }
}
