package emu.grasscutter.game.tps;

import dev.morphia.annotations.Entity;

import emu.grasscutter.data.GameData;
import emu.grasscutter.game.entity.EntityAvatar;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.net.proto.AbilityInvokeEntryOuterClass.AbilityInvokeEntry;
import emu.grasscutter.net.proto.AbilityMetaUpdateTpsWeaponAmmunitionOuterClass.AbilityMetaUpdateTpsWeaponAmmunition;

import lombok.Getter;

import java.util.*;

/** One atomic player-document result, retained as evidence of the last committed report. */
@Entity
@Getter
public final class TpsReportReceipt {
    private String operationId, previousOperationId, invocation;
    private long createdAt;
    private int uid, sceneId, avatarId;
    private Map<Integer, Integer> before = new LinkedHashMap<>(), after = new LinkedHashMap<>();
    private Map<Integer, Integer> magazinesBefore = new LinkedHashMap<>(),
            magazinesAfter = new LinkedHashMap<>();
    private Map<Integer, Integer> limits = new LinkedHashMap<>();

    public TpsReportReceipt() {}

    TpsReportReceipt(
            Player player,
            EntityAvatar avatar,
            AbilityInvokeEntry report,
            Map<Integer, Integer> after,
            Map<Integer, Integer> magazinesAfter,
            long now) {
        operationId = UUID.randomUUID().toString();
        previousOperationId =
                player.getLastTpsReport() == null
                        ? null
                        : player.getLastTpsReport().getOperationId();
        createdAt = now;
        uid = player.getUid();
        sceneId = avatar.getScene().getId();
        avatarId = avatar.getAvatar().getAvatarId();
        require(report.getSerializedSize() <= 1024 * 1024, "TPS 上报超过记录大小限制。");
        invocation = Base64.getEncoder().encodeToString(report.toByteArray());
        before = new LinkedHashMap<>(player.getTpsAmmunition());
        this.after = new LinkedHashMap<>(after);
        magazinesBefore = new LinkedHashMap<>(player.getTpsMagazineAmmunition());
        this.magazinesAfter = new LinkedHashMap<>(magazinesAfter);
        for (var entry : update().getAmmunitionListList())
            if (entry.getChangeCount() < 0) {
                var data = GameData.getTpsAmmunitionDataMap().get(entry.getAmmunitionConfigId());
                require(data != null, "TPS 上报弹药池不存在。");
                limits.put(data.getId(), TpsWeaponSystem.ammunitionLimit(player, data));
            }
        validate();
    }

    private AbilityMetaUpdateTpsWeaponAmmunition update() {
        try {
            require(invocation != null && invocation.length() <= 1400000, "TPS 上报记录损坏。");
            var bytes = Base64.getDecoder().decode(invocation);
            require(bytes.length <= 1024 * 1024, "TPS 上报记录过大。");
            var report = AbilityInvokeEntry.parseFrom(bytes);
            require(report.getEntityId() > 0, "TPS 上报缺少实体。");
            return AbilityMetaUpdateTpsWeaponAmmunition.parseFrom(report.getAbilityData());
        } catch (com.google.protobuf.InvalidProtocolBufferException
                | IllegalArgumentException invalid) {
            throw new IllegalStateException("TPS 上报记录无法解码。", invalid);
        }
    }

    public void validate() {
        require(
                operationId != null
                        && UUID.fromString(operationId).toString().equals(operationId)
                        && createdAt > 0
                        && uid >= 0
                        && sceneId > 0
                        && avatarId > 0,
                "TPS 上报记录身份无效。");
        if (previousOperationId != null)
            require(
                    !previousOperationId.equals(operationId)
                            && UUID.fromString(previousOperationId)
                                    .toString()
                                    .equals(previousOperationId),
                    "TPS 前序记录无效。");
        for (var map : Arrays.asList(before, after, magazinesBefore, magazinesAfter, limits))
            require(
                    map != null
                            && map.entrySet().stream()
                                    .allMatch(
                                            e ->
                                                    e.getKey() != null
                                                            && e.getKey() > 0
                                                            && e.getValue() != null),
                    "TPS 上报快照损坏。");
        var update = update();
        require(
                update.getUpdateTypeValue() == 0 || update.getUpdateTypeValue() == 1,
                "TPS 上报类型无效。");
        var changes = new HashMap<Integer, Long>();
        for (var entry : update.getAmmunitionListList()) {
            require(
                    entry.getChangeCount() <= 0
                            && (update.getUpdateTypeValue() == 0 || entry.getChangeCount() == 0),
                    "TPS 上报不是合法消耗或换弹。");
            if (entry.getChangeCount() != 0)
                changes.merge(
                        entry.getAmmunitionConfigId(), (long) entry.getChangeCount(), Long::sum);
        }
        require(limits.keySet().equals(changes.keySet()), "TPS 上报冻结容量不完整。");
        var expected = new LinkedHashMap<>(before);
        for (var entry : changes.entrySet()) {
            int limit = limits.get(entry.getKey());
            require(limit >= 0, "TPS 上报冻结容量无效。");
            long count =
                    Math.max(0, Math.min(before.getOrDefault(entry.getKey(), limit), limit))
                            + entry.getValue();
            require(count >= 0 && count <= limit, "TPS 上报消耗不符。");
            expected.put(entry.getKey(), (int) count);
        }
        var expectedMagazines = new LinkedHashMap<>(magazinesBefore);
        var seen = new HashSet<Integer>();
        for (var magazine : update.getAccessoryListList()) {
            require(
                    magazine.getAmmunitionType() > 0 && magazine.getCurrentAmmunition() >= 0,
                    "TPS 弹匣记录无效。");
            int slot = magazine.getAmmunitionType();
            if (!seen.add(slot))
                require(
                        expectedMagazines.get(slot) == magazine.getCurrentAmmunition(),
                        "TPS 弹匣记录冲突。");
            expectedMagazines.put(slot, magazine.getCurrentAmmunition());
        }
        require(
                expected.equals(after)
                        && expectedMagazines.equals(magazinesAfter)
                        && (!before.equals(after) || !magazinesBefore.equals(magazinesAfter)),
                "TPS 上报结果与计划不符。");
    }

    boolean samePlan(TpsReportReceipt other) {
        return other != null
                && Objects.equals(operationId, other.operationId)
                && Objects.equals(previousOperationId, other.previousOperationId)
                && createdAt == other.createdAt
                && uid == other.uid
                && sceneId == other.sceneId
                && avatarId == other.avatarId
                && Objects.equals(invocation, other.invocation)
                && before.equals(other.before)
                && after.equals(other.after)
                && magazinesBefore.equals(other.magazinesBefore)
                && magazinesAfter.equals(other.magazinesAfter)
                && limits.equals(other.limits);
    }

    static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
