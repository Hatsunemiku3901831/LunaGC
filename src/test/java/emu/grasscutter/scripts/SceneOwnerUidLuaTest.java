package emu.grasscutter.scripts;

import static org.junit.jupiter.api.Assertions.*;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.game.avatar.Avatar;
import emu.grasscutter.game.entity.EntityAvatar;
import emu.grasscutter.game.entity.GameEntity;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.world.Scene;
import emu.grasscutter.game.world.World;
import emu.grasscutter.scripts.data.SceneGroup;
import emu.grasscutter.scripts.data.ScriptArgs;
import java.lang.reflect.Field;
import java.util.LinkedHashMap;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.luaj.vm2.Globals;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.lib.jse.JsePlatform;
import sun.misc.Unsafe;

/** Real LuaJ/userdata calls over plain objects, without world/server/player constructors. */
final class SceneOwnerUidLuaTest {
    private ScriptLib library;
    private Globals globals;
    private World world;
    private Player guest;
    private LuaValue previousLibrary;

    @BeforeEach
    void setup() throws Exception {
        library = new ScriptLib();
        var expectedData = System.getProperty("sceneOwnerUid.expectedData");
        if (expectedData != null)
            assertEquals(expectedData, Grasscutter.getConfig().folderStructure.data);
        var bridge = new ScriptLibUserdata(library);
        var field = ScriptLoader.class.getDeclaredField("scriptLibLua");
        field.setAccessible(true);
        previousLibrary = (LuaValue) field.get(null);
        field.set(null, bridge);

        guest = player(27);
        var other = player(28);
        var owner = player(10001);
        world = allocate(World.class);
        set(world, World.class, "host", owner);
        var scene = allocate(Scene.class);
        set(scene, Scene.class, "world", world);
        // The triggering player is deliberately first; the host is a different player.
        set(scene, Scene.class, "players", List.of(guest, owner, other));
        var entities = new LinkedHashMap<Integer, GameEntity>();
        var guestAvatar = avatar(guest, 7001);
        entities.put(guestAvatar.getId(), guestAvatar);
        entities.put(7777, avatar(owner, 7777));
        entities.put(7999, avatar(other, 7999));
        set(scene, Scene.class, "entities", entities);
        var manager = allocate(SceneScriptManager.class);
        set(manager, SceneScriptManager.class, "scene", scene);
        library.setSceneScriptManager(manager);
        library.setCurrentEntity(guestAvatar);
        var group = SceneGroup.of(123);
        library.setCurrentGroup(group);

        globals = JsePlatform.standardGlobals();
        globals.set("ScriptLib", bridge);
        globals.set(
                "context",
                library.createEventContext(
                        new ScriptArgs(group.id, 1).setUid(guest.getUid()), group));
    }

    @AfterEach
    void cleanup() throws Exception {
        library.removeCurrentEntity();
        library.removeCurrentGroup();
        library.removeSceneScriptManager();
        var field = ScriptLoader.class.getDeclaredField("scriptLibLua");
        field.setAccessible(true);
        field.set(null, previousLibrary);
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 10001, Integer.MAX_VALUE})
    void eventContextReturnsActualWorldHostUidRatherThanTriggerOrFirstPlayer(int uid) {
        world.getHost().setUid(uid);

        assertEquals(27, globals.get("context").get("uid").checkint());
        assertSame(guest, ((EntityAvatar) library.getCurrentEntity().orElseThrow()).getPlayer());
        assertEquals(uid, lua("return ScriptLib.GetSceneOwnerUid(context)").checkint());
        assertEquals(uid, lua("return context.GetSceneOwnerUid(context)").checkint());
    }

    @Test
    void returnedOwnerUidFeedsExistingAvatarLookupThroughTheSameContext() {
        assertEquals(
                7777,
                lua("return ScriptLib.GetAvatarEntityIdByUid(context,"
                        + " ScriptLib.GetSceneOwnerUid(context))")
                        .checkint());
        assertEquals(
                7001,
                lua("return ScriptLib.GetAvatarEntityIdByUid(context, context.uid)").checkint());
    }

    @ParameterizedTest
    @ValueSource(strings = {"nil", "{}", "42", "'wrong-context'"})
    void wrongReceiversUseTheExistingLuaBridgeRejection(String receiver) {
        assertThrows(
                LuaError.class, () -> lua("return ScriptLib.GetSceneOwnerUid(" + receiver + ")"));
        assertThrows(
                LuaError.class,
                () -> lua("return ScriptLib.GetAvatarEntityIdByUid(" + receiver + ", 10001)"));
    }

    @Test
    void unknownMemberStillFailsInsteadOfReturningAPlaceholder() {
        assertThrows(LuaError.class, () -> lua("return ScriptLib.Cn72UnknownOwnerQuery(context)"));
    }

    private LuaValue lua(String text) {
        return globals.load(text).call();
    }

    private static Player player(int uid) throws Exception {
        var player = allocate(Player.class);
        player.setUid(uid);
        return player;
    }

    private static EntityAvatar avatar(Player player, int entityId) throws Exception {
        var avatar = allocate(Avatar.class);
        set(avatar, Avatar.class, "owner", player);
        var entity = allocate(EntityAvatar.class);
        set(entity, EntityAvatar.class, "avatar", avatar);
        entity.setId(entityId);
        return entity;
    }

    private static <T> T allocate(Class<T> type) throws Exception {
        Field field = Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        return type.cast(((Unsafe) field.get(null)).allocateInstance(type));
    }

    private static void set(Object target, Class<?> owner, String name, Object value)
            throws Exception {
        var field = owner.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
}
