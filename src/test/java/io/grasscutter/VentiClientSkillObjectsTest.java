package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonParser;
import com.google.protobuf.ByteString;

import emu.grasscutter.data.GameData;
import emu.grasscutter.data.binout.AbilityData;
import emu.grasscutter.data.binout.AbilityModifier.AbilityModifierAction;
import emu.grasscutter.data.excels.GadgetData;
import emu.grasscutter.game.ability.Ability;
import emu.grasscutter.game.ability.actions.ActionCreateGadget;
import emu.grasscutter.game.avatar.Avatar;
import emu.grasscutter.game.entity.*;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.props.FightProperty;
import emu.grasscutter.game.world.Position;
import emu.grasscutter.net.packet.PacketOpcodes;
import emu.grasscutter.net.proto.AbilityActionCreateGadgetOuterClass.AbilityActionCreateGadget;
import emu.grasscutter.net.proto.EvtCreateGadgetNotifyOuterClass.EvtCreateGadgetNotify;
import emu.grasscutter.net.proto.SceneEntityDisappearNotifyOuterClass.SceneEntityDisappearNotify;
import emu.grasscutter.net.proto.VectorOuterClass.Vector;
import emu.grasscutter.net.proto.VisionTypeOuterClass.VisionType;
import emu.grasscutter.server.game.GameSession;
import emu.grasscutter.server.packet.recv.HandlerEvtCreateGadgetNotify;
import emu.grasscutter.utils.JsonUtils;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

class VentiClientSkillObjectsTest {
    private static final Set<Integer> KINDS = Set.of(41022001, 41022002, 41022003, 41022014);
    private static List<GadgetData> gadgets;
    private static AbilityData wind;
    private static AbilityData hurricane;
    private static AbilityModifierAction windAction;
    private static AbilityModifierAction hurricaneAction;
    private AbilityTestFixture fixture;
    private AbilityTestFixture.TestPlayer other;
    private EntityAvatar venti;
    private final Map<Integer, GadgetData> oldGadgets = new HashMap<>();

    @BeforeAll
    static void resources() throws Exception {
        var root = Path.of("resources");
        gadgets =
                JsonUtils.loadToList(
                                root.resolve("ExcelBinOutput/GadgetExcelConfigData.json"),
                                GadgetData.class)
                        .stream()
                        .filter(g -> KINDS.contains(g.getId()))
                        .toList();
        assertEquals(
                KINDS,
                gadgets.stream()
                        .map(GadgetData::getId)
                        .collect(java.util.stream.Collectors.toSet()));
        for (var row :
                JsonParser.parseString(
                                Files.readString(
                                        root.resolve(
                                                "BinOutput/Ability/Temp/AvatarAbilities/ConfigAbility_Avatar_Venti.json")))
                        .getAsJsonArray()) {
            var data = row.getAsJsonObject().getAsJsonObject("Default");
            if ("Avatar_Venti_WindBlade".equals(data.get("abilityName").getAsString())) {
                wind = JsonUtils.decode(data.toString(), AbilityData.class);
                windAction =
                        JsonUtils.decode(
                                data.getAsJsonObject("modifiers")
                                        .getAsJsonObject("TriggerAttack")
                                        .getAsJsonArray("onAdded")
                                        .get(0)
                                        .toString(),
                                AbilityModifierAction.class);
            }
            if ("Avatar_Venti_Hurricane".equals(data.get("abilityName").getAsString())) {
                hurricane = JsonUtils.decode(data.toString(), AbilityData.class);
                hurricaneAction =
                        JsonUtils.decode(
                                data.getAsJsonObject("modifiers")
                                        .getAsJsonObject(
                                                "Avatar_Venti_Hurricane_Hexenzirkel_ChooseEnemy")
                                        .getAsJsonArray("onAdded")
                                        .get(2)
                                        .getAsJsonObject()
                                        .getAsJsonArray("successActions")
                                        .get(0)
                                        .toString(),
                                AbilityModifierAction.class);
            }
        }
        assertEquals(41022002, windAction.gadgetID);
        assertEquals(41022001, hurricaneAction.gadgetID);
    }

