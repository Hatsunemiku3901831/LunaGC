package emu.grasscutter.data.excels.reliquary;

import emu.grasscutter.data.*;

import lombok.Getter;

import java.util.*;

/** The client's explicit purchase IDs and weighted artifact templates for each slot. */
@Getter
@ResourceType(name = "ReliquaryOfferPurchaseExcelConfigData.json")
public final class ReliquaryOfferPurchaseData extends GameResource {
    @Getter(onMethod_ = @Override)
    private int id;

    private int offerSetId;
    private int[] flowerIds, featherIds, timepieceIds, gobletIds, circletIds, weights;

    public int[] pieces(int slot) {
        return switch (slot) {
            case 1 -> flowerIds;
            case 2 -> featherIds;
            case 3 -> timepieceIds;
            case 4 -> gobletIds;
            case 5 -> circletIds;
            default -> new int[0];
        };
    }

    @Override
    public void onLoad() {
        if (id <= 0
                || offerSetId <= 0
                || weights == null
                || weights.length == 0
                || Arrays.stream(weights).anyMatch(x -> x < 0)
                || Arrays.stream(weights).noneMatch(x -> x > 0))
            throw new IllegalArgumentException("Invalid artifact purchase weights: " + id);
        for (int slot = 1; slot <= 5; slot++) {
            var ids = pieces(slot);
            if (ids == null
                    || ids.length != weights.length
                    || Arrays.stream(ids).anyMatch(x -> x <= 0)
                    || Arrays.stream(ids).distinct().count() != ids.length)
                throw new IllegalArgumentException("Invalid artifact purchase templates: " + id);
        }
    }
}
