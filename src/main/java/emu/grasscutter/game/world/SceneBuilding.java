package emu.grasscutter.game.world;

import dev.morphia.annotations.*;

import lombok.Getter;

import java.util.UUID;

/** A host-owned placement, independent of its temporary scene entity ID. */
@Entity(value = "scene_buildings", useDiscriminator = false)
public final class SceneBuilding {
    @Id @Getter private String id;
    @Indexed @Getter private int ownerUid;
    @Getter private int sceneId;
    @Getter private int gadgetId;
    private Position position;
    private Position rotation;
    @Transient @Getter private boolean persistent = true;

    public SceneBuilding() {}

    public SceneBuilding(
            int ownerUid,
            int sceneId,
            int gadgetId,
            Position position,
            Position rotation,
            boolean persistent) {
        id = UUID.randomUUID().toString();
        this.ownerUid = ownerUid;
        this.sceneId = sceneId;
        this.gadgetId = gadgetId;
        this.position = new Position(position);
        this.rotation = new Position(rotation);
        this.persistent = persistent;
    }

    public Position getPosition() {
        return position == null ? null : new Position(position);
    }

    public Position getRotation() {
        return rotation == null ? null : new Position(rotation);
    }
}
