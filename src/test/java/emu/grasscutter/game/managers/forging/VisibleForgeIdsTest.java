package emu.grasscutter.game.managers.forging;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import emu.grasscutter.data.excels.ForgeData;
import java.lang.reflect.Field;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Login replaces the client's forge-recipe set with this list. */
public final class VisibleForgeIdsTest {

    @Test
    public void adventureRankTenSeesDefaultOreAndKeepsUnlockedDiagrams() throws Exception {
        // 11001/11006 are default-show ore at AR 2 and 5. 12001 is default-show at AR 30.
        // 13011 is a diagram and stays hidden until the player unlocks it.
        var recipes =
                List.of(
                        recipe(11001, 2, true),
                        recipe(11006, 5, true),
                        recipe(12001, 30, true),
                        recipe(13011, 15, false));

        var visible = ForgingManager.visibleForgeIds(List.of(13011), recipes, 10);

        assertTrue(visible.contains(11001));
        assertTrue(visible.contains(11006));
        assertTrue(visible.contains(13011));
        assertFalse(visible.contains(12001));
        assertEquals(3, visible.size());
    }

    @Test
    public void emptyUnlockStillShowsRecipesTheRankHasReached() throws Exception {
        var visible =
                ForgingManager.visibleForgeIds(
                        List.of(), List.of(recipe(11001, 2, true), recipe(11002, 5, true)), 10);

        assertEquals(List.of(11001, 11002), visible);
    }

    @Test
    public void aZeroIdIsDropped() throws Exception {
        var visible = ForgingManager.visibleForgeIds(List.of(0, 11001), List.of(), 10);

        assertEquals(List.of(11001), visible);
    }

    private static ForgeData recipe(int id, int playerLevel, boolean defaultShow) throws Exception {
        ForgeData data = new ForgeData();
        set(data, "id", id);
        set(data, "playerLevel", playerLevel);
        set(data, "isDefaultShow", defaultShow);
        return data;
    }

    private static void set(ForgeData data, String name, Object value) throws Exception {
        Field field = ForgeData.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(data, value);
    }
}
