package emu.grasscutter.game.reliquary;

import emu.grasscutter.database.*;
import emu.grasscutter.game.inventory.*;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.utils.JsonUtils;

import org.bson.*;
import org.bson.json.JsonMode;
import org.bson.json.JsonWriterSettings;
import org.bson.types.ObjectId;

import java.time.Duration;
import java.util.Objects;

/** Shared acknowledged-document checks for explicit artifact economic recovery. */
final class ArtifactRecoveryStore {
    private final Player player;

    ArtifactRecoveryStore(Player player) {
        this.player = player;
    }

    static void require(boolean valid, String reason) {
        if (!valid) throw new IllegalStateException(reason);
    }

    void flush() {
        try {
            require(DatabaseHelper.awaitIdle(Duration.ofSeconds(5)), "数据库仍有待写入操作，请稍后重试。");
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("核对被中断。", interrupted);
        }
    }

    ArtifactTransmuterData savedState() {
        var mapper = DatabaseManager.getGameDatastore().getMapper();
        // A full Player decode runs onLoad -> codex migration -> save of a separate player copy.
        // Read only this embedded value so an audit cannot overwrite a later economic commit.
        var raw =
                mapper.getCollection(Player.class)
                        .withDocumentClass(BsonDocument.class)
                        .find(new BsonDocument("_id", new BsonInt32(player.getUid())))
                        .projection(new BsonDocument("artifactTransmuterData", new BsonInt32(1)))
                        .first();
        require(raw != null, "玩家存档不存在。");
        if (!raw.containsKey("artifactTransmuterData") || raw.isNull("artifactTransmuterData"))
            return new ArtifactTransmuterData();
        // This embedded record contains only strings, numbers, enums, maps and lists.
        // Keep the audit independent of entity lifecycle decoding.
        return JsonUtils.decode(
                raw.getDocument("artifactTransmuterData")
                        .toJson(JsonWriterSettings.builder().outputMode(JsonMode.RELAXED).build()),
                ArtifactTransmuterData.class);
    }

    GameItem live(String id) {
        var matches =
                player.getInventory().getItems().values().stream()
                        .filter(
                                x ->
                                        x.getObjectId() != null
                                                && x.getObjectId().toHexString().equals(id))
                        .limit(2)
                        .toList();
        require(matches.size() <= 1, "同一持久 ID 对应多个背包条目，需先核对。");
        return matches.isEmpty() ? null : matches.get(0);
    }

    GameItem stored(String id) {
        require(id != null && ObjectId.isValid(id), "记录中的物品持久 ID 无效。");
        return DatabaseManager.getGameDatastore()
                .find(GameItem.class)
                .field("id")
                .equal(new ObjectId(id))
                .first();
    }

    void agree(GameItem live, GameItem stored, int itemId) {
        if (live == null && stored != null && stored.getCount() == 0 && stored.isDeleted()) {
            require(
                    stored.getOwnerId() == player.getUid() && stored.getItemId() == itemId,
                    "已消耗物品的归属与记录不符。");
            return;
        }
        require((live == null) == (stored == null), "背包与存档的物品存在状态不同，请重新登录后核对。");
        if (live == null) return;
        require(
                live.getOwnerId() == player.getUid()
                        && stored.getOwnerId() == player.getUid()
                        && live.getItemId() == itemId
                        && stored.getItemId() == itemId
                        && live.getCount() > 0
                        && live.getCount() == stored.getCount()
                        && live.getObjectId().equals(stored.getObjectId()),
                "物品归属、数量或持久 ID 与记录不符。");
        if (live.getItemData() != null && live.getItemType() == ItemType.ITEM_RELIQUARY)
            require(
                    live.getLevel() == stored.getLevel()
                            && live.getExp() == stored.getExp()
                            && live.getTotalExp() == stored.getTotalExp()
                            && live.getMainPropId() == stored.getMainPropId()
                            && Objects.equals(
                                    live.getAppendPropIdList(), stored.getAppendPropIdList())
                            && live.isLocked() == stored.isLocked()
                            && live.getEquipCharacter() == stored.getEquipCharacter()
                            && live.getPurchasedAppendPropIdList()
                                    .equals(stored.getPurchasedAppendPropIdList())
                            && live.getDefiniteAppendPropIdList()
                                    .equals(stored.getDefiniteAppendPropIdList())
                            && live.getDefinedInitialAppendCount()
                                    == stored.getDefinedInitialAppendCount()
                            && live.getDefinedUpgradeGuarantee()
                                    == stored.getDefinedUpgradeGuarantee(),
                    "圣遗物属性与存档不同，请重新登录后核对。");
    }
}
