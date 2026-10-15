package emu.grasscutter.game.managers;

import ch.qos.logback.classic.Logger;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.data.GameData;
import emu.grasscutter.data.common.ItemParamData;
import emu.grasscutter.data.excels.*;
import emu.grasscutter.game.city.CityInfoData;
import emu.grasscutter.game.entity.EntityAvatar;
import emu.grasscutter.game.player.*;
import emu.grasscutter.game.props.*;
import emu.grasscutter.game.quest.enums.QuestContent;
import emu.grasscutter.net.proto.ChangeHpReasonOuterClass.ChangeHpReason;
import emu.grasscutter.net.proto.PropChangeReasonOuterClass.PropChangeReason;
import emu.grasscutter.server.event.player.PlayerLevelStatueEvent;
import emu.grasscutter.server.packet.send.*;

import java.util.*;

// Statue of the Seven Manager
public class SotSManager extends BasePlayerManager {

    // NOTE: Spring volume balance *1  = fight prop HP *100

    public static final int GlobalMaximumSpringVolume =
            PlayerProperty.PROP_MAX_SPRING_VOLUME.getMax();
    private final Logger logger = Grasscutter.getLogger();
    private final boolean enablePriorityHealing = false;
    private Timer autoRecoverTimer;
    private final Runnable persistOffer;
    private final java.util.function.Consumer<List<ItemParamData>> deliverOffer;
    private final java.util.function.Consumer<emu.grasscutter.game.inventory.GameItem>
            persistOfferCost;

    public SotSManager(Player player) {
        this(
                player,
                () -> emu.grasscutter.database.DatabaseHelper.saveGameSync(player),
                rewards ->
                        InvestigationRewardDelivery.grant(
                                player, rewards, ActionReason.CityLevelupReward));
    }

    public SotSManager(
            Player player,
            Runnable persistOffer,
            java.util.function.Consumer<List<ItemParamData>> deliverOffer) {
        this(
                player,
                persistOffer,
                deliverOffer,
                emu.grasscutter.database.DatabaseHelper::saveGameSync);
    }

    public SotSManager(
            Player player,
            Runnable persistOffer,
            java.util.function.Consumer<List<ItemParamData>> deliverOffer,
            java.util.function.Consumer<emu.grasscutter.game.inventory.GameItem> persistOfferCost) {
        super(player);
        this.persistOffer = Objects.requireNonNull(persistOffer);
        this.deliverOffer = Objects.requireNonNull(deliverOffer);
        this.persistOfferCost = Objects.requireNonNull(persistOfferCost);
    }

    public boolean getIsAutoRecoveryEnabled() {
        return player.getProperty(PlayerProperty.PROP_IS_SPRING_AUTO_USE) == 1;
    }

    public void setIsAutoRecoveryEnabled(boolean enabled) {
        player.setProperty(PlayerProperty.PROP_IS_SPRING_AUTO_USE, enabled ? 1 : 0);
        player.save();
    }

    public int getAutoRecoveryPercentage() {
        return player.getProperty(PlayerProperty.PROP_SPRING_AUTO_USE_PERCENT);
    }

    public void setAutoRecoveryPercentage(int percentage) {
        player.setProperty(PlayerProperty.PROP_SPRING_AUTO_USE_PERCENT, percentage);
        player.save();
    }

    public long getLastUsed() {
        return player.getSpringLastUsed();
    }

    public void setLastUsed() {
        player.setSpringLastUsed(System.currentTimeMillis() / 1000);
        player.save();
    }

    public int getMaxVolume() {
        return player.getProperty(PlayerProperty.PROP_MAX_SPRING_VOLUME);
    }

    public void setMaxVolume(int volume) {
        player.setProperty(PlayerProperty.PROP_MAX_SPRING_VOLUME, volume);
        player.save();
    }

    public int getCurrentVolume() {
        return player.getProperty(PlayerProperty.PROP_CUR_SPRING_VOLUME);
    }

