package emu.grasscutter.game.managers.mapmark;

import dev.morphia.annotations.Entity;

import emu.grasscutter.game.world.Position;
import emu.grasscutter.net.proto.MapMarkFromTypeOuterClass.MapMarkFromType;
import emu.grasscutter.net.proto.MapMarkPointOuterClass.MapMarkPoint;
import emu.grasscutter.net.proto.MapMarkPointTypeOuterClass.MapMarkPointType;

@Entity
public class MapMark {
    private int sceneId;
    private String name;
    private Position position;
    private MapMarkPointType mapMarkPointType;
    private int monsterId;
    private MapMarkFromType mapMarkFromType;
    private int questId;
    private int trackingItemId;

    public int getTrackingItemId() {
        return trackingItemId;
    }

    public static MapMark materialPoint(int sceneId, Position position, int itemId, String name) {
        if (sceneId <= 0
                || itemId <= 0
                || position == null
                || !Float.isFinite(position.getX())
                || !Float.isFinite(position.getY())
                || !Float.isFinite(position.getZ()))
            throw new IllegalArgumentException("Invalid material map point");
        var mark =
                new MapMark(
                        MapMarkPoint.newBuilder()
                                .setSceneId(sceneId)
                                .setName(name)
                                .setPos(position.toProto())
                                .setPointType(MapMarkPointType.MapMarkPointType_COLLECTION)
                                .setFromType(MapMarkFromType.MapMarkFromType_NOE)
                                .build());
        mark.trackingItemId = itemId;
        return mark;
    }

    @Deprecated // Morhpia
    public MapMark() {
        this.mapMarkPointType = MapMarkPointType.MapMarkPointType_MONSTER;
        this.mapMarkFromType = MapMarkFromType.MapMarkFromType_MONSTER;
    }

    public MapMark(MapMarkPoint mapMarkPoint) {
        this.sceneId = mapMarkPoint.getSceneId();
        this.name = mapMarkPoint.getName();
        this.position = new Position(mapMarkPoint.getPos());
        this.mapMarkPointType = mapMarkPoint.getPointType();
        this.monsterId = mapMarkPoint.getMonsterId();
        this.mapMarkFromType = mapMarkPoint.getFromType();
        this.questId = mapMarkPoint.getQuestId();
    }

    public int getSceneId() {
        return this.sceneId;
    }

    public String getName() {
        return this.name;
    }

    public Position getPosition() {
        return this.position;
    }

    public MapMarkPointType getMapMarkPointType() {
        return this.mapMarkPointType;
    }

    public int getMonsterId() {
        return this.monsterId;
    }

    public MapMarkFromType getMapMarkFromType() {
        return this.mapMarkFromType;
    }

    public int getQuestId() {
        return this.questId;
    }
}
