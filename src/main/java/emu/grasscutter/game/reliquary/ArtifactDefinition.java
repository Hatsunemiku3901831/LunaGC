package emu.grasscutter.game.reliquary;

import emu.grasscutter.data.*;
import emu.grasscutter.data.excels.ItemData;
import emu.grasscutter.data.excels.reliquary.*;
import emu.grasscutter.game.inventory.*;
import emu.grasscutter.game.props.FightProperty;
import emu.grasscutter.net.proto.ReliquaryOfferDefineReqOuterClass.ReliquaryOfferDefineReq;
import emu.grasscutter.net.proto.RetcodeOuterClass.Retcode;
import emu.grasscutter.utils.objects.WeightedList;

import java.util.*;

/** A fully validated artifact, built before the economic intent is reserved. */
final class ArtifactDefinition {
    record Plan(int purchaseId, int setId, int cost, GameItem item) {}

    static final class Invalid extends RuntimeException {
        final int code;

        Invalid(int code) {
            this.code = code;
        }
    }

    private static void require(boolean valid, int code) {
        if (!valid) throw new Invalid(code);
    }

    static Plan plan(ReliquaryOfferDefineReq request, ArtifactTransmuterRules rules) {
        var offer = GameData.getReliquaryOfferPurchaseDataMap().get(request.getPurchaseId());
        require(offer != null, Retcode.RET_RELIQUARY_OFFER_PURCHASE_VERSION_IDX_ERROR_VALUE);
        int slot = request.getEquipType();
        require(slot >= 1 && slot <= 5, Retcode.RET_FAIL_VALUE);
        require(
                request.getAppendPropTypeListCount() == rules.selectedPropCount()
                        && new HashSet<>(request.getAppendPropTypeListList()).size()
                                == rules.selectedPropCount()
                        && !request.getAppendPropTypeListList().contains(request.getMainPropType()),
                Retcode.RET_FAIL_VALUE);
        var main = FightProperty.getPropById(request.getMainPropType());
        require(main != null && main != FightProperty.FIGHT_PROP_NONE, Retcode.RET_FAIL_VALUE);
        var templates = offer.pieces(slot);
        var weights = offer.getWeights();
        require(
                templates != null
                        && weights != null
                        && templates.length == weights.length
                        && templates.length > 0,
                Retcode.RET_NOT_FOUND_CONFIG_VALUE);
        var pieces = new WeightedList<ItemData>();
        for (int i = 0; i < templates.length; i++) {
            var piece = GameData.getItemDataMap().get(templates[i]);
            require(
                    piece != null
                            && piece.getItemType() == ItemType.ITEM_RELIQUARY
                            && piece.getRankLevel() == 5
                            && piece.getEquipType().getValue() == slot
                            && piece.getSetId() == offer.getOfferSetId()
                            && piece.getAppendPropNum() >= rules.selectedPropCount()
                            && piece.getAppendPropNum() >= 3
                            && piece.getAppendPropNum() <= 4
                            && piece.getMaxLevel() >= 1
                            && rules.guaranteedUpgradeHits()
                                    <= (piece.getMaxLevel() - 1) / 4
                                            - (4 - piece.getAppendPropNum()),
                    Retcode.RET_NOT_FOUND_CONFIG_VALUE);
            require(weights[i] >= 0, Retcode.RET_NOT_FOUND_CONFIG_VALUE);
            if (weights[i] > 0) pieces.add(weights[i], piece);
        }
        require(pieces.size() > 0, Retcode.RET_NOT_FOUND_CONFIG_VALUE);
        var piece = pieces.next();
        var mainData =
                GameData.getReliquaryMainPropDataMap().values().stream()
                        .filter(
                                x ->
                                        x.getPropDepotId() == piece.getMainPropDepotId()
                                                && x.getFightProp() == main
                                                && x.getWeight() > 0)
                        .findFirst()
                        .orElse(null);
        require(mainData != null, Retcode.RET_FAIL_VALUE);
        var pool = GameDepot.getRelicAffixList(piece.getAppendPropDepotId());
        require(pool != null, Retcode.RET_NOT_FOUND_CONFIG_VALUE);
        var used = new HashSet<FightProperty>();
        used.add(main);
        var append = new ArrayList<Integer>();
        var selected = new ArrayList<Integer>();
        for (int id : request.getAppendPropTypeListList()) {
            var wanted = FightProperty.getPropById(id);
            require(
                    wanted != null && wanted != FightProperty.FIGHT_PROP_NONE && used.add(wanted),
                    Retcode.RET_FAIL_VALUE);
            var picked = pick(pool, Set.of(wanted), true);
            require(picked != null, Retcode.RET_FAIL_VALUE);
            append.add(picked.getId());
            selected.add(picked.getId());
        }
        while (append.size() < piece.getAppendPropNum()) {
            var picked = pick(pool, used, false);
            require(picked != null, Retcode.RET_NOT_FOUND_CONFIG_VALUE);
            append.add(picked.getId());
            used.add(picked.getFightProp());
        }
        var item = new GameItem(piece);
        item.setMainPropId(mainData.getId());
        item.getAppendPropIdList().clear();
        item.getAppendPropIdList().addAll(append);
        item.setLocked(true);
        item.markAsDefinedReliquary(selected, rules.guaranteedUpgradeHits());
        return new Plan(offer.getId(), offer.getOfferSetId(), rules.slotCosts().get(slot), item);
    }

    private static ReliquaryAffixData pick(
            List<ReliquaryAffixData> pool, Set<FightProperty> props, boolean include) {
        var choices = new WeightedList<ReliquaryAffixData>();
        for (var row : pool)
            if (row.getFightProp() != null
                    && row.getFightProp() != FightProperty.FIGHT_PROP_NONE
                    && Float.isFinite(row.getPropValue())
                    && row.getPropValue() > 0
                    && row.getWeight() > 0
                    && props.contains(row.getFightProp()) == include)
                choices.add(row.getWeight(), row);
        return choices.size() == 0 ? null : choices.next();
    }
}