    @BeforeEach
    void setup() throws Exception {
        fixture = new AbilityTestFixture();
        fixture.player.setUid(10001);
        fixture.player.setWorld(fixture.scene.getWorld());
        other = new AbilityTestFixture.TestPlayer();
        other.setUid(10002);
        other.setScene(fixture.scene);
        other.setWorld(fixture.scene.getWorld());
        for (var data : gadgets)
            oldGadgets.put(data.getId(), GameData.getGadgetDataMap().put(data.getId(), data));
        venti = avatar(fixture.player, 10000022);
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

    private EntityAvatar avatar(Player player, int id) {
        var avatar =
                new Avatar() {
                    @Override
                    public Player getPlayer() {
                        return player;
                    }

                    @Override
                    public int getAvatarId() {
                        return id;
                    }

                    @Override
                    public void save() {}
                };
        avatar.setFightProperty(FightProperty.FIGHT_PROP_MAX_HP, 100);
        avatar.setFightProperty(FightProperty.FIGHT_PROP_CUR_HP, 100);
        var entity = new EntityAvatar(fixture.scene, avatar);
        fixture.scene.getEntities().put(entity.getId(), entity);
        player.getTeamManager().getActiveTeam().add(entity);
        return entity;
    }

    private EntityClientGadget create(int id, int kind) throws Exception {
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
                                .setEntityId(id)
                                .setConfigId(kind)
                                .setOwnerEntityId(venti.getId())
                                .build()
                                .toByteArray());
        return (EntityClientGadget) fixture.scene.getEntityById(id);
    }

    private class Shell extends EntityGadget {
        Consumer<Shell> removed = ignored -> {};

        Shell(int kind, GameEntity owner) {
            super(fixture.scene, kind, new Position());
            setOwner(owner);
            fixture.scene.getEntities().put(getId(), this);
        }

        @Override
        public void onRemoved() {
            super.onRemoved();
            removed.accept(this);
        }
    }

    private EntityClientGadget replacement(int id) {
        var value =
                new EntityClientGadget(
                        fixture.scene,
                        other,
                        EvtCreateGadgetNotify.newBuilder().setEntityId(id).build());
        fixture.scene.onPlayerCreateGadget(value);
        return value;
    }

    private List<Integer> disappeared() throws Exception {
        var ids = new ArrayList<Integer>();
        for (var packet : fixture.scene.packets)
            if (packet.getOpcode() == PacketOpcodes.SceneEntityDisappearNotify)
                ids.addAll(
                        SceneEntityDisappearNotify.parseFrom(packet.getData()).getEntityListList());
        return ids;
    }

    private void present(GameEntity entity) {
        assertSame(entity, fixture.scene.getEntityById(entity.getId()));
    }

