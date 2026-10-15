package emu.grasscutter.game.reliquary;

import static emu.grasscutter.game.reliquary.ArtifactRecoveryStore.require;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.data.GameData;
import emu.grasscutter.game.inventory.*;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.net.proto.ReliquaryDustSelectReqOuterClass.ReliquaryDustSelectReq;
import emu.grasscutter.utils.JsonUtils;

import org.bson.types.ObjectId;

import java.util.*;
import java.util.function.Consumer;

/** Resume one durable reshape; READY never means permission to adopt on the player's behalf. */
final class ArtifactReshapeRecovery {
    private final Player player;
    private final Runnable save;
    private final Consumer<GameItem> saveItem;
    private final ArtifactRecoveryStore store;

    ArtifactReshapeRecovery(Player player, Runnable save, Consumer<GameItem> saveItem) {
        this.player = player;
        this.save = save;
        this.saveItem = saveItem;
        this.store = new ArtifactRecoveryStore(player);
    }

    static String operationId(String key, ArtifactReshapeCandidate candidate) {
        var id = candidate.getOperationId();
        return id == null || id.isBlank() ? "legacy:" + key : id;
    }

    static long matches(ArtifactTransmuterData data, String operationId) {
        return data.getReshapeCandidates().entrySet().stream()
                .filter(
                        x ->
                                x.getValue() != null
                                        && operationId != null
                                        && operationId.equals(
                                                operationId(x.getKey(), x.getValue())))
                .count();
    }

    List<String> inspect() {
        var lines = new ArrayList<String>();
        for (var entry :
                new TreeMap<>(player.getArtifactTransmuterData().getReshapeCandidates())
                        .entrySet()) {
            var candidate = entry.getValue();
            if (candidate == null) {
                lines.add("reshape=" + entry.getKey() + " invalid=null");
                continue;
            }
            lines.add(
                    "reshape="
                            + entry.getKey()
                            + " operation="
                            + operationId(entry.getKey(), candidate)
                            + " phase="
                            + candidate.getPhase()
                            + " cost="
                            + candidate.getCost()
                            + " progress="
                            + candidate.getProgressBefore()
                            + "->"
                            + candidate.getProgressAfter());
            if (candidate.getMaterialObjectId() == null
                    || !ObjectId.isValid(candidate.getMaterialObjectId())) {
                lines.add("旧重塑记录缺少付款快照，不猜测扣尘或退款。");
                continue;
            }
            var dust = store.live(candidate.getMaterialObjectId());
            var stored = store.stored(candidate.getMaterialObjectId());
            lines.add(
                    "material="
                            + candidate.getMaterialId()
                            + " objectId="
                            + candidate.getMaterialObjectId()
                            + " before="
                            + candidate.getMaterialBefore()
                            + " consume="
                            + candidate.getCost()
                            + " live="
                            + (dust == null ? 0 : dust.getCount())
                            + " stored="
                            + (stored == null ? 0 : stored.getCount()));
        }
        return lines;
    }

    private void validate(GameItem item, ArtifactReshapeCandidate candidate, boolean resume) {
        require(
                item != null
                        && item.getCount() == 1
                        && item.getItemId() == candidate.getItemId()
                        && item.getLevel() == candidate.getLevel()
                        && item.getMainPropId() == candidate.getMainPropId()
                        && item.getAppendPropIdList() != null
                        && candidate.getOriginal() != null,
                "重塑圣遗物已丢失或主属性、等级、数量与预留不同。");
        require(
                candidate.getPhase() != null
                        && candidate.getCost() > 0
                        && candidate.getProgressBefore() >= 0
                        && candidate.getProgressAfter()
                                == candidate.getProgressBefore() + candidate.getCost()
                        && candidate.getProgressAfter() <= 0xffff_ffffL,
                "重塑阶段、成本或进度快照无效。");
        if (!resume && candidate.getPhase() == ArtifactReshapeCandidate.Phase.RESERVED) return;
        require(
                item.getItemData() != null && ArtifactReshape.matches(item, candidate),
                "重塑圣遗物资源无效。");
        var original = candidate.getOriginal();
        var result = candidate.getResult();
        var selected = candidate.getSelected();
        require(
                original != null
                        && result != null
                        && (original.size() == 8 || original.size() == 9)
                        && original.size() == result.size()
                        && selected != null
                        && selected.size() == 2
                        && new HashSet<>(selected).size() == 2,
                "重塑候选词条快照不完整。");
        var originalTypes = types(item, original);
        require(
                originalTypes.equals(types(item, result)) && originalTypes.containsAll(selected),
                "重塑候选的属性类型与预留不同。");
    }

    private Set<Integer> types(GameItem item, List<Integer> rolls) {
        var types = new HashSet<Integer>();
        for (int index = 0; index < rolls.size(); index++) {
            var row =
                    rolls.get(index) == null
                            ? null
                            : GameData.getReliquaryAffixDataMap().get(rolls.get(index));
            require(
                    row != null
                            && row.getDepotId() == item.getItemData().getAppendPropDepotId()
                            && row.getFightProp() != null
                            && row.getFightProp().getId() > 0
                            && Float.isFinite(row.getPropValue())
                            && row.getPropValue() > 0,
                    "重塑词条对应的资源无效。");
            int type = row.getFightProp().getId();
            require(index < 4 ? types.add(type) : types.contains(type), "重塑词条类型无效。");
        }
        return types;
    }

