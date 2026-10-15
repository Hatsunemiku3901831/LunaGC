package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import emu.grasscutter.data.GameData;
import emu.grasscutter.data.ResourceLoader.AbilityConfigData;
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
import emu.grasscutter.utils.JsonUtils;

import org.junit.jupiter.api.*;

import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

class GadgetOwnerLifetimeTest {
    private static AbilityData prism;
    private static GadgetData data;
    private AbilityTestFixture fixture;
    private LivingOwner owner;
    private GadgetData previous;

    private class LivingOwner extends AbilityTestFixture.TestEntity {
        LivingOwner(int id) {
            super(fixture.scene);
            setId(id);
            setFightProperty(FightProperty.FIGHT_PROP_MAX_HP, 100);
            setFightProperty(FightProperty.FIGHT_PROP_CUR_HP, 100);
            fixture.scene.getEntities().put(id, this);
        }

        void revive() {
            setDead(false);
        }
    }

    @BeforeAll
    static void resources() throws Exception {
        var root = Path.of("resources");
        prism =
                JsonUtils.loadToList(
                                root.resolve(
                                        "BinOutput/Ability/Temp/MonsterAbilities/ConfigAbility_Monster_Effigy_Electric.json"),
                                AbilityConfigData.class)
                        .stream()
                        .map(row -> row.Default)
                        .filter(
                                a ->
                                        a.abilityName.equals(
                                                "Monster_Effigy_Electric_CreateRebornPart3"))
                        .findFirst()
                        .orElseThrow();
        assertEquals(3, prism.onAbilityStart.length);
        for (var action : prism.onAbilityStart) assertTrue(action.lifeByOwnerIsAlive);
        data =
                JsonUtils.loadToList(
                                root.resolve("ExcelBinOutput/GadgetExcelConfigData.json"),
                                GadgetData.class)
                        .stream()
                        .filter(g -> g.getId() == 42004010)
                        .findFirst()
                        .orElseThrow();
    }

    @BeforeEach
    void setup() throws Exception {
        fixture = new AbilityTestFixture();
        fixture.player.setUid(10001);
        fixture.player.setWorld(fixture.scene.getWorld());
        previous = GameData.getGadgetDataMap().put(data.getId(), data);
        owner = new LivingOwner(51);
    }

    @AfterEach
    void close() {
        if (previous == null) GameData.getGadgetDataMap().remove(data.getId());
        else GameData.getGadgetDataMap().put(data.getId(), previous);
        fixture.close();
    }

    private EntityGadget create(GameEntity source, GameEntity attached, boolean linked) {
        var ability =
                new Ability(
                        JsonUtils.decode("{abilityName:'Owner_Lifetime'}", AbilityData.class),
                        source,
                        fixture.player);
        var action =
                JsonUtils.decode(
                        "{$type:'CreateGadget',gadgetID:42004010,byServer:true,lifeByOwnerIsAlive:"
                                + linked
                                + ",ownerIsTarget:"
                                + (source != attached)
                                + "}",
                        AbilityModifierAction.class);
        return execute(ability, action, attached, 10);
    }

    private EntityGadget execute(
            Ability ability, AbilityModifierAction action, GameEntity target, int x) {
        var existing = new HashSet<>(fixture.scene.getEntities().keySet());
        var payload =
                AbilityActionCreateGadget.newBuilder()
                        .setPos(Vector.newBuilder().setX(x).setY(2))
                        .build();
        assertTrue(
                new ActionCreateGadget()
                        .executeResolved(ability, action, payload.toByteString(), target));
        return fixture.scene.getEntities().values().stream()
                .filter(e -> e instanceof EntityGadget && !existing.contains(e.getId()))
                .map(e -> (EntityGadget) e)
                .findFirst()
                .orElseThrow();
    }

    private EntityGadget linked() {
        return create(owner, owner, true);
    }

    private void present(GameEntity value) {
        assertSame(value, fixture.scene.getEntityById(value.getId()));
    }

    private void gone(EntityGadget value) throws Exception {
        assertNull(fixture.scene.getEntities().get(value.getId()));
        assertTrue(value.isAlive(), "owner expiry is removal, not another gadget kill");
        var notices = new ArrayList<SceneEntityDisappearNotify>();
        for (var packet : fixture.scene.packets)
            if (packet.getOpcode() == PacketOpcodes.SceneEntityDisappearNotify) {
                var body = SceneEntityDisappearNotify.parseFrom(packet.getData());
                if (body.getEntityListList().contains(value.getId())) notices.add(body);
            }
        assertEquals(1, notices.size());
        assertEquals(VisionType.VisionType_VISION_REMOVE, notices.get(0).getDisappearType());
    }

