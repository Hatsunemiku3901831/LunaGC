package emu.grasscutter.game.player;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.data.GameData;
import emu.grasscutter.game.entity.*;
import emu.grasscutter.game.inventory.*;
import emu.grasscutter.game.world.*;
import emu.grasscutter.net.proto.RetcodeOuterClass.Retcode;
import emu.grasscutter.net.proto.UseWidgetCreateGadgetReqOuterClass.UseWidgetCreateGadgetReq;
import emu.grasscutter.net.proto.VisionTypeOuterClass.VisionType;
import emu.grasscutter.net.proto.WidgetCoolDownDataOuterClass.WidgetCoolDownData;
import emu.grasscutter.net.proto.WidgetCreateLocationInfoOuterClass.WidgetCreateLocationInfo;
import emu.grasscutter.net.proto.WidgetDoBagReqOuterClass.WidgetDoBagReq;
import emu.grasscutter.server.packet.send.*;

import java.util.*;
import java.util.function.LongSupplier;

/**
 * Reusable creators, serialized with scene transfer and scene mutations. No inventory is consumed.
 */
public final class WidgetPlacementManager {
    // Server validation radius; this does not claim the client's preview distance is identical.
    private static final double MAX_PLACEMENT_DISTANCE = 20;
    private final Player player;
    private final LongSupplier clock;
    private final Map<Integer, PlacedWidget> placements = new HashMap<>();
    private final Map<Integer, Long> cooldowns = new HashMap<>();

    public WidgetPlacementManager(Player player, LongSupplier clock) {
        this.player = Objects.requireNonNull(player);
        this.clock = Objects.requireNonNull(clock);
    }

    public int useBag(WidgetDoBagReq request) {
        if (!request.hasWidgetCreatorInfo())
            return Retcode.RET_WIDGET_QUICK_USE_REQ_PARAM_ERROR.getNumber();
        var creator = request.getWidgetCreatorInfo();
        return operate(
                request.getMaterialId(),
                creator.getOpTypeValue(),
                creator.getEntityId(),
                creator.hasLocationInfo() ? creator.getLocationInfo() : null);
    }

    public int create(UseWidgetCreateGadgetReq request) {
        var location = WidgetCreateLocationInfo.newBuilder();
        if (request.hasPos()) location.setPos(request.getPos());
        if (request.hasRot()) location.setRot(request.getRot());
        return operate(request.getMaterialId(), 2, 0, location.build());
    }

    private int operate(int materialId, int op, int entityId, WidgetCreateLocationInfo location) {
        synchronized (player.getSceneTransferLock()) {
            var scene = player.getScene();
            if (scene == null) return Retcode.RET_WIDGET_QUICK_USE_REQ_PARAM_ERROR.getNumber();
            synchronized (scene) {
                synchronized (this) {
                    var avatar = player.getTeamManager().getCurrentAvatarEntity();
                    if (!scene.canRelocatePlayer(player))
                        return Retcode.RET_WIDGET_QUICK_USE_REQ_PARAM_ERROR.getNumber();
                    var config = WidgetCatalog.current().creator(materialId);
                    if (config == null) return Retcode.RET_INVALID_WIDGET_MATERIAL_ID.getNumber();
                    if (!owned(materialId)) return Retcode.RET_ITEM_NOT_EXIST.getNumber();
                    if (op != 1 && op != 2)
                        return Retcode.RET_WIDGET_QUICK_USE_REQ_PARAM_ERROR.getNumber();
                    var previous = placements.get(materialId);
                    if (previous != null
                            && (previous.getScene() != scene
                                    || scene.getEntityById(previous.getId()) != previous)) {
                        placements.remove(materialId, previous);
                        player.getTeamManager().getGadgets().remove(previous);
                        previous = null;
                    }
                    if (entityId != 0 && (previous == null || previous.getId() != entityId))
                        return Retcode.RET_GADGET_NOT_EXIST.getNumber();
                    if (op == 1) {
                        if (previous == null) return Retcode.RET_GADGET_NOT_EXIST.getNumber();
                        remove(previous);
                        return scene.getEntityById(previous.getId()) == previous
                                ? Retcode.RET_GADGET_CREATE_FAIL.getNumber()
                                : 0;
                    }
                    if (location == null || !location.hasPos())
                        return Retcode.RET_WIDGET_QUICK_USE_REQ_PARAM_ERROR.getNumber();
                    var pos = new Position(location.getPos());
                    var rot = location.hasRot() ? new Position(location.getRot()) : new Position();
                    if (!finite(pos)
                            || !finite(rot)
                            || !finite(avatar.getPosition())
                            || pos.computeDistance(avatar.getPosition()) > MAX_PLACEMENT_DISTANCE)
                        return Retcode.RET_WIDGET_QUICK_USE_REQ_PARAM_ERROR.getNumber();
                    long now = clock.getAsLong();
                    if (cooldowns.getOrDefault(config.cooldownGroup(), 0L) > now)
                        return Retcode.RET_ITEM_IN_COOLDOWN.getNumber();
                    var data = GameData.getGadgetDataMap().get(config.gadgetId());
                    if (data == null
                            || data.getType() == null
                            || data.getType() != emu.grasscutter.game.props.EntityType.Gadget
                                    && data.getType()
                                            != emu.grasscutter.game.props.EntityType.Worktop)
                        return Retcode.RET_GADGET_CREATE_FAIL.getNumber();
                    PlacedWidget created = null;
                    try {
                        created = new PlacedWidget(scene, materialId, config.gadgetId(), pos, rot);
                        created.setOwner(avatar);
                        created.initAbilities();
                        created.buildContent();
                        scene.addEntity(created);
                        if (scene.getEntityById(created.getId()) != created)
                            throw new IllegalStateException("Widget not registered");
                    } catch (RuntimeException failed) {
                        if (created != null) remove(created);
                        Grasscutter.getLogger()
                                .warn(
                                        "Could not place widget {} for {}",
                                        materialId,
                                        player.getUid(),
                                        failed);
                        return Retcode.RET_GADGET_CREATE_FAIL.getNumber();
                    }
                    placements.put(materialId, created);
                    player.getTeamManager().getGadgets().add(created);
                    if (previous != null) {
                        remove(previous);
                        if (scene.getEntityById(previous.getId()) == previous) {
                            remove(created);
                            placements.put(materialId, previous);
                            notifyGadgets(previous.getGadgetId());
                            return Retcode.RET_GADGET_CREATE_FAIL.getNumber();
                        }
                    }
                    long expires = now + config.cooldownMillis();
                    cooldowns.put(config.cooldownGroup(), expires);
                    notifyGadgets(config.gadgetId());
                    send(new PacketWidgetCoolDownNotify(config.cooldownGroup(), expires, true));
                    return 0;
                }
            }
        }
    }

