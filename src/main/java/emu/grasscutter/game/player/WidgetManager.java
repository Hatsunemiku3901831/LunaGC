package emu.grasscutter.game.player;

import emu.grasscutter.GameConstants;
import emu.grasscutter.data.GameData;
import emu.grasscutter.game.ability.Ability;
import emu.grasscutter.game.entity.EntityTeam;
import emu.grasscutter.game.inventory.*;
import emu.grasscutter.game.world.Scene;
import emu.grasscutter.net.proto.AbilityControlBlockOuterClass.AbilityControlBlock;
import emu.grasscutter.net.proto.AbilityEmbryoOuterClass.AbilityEmbryo;
import emu.grasscutter.net.proto.LunchBoxDataOuterClass.LunchBoxData;
import emu.grasscutter.net.proto.RetcodeOuterClass.Retcode;
import emu.grasscutter.net.proto.WidgetSlotDataOuterClass.WidgetSlotData;
import emu.grasscutter.net.proto.WidgetSlotOpOuterClass.WidgetSlotOp;
import emu.grasscutter.server.packet.send.*;
import emu.grasscutter.utils.Utils;

import java.util.*;
import java.util.function.Supplier;

/** Owned quick-use and follower slots, with stable IDs for each team's widget abilities. */
public final class WidgetManager {
    public record Result(int retcode, List<Integer> tags) {}

    private record Installed(
            int id, String name, String override, Ability instance, boolean owned) {}

    private final Player player;
    private final Map<String, Installed> active = new LinkedHashMap<>();
    private final Map<String, Integer> reserved = new HashMap<>();
    private EntityTeam boundTeam;
    private Scene boundScene;
    private boolean savePending;

    private record Operation<T>(T value, boolean save) {}

    public WidgetManager(Player player) {
        this.player = player;
    }

    private <T> T inScene(Supplier<T> action) {
        var scene = player.getScene();
        Operation<T> result;
        if (scene == null) {
            synchronized (this) {
                result = capture(action);
            }
        } else
            synchronized (scene) {
                synchronized (this) {
                    result = capture(action);
                }
            }
        if (result.save()) {
            if (Thread.holdsLock(this)
                    || scene != null && Thread.holdsLock(scene)
                    || Thread.holdsLock(player.getInventory()))
                emu.grasscutter.game.ability.AbilityManager.eventExecutor.submit(player::save);
            else player.save();
        }
        return result.value();
    }

    private <T> Operation<T> capture(Supplier<T> action) {
        var value = action.get();
        boolean save = savePending;
        savePending = false;
        return new Operation<>(value, save);
    }

    private boolean owned(int id) {
        var data = GameData.getItemDataMap().get(id);
        var item = player.getInventory().getItemById(id);
        return data != null
                && data.getItemType() == ItemType.ITEM_MATERIAL
                && data.getMaterialType() == MaterialType.MATERIAL_WIDGET
                && item != null
                && item.getCount() > 0
                && item.getOwnerId() == player.getUid()
                && player.getInventory().getItemByGuid(item.getGuid()) == item;
    }

    private int slot(int tag) {
        return tag == 0 ? player.getWidgetId() : player.getWidgetPetId();
    }

    private void slot(int tag, int id) {
        if (tag == 0) player.setWidgetId(id);
        else player.setWidgetPetId(id);
    }

    private void normalize() {
        var catalog = WidgetCatalog.current();
        if (!catalog.available()) return;
        var quick = catalog.get(player.getWidgetId());
        if (quick != null
                && owned(quick.materialId())
                && !quick.tags().contains(0)
                && quick.tags().contains(1)
                && player.getWidgetPetId() == 0) player.setWidgetPetId(quick.materialId());
        for (int tag = 0; tag <= 1; tag++) {
            int id = slot(tag);
            var entry = catalog.get(id);
            if (id != 0 && (entry == null || !entry.tags().contains(tag) || !owned(id)))
                slot(tag, 0);
        }
    }

    public Result setSlot(int materialId, int op, List<Integer> requested) {
        return inScene(
                () -> {
                    if (op != 0 && op != 1)
                        return new Result(
                                Retcode.RET_WIDGET_QUICK_USE_REQ_PARAM_ERROR.getNumber(),
                                List.of());
                    var config = WidgetCatalog.current().get(materialId);
                    var tags = new TreeSet<Integer>(requested);
                    if (tags.isEmpty()) {
                        if (config != null) tags.addAll(config.tags());
                        else if (op == 1) {
                            for (int tag = 0; tag <= 1; tag++)
                                if (slot(tag) == materialId && materialId != 0) tags.add(tag);
                            if (materialId == 0) tags.add(0);
                        }
                    }
                    if (tags.isEmpty()
                            || tags.stream().anyMatch(t -> t != 0 && t != 1)
                            || op == 0 && (config == null || !config.tags().containsAll(tags)))
                        return new Result(
                                Retcode.RET_INVALID_WIDGET_MATERIAL_ID.getNumber(), List.of());
                    if (op == 0 && !owned(materialId))
                        return new Result(
                                Retcode.RET_ITEM_NOT_EXIST.getNumber(), List.copyOf(tags));
                    if (op == 1
                            && materialId != 0
                            && tags.stream().anyMatch(t -> slot(t) != materialId))
                        return new Result(
                                Retcode.RET_INVALID_WIDGET_MATERIAL_ID.getNumber(),
                                List.copyOf(tags));
                    int oldQuick = slot(0), oldPet = slot(1);
                    for (int tag : tags) slot(tag, op == 0 ? materialId : 0);
                    refresh(oldQuick, oldPet, true);
                    return new Result(0, List.copyOf(tags));
                });
    }