    @Test
    void real_rebirth_prisms_expire_after_their_owner_dies() throws Exception {
        var ability = new Ability(prism, owner, fixture.player);
        var gadgets = new ArrayList<EntityGadget>();
        for (int i = 0; i < 3; i++)
            gadgets.add(execute(ability, prism.onAbilityStart[i], owner, i + 10));
        owner.onDeath(0);
        for (var gadget : gadgets) {
            gadget.onTick(1);
            gone(gadget);
        }
    }

    @Test
    void living_owners_keep_their_gadgets() {
        var gadget = linked();
        gadget.onTick(1);
        gadget.onTick(2);
        present(gadget);
    }

    @Test
    void surviving_gadget_callbacks_do_not_hold_the_scene_monitor() {
        var gadget =
                new EntityGadget(fixture.scene, 42004010, new Position()) {
                    @Override
                    public void onAbilityTick() {
                        assertFalse(
                                Thread.holdsLock(fixture.scene),
                                "controllers may enter the scene from another thread");
                    }
                };
        gadget.setOwner(owner);
        gadget.setLifeByOwnerIsAlive(true);
        fixture.scene.addEntity(gadget);
        gadget.onTick(1);
        present(gadget);
    }

    @Test
    void the_scene_actor_can_own_a_gadget_without_a_physical_entity_map_entry() throws Exception {
        var actor = fixture.scene.getSceneEntity();
        var gadget = create(actor, actor, true);
        gadget.onTick(1);
        present(gadget);
        actor.onDeath(0);
        gadget.onTick(2);
        gone(gadget);
    }

    @Test
    void a_weapon_registry_owner_stays_valid_until_removed() throws Exception {
        fixture.scene.getEntities().remove(owner.getId(), owner);
        fixture.scene.getWeaponEntities().put(owner.getId(), owner);
        var gadget = linked();
        gadget.onTick(1);
        present(gadget);
        fixture.scene.getWeaponEntities().remove(owner.getId(), owner);
        gadget.onTick(2);
        gone(gadget);
    }

    @Test
    void the_default_flag_does_not_make_legacy_objects_follow_owner_death() {
        var gadget = create(owner, owner, false);
        owner.onDeath(0);
        gadget.onTick(1);
        present(gadget);
    }

    @Test
    void zero_hp_without_a_confirmed_dead_state_does_not_expire_the_gadget() {
        var gadget = linked();
        owner.setFightProperty(FightProperty.FIGHT_PROP_CUR_HP, 0);
        gadget.onTick(1);
        present(gadget);
    }

    @Test
    void a_death_and_revival_between_ticks_cannot_preserve_an_old_gadget() throws Exception {
        var gadget = linked();
        owner.onDeath(0);
        owner.revive();
        gadget.onTick(1);
        gone(gadget);
    }

    @Test
    void the_hp_death_transition_is_also_kept_after_revival() throws Exception {
        var gadget = linked();
        owner.setFightProperty(FightProperty.FIGHT_PROP_CUR_HP, 0);
        owner.checkIfDead();
        owner.revive();
        owner.setFightProperty(FightProperty.FIGHT_PROP_CUR_HP, 100);
        gadget.onTick(1);
        gone(gadget);
    }

    @Test
    void removing_an_alive_owner_ends_the_binding() throws Exception {
        var gadget = linked();
        fixture.scene.removeEntity(owner);
        assertTrue(owner.isAlive());
        gadget.onTick(1);
        gone(gadget);
    }

    @Test
    void reactivating_the_same_owner_object_does_not_resume_the_old_binding() throws Exception {
        var gadget = linked();
        owner.invalidateAbilityLifecycle();
        owner.activateAbilityLifecycle();
        gadget.onTick(1);
        gone(gadget);
    }

    @Test
    void a_different_actor_reusing_the_owner_id_does_not_take_over_the_binding() throws Exception {
        var gadget = linked();
        var current = new LivingOwner(owner.getId());
        gadget.onTick(1);
        gone(gadget);
        present(current);
    }

    @Test
    void changing_an_owners_id_ends_its_original_binding() throws Exception {
        var gadget = linked();
        owner.setId(52);
        gadget.onTick(1);
        gone(gadget);
    }

    @Test
    void explicitly_reparenting_a_gadget_binds_the_new_owner() throws Exception {
        var gadget = linked();
        var next = new LivingOwner(52);
        gadget.setOwner(next);
        owner.onDeath(0);
        gadget.onTick(1);
        present(gadget);
        next.onDeath(0);
        gadget.onTick(2);
        gone(gadget);
    }

    @Test
    void a_missing_owner_cannot_keep_a_linked_gadget_alive() throws Exception {
        var gadget = linked();
        gadget.setOwner(null);
        gadget.onTick(1);
        gone(gadget);
    }

