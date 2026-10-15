package emu.grasscutter.game.reliquary;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.data.*;
import emu.grasscutter.data.excels.reliquary.ReliquaryAffixData;
import emu.grasscutter.game.inventory.*;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.net.proto.ReliquaryDustReqOuterClass.ReliquaryDustReq;
import emu.grasscutter.net.proto.ReliquaryDustRspOuterClass.ReliquaryDustRsp;
import emu.grasscutter.net.proto.ReliquaryDustSelectReqOuterClass.ReliquaryDustSelectReq;
import emu.grasscutter.net.proto.ReliquaryDustSelectRspOuterClass.ReliquaryDustSelectRsp;
import emu.grasscutter.net.proto.ReliquaryOfferCandidateOuterClass.ReliquaryOfferCandidate;
import emu.grasscutter.net.proto.ReliquaryOfferCompanionNotifyOuterClass.ReliquaryOfferCompanionNotify;
import emu.grasscutter.net.proto.RetcodeOuterClass.Retcode;
import emu.grasscutter.server.packet.send.*;
import emu.grasscutter.utils.objects.WeightedList;

import java.util.*;
import java.util.function.Consumer;

/** Reshape and choose under one inventory monitor, retaining uncertain economic intents. */
final class ArtifactReshape {
    private final Player player;
    private final Runnable save;
    private final Consumer<GameItem> saveItem;

    ArtifactReshape(Player player, Runnable save, Consumer<GameItem> saveItem) {
        this.player = player;
        this.save = save;
        this.saveItem = saveItem;
    }

    private static final class Invalid extends RuntimeException {
        final int code;

        Invalid(int code) {
            this.code = code;
        }
    }

    private static void require(boolean valid, int code) {
        if (!valid) throw new Invalid(code);
    }

    static boolean unsettled(ArtifactTransmuterData data) {
        return data.getReshapeCandidates().values().stream()
                .anyMatch(x -> x == null || x.getPhase() != ArtifactReshapeCandidate.Phase.READY);
    }

    static boolean holds(Player player, GameItem item) {
        return item != null
                && item.getItemType() == ItemType.ITEM_RELIQUARY
                && item.getOwnerId() == player.getUid()
                && item.getObjectId() != null
                && player.getArtifactTransmuterData()
                        .getReshapeCandidates()
                        .containsKey(item.getObjectId().toHexString());
    }

    private GameItem owned(long guid) {
        var item = player.getInventory().getItemByGuid(guid);
        return item != null
                        && item.getOwnerId() == player.getUid()
                        && item.getItemData() != null
                        && item.getItemData().getId() == item.getItemId()
                        && item.getCount() > 0
                ? item
                : null;
    }

    private void put(
            ArtifactTransmuterData data, ArtifactReshapeCandidate candidate, boolean remove) {
        var next = new HashMap<>(data.getReshapeCandidates());
        if (remove) next.remove(candidate.getItemObjectId());
        else next.put(candidate.getItemObjectId(), candidate);
        data.setReshapeCandidates(next);
    }