    /** A request patches the native Hp=1 and Revive=2 slots; material zero clears that slot. */
    public int setLunchBox(Map<Integer, Integer> choices) {
        return inScene(
                () -> {
                    if (!owned(WidgetCatalog.LUNCH_BOX))
                        return Retcode.RET_ITEM_NOT_EXIST.getNumber();
                    if (choices == null || choices.size() > 2)
                        return Retcode.RET_LUNCH_BOX_DATA_ERROR.getNumber();
                    var catalog = WidgetCatalog.current();
                    for (var choice : choices.entrySet()) {
                        var slot = choice.getKey();
                        var id = choice.getValue();
                        if (slot == null || slot != 1 && slot != 2 || id == null || id < 0)
                            return Retcode.RET_LUNCH_BOX_DATA_ERROR.getNumber();
                        if (id == 0) continue;
                        if (!catalog.acceptsFood(slot, GameData.getItemDataMap().get(id)))
                            return Retcode.RET_LUNCH_BOX_DATA_ERROR.getNumber();
                        var item = player.getInventory().getItemById(id);
                        if (item == null
                                || item.getCount() <= 0
                                || item.getOwnerId() != player.getUid()
                                || player.getInventory().getItemByGuid(item.getGuid()) != item)
                            return Retcode.RET_ITEM_NOT_EXIST.getNumber();
                    }
                    var old = player.getLunchBoxFood();
                    var next = old == null ? new HashMap<Integer, Integer>() : new HashMap<>(old);
                    choices.forEach(
                            (slot, id) -> {
                                if (id == 0) next.remove(slot);
                                else next.put(slot, id);
                            });
                    if (!next.equals(old)) {
                        player.setLunchBoxFood(next);
                        savePending = true;
                    }
                    return 0;
                });
    }

    /** Depleted food remains selected so a refill restores it without reconfiguring the widget. */
    public LunchBoxData lunchBox() {
        return inScene(
                () -> {
                    var result = LunchBoxData.newBuilder();
                    var choices = player.getLunchBoxFood();
                    if (choices != null)
                        choices.forEach(
                                (slot, id) -> {
                                    if (slot != null
                                            && id != null
                                            && WidgetCatalog.current()
                                                    .acceptsFood(
                                                            slot,
                                                            GameData.getItemDataMap().get(id)))
                                        result.putSlotMaterialMap(slot, id);
                                });
                    return result.build();
                });
    }

    private void refresh(int oldQuick, int oldPet, boolean notify) {
        normalize();
        reconcile();
        boolean changed = oldQuick != slot(0) || oldPet != slot(1);
        if (changed) savePending = true;
        if (!notify || !changed) return;
        for (int tag = 0; tag <= 1; tag++) {
            int old = tag == 0 ? oldQuick : oldPet;
            if (old == slot(tag)) continue;
            if (old != 0)
                player.sendPacket(
                        new PacketWidgetSlotChangeNotify(
                                old, tag, WidgetSlotOp.WidgetSlotOp_DETACH));
            if (slot(tag) != 0)
                player.sendPacket(
                        new PacketWidgetSlotChangeNotify(
                                slot(tag), tag, WidgetSlotOp.WidgetSlotOp_ATTACH));
        }
        if (oldPet != slot(1)) {
            notifyGroup(oldPet, false);
            notifyGroup(slot(1), true);
            if (boundTeam != null)
                player.sendPacket(
                        new PacketAbilityChangeNotify(
                                boundTeam.getId(),
                                player.getTeamManager().getAbilityControlBlock()));
        }
    }

    public void refresh() {
        inScene(
                () -> {
                    refresh(slot(0), slot(1), true);
                    return null;
                });
    }

    public void prepareForScene() {
        inScene(
                () -> {
                    refresh(slot(0), slot(1), false);
                    return null;
                });
    }

    public void syncEquipped() {
        inScene(
                () -> {
                    refresh(slot(0), slot(1), false);
                    notifyGroup(slot(1), true);
                    if (slot(1) != 0 && boundTeam != null)
                        player.sendPacket(
                                new PacketAbilityChangeNotify(
                                        boundTeam.getId(),
                                        player.getTeamManager().getAbilityControlBlock()));
                    return null;
                });
    }