    @Test
    void a_binding_created_while_dead_does_not_start_after_revival() throws Exception {
        owner.onDeath(0);
        var gadget = linked();
        owner.revive();
        gadget.onTick(1);
        gone(gadget);
    }

    @Test
    void owner_is_target_uses_the_attached_actor_instead_of_the_caster() throws Exception {
        var target = new LivingOwner(52);
        var gadget = create(owner, target, true);
        owner.onDeath(0);
        gadget.onTick(1);
        present(gadget);
        target.onDeath(0);
        gadget.onTick(2);
        gone(gadget);
    }

    @Test
    void a_background_party_member_stays_valid_until_removed_from_the_party() throws Exception {
        var avatar =
                new Avatar() {
                    @Override
                    public Player getPlayer() {
                        return fixture.player;
                    }

                    @Override
                    public int getAvatarId() {
                        return 10000073;
                    }

                    @Override
                    public void save() {}
                };
        avatar.setFightProperty(FightProperty.FIGHT_PROP_MAX_HP, 100);
        avatar.setFightProperty(FightProperty.FIGHT_PROP_CUR_HP, 100);
        var member = new EntityAvatar(fixture.scene, avatar);
        fixture.scene.getEntities().put(member.getId(), member);
        fixture.player.getTeamManager().getActiveTeam().add(member);
        var gadget = create(member, member, true);
        fixture.scene.getEntities().remove(member.getId(), member);
        gadget.onTick(1);
        present(gadget);
        fixture.player.getTeamManager().getActiveTeam().remove(member);
        gadget.onTick(2);
        gone(gadget);
    }

    @Test
    void team_owners_use_the_current_team_instance() throws Exception {
        var team = new EntityTeam(fixture.player);
        fixture.player.getTeamManager().setEntity(team);
        var gadget = create(team, team, true);
        gadget.onTick(1);
        present(gadget);
        fixture.player.getTeamManager().setEntity(new EntityTeam(fixture.player));
        gadget.onTick(2);
        gone(gadget);
    }

    @Test
    void world_owners_remain_valid_without_a_physical_entity_map_entry() throws Exception {
        var world = fixture.scene.getWorld().getEntity();
        var gadget = create(world, world, true);
        gadget.onTick(1);
        present(gadget);
        world.onDeath(0);
        gadget.onTick(2);
        gone(gadget);
    }

    @Test
    void a_client_parent_disappearing_expires_its_server_child() throws Exception {
        var parent =
                new EntityClientGadget(
                        fixture.scene,
                        fixture.player,
                        EvtCreateGadgetNotify.newBuilder()
                                .setEntityId(501)
                                .setOwnerEntityId(owner.getId())
                                .build());
        fixture.scene.onPlayerCreateGadget(parent);
        var child = create(parent, parent, true);
        fixture.scene.onPlayerDestroyGadget(fixture.player, parent.getId());
        child.onTick(1);
        gone(child);
    }

    @Test
    void a_stale_tick_cannot_remove_a_replacement_of_the_gadget_itself() {
        var old = linked();
        fixture.scene.removeEntity(old);
        var current = new EntityGadget(fixture.scene, 42004010, new Position());
        current.setId(old.getId());
        fixture.scene.addEntity(current);
        owner.onDeath(0);
        old.onTick(1);
        present(current);
        assertTrue(current.isAbilityLifecycleCurrent(current.getAbilityLifecycleVersion()));
    }

    @Test
    void an_expiry_callback_can_replace_the_removed_id_without_losing_the_new_object()
            throws Exception {
        var gadget = linked();
        var current = new AtomicReference<EntityClientGadget>();
        var callback =
                new EntityGadget(fixture.scene, 42004010, new Position()) {
                    @Override
                    public void onRemoved() {
                        var fresh =
                                new EntityClientGadget(
                                        fixture.scene,
                                        fixture.player,
                                        EvtCreateGadgetNotify.newBuilder()
                                                .setEntityId(gadget.getId())
                                                .build());
                        current.set(fresh);
                        fixture.scene.onPlayerCreateGadget(fresh);
                    }
                };
        fixture.scene.getEntities().put(callback.getId(), callback);
        gadget.getChildren().add(callback);
        owner.onDeath(0);
        gadget.onTick(1);
        assertNotNull(current.get());
        present(current.get());
        for (var packet : fixture.scene.packets)
            if (packet.getOpcode() == PacketOpcodes.SceneEntityDisappearNotify)
                assertFalse(
                        SceneEntityDisappearNotify.parseFrom(packet.getData())
                                .getEntityListList()
                                .contains(current.get().getId()));
    }
}