    ReliquaryDustRsp roll(ReliquaryDustReq request) {
        var response = ReliquaryDustRsp.newBuilder().setReliquaryGuid(request.getReliquaryGuid());
        synchronized (player.getInventory()) {
            try {
                var data = player.getArtifactTransmuterData();
                require(data.getPending() == null && !unsettled(data), Retcode.RET_SVR_ERROR_VALUE);
                var item = owned(request.getReliquaryGuid());
                require(
                        item != null
                                && item.getItemType() == ItemType.ITEM_RELIQUARY
                                && item.getCount() == 1
                                && item.getObjectId() != null,
                        Retcode.RET_ITEM_NOT_EXIST_VALUE);
                var prior = data.getReshapeCandidates().get(item.getObjectId().toHexString());
                if (prior != null) {
                    var selected = selections(item, request.getAppendPropTypeListList());
                    require(
                            new HashSet<>(selected).equals(new HashSet<>(prior.getSelected()))
                                    && selected.size() == prior.getSelected().size(),
                            Retcode.RET_FAIL_VALUE);
                    require(
                            matches(item, prior)
                                    && item.getAppendPropIdList().equals(prior.getOriginal()),
                            Retcode.RET_SVR_ERROR_VALUE);
                    notifyState();
                    return response.build();
                }
                var candidate =
                        plan(
                                item,
                                request,
                                data.getReshapeProgress(),
                                ArtifactReshapeRules.current());
                var dust =
                        player.getInventory()
                                .getItemById(ArtifactReshapeRules.current().materialId());
                require(
                        dust != null
                                && owned(dust.getGuid()) == dust
                                && dust.getItemType() == ItemType.ITEM_MATERIAL
                                && dust.getCount() >= candidate.getCost(),
                        Retcode.RET_ITEM_COUNT_NOT_ENOUGH_VALUE);
                put(data, candidate, false);
                item.setReshapePending(true);
                save.run();
                int before = dust.getCount();
                if (!player.getInventory().removeItem(dust, candidate.getCost())
                        || before - dust.getCount() != candidate.getCost())
                    throw new IllegalStateException("Reshape payment was not exact");
                saveItem.accept(dust);
                data.setReshapeProgress(candidate.getProgressAfter());
                candidate.setPhase(ArtifactReshapeCandidate.Phase.READY);
                try {
                    save.run();
                } catch (RuntimeException uncertain) {
                    candidate.setPhase(ArtifactReshapeCandidate.Phase.RESERVED);
                    throw uncertain;
                }
                notifyState();
            } catch (Invalid denied) {
                response.setRetcode(denied.code);
            } catch (RuntimeException failure) {
                Grasscutter.getLogger()
                        .error("Artifact reshape failed for UID {}", player.getUid(), failure);
                response.setRetcode(Retcode.RET_SVR_ERROR_VALUE);
            }
        }
        return response.build();
    }

    ReliquaryDustSelectRsp select(ReliquaryDustSelectReq request) {
        var response = ReliquaryDustSelectRsp.newBuilder();
        synchronized (player.getInventory()) {
            try {
                var data = player.getArtifactTransmuterData();
                require(data.getPending() == null, Retcode.RET_SVR_ERROR_VALUE);
                var item = owned(request.getReliquaryGuid());
                require(
                        item != null && item.getObjectId() != null,
                        Retcode.RET_ITEM_NOT_EXIST_VALUE);
                var candidate = data.getReshapeCandidates().get(item.getObjectId().toHexString());
                require(candidate != null, Retcode.RET_FAIL_VALUE);
                var decision =
                        request.getIsNew()
                                ? ArtifactReshapeCandidate.Phase.ADOPTING
                                : ArtifactReshapeCandidate.Phase.KEEPING;
                require(
                        candidate.getPhase() == ArtifactReshapeCandidate.Phase.READY
                                || candidate.getPhase() == decision,
                        Retcode.RET_SVR_ERROR_VALUE);
                require(matches(item, candidate), Retcode.RET_SVR_ERROR_VALUE);
                var result = request.getIsNew() ? candidate.getResult() : candidate.getOriginal();
                require(
                        item.getAppendPropIdList().equals(candidate.getOriginal())
                                || candidate.getPhase() == decision
                                        && item.getAppendPropIdList().equals(result),
                        Retcode.RET_SVR_ERROR_VALUE);
                candidate.setPhase(decision);
                save.run();
                item.getAppendPropIdList().clear();
                item.getAppendPropIdList().addAll(result);
                saveItem.accept(item);
                if (item.getEquipCharacter() > 0) {
                    var avatar = player.getAvatars().getAvatarById(item.getEquipCharacter());
                    if (avatar != null) avatar.recalcStats();
                }
                player.sendPacket(new PacketStoreItemChangeNotify(item));
                put(data, candidate, true);
                try {
                    save.run();
                } catch (RuntimeException uncertain) {
                    put(data, candidate, false);
                    throw uncertain;
                }
                item.setReshapePending(false);
                notifyState();
            } catch (Invalid denied) {
                response.setRetcode(denied.code);
            } catch (RuntimeException failure) {
                Grasscutter.getLogger()
                        .error(
                                "Artifact reshape selection failed for UID {}",
                                player.getUid(),
                                failure);
                response.setRetcode(Retcode.RET_SVR_ERROR_VALUE);
            }
        }
        return response.build();
    }

