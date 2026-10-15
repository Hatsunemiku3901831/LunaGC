package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import emu.grasscutter.data.GameData;
import emu.grasscutter.data.excels.CityData;
import emu.grasscutter.data.excels.CityLevelupData;
import emu.grasscutter.game.city.CityInfoData;
import emu.grasscutter.net.proto.GetSceneAreaRspOuterClass.GetSceneAreaRsp;
import emu.grasscutter.server.packet.send.PacketGetSceneAreaRsp;
import emu.grasscutter.utils.JsonUtils;

import org.junit.jupiter.api.*;

import java.util.*;

class StatueSceneSnapshotTest {
    StatueOfferingTest env;

    @BeforeAll
    static void resources() throws Exception {
        StatueOfferingTest.resources();
    }

    @BeforeEach
    void setup() throws Exception {
        env = new StatueOfferingTest();
        env.setup();
        env.statue();
    }

    @AfterEach
    void close() throws Exception {
        env.restoreStatueData();
        env.cleanup();
    }

    GetSceneAreaRsp snapshot(int scene) throws Exception {
        return GetSceneAreaRsp.parseFrom(new PacketGetSceneAreaRsp(env.player, scene).getData());
    }

    List<Integer> cities(GetSceneAreaRsp snapshot) {
        return snapshot.getCityInfoListList().stream().map(x -> x.getCityId()).toList();
    }

    @Test
    void actualCityTablesIncludeAllEightStatueRegions() throws Exception {
        assertEquals(List.of(1, 2, 3, 4, 5, 6, 7, 8), cities(snapshot(3)));
    }

    @Test
    void laterRegionLevelAndPartialOfferingAreNotLostInSnapshot() throws Exception {
        var info = env.manager.getCityInfo(7);
        info.setLevel(6);
        info.setNumCrystal(17);
        var wire =
                snapshot(3).getCityInfoListList().stream()
                        .filter(x -> x.getCityId() == 7)
                        .findFirst()
                        .orElseThrow();
        assertEquals(6, wire.getLevel());
        assertEquals(17, wire.getCrystalNum());
    }

    @Test
    void unrelatedSceneDoesNotReceiveTeyvatCities() throws Exception {
        assertTrue(snapshot(401).getCityInfoListList().isEmpty());
    }

    @Test
    void readingSnapshotDoesNotCreateSavedCityProgress() throws Exception {
        env.player.setCityInfoData(new HashMap<>());
        assertEquals(8, snapshot(3).getCityInfoListCount());
        assertTrue(env.player.getCityInfoData().isEmpty());
        assertEquals(0, env.saves.get());
    }

    void configuredCity(int id, int scene) {
        var city = new CityData();
        city.setCityId(id);
        city.setSceneId(scene);
        city.setAreaIdVec(List.of(9000 + id));
        env.put(GameData.getCityDataMap(), id, city);
    }

    @Test
    void addedResourceCityIsIncludedWithoutAHardcodedIdLimit() throws Exception {
        configuredCity(12, 3);
        var level = JsonUtils.decode("{cityId:12,sceneId:3,level:1}", CityLevelupData.class);
        env.put(GameData.getCityLevelupDataMap(), level.getId(), level);
        assertEquals(List.of(1, 2, 3, 4, 5, 6, 7, 8, 12), cities(snapshot(3)));
    }

    @Test
    void savedCustomCityInThisSceneIsPreserved() throws Exception {
        configuredCity(50, 3);
        var info = new CityInfoData(50);
        info.setLevel(4);
        env.manager.addCityInfo(info);
        var wire =
                snapshot(3).getCityInfoListList().stream()
                        .filter(x -> x.getCityId() == 50)
                        .findFirst()
                        .orElseThrow();
        assertEquals(4, wire.getLevel());
    }

    @Test
    void cityDefinitionFromAnotherSceneIsNotSentEvenIfSaved() throws Exception {
        configuredCity(50, 401);
        env.manager.addCityInfo(new CityInfoData(50));
        assertFalse(cities(snapshot(3)).contains(50));
        assertEquals(List.of(50), cities(snapshot(401)));
    }

    @Test
    void queryRetainsThePlayersActualAreaUnlocks() throws Exception {
        env.player.getUnlockedSceneAreas(3).clear();
        env.player.getUnlockedSceneAreas(3).add(33);
        assertEquals(List.of(33), snapshot(3).getAreaIdListList());
        assertEquals(Set.of(33), env.player.getUnlockedSceneAreas(3));
    }
}
