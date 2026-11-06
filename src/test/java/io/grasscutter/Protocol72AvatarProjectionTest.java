package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import com.google.protobuf.CodedOutputStream;
import com.google.protobuf.Descriptors.FieldDescriptor;
import com.google.protobuf.Message;
import emu.grasscutter.Grasscutter;
import emu.grasscutter.game.avatar.Avatar;
import emu.grasscutter.game.inventory.GameItem;
import emu.grasscutter.game.inventory.ItemType;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.player.TeamInfo;
import emu.grasscutter.game.player.TeamManager;
import emu.grasscutter.net.proto.Client72AvatarWireOuterClass.ILEPMBEJKIA;
import emu.grasscutter.net.proto.PlayerStoreNotifyOuterClass.PlayerStoreNotify;
import emu.grasscutter.server.packet.send.PacketPlayerStoreNotify;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.*;
import org.junit.jupiter.api.Test;

class Protocol72AvatarProjectionTest {
  private static void field(Object target, String name, Object value) throws Exception {
    Field f =
        (target instanceof EquipFixture ? GameItem.class : target.getClass())
            .getDeclaredField(name);
    f.setAccessible(true);
    f.set(target, value);
  }

  private static FieldDescriptor f(Message.Builder b, int number) {
    return b.getDescriptorForType().findFieldByNumber(number);
  }

  private static Message.Builder child(Message.Builder b, int number) {
    return b.newBuilderForField(f(b, number));
  }

  private static void set(Message.Builder b, int number, Object value) {
    b.setField(f(b, number), value);
  }

  private static void add(Message.Builder b, int number, Object value) {
    b.addRepeatedField(f(b, number), value);
  }

  private static void entry(Message.Builder b, int number, int key, Message value) {
    var e = child(b, number);
    set(e, 1, key);
    set(e, 2, value);
    add(b, number, e.build());
  }

  private static final class EquipFixture extends GameItem {
    @Override
    public ItemType getItemType() {
      return ItemType.ITEM_WEAPON;
    }
  }

  private static byte[] deterministicWire(Message message) throws IOException {
    var bytes = new ByteArrayOutputStream();
    var output = CodedOutputStream.newInstance(bytes);
    output.useDeterministicSerialization();
    message.writeTo(output);
    output.flush();
    return bytes.toByteArray();
  }

  private static Message project(
      List<Avatar> avatars, TeamManager manager, List<Integer> propertyOrder) {
    var root = ILEPMBEJKIA.newBuilder();
    Map<Integer, Avatar> byId = new HashMap<>();
    for (var avatar : avatars) {
      byId.put(avatar.getAvatarId(), avatar);
      var a = child(root, 6);
      set(a, 1, avatar.getAvatarId());
      set(a, 2, avatar.getGuid());
      var properties =
          Map.of(1001, avatar.getExp(), 1002, avatar.getPromoteLevel(), 4001, avatar.getLevel());
      for (int key : propertyOrder) {
        var value = child(child(a, 3), 2);
        set(value, 1, key);
        set(value, 2, (long) properties.get(key));
        entry(a, 3, key, value.build());
      }
      for (var item : avatar.getEquips().values()) add(a, 5, item.getGuid());
      add(root, 6, a.build());
    }
    for (var team : manager.getTeams().entrySet()) {
      var t = child(child(root, 5), 2);
      set(t, 2, team.getValue().getName());
      for (int id : team.getValue().getAvatars()) add(t, 12, byId.get(id).getGuid());
      entry(root, 5, team.getKey(), t.build());
    }
    set(root, 12, manager.getCurrentTeamId());
    return root.build();
  }