    public void setCurrentVolume(int volume) {
        player.setProperty(PlayerProperty.PROP_CUR_SPRING_VOLUME, volume);
        setLastUsed();
        player.save();
    }

    public void handleEnterTransPointRegionNotify() {
        logger.trace("Player entered statue region");
        autoRevive();
        if (autoRecoverTimer == null) {
            autoRecoverTimer = new Timer();
            autoRecoverTimer.schedule(new AutoRecoverTimerTick(), 2500, 15000);
        }
    }

    public void handleExitTransPointRegionNotify() {
        logger.trace("Player left statue region");
        if (autoRecoverTimer != null) {
            autoRecoverTimer.cancel();
            autoRecoverTimer = null;
        }
    }

    // autoRevive automatically revives all team members.
    public void autoRevive() {
        player.getTeamManager()
                .getActiveTeam()
                .forEach(
                        entity -> {
                            boolean isAlive = entity.isAlive();
                            if (isAlive) {
                                return;
                            }
                            logger.trace(
                                    "Reviving avatar "
                                            + entity.getAvatar().getAvatarData().getName());
                            player.getTeamManager().reviveAvatar(entity.getAvatar());
                            player.getTeamManager().healAvatar(entity.getAvatar(), 30, 0);
                        });
    }

    public void checkAndHealAvatar(EntityAvatar entity) {
        int maxHP = (int) (entity.getFightProperty(FightProperty.FIGHT_PROP_MAX_HP) * 100);
        int currentHP = (int) (entity.getFightProperty(FightProperty.FIGHT_PROP_CUR_HP) * 100);
        if (currentHP == maxHP) {
            return;
        }
        int targetHP = maxHP * getAutoRecoveryPercentage() / 100;

        if (targetHP > currentHP) {
            int needHP = targetHP - currentHP;
            int currentVolume = getCurrentVolume();
            if (currentVolume >= needHP) {
                // sufficient
                setCurrentVolume(currentVolume - needHP);
            } else {
                // insufficient balance
                needHP = currentVolume;
                setCurrentVolume(0);
            }
            if (needHP > 0) {
                logger.trace(
                        "Healing avatar "
                                + entity.getAvatar().getAvatarData().getName()
                                + " +"
                                + needHP);
                player.getTeamManager().healAvatar(entity.getAvatar(), 0, needHP);
                player.getSession()
                        .send(
                                new PacketEntityFightPropChangeReasonNotify(
                                        entity,
                                        FightProperty.FIGHT_PROP_CUR_HP,
                                        ((float) needHP / 100),
                                        List.of(3),
                                        PropChangeReason
                                                .PropChangeReason_PROP_CHANGE_STATUE_RECOVER,
                                        ChangeHpReason.ChangeHpReason_CHANGE_HP_ADD_STATUE));
                player.getSession()
                        .send(
                                new PacketEntityFightPropUpdateNotify(
                                        entity, FightProperty.FIGHT_PROP_CUR_HP));
            }
        }
    }

    public void refillSpringVolume() {
        // Temporary: Max spring volume depends on level of the statues in Mondstadt and Liyue.
        // Override
        // until we have statue level.
        // TODO: remove
        // https://genshin-impact.fandom.com/wiki/Statue_of_The_Seven#:~:text=region%20of%20Inazuma.-,Statue%20Levels,-Upon%20first%20unlocking
        setMaxVolume(8500000);
        // Temporary: Auto enable 100% statue recovery until we can adjust statue settings in game
        // TODO: remove
        setAutoRecoveryPercentage(100);
        setIsAutoRecoveryEnabled(true);

        int maxVolume = getMaxVolume();
        int currentVolume = getCurrentVolume();
        if (currentVolume < maxVolume) {
            long now = System.currentTimeMillis() / 1000;
            int secondsSinceLastUsed = (int) (now - getLastUsed());
            // 15s = 1% max volume
            int volumeRefilled = secondsSinceLastUsed * maxVolume / 15 / 100;
            logger.trace("Statue has refilled HP volume: " + volumeRefilled);
            currentVolume = Math.min(currentVolume + volumeRefilled, maxVolume);
            logger.trace("Statue remaining HP volume: " + currentVolume);
            setCurrentVolume(currentVolume);
        }
    }

