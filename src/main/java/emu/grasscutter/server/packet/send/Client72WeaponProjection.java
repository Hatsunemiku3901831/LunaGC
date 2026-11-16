package emu.grasscutter.server.packet.send;

import emu.grasscutter.game.inventory.GameItem;
import emu.grasscutter.net.proto.Client72InventoryWireOuterClass.FNECPDFLEHN;

/** Proven weapon fields only; the map uses saved affix groups and zero-based saved levels. */
public final class Client72WeaponProjection {
    private Client72WeaponProjection() {}

    public static FNECPDFLEHN project(GameItem item) {
        var weapon = FNECPDFLEHN.newBuilder()
                .setNJAGFJINHMA(item.getLevel())
                .setNILMBAGDEAL(item.getExp())
                .setKFDCBKBPPGL(item.getPromoteLevel());
        for (int group : item.getAffixes()) {
            if (group > 0) weapon.putJMGKMMFNABP(group, item.getRefinement());
        }
        return weapon.build();
    }
}
