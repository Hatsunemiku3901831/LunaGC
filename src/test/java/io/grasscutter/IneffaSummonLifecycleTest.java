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
import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.AbilityActionCreateGadgetOuterClass.AbilityActionCreateGadget;
import emu.grasscutter.net.proto.EvtCreateGadgetNotifyOuterClass.EvtCreateGadgetNotify;
import emu.grasscutter.net.proto.EvtDestroyGadgetNotifyOuterClass.EvtDestroyGadgetNotify;
import emu.grasscutter.net.proto.SceneEntityDisappearNotifyOuterClass.SceneEntityDisappearNotify;
import emu.grasscutter.net.proto.VectorOuterClass.Vector;
import emu.grasscutter.server.game.GameSession;
import emu.grasscutter.server.packet.recv.HandlerEvtCreateGadgetNotify;
import emu.grasscutter.server.packet.recv.HandlerEvtDestroyGadgetNotify;
import emu.grasscutter.utils.JsonUtils;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

class IneffaSummonLifecycleTest {
    private static final int RELAY = 41116005;
    private static final Set<Integer> CLIENT_IDS =
            Set.of(RELAY, 41116006, 41116007, 41116008, 41116009, 41116010, 41116011);
    private static List<GadgetData> gadgets;
    private static AbilityData burst;
    private static AbilityModifierAction burstRelay;
    private final Map<Integer, GadgetData> oldGadgets = new HashMap<>();
    private AbilityTestFixture fixture;
    private EntityAvatar caster;
    private AbilityTestFixture.TestPlayer other;

    @BeforeAll
    static void resources() throws Exception {
        var root = Path.of("resources");
        gadgets =
                JsonUtils.loadToList(
                                root.resolve("ExcelBinOutput/GadgetExcelConfigData.json"),
                                GadgetData.class)
                        .stream()
                        .filter(g -> CLIENT_IDS.contains(g.getId()))
                        .toList();
        assertEquals(
                CLIENT_IDS,
                gadgets.stream()
                        .map(GadgetData::getId)
                        .collect(java.util.stream.Collectors.toSet()));
        for (var row :
                JsonParser.parseString(
                                Files.readString(
                                        root.resolve(
                                                "BinOutput/Ability/Temp/AvatarAbilities/ConfigAbility_Avatar_Ineffa.json")))
                        .getAsJsonArray()) {
            var data = row.getAsJsonObject().getAsJsonObject("Default");
            if (!"Avatar_Ineffa_Burst_CreateRelay".equals(data.get("abilityName").getAsString()))
                continue;
            burst = JsonUtils.decode(data.toString(), AbilityData.class);
            burstRelay =
                    JsonUtils.decode(
                            data.getAsJsonArray("onAbilityStart")
                                    .get(3)
                                    .getAsJsonObject()
                                    .getAsJsonArray("successActions")
                                    .get(1)
                                    .toString(),
                            AbilityModifierAction.class);
        }
        assertNotNull(burstRelay);
        assertEquals(RELAY, burstRelay.gadgetID);
    }

    @BeforeEach
    void scene() throws Exception {
        fixture = new AbilityTestFixture();
        fixture.player.setUid(10001);
        fixture.player.setWorld(fixture.scene.getWorld());
        for (var data : gadgets)
            oldGadgets.put(data.getId(), GameData.getGadgetDataMap().put(data.getId(), data));
        caster = avatar(fixture.player, 10000116);
        other = new AbilityTestFixture.TestPlayer();
        other.setUid(10002);
        other.setScene(fixture.scene);
        other.setWorld(fixture.scene.getWorld());
    }