    public List<WidgetSlotData> slots() {
        return inScene(
                () -> {
                    refresh(slot(0), slot(1), true);
                    var result = new ArrayList<WidgetSlotData>();
                    for (int tag = 0; tag <= 1; tag++)
                        if (slot(tag) != 0)
                            result.add(
                                    WidgetSlotData.newBuilder()
                                            .setTagValue(tag)
                                            .setMaterialId(slot(tag))
                                            .setIsActive(true)
                                            .build());
                    return List.copyOf(result);
                });
    }

    private void notifyGroup(int id, boolean attach) {
        var config = WidgetCatalog.current().get(id);
        if (config != null && config.hasGroup())
            player.sendPacket(new PacketWidgetUseAttachAbilityGroupChangeNotify(id, attach));
    }

    private void drop(Installed entry) {
        if (!entry.owned() || boundTeam == null) return;
        var instances = boundTeam.getInstancedAbilities();
        int index = entry.id() - 1;
        if (entry.instance() != null) {
            entry.instance().removeInstalledModifiers();
            if (index < instances.size() && instances.get(index) == entry.instance())
                instances.set(index, null);
        }
    }

    private void reconcile() {
        var team = player.getTeamManager().getEntity();
        var scene = player.getScene();
        if (team != boundTeam || scene != boundScene) {
            active.values().forEach(this::drop);
            if (boundTeam != null) {
                var instances = boundTeam.getInstancedAbilities();
                while (!instances.isEmpty() && instances.get(instances.size() - 1) == null)
                    instances.remove(instances.size() - 1);
            }
            active.clear();
            reserved.clear();
            boundTeam = team;
            boundScene = scene;
        }
        if (team == null || team.getPlayer() != player || scene == null) return;
        var config = WidgetCatalog.current().get(slot(1));
        var desired = new LinkedHashMap<String, WidgetCatalog.GroupAbility>();
        if (config != null) config.abilities().forEach(a -> desired.put(a.name(), a));
        for (var name : new ArrayList<>(active.keySet()))
            if (!desired.containsKey(name)) drop(active.remove(name));
        var base = player.getTeamManager().getBaseAbilityControlBlock();
        var baseHashes = new HashSet<Integer>();
        base.getAbilityEmbryoListList().forEach(a -> baseHashes.add(a.getAbilityNameHash()));
        var instances = team.getInstancedAbilities();
        for (var ability : desired.values()) {
            var installed = active.get(ability.name());
            if (installed != null
                    && installed.instance() != null
                    && installed.id() <= instances.size()
                    && instances.get(installed.id() - 1) == installed.instance()) continue;
            if (baseHashes.contains(Utils.abilityHash(ability.name()))) continue;
            var existing =
                    instances.stream()
                            .filter(Objects::nonNull)
                            .filter(a -> ability.name().equals(a.getData().abilityName))
                            .findFirst()
                            .orElse(null);
            if (existing != null
                    && instances.indexOf(existing) >= base.getAbilityEmbryoListCount()) {
                active.put(
                        ability.name(),
                        new Installed(
                                instances.indexOf(existing) + 1,
                                ability.name(),
                                ability.override(),
                                existing,
                                false));
                continue;
            }
            int id = reserved.getOrDefault(ability.name(), 0);
            if (id == 0 || id <= instances.size() && instances.get(id - 1) != null) {
                id = Math.max(instances.size(), base.getAbilityEmbryoListCount()) + 1;
                reserved.put(ability.name(), id);
            }
            while (instances.size() < id) instances.add(null);
            var data = GameData.getAbilityData(ability.name());
            Ability instance = null;
            if (data != null) {
                player.getAbilityManager().addAbilityToEntity(team, data);
                instance = instances.get(instances.size() - 1);
                instances.set(id - 1, instance);
                instances.remove(instances.size() - 1);
            }
            active.put(
                    ability.name(),
                    new Installed(id, ability.name(), ability.override(), instance, true));
        }
    }

    public synchronized boolean allowsTeamAbility(int id, String name) {
        if (!reserved.containsValue(id)) return true;
        return active.values().stream().anyMatch(a -> a.id() == id && a.name().equals(name));
    }

    public synchronized AbilityControlBlock extend(AbilityControlBlock base) {
        if (active.isEmpty()) return base;
        var result = base.toBuilder();
        var hashes = new HashSet<Integer>();
        base.getAbilityEmbryoListList().forEach(a -> hashes.add(a.getAbilityNameHash()));
        active.values().stream()
                .sorted(Comparator.comparingInt(Installed::id))
                .forEach(
                        a -> {
                            int hash = Utils.abilityHash(a.name());
                            if (!hashes.add(hash)) return;
                            result.addAbilityEmbryoList(
                                    AbilityEmbryo.newBuilder()
                                            .setAbilityId(a.id())
                                            .setAbilityNameHash(hash)
                                            .setAbilityOverrideNameHash(
                                                    a.override().isEmpty()
                                                            ? GameConstants.DEFAULT_ABILITY_NAME
                                                            : Utils.abilityHash(a.override())));
                        });
        return result.build();
    }
}