    private boolean owned(int id) {
        var data = GameData.getItemDataMap().get(id);
        var inventory = player.getInventory();
        var item = inventory.getItemById(id);
        return data != null
                && data.getItemType() == ItemType.ITEM_MATERIAL
                && data.getMaterialType() == MaterialType.MATERIAL_WIDGET
                && item != null
                && item.getCount() > 0
                && item.getOwnerId() == player.getUid()
                && inventory.getItemByGuid(item.getGuid()) == item;
    }

    private static boolean finite(Position position) {
        return Float.isFinite(position.getX())
                && Float.isFinite(position.getY())
                && Float.isFinite(position.getZ());
    }

    private void remove(PlacedWidget widget) {
        try {
            widget.getScene().removeEntity(widget, VisionType.VisionType_VISION_REMOVE);
        } catch (RuntimeException failed) {
            Grasscutter.getLogger()
                    .warn(
                            "Could not finish widget removal {} for {}",
                            widget.getId(),
                            player.getUid(),
                            failed);
        }
    }

    private void removed(PlacedWidget widget) {
        synchronized (widget.getScene()) {
            synchronized (this) {
                boolean current = placements.remove(widget.materialId, widget);
                player.getTeamManager().getGadgets().remove(widget);
                var replacement = placements.get(widget.materialId);
                if (current
                        || replacement == null
                        || replacement.getGadgetId() != widget.getGadgetId())
                    notifyGadgets(widget.getGadgetId());
            }
        }
    }

    private void notifyGadgets(int gadgetId) {
        var ids =
                placements.values().stream()
                        .filter(
                                w ->
                                        w.getGadgetId() == gadgetId
                                                && w.getScene().getEntityById(w.getId()) == w)
                        .map(EntityGadget::getId)
                        .sorted()
                        .toList();
        send(new PacketWidgetGadgetDataNotify(gadgetId, ids));
    }

    private void send(emu.grasscutter.net.packet.BasePacket packet) {
        try {
            player.sendPacket(packet);
        } catch (RuntimeException failed) {
            Grasscutter.getLogger()
                    .debug("Could not synchronize widget state for {}", player.getUid(), failed);
        }
    }

    public synchronized List<WidgetCoolDownData> cooldowns() {
        long now = clock.getAsLong();
        cooldowns.entrySet().removeIf(e -> e.getValue() <= now);
        return cooldowns.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(
                        e ->
                                WidgetCoolDownData.newBuilder()
                                        .setId(e.getKey())
                                        .setCoolDownTime(e.getValue())
                                        .setIsSuccess(true)
                                        .build())
                .toList();
    }

    private final class PlacedWidget extends EntityGadget {
        private final int materialId;
        private boolean abilitiesInitialized;

        private PlacedWidget(
                Scene scene, int materialId, int gadgetId, Position pos, Position rot) {
            super(scene, gadgetId, pos, rot);
            this.materialId = materialId;
        }

        @Override
        public void initAbilities() {
            // EntityGadget's constructor calls this before the placing avatar is assigned.
            if (getOwner() == null) return;
            synchronized (getScene()) {
                if (abilitiesInitialized) return;
                abilitiesInitialized = true;
                var config = getConfigGadget();
                if (config == null || config.getAbilities() == null) return;
                for (var entry : config.getAbilities()) {
                    var data = GameData.getAbilityData(entry.getAbilityName());
                    if (data != null) player.getAbilityManager().addAbilityToEntity(this, data);
                }
            }
        }

        @Override
        public void onRemoved() {
            try {
                super.onRemoved();
            } finally {
                removed(this);
            }
        }
    }
}