    private boolean matches(GameItem item, ArtifactReshapeCandidate candidate) {
        return item.getCount() == 1
                && item.getItemType() == ItemType.ITEM_RELIQUARY
                && item.getItemId() == candidate.getItemId()
                && item.getLevel() == candidate.getLevel()
                && item.getMainPropId() == candidate.getMainPropId();
    }

    void onLogin(boolean ownsGadget) {
        synchronized (player.getInventory()) {
            var data = player.getArtifactTransmuterData();
            for (var item : player.getInventory().getItems().values())
                item.setReshapePending(holds(player, item));
            try {
                if (ownsGadget
                        || data.getReshapeProgress() != 0
                        || !data.getReshapeCandidates().isEmpty()
                        || player.getInventory().getItemCountById(105006) > 0) notifyState();
            } catch (RuntimeException failure) {
                Grasscutter.getLogger()
                        .error(
                                "Artifact reshape login snapshot failed for UID {}",
                                player.getUid(),
                                failure);
            }
        }
    }

    private void notifyState() {
        var data = player.getArtifactTransmuterData();
        require(
                data.getReshapeProgress() >= 0 && data.getReshapeProgress() <= 0xffff_ffffL,
                Retcode.RET_SVR_ERROR_VALUE);
        var notice =
                ReliquaryOfferCompanionNotify.newBuilder()
                        .setProgress((int) data.getReshapeProgress());
        for (var item :
                player.getInventory().getItems().values().stream()
                        .sorted(Comparator.comparingLong(GameItem::getGuid))
                        .toList()) {
            if (item.getObjectId() == null || owned(item.getGuid()) != item) continue;
            var candidate = data.getReshapeCandidates().get(item.getObjectId().toHexString());
            if (candidate == null
                    || candidate.getPhase() == null
                    || candidate.getPhase() == ArtifactReshapeCandidate.Phase.RESERVED
                    || !matches(item, candidate)) continue;
            notice.addCandidateList(
                    ReliquaryOfferCandidate.newBuilder()
                            .setReliquaryGuid(item.getGuid())
                            .addAllAppendPropIdList(candidate.getResult())
                            .addAllAppendPropTypeList(candidate.getSelected()));
        }
        player.sendPacket(new PacketReliquaryOfferCompanionNotify(notice.build()));
    }

    private static List<Integer> selections(GameItem item, List<Integer> submitted) {
        var fixed = item.getPurchasedAppendPropIdList();
        if (fixed.isEmpty()) fixed = item.getDefiniteAppendPropIdList();
        if (fixed.isEmpty() && item.getDefinedInitialAppendCount() == 0)
            return List.copyOf(submitted);
        int count = ArtifactReshapeRules.current().selectedCount();
        require(fixed.size() == count, Retcode.RET_FAIL_VALUE);
        var types = new LinkedHashSet<Integer>();
        for (int id : fixed) {
            var row = GameData.getReliquaryAffixDataMap().get(id);
            require(
                    row != null
                            && row.getDepotId() == item.getItemData().getAppendPropDepotId()
                            && row.getFightProp() != null
                            && row.getFightProp().getId() > 0
                            && types.add(row.getFightProp().getId()),
                    Retcode.RET_FAIL_VALUE);
        }
        require(
                submitted.isEmpty()
                        || submitted.size() == count && new HashSet<>(submitted).equals(types),
                Retcode.RET_FAIL_VALUE);
        return List.copyOf(types);
    }