    @ParameterizedTest
    @ValueSource(ints = {41022001, 41022002, 41022003, 41022014})
    void venti_owned_skill_objects_do_not_get_a_duplicate_server_copy(int kind) {
        var ability =
                new Ability(
                        JsonUtils.decode("{abilityName:'Venti_Skill'}", AbilityData.class),
                        venti,
                        fixture.player);
        var action =
                JsonUtils.decode(
                        "{$type:'CreateGadget',gadgetID:" + kind + "}",
                        AbilityModifierAction.class);
        assertTrue(new ActionCreateGadget().execute(ability, action, ByteString.EMPTY, venti));
        assertTrue(
                fixture.scene.getEntities().values().stream()
                        .noneMatch(e -> e instanceof EntityGadget g && g.getGadgetId() == kind));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void actual_windblade_and_hurricane_actions_keep_the_client_created_object(boolean burst) {
        var data = burst ? hurricane : wind;
        var action = burst ? hurricaneAction : windAction;
        var ability = new Ability(data, venti, fixture.player);
        var payload =
                AbilityActionCreateGadget.newBuilder()
                        .setPos(Vector.newBuilder().setX(10).setZ(20))
                        .build();
        assertTrue(
                new ActionCreateGadget().execute(ability, action, payload.toByteString(), venti));
        assertTrue(
                fixture.scene.getEntities().values().stream()
                        .noneMatch(
                                e ->
                                        e instanceof EntityGadget g
                                                && g.getGadgetId() == action.gadgetID));
    }

    @Test
    void other_characters_and_monsters_keep_their_server_objects() {
        for (var owner : List.of(avatar(fixture.player, 10000116), fixture.entity)) {
            var ability =
                    new Ability(
                            JsonUtils.decode("{abilityName:'Other_Skill'}", AbilityData.class),
                            owner,
                            fixture.player);
            var action =
                    JsonUtils.decode(
                            "{$type:'CreateGadget',gadgetID:41022002}",
                            AbilityModifierAction.class);
            assertTrue(new ActionCreateGadget().execute(ability, action, ByteString.EMPTY, owner));
            assertTrue(
                    fixture.scene.getEntities().values().stream()
                            .anyMatch(
                                    e ->
                                            e instanceof EntityGadget g
                                                    && g.getGadgetId() == 41022002
                                                    && g.getOwner() == owner));
        }
    }

    @Test
    void arrival_purges_owned_nested_shells_without_touching_another_players_shells()
            throws Exception {
        var direct = new Shell(41022001, venti);
        var nested = new Shell(41022002, direct);
        var land = new Shell(41022003, venti);
        var field = new Shell(41022014, venti);
        var unrelated = new Shell(70700001, venti);
        other.setUid(fixture.player.getUid());
        var foreign = new Shell(41022001, avatar(other, 10000022));
        var cycle = new Shell(41022002, null);
        cycle.setOwner(cycle);
        var client = create(501, 41022001);
        for (var owned : List.of(direct, nested, land, field))
            assertNull(fixture.scene.getEntityById(owned.getId()));
        present(client);
        present(unrelated);
        present(foreign);
        present(cycle);
    }

    @Test
    void independent_client_wind_effects_are_not_mutually_exclusive() throws Exception {
        var clients = new ArrayList<EntityClientGadget>();
        int id = 501;
        for (int kind : List.of(41022001, 41022002, 41022003, 41022014, 41022001))
            clients.add(create(id++, kind));
        clients.forEach(this::present);
        assertEquals(5, fixture.player.getTeamManager().getGadgets().size());
    }

    @Test
    void server_shell_removal_does_not_hide_its_callback_replacement() throws Exception {
        var old = new Shell(41022001, venti);
        var current = new AtomicReference<EntityClientGadget>();
        old.removed = self -> current.set(replacement(self.getId()));
        create(501, 41022001);
        assertNotNull(current.get());
        present(current.get());
        assertFalse(disappeared().contains(current.get().getId()));
    }

    @Test
    void a_shell_callback_cannot_remove_another_snapshot_entries_replacement() throws Exception {
        var shells = List.of(new Shell(41022001, venti), new Shell(41022002, venti));
        var current = new AtomicReference<EntityClientGadget>();
        Consumer<Shell> replaceNext =
                removed -> {
                    if (current.get() != null) return;
                    var next = shells.stream().filter(s -> s != removed).findFirst().orElseThrow();
                    current.set(replacement(next.getId()));
                };
        shells.forEach(s -> s.removed = replaceNext);
        create(501, 41022001);
        assertNotNull(current.get());
        present(current.get());
        assertFalse(disappeared().contains(current.get().getId()));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void batch_removal_checks_replacements_after_all_callbacks(boolean replaceEarlier)
            throws Exception {
        var first = new Shell(41022001, venti);
        var second = new Shell(41022002, venti);
        var current = new AtomicReference<EntityClientGadget>();
        if (replaceEarlier) second.removed = ignored -> current.set(replacement(first.getId()));
        else first.removed = self -> current.set(replacement(self.getId()));
        fixture.scene.removeEntities(List.of(first, second), VisionType.VisionType_VISION_REMOVE);
        assertNotNull(current.get());
        present(current.get());
        assertFalse(disappeared().contains(first.getId()));
        assertEquals(List.of(second.getId()), disappeared());
    }
}