  @Test
  void getterProjectionAndZeroPresence() throws Exception {
    Grasscutter.getConfig();
    var a = new Avatar();
    field(a, "avatarId", 1001);
    field(a, "guid", 9007199254740993L);
    a.setLevel(42);
    a.setExp(275);
    a.setPromoteLevel(3);
    var b = new Avatar();
    field(b, "avatarId", 1002);
    field(b, "guid", 9007199254740995L);
    b.setLevel(0);
    b.setExp(0);
    b.setPromoteLevel(0);
    var item = new EquipFixture();
    field(item, "guid", 4294967297L);
    a.getEquips().put(1, item);
    var team = new TeamInfo(new ArrayList<>(List.of(1002, 1001)));
    team.setName("队伍 A");
    var manager = new TeamManager();
    var teams = new LinkedHashMap<Integer, TeamInfo>();
    teams.put(7, team);
    field(manager, "teams", teams);
    field(manager, "currentTeamIndex", 7);
    var before =
        List.of(
            a.getAvatarId(),
            a.getGuid(),
            a.getLevel(),
            a.getExp(),
            a.getPromoteLevel(),
            item.getGuid(),
            List.copyOf(team.getAvatars()),
            team.getName(),
            manager.getCurrentTeamId(),
            Map.copyOf(a.getEquips()));
    Message result = project(List.of(a, b), manager, List.of(1001, 1002, 4001));
    Message reversed = project(List.of(a, b), manager, List.of(4001, 1002, 1001));
    assertArrayEquals(deterministicWire(result), deterministicWire(reversed));
    var player = new Player();
    player.getInventory().getItems().put(item.getGuid(), item);
    byte[] storeWire = new PacketPlayerStoreNotify(player).getData();
    Message storeItem = PlayerStoreNotify.parseFrom(storeWire).getItemList(0);
    assertEquals(
        item.getGuid(), storeItem.getField(storeItem.getDescriptorForType().findFieldByNumber(2)));
    assertSame(item, player.getInventory().getItems().get(item.getGuid()));
    var r = result.getDescriptorForType();
    assertEquals(
        Set.of(5, 6, 12),
        result.getAllFields().keySet().stream()
            .map(FieldDescriptor::getNumber)
            .collect(java.util.stream.Collectors.toSet()));
    var rows = (List<?>) result.getField(r.findFieldByNumber(6));
    assertEquals(2, rows.size());
    Message first = (Message) rows.get(0);
    Message second = (Message) rows.get(1);
    assertEquals(1001, first.getField(first.getDescriptorForType().findFieldByNumber(1)));
    assertEquals(
        9007199254740993L, first.getField(first.getDescriptorForType().findFieldByNumber(2)));
    assertEquals(
        List.of(4294967297L), first.getField(first.getDescriptorForType().findFieldByNumber(5)));
    for (Message row : List.of(first, second)) {
      var props = (List<?>) row.getField(row.getDescriptorForType().findFieldByNumber(3));
      assertEquals(3, props.size());
      for (Object o : props) {
        Message e = (Message) o;
        int key = (int) e.getField(e.getDescriptorForType().findFieldByNumber(1));
        Message v = (Message) e.getField(e.getDescriptorForType().findFieldByNumber(2));
        assertEquals(key, v.getField(v.getDescriptorForType().findFieldByNumber(1)));
        var expected = row == first ? Map.of(1001, 275L, 1002, 3L, 4001, 42L).get(key) : 0L;
        assertTrue(v.hasField(v.getDescriptorForType().findFieldByNumber(2)));
        assertEquals(expected, v.getField(v.getDescriptorForType().findFieldByNumber(2)));
      }
    }
    Message te = (Message) ((List<?>) result.getField(r.findFieldByNumber(5))).get(0);
    assertEquals(7, te.getField(te.getDescriptorForType().findFieldByNumber(1)));
    Message tv = (Message) te.getField(te.getDescriptorForType().findFieldByNumber(2));
    assertEquals("队伍 A", tv.getField(tv.getDescriptorForType().findFieldByNumber(2)));
    assertEquals(
        List.of(9007199254740995L, 9007199254740993L),
        tv.getField(tv.getDescriptorForType().findFieldByNumber(12)));
    assertEquals(7, result.getField(r.findFieldByNumber(12)));
    assertEquals(
        before,
        List.of(
            a.getAvatarId(),
            a.getGuid(),
            a.getLevel(),
            a.getExp(),
            a.getPromoteLevel(),
            item.getGuid(),
            List.copyOf(team.getAvatars()),
            team.getName(),
            manager.getCurrentTeamId(),
            Map.copyOf(a.getEquips())));
    assertEquals(0, b.getLevel());
    assertEquals(0, b.getExp());
    assertEquals(0, b.getPromoteLevel());
    System.out.println("CN72_AVATAR_STORE " + HexFormat.of().formatHex(storeWire));
    System.out.println(
        "CN72_AVATAR_PROJECTION " + HexFormat.of().formatHex(deterministicWire(result)));
  }
}
