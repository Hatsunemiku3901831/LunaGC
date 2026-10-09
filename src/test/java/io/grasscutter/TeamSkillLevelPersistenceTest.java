package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import com.mongodb.client.MongoClients;

import dev.morphia.Morphia;
import dev.morphia.mapping.MapperOptions;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.game.avatar.Avatar;

import org.junit.jupiter.api.*;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

class TeamSkillLevelPersistenceTest {
    @Test
    void temporaryPartyBonusDoesNotSurviveMongoStorage() throws Exception {
        Grasscutter.getLogger();
        String uri = System.getenv("LUNAGC_TEST_MONGO_URI");
        Assumptions.assumeTrue(uri != null && !uri.isBlank());
        String database =
                "lunagc_team_skill_levels_" + UUID.randomUUID().toString().replace("-", "");
        try (var client = MongoClients.create(uri)) {
            try {
                var datastore =
                        Morphia.createDatastore(
                                client,
                                database,
                                MapperOptions.builder()
                                        .storeEmpties(true)
                                        .storeNulls(false)
                                        .build());
                datastore.getMapper().map(Avatar.class);
                var avatar = new Avatar();
                var levels = Avatar.class.getDeclaredField("skillLevelMap");
                levels.setAccessible(true);
                levels.set(avatar, new HashMap<>(Map.of(10731, 4)));
                assertTrue(avatar.setTeamSkillLevelBonuses(Map.of(7331, 1)));
                datastore.save(avatar);
                var raw = client.getDatabase(database).getCollection("avatars").find().first();
                assertNotNull(raw);
                assertFalse(raw.containsKey("teamSkillLevelBonuses"));
                assertEquals(
                        4, raw.get("skillLevelMap", org.bson.Document.class).getInteger("10731"));
                var loaded = datastore.find(Avatar.class).first();
                assertNotNull(loaded);
                assertTrue(
                        loaded.setTeamSkillLevelBonuses(Map.of(7331, 1)),
                        "Reconnect starts without a stored party contribution");
            } finally {
                client.getDatabase(database).drop();
            }
        }
    }
}