    private void cancel(
            ArtifactTransmuterData data,
            String key,
            ArtifactReshapeCandidate candidate,
            GameItem item) {
        var next = new HashMap<>(data.getReshapeCandidates());
        next.remove(key);
        data.setReshapeCandidates(next);
        try {
            save.run();
        } catch (RuntimeException failure) {
            next.put(key, candidate);
            data.setReshapeCandidates(next);
            throw failure;
        }
        item.setReshapePending(false);
    }

    private void reserved(
            ArtifactTransmuterData data,
            String key,
            ArtifactReshapeCandidate candidate,
            GameItem item,
            boolean resume) {
        require(
                candidate.getMaterialId() > 0
                        && candidate.getMaterialBefore() >= candidate.getCost()
                        && candidate.getMaterialObjectId() != null
                        && ObjectId.isValid(candidate.getMaterialObjectId()),
                "旧重塑记录没有完整付款快照，不能猜测扣尘或退款。");
        var dust = store.live(candidate.getMaterialObjectId());
        store.agree(dust, store.stored(candidate.getMaterialObjectId()), candidate.getMaterialId());
        int count = dust == null ? 0 : dust.getCount();
        boolean paid = count == candidate.getMaterialBefore() - candidate.getCost();
        require(paid || count == candidate.getMaterialBefore(), "启圣之尘数量不等于付款前后快照。");
        require(
                data.getReshapeProgress() == candidate.getProgressBefore()
                        || paid && data.getReshapeProgress() == candidate.getProgressAfter(),
                "重塑进度与付款快照不符。");
        require(item.getAppendPropIdList().equals(candidate.getOriginal()), "待付款圣遗物已改变。");
        if (!resume) {
            require(
                    !paid && data.getReshapeProgress() == candidate.getProgressBefore(),
                    "不能取消已扣尘的重塑；请继续付款确认并在客户端选择结果。");
            cancel(data, key, candidate, item);
            return;
        }
        if (!paid) {
            require(
                    dust.getItemData() != null
                            && dust.getItemType() == ItemType.ITEM_MATERIAL
                            && dust.getItemData().getId() == candidate.getMaterialId()
                            && !dust.getItemData().isUseOnGain(),
                    "待支付材料资源无效。");
            require(
                    player.getInventory().removeItem(dust, candidate.getCost())
                            && count - dust.getCount() == candidate.getCost(),
                    "重塑扣尘未得到准确确认。");
            saveItem.accept(dust);
        }
        data.setReshapeProgress(candidate.getProgressAfter());
        candidate.setPhase(ArtifactReshapeCandidate.Phase.READY);
        try {
            save.run();
        } catch (RuntimeException failure) {
            candidate.setPhase(ArtifactReshapeCandidate.Phase.RESERVED);
            throw failure;
        }
    }

    void reconcile(String operationId, boolean resume) {
        synchronized (player.getInventory()) {
            store.flush();
            var data = player.getArtifactTransmuterData();
            require(matches(data, operationId) == 1, "未找到唯一对应操作 ID 的重塑记录。");
            require(data.getPending() == null, "请先核对未完成的萃取／制作记录。");
            var entry =
                    data.getReshapeCandidates().entrySet().stream()
                            .filter(
                                    x ->
                                            x.getValue() != null
                                                    && operationId.equals(
                                                            operationId(x.getKey(), x.getValue())))
                            .findFirst()
                            .orElseThrow();
            var key = entry.getKey();
            var candidate = entry.getValue();
            require(key.equals(candidate.getItemObjectId()), "重塑记录的物品持久 ID 不一致。");
            require(
                    JsonUtils.toJson(data).equals(JsonUtils.toJson(store.savedState())),
                    "重塑内存状态与存档不同，请重新登录后核对。");
            var item = store.live(key);
            store.agree(item, store.stored(key), candidate.getItemId());
            validate(item, candidate, resume);
            if (candidate.getPhase() == ArtifactReshapeCandidate.Phase.RESERVED) {
                reserved(data, key, candidate, item, resume);
            } else {
                require(resume, "已确认付款或已选择结果的重塑不能取消，也不退尘。");
                require(
                        data.getReshapeProgress() >= candidate.getProgressAfter()
                                && data.getReshapeProgress() <= 0xffff_ffffL,
                        "已付款重塑的进度不符。");
                if (candidate.getPhase() == ArtifactReshapeCandidate.Phase.READY) {
                    require(
                            item.getAppendPropIdList().equals(candidate.getOriginal()),
                            "待选择圣遗物已改变。");
                } else {
                    var response =
                            new ArtifactReshape(player, save, saveItem)
                                    .select(
                                            ReliquaryDustSelectReq.newBuilder()
                                                    .setReliquaryGuid(item.getGuid())
                                                    .setIsNew(
                                                            candidate.getPhase()
                                                                    == ArtifactReshapeCandidate
                                                                            .Phase.ADOPTING)
                                                    .build());
                    require(response.getRetcode() == 0, "结果选择仍未确认，已保留原决定与记录。");
                }
            }
            Grasscutter.getLogger()
                    .info(
                            "Artifact reshape recovery uid={} operation={} action={} phase={}",
                            player.getUid(),
                            operationId,
                            resume ? "resume" : "cancel",
                            candidate.getPhase());
            try {
                new ArtifactReshape(player, save, saveItem).notifyState();
            } catch (RuntimeException failure) {
                Grasscutter.getLogger()
                        .warn(
                                "Reshape recovery committed but notification failed for UID {}",
                                player.getUid(),
                                failure);
            }
        }
    }
}