    private class AutoRecoverTimerTick extends TimerTask {
        // autoRecover checks player setting to see if auto recover is enabled, and refill HP to the
        // predefined level.
        public void run() {
            refillSpringVolume();

            logger.trace(
                    "isAutoRecoveryEnabled: "
                            + getIsAutoRecoveryEnabled()
                            + "\tautoRecoverPercentage: "
                            + getAutoRecoveryPercentage());

            if (getIsAutoRecoveryEnabled()) {
                List<EntityAvatar> activeTeam = player.getTeamManager().getActiveTeam();
                // When the statue does not have enough remaining volume:
                //      Enhanced experience: Enable priority healing
                //                              The current active character will get healed first,
                // then
                // sequential.
                //      Vanilla experience: Disable priority healing
                //                              Sequential healing based on character index.
                int priorityIndex =
                        enablePriorityHealing
                                ? player.getTeamManager().getCurrentCharacterIndex()
                                : -1;
                if (priorityIndex >= 0) {
                    checkAndHealAvatar(activeTeam.get(priorityIndex));
                }
                for (int i = 0; i < activeTeam.size(); i++) {
                    if (i != priorityIndex) {
                        checkAndHealAvatar(activeTeam.get(i));
                    }
                }
            }
        }
    }

    public CityData getCityByAreaId(int areaId) {
        return GameData.getCityDataMap().values().stream()
                .filter(
                        city ->
                                city != null
                                        && city.getAreaIdVec() != null
                                        && city.getAreaIdVec().contains(areaId))
                .findFirst()
                .orElse(null);
    }

    public CityInfoData getCityInfo(int cityId) {
        if (player.getCityInfoData() == null) player.setCityInfoData(new HashMap<>());
        var cityInfo = player.getCityInfoData().get(cityId);
        if (cityInfo == null) {
            cityInfo = new CityInfoData(cityId);
            player.getCityInfoData().put(cityId, cityInfo);
        }
        return cityInfo;
    }

    public void addCityInfo(CityInfoData cityInfoData) {
        if (player.getCityInfoData() == null) player.setCityInfoData(new HashMap<>());

        player.getCityInfoData().put(cityInfoData.getCityId(), cityInfoData);
    }

    /** A read-only scene snapshot includes resource cities without creating saved progress. */
    public List<emu.grasscutter.net.proto.CityInfoOuterClass.CityInfo> citySnapshot(int sceneId) {
        synchronized (player.getInventory()) {
            var offeredCities = new HashSet<Integer>();
            for (var row : GameData.getStatuePromoteDataMap().values())
                if (row != null && row.getLevel() > 0) offeredCities.add(row.getCityId());
            for (var row : GameData.getCityLevelupDataMap().values())
                if (row != null && row.getLevel() > 0) offeredCities.add(row.getCityId());
            var saved = player.getCityInfoData();
            var snapshot =
                    new TreeMap<Integer, emu.grasscutter.net.proto.CityInfoOuterClass.CityInfo>();
            for (var city : GameData.getCityDataMap().values()) {
                if (city == null || city.getCityId() <= 0) continue;
                int cityId = city.getCityId();
                var info = saved == null ? null : saved.get(cityId);
                if (info == null && !offeredCities.contains(cityId)) continue;
                int cityScene = citySceneId(cityId);
                if (cityScene != sceneId || sceneId <= 0) continue;
                snapshot.put(
                        cityId,
                        (info == null ? new emu.grasscutter.game.city.CityInfoData(cityId) : info)
                                .toProto());
            }
            return List.copyOf(snapshot.values());
        }
    }