    private static ArtifactReshapeCandidate plan(
            GameItem item, ReliquaryDustReq request, long progress, ArtifactReshapeRules rules) {
        var definition = item.getItemData();
        require(
                definition.getRankLevel() >= rules.minimumRank()
                        && item.getLevel() >= rules.minimumLevel()
                        && item.getLevel() == definition.getMaxLevel(),
                Retcode.RET_FAIL_VALUE);
        var original = List.copyOf(item.getAppendPropIdList());
        int initial = original.size() - (item.getLevel() - 1) / 4;
        require(
                initial >= rules.minimumInitialProps() && initial <= 4 && original.size() >= 4,
                Retcode.RET_FAIL_VALUE);
        var types = new LinkedHashSet<Integer>();
        for (int index = 0; index < original.size(); index++) {
            var row = GameData.getReliquaryAffixDataMap().get(original.get(index));
            require(
                    row != null
                            && row.getDepotId() == definition.getAppendPropDepotId()
                            && row.getFightProp() != null
                            && row.getFightProp().getId() > 0
                            && Float.isFinite(row.getPropValue())
                            && row.getPropValue() > 0,
                    Retcode.RET_FAIL_VALUE);
            if (index < 4) require(types.add(row.getFightProp().getId()), Retcode.RET_FAIL_VALUE);
            else require(types.contains(row.getFightProp().getId()), Retcode.RET_FAIL_VALUE);
        }
        var main = GameData.getReliquaryMainPropDataMap().get(item.getMainPropId());
        require(
                main != null
                        && main.getFightProp() != null
                        && main.getPropDepotId() == definition.getMainPropDepotId()
                        && main.getWeight() > 0
                        && !types.contains(main.getFightProp().getId()),
                Retcode.RET_FAIL_VALUE);
        var selected = selections(item, request.getAppendPropTypeListList());
        require(
                selected.size() == rules.selectedCount()
                        && new HashSet<>(selected).size() == selected.size()
                        && types.containsAll(selected),
                Retcode.RET_FAIL_VALUE);
        int cost =
                (initial == 3 ? rules.threePropCosts() : rules.fourPropCosts())
                        .get(definition.getEquipType().getValue());
        require(progress >= 0 && progress <= 0xffff_ffffL - cost, Retcode.RET_SVR_ERROR_VALUE);
        int tier = rules.tier(progress, cost), guarantee = rules.guarantees().get(tier);
        require(guarantee <= original.size() - 4, Retcode.RET_NOT_FOUND_CONFIG_VALUE);
        var pool = GameDepot.getRelicAffixList(definition.getAppendPropDepotId());
        require(pool != null, Retcode.RET_NOT_FOUND_CONFIG_VALUE);
        var base = new HashMap<Integer, WeightedList<Integer>>();
        var growth = new HashMap<Integer, WeightedList<Integer>>();
        for (int type : types) {
            base.put(type, new WeightedList<>());
            growth.put(type, new WeightedList<>());
        }
        for (ReliquaryAffixData row : pool) {
            if (row.getFightProp() == null
                    || !types.contains(row.getFightProp().getId())
                    || !Float.isFinite(row.getPropValue())
                    || row.getPropValue() <= 0) continue;
            int type = row.getFightProp().getId();
            if (row.getWeight() > 0) base.get(type).add(row.getWeight(), row.getId());
            if (row.getUpgradeWeight() > 0)
                growth.get(type).add(row.getUpgradeWeight(), row.getId());
        }
        require(
                base.values().stream().allMatch(x -> x.size() > 0)
                        && growth.values().stream().allMatch(x -> x.size() > 0),
                Retcode.RET_NOT_FOUND_CONFIG_VALUE);
        var result = new ArrayList<Integer>();
        types.forEach(type -> result.add(base.get(type).next()));
        int hits = 0;
        for (int remaining = original.size() - 4; remaining > 0; remaining--) {
            var options = remaining <= guarantee - hits ? selected : new ArrayList<>(types);
            int type =
                    options.get(
                            java.util.concurrent.ThreadLocalRandom.current()
                                    .nextInt(options.size()));
            result.add(growth.get(type).next());
            if (selected.contains(type)) hits++;
        }
        var candidate = new ArtifactReshapeCandidate();
        candidate.setItemObjectId(item.getObjectId().toHexString());
        candidate.setItemId(item.getItemId());
        candidate.setLevel(item.getLevel());
        candidate.setMainPropId(item.getMainPropId());
        candidate.setOriginal(original);
        candidate.setResult(List.copyOf(result));
        candidate.setSelected(selected);
        candidate.setCost(cost);
        candidate.setTier(tier);
        candidate.setProgressBefore(progress);
        candidate.setProgressAfter(progress + cost);
        candidate.setPhase(ArtifactReshapeCandidate.Phase.RESERVED);
        return candidate;
    }
}
