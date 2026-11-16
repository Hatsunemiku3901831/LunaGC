package emu.grasscutter.server.packet.send;

import static org.junit.jupiter.api.Assertions.*;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.game.inventory.GameItem;
import emu.grasscutter.data.excels.ItemData;
import emu.grasscutter.utils.JsonUtils;
import com.google.gson.JsonParser;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class Client72WeaponProjectionTest {
    @ParameterizedTest
    @CsvSource({"0,00", "3,03", "4,04"})
    void savedZeroBasedRefinementIsTheUnmodifiedGroupLevel(long refinement, String value) {
        Grasscutter.getConfig();
        var item = weapon((int) refinement, 321);
        item.setLevel(90); item.setExp(4951); item.setPromoteLevel(6);
        var weapon = Client72WeaponProjection.project(item);
        assertEquals(Map.of(321, (int) refinement), weapon.getJMGKMMFNABPMap());
        assertArrayEquals(HexFormat.of().parseHex("085a10d7261806220508c10210" + value), weapon.toByteArray());
        assertEquals(refinement, item.getRefinement());
        assertEquals(List.of(321), item.getAffixes());
        assertFalse(weapon.hasMNCFEDPDEAG());
        assertEquals(0, weapon.getFLGGIMBHFOOCount());
    }

    @Test
    void twoSavedGroupsAreNotConvertedIntoFlatResourceLookupIds() {
        Grasscutter.getConfig();
        var item = weapon(4, 321, 654);
        var weapon = Client72WeaponProjection.project(item);
        assertEquals(Map.of(321, 4, 654, 4), weapon.getJMGKMMFNABPMap());
        assertArrayEquals(HexFormat.of().parseHex("080110001800220508c10210042205088e051004"), weapon.toByteArray());
    }

    @Test
    void emptyAffixesAndOneExplicitZeroLevelHaveDifferentWire() {
        Grasscutter.getConfig();
        var empty = weapon(0);
        var zero = weapon(0, 321);
        assertTrue(Client72WeaponProjection.project(empty).getJMGKMMFNABPMap().isEmpty());
        assertEquals(Map.of(321, 0), Client72WeaponProjection.project(zero).getJMGKMMFNABPMap());
        assertArrayEquals(HexFormat.of().parseHex("080110001800"), Client72WeaponProjection.project(empty).toByteArray());
        assertArrayEquals(HexFormat.of().parseHex("080110001800220508c1021000"), Client72WeaponProjection.project(zero).toByteArray());
    }

    private static GameItem weapon(int refinement, int... groups) {
        var data = JsonUtils.decode(JsonParser.parseString("{\"id\":11101,\"itemType\":\"ITEM_WEAPON\",\"skillAffix\":"
                + Arrays.toString(groups) + "}"), ItemData.class);
        var item = new GameItem(data);
        item.setRefinement(refinement);
        return item;
    }
}