    public int citySceneId(int cityId) {
        var city = GameData.getCityDataMap().get(cityId);
        if (city == null) return 0;
        int scene = city.getSceneId();
        var first = GameData.getCityLevelupData(cityId, 1);
        return scene == 0 && first != null ? first.getSceneId() : scene;
    }

    /** Administrative progress edit; it does not replay offering rewards or stamina changes. */
    public void setCityLevel(int cityId, int level) {
        synchronized (player.getInventory()) {
            var city = GameData.getCityDataMap().get(cityId);
            int sceneId = citySceneId(cityId);
            boolean configured =
                    level >= 1
                            && (GameData.getCityLevelupDataMap().values().stream()
                                            .anyMatch(
                                                    x ->
                                                            x != null
                                                                    && x.getCityId() == cityId
                                                                    && x.getLevel() > 0
                                                                    && (level == 1
                                                                            || x.getLevel()
                                                                                    == level))
                                    || GameData.getStatuePromoteDataMap().values().stream()
                                            .anyMatch(
                                                    x ->
                                                            x != null
                                                                    && x.getCityId() == cityId
                                                                    && x.getLevel() > 0
                                                                    && (level == 1
                                                                            || x.getLevel()
                                                                                    == level)));
            if (city == null || cityId <= 0 || sceneId <= 0 || !configured)
                throw new IllegalArgumentException(
                        "Unknown statue city or level: " + cityId + "/" + level);
            var original = player.getCityInfoData();
            var before = original == null ? null : original.get(cityId);
            if (before != null && before.getPendingOffer() != null)
                throw new IllegalStateException(
                        "Unresolved offering: " + before.getPendingOffer().getOperationId());
            var updated = new CityInfoData(cityId);
            updated.setLevel(level);
            var map = original == null ? new HashMap<Integer, CityInfoData>() : original;
            map.put(cityId, updated);
            player.setCityInfoData(map);
            try {
                persistOffer.run();
            } catch (RuntimeException error) {
                if (before == null) map.remove(cityId);
                else map.put(cityId, before);
                player.setCityInfoData(original);
                throw error;
            }
            logger.info(
                    "Administrative statue edit: UID {}, city {}, level {} -> {} (no rewards)",
                    player.getUid(),
                    cityId,
                    before == null ? 1 : before.getLevel(),
                    level);
            int areaId =
                    city.getAreaIdVec() == null || city.getAreaIdVec().isEmpty()
                            ? 0
                            : city.getAreaIdVec().get(0);
            player.sendPacket(new PacketLevelupCityRsp(sceneId, level, cityId, 0, areaId, 0));
            player.sendPacket(new PacketGetSceneAreaRsp(player, sceneId));
        }
    }

    public void levelUpSotS(int areaId, int sceneId, int itemNum) {
        var result =
                StatueOffering.offer(
                        this,
                        areaId,
                        sceneId,
                        itemNum,
                        persistOffer,
                        deliverOffer,
                        persistOfferCost);
        player.sendPacket(
                new PacketLevelupCityRsp(
                        sceneId,
                        result.level(),
                        result.cityId(),
                        result.crystals(),
                        areaId,
                        result.retcode()));
        if (result.retcode() != 0 || !result.leveled()) return;
        for (int force : result.forces()) player.getScene().unlockForce(force);
        player.getQuestManager()
                .queueEvent(QuestContent.QUEST_CONTENT_CITY_LEVEL_UP, result.cityId(), areaId);
        InvestigationHandbook.refreshAndNotify(player);
        var snapshot = new CityInfoData(result.cityId());
        snapshot.setLevel(result.level());
        snapshot.setNumCrystal(result.crystals());
        new PlayerLevelStatueEvent(this.getPlayer(), snapshot, sceneId, areaId).call();
    }
}