    @AfterEach
    void restore() {
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

    private GameSession session(Player player) {
        return new GameSession(null) {
            @Override
            public Player getPlayer() {
                return player;
            }

            @Override
            public void send(BasePacket packet) {
                ((AbilityTestFixture.TestPlayer) player).packets.add(packet);
            }
        };
    }

    private EvtCreateGadgetNotify notify(int id, int type, int owner) {
        return EvtCreateGadgetNotify.newBuilder()
                .setEntityId(id)
                .setConfigId(type)
                .setOwnerEntityId(owner)
                .build();
    }

    private EntityClientGadget create(Player player, int id, int type) throws Exception {
        new HandlerEvtCreateGadgetNotify()
                .handle(
                        session(player),
                        new byte[0],
                        notify(id, type, player.getTeamManager().getCurrentAvatarEntity().getId())
                                .toByteArray());
        return (EntityClientGadget) fixture.scene.getEntityById(id);
    }

    private void destroy(Player player, int id) throws Exception {
        new HandlerEvtDestroyGadgetNotify()
                .handle(
                        session(player),
                        new byte[0],
                        EvtDestroyGadgetNotify.newBuilder().setEntityId(id).build().toByteArray());
    }

    private EntityGadget shell(int type, GameEntity owner) {
        var result = new EntityGadget(fixture.scene, type, new Position());
        result.setOwner(owner);
        fixture.scene.getEntities().put(result.getId(), result);
        return result;
    }

    private void present(GameEntity value) {
        assertSame(value, fixture.scene.getEntityById(value.getId()));
    }

    private void absent(GameEntity value) {
        assertNull(fixture.scene.getEntityById(value.getId()));
        if (value instanceof EntityClientGadget client)
            assertFalse(client.getOwner().getTeamManager().getGadgets().contains(client));
    }

    @ParameterizedTest
    @ValueSource(ints = {41116005, 41116006, 41116007, 41116008, 41116009, 41116010, 41116011})
    void owned_ineffa_skill_objects_do_not_get_a_second_server_copy(int id) {
        var ability =
                new Ability(
                        JsonUtils.decode("{abilityName:'Ineffa_Chain'}", AbilityData.class),
                        caster,
                        fixture.player);
        var action =
                JsonUtils.decode(
                        "{$type:'CreateGadget',gadgetID:" + id + "}", AbilityModifierAction.class);
        assertTrue(new ActionCreateGadget().execute(ability, action, ByteString.EMPTY, caster));
        assertTrue(
                fixture.scene.getEntities().values().stream()
                        .noneMatch(e -> e instanceof EntityGadget g && g.getGadgetId() == id));
    }

    @Test
    void the_current_burst_resource_does_not_create_an_extra_server_relay() {
        var ability = new Ability(burst, caster, fixture.player);
        var position =
                AbilityActionCreateGadget.newBuilder()
                        .setPos(Vector.newBuilder().setX(10).setY(20))
                        .build();
        assertTrue(
                new ActionCreateGadget()
                        .execute(ability, burstRelay, position.toByteString(), caster));
        assertTrue(
                fixture.scene.getEntities().values().stream()
                        .noneMatch(e -> e instanceof EntityGadget g && g.getGadgetId() == RELAY));
    }

    @Test
    void another_character_and_monsters_keep_their_server_creation_path() {
        for (var owner : List.of(avatar(fixture.player, 10000073), fixture.entity)) {
            var ability =
                    new Ability(
                            JsonUtils.decode("{abilityName:'Other_Chain'}", AbilityData.class),
                            owner,
                            fixture.player);
            var action =
                    JsonUtils.decode(
                            "{$type:'CreateGadget',gadgetID:" + RELAY + "}",
                            AbilityModifierAction.class);
            assertTrue(new ActionCreateGadget().execute(ability, action, ByteString.EMPTY, owner));
            assertTrue(
                    fixture.scene.getEntities().values().stream()
                            .anyMatch(
                                    e ->
                                            e instanceof EntityGadget g
                                                    && g.getGadgetId() == RELAY
                                                    && g.getOwner() == owner));
        }
    }

    @Test
    void resummoning_retires_the_old_relay_and_assists_but_keeps_burst_bullets() throws Exception {
        var old = create(fixture.player, 501, RELAY);
        var hit = create(fixture.player, 502, 41116006);
        var move = create(fixture.player, 503, 41116007);
        var move2 = create(fixture.player, 504, 41116008);
        var bullet = create(fixture.player, 505, 41116009);
        var current = create(fixture.player, 601, RELAY);
        for (var value : List.of(old, hit, move, move2)) absent(value);
        present(current);
        present(bullet);
        assertTrue(fixture.player.getTeamManager().getGadgets().contains(current));
    }

    @Test
    void destroying_the_relay_removes_assists_but_not_independent_burst_objects() throws Exception {
        var relay = create(fixture.player, 501, RELAY);
        var hit = create(fixture.player, 502, 41116006);
        var assist = create(fixture.player, 503, 41116007);
        var bullet = create(fixture.player, 505, 41116011);
        destroy(fixture.player, relay.getId());
        for (var value : List.of(relay, hit, assist)) absent(value);
        present(bullet);
    }

    @Test
    void destroying_an_assist_keeps_the_relay_and_other_assists() throws Exception {
        var relay = create(fixture.player, 501, RELAY);
        var hit = create(fixture.player, 502, 41116006);
        var assist = create(fixture.player, 503, 41116008);
        destroy(fixture.player, hit.getId());
        absent(hit);
        present(relay);
        present(assist);
    }

    @Test
    void a_late_destroy_for_the_retired_root_cannot_remove_the_new_family() throws Exception {
        create(fixture.player, 501, RELAY);
        var current = create(fixture.player, 601, RELAY);
        var assist = create(fixture.player, 602, 41116007);
        destroy(fixture.player, 501);
        present(current);
        present(assist);
    }

    @Test
    void replacement_cleans_owned_server_shells_through_nested_owners() throws Exception {
        var direct = shell(RELAY, caster);
        var nested = shell(41116006, direct);
        var unrelated = shell(70700001, caster);
        var foreign = shell(RELAY, avatar(other, 10000116));
        var cycle = shell(41116007, null);
        cycle.setOwner(cycle);
        var relay = create(fixture.player, 501, RELAY);
        absent(direct);
        absent(nested);
        present(unrelated);
        present(foreign);
        present(cycle);
        present(relay);
    }

    @Test
    void another_players_client_family_survives_replacement() throws Exception {
        avatar(other, 10000116);
        var foreign = create(other, 701, RELAY);
        var foreignAssist = create(other, 702, 41116006);
        create(fixture.player, 501, RELAY);
        create(fixture.player, 502, 41116006);
        create(fixture.player, 601, RELAY);
        present(foreign);
        present(foreignAssist);
    }

    @Test
    void a_player_cannot_destroy_another_players_client_gadget_even_with_the_same_uid()
            throws Exception {
        other.setUid(fixture.player.getUid());
        var owned = create(fixture.player, 501, RELAY);
        destroy(other, owned.getId());
        present(owned);
        assertTrue(fixture.player.getTeamManager().getGadgets().contains(owned));
    }

    @Test
    void trusted_scene_callbacks_keep_their_explicit_replacement_contract() throws Exception {
        create(fixture.player, 501, 0);
        var duplicate =
                new EntityClientGadget(fixture.scene, other, notify(501, 0, caster.getId()));
        fixture.scene.onPlayerCreateGadget(duplicate);
        present(duplicate);
        assertTrue(other.getTeamManager().getGadgets().contains(duplicate));
    }

    @Test
    void wire_notifications_cannot_replace_another_players_existing_id() throws Exception {
        var current = create(fixture.player, 501, RELAY);
        avatar(other, 10000116);
        assertSame(current, create(other, 501, RELAY));
        present(current);
        assertTrue(other.getTeamManager().getGadgets().isEmpty());
    }

    @Test
    void concurrent_relay_creations_leave_one_registered_owned_root() throws Exception {
        var pool = Executors.newFixedThreadPool(8);
        var start = new CountDownLatch(1);
        try {
            var jobs = new ArrayList<Future<?>>();
            for (int i = 0; i < 8; i++) {
                int id = 501 + i;
                jobs.add(
                        pool.submit(
                                () -> {
                                    start.await();
                                    create(fixture.player, id, RELAY);
                                    return null;
                                }));
            }
            start.countDown();
            for (var job : jobs) job.get(10, TimeUnit.SECONDS);
            var remaining =
                    fixture.scene.getEntities().values().stream()
                            .filter(
                                    e ->
                                            e instanceof EntityClientGadget client
                                                    && client.getGadgetId() == RELAY)
                            .toList();
            assertEquals(1, remaining.size());
            assertEquals(1, fixture.player.getTeamManager().getGadgets().size());
            assertTrue(fixture.player.getTeamManager().getGadgets().contains(remaining.get(0)));
        } finally {
            pool.shutdownNow();
            assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS));
        }
    }

    @Test
    void client_creation_is_sent_to_the_peer_without_echoing_a_duplicate_to_its_owner()
            throws Exception {
        joinPeers();
        create(fixture.player, 501, RELAY);
        assertEquals(
                1,
                other.packets.stream()
                        .filter(p -> p.getOpcode() == PacketOpcodes.SceneEntityAppearNotify)
                        .count());
        assertTrue(
                fixture.player.packets.stream()
                        .noneMatch(p -> p.getOpcode() == PacketOpcodes.SceneEntityAppearNotify));
    }

    private void joinPeers() {
        fixture.player.setSession(session(fixture.player));
        other.setSession(session(other));
        fixture.player.getTeamManager().setEntity(new EntityTeam(fixture.player));
        other.getTeamManager().setEntity(new EntityTeam(other));
        fixture.scene.getPlayers().add(fixture.player);
        fixture.scene.getPlayers().add(other);
    }

    @Test
    void duplicate_notifications_do_not_restart_the_family_or_emit_disappearances()
            throws Exception {
        var current = create(fixture.player, 501, RELAY);
        var assist = create(fixture.player, 502, 41116008);
        fixture.scene.packets.clear();
        assertSame(current, create(fixture.player, 501, RELAY));
        present(assist);
        assertTrue(
                fixture.scene.packets.stream()
                        .noneMatch(p -> p.getOpcode() == PacketOpcodes.SceneEntityDisappearNotify));
    }

    @Test
    void retirement_sends_one_disappearance_for_every_removed_client_piece() throws Exception {
        create(fixture.player, 501, RELAY);
        create(fixture.player, 502, 41116006);
        fixture.scene.packets.clear();
        create(fixture.player, 601, RELAY);
        var ids = new ArrayList<Integer>();
        for (var packet : fixture.scene.packets)
            if (packet.getOpcode() == PacketOpcodes.SceneEntityDisappearNotify)
                ids.addAll(
                        SceneEntityDisappearNotify.parseFrom(packet.getData()).getEntityListList());
        assertEquals(Set.of(501, 502), new HashSet<>(ids));
        assertEquals(2, ids.size());
    }

    private void noDisappearance(int id) throws Exception {
        for (var packet : fixture.scene.packets)
            if (packet.getOpcode() == PacketOpcodes.SceneEntityDisappearNotify)
                assertFalse(
                        SceneEntityDisappearNotify.parseFrom(packet.getData())
                                .getEntityListList()
                                .contains(id));
    }

    @Test
    void assist_removal_cannot_delete_or_hide_a_foreign_replacement_of_the_retiring_piece()
            throws Exception {
        var old = create(fixture.player, 501, RELAY);
        var replacement = new AtomicReference<EntityClientGadget>();
        var assist =
                new EntityClientGadget(
                        fixture.scene, fixture.player, notify(502, 41116006, caster.getId())) {
                    @Override
                    public void onRemoved() {
                        var current =
                                new EntityClientGadget(
                                        fixture.scene,
                                        other,
                                        IneffaSummonLifecycleTest.this.notify(502, 0, 0));
                        replacement.set(current);
                        fixture.scene.onPlayerCreateGadget(current);
                    }
                };
        fixture.scene.onPlayerCreateGadget(assist);
        var current = create(fixture.player, 601, RELAY);
        assertNotNull(replacement.get());
        present(replacement.get());
        assertTrue(
                replacement
                        .get()
                        .isAbilityLifecycleCurrent(replacement.get().getAbilityLifecycleVersion()));
        assertTrue(other.getTeamManager().getGadgets().contains(replacement.get()));
        assertFalse(fixture.player.getTeamManager().getGadgets().contains(old));
        present(current);
        assertFalse(fixture.player.getTeamManager().getGadgets().contains(assist));
        noDisappearance(502);
    }

    @Test
    void a_removal_callback_reusing_the_same_id_keeps_its_new_object_visible() throws Exception {
        var replacement = new AtomicReference<EntityClientGadget>();
        var old =
                new EntityClientGadget(
                        fixture.scene, fixture.player, notify(501, 0, caster.getId())) {
                    @Override
                    public void onRemoved() {
                        var current =
                                new EntityClientGadget(
                                        fixture.scene,
                                        other,
                                        IneffaSummonLifecycleTest.this.notify(501, 0, 0));
                        replacement.set(current);
                        fixture.scene.onPlayerCreateGadget(current);
                    }
                };
        fixture.scene.onPlayerCreateGadget(old);
        destroy(fixture.player, old.getId());
        assertNotNull(replacement.get());
        present(replacement.get());
        assertTrue(other.getTeamManager().getGadgets().contains(replacement.get()));
        assertFalse(fixture.player.getTeamManager().getGadgets().contains(old));
        noDisappearance(501);
    }

    @Test
    void cleanup_callbacks_cannot_echo_an_outdated_appearance_after_replacing_the_new_root()
            throws Exception {
        create(fixture.player, 501, RELAY);
        var replacement = new AtomicReference<EntityClientGadget>();
        var assist =
                new EntityClientGadget(
                        fixture.scene, fixture.player, notify(502, 41116006, caster.getId())) {
                    @Override
                    public void onRemoved() {
                        var current =
                                new EntityClientGadget(
                                        fixture.scene,
                                        other,
                                        IneffaSummonLifecycleTest.this.notify(601, 0, 0));
                        replacement.set(current);
                        fixture.scene.onPlayerCreateGadget(current);
                    }
                };
        fixture.scene.onPlayerCreateGadget(assist);
        joinPeers();
        create(fixture.player, 601, RELAY);
        assertNotNull(replacement.get());
        present(replacement.get());
        assertTrue(
                other.packets.stream()
                        .noneMatch(p -> p.getOpcode() == PacketOpcodes.SceneEntityAppearNotify));
        assertTrue(
                fixture.player.getTeamManager().getGadgets().stream()
                        .noneMatch(g -> g.getId() == 601));
    }
}
