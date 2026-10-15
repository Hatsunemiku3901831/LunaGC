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
    private boolean springHealing;
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
        synchronized (player) {
            if (player.setProperty(PlayerProperty.PROP_CUR_SPRING_VOLUME, volume)
                    && getCurrentVolume() == volume) {
                player.setSpringLastUsed(System.currentTimeMillis() / 1000);
                player.save();
            }
        }
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
        synchronized (player) {
            if (springHealing
                    || !getIsAutoRecoveryEnabled()
                    || entity == null
                    || entity.getPlayer() != player
                    || entity.getScene() != player.getScene()
                    || !player.getTeamManager().getActiveTeam().contains(entity)
                    || !entity.isAlive()) return;
            double max = entity.getFightProperty(FightProperty.FIGHT_PROP_MAX_HP);
            double before = entity.getFightProperty(FightProperty.FIGHT_PROP_CUR_HP);
            if (!Double.isFinite(max) || !Double.isFinite(before) || max <= 0 || before <= 0)
                return;
            int percent = Math.max(0, Math.min(100, getAutoRecoveryPercentage()));
            double needed = Math.max(0d, max * percent - before * 100d);
            int available = Math.max(0, Math.min(getMaxVolume(), getCurrentVolume()));
            int requested = (int) Math.min(available, Math.floor(needed));
            if (requested <= 0) return;
            springHealing = true;
            boolean reserved = false;
            int spent = 0;
            try {
                reserved =
                        player.setProperty(
                                        PlayerProperty.PROP_CUR_SPRING_VOLUME,
                                        available - requested)
                                && getCurrentVolume() == available - requested;
                if (!reserved) return;
                if (recoverAvatar(entity, requested)) {
                    double after = entity.getFightProperty(FightProperty.FIGHT_PROP_CUR_HP);
                    if (Double.isFinite(after))
                        spent =
                                (int)
                                        Math.min(
                                                requested,
                                                Math.max(0L, Math.round((after - before) * 100d)));
                }
            } catch (RuntimeException error) {
                double after = entity.getFightProperty(FightProperty.FIGHT_PROP_CUR_HP);
                if (reserved && Double.isFinite(after))
                    spent =
                            (int)
                                    Math.min(
                                            requested,
                                            Math.max(0L, Math.round((after - before) * 100d)));
                logger.error(
                        "Statue healing failed for UID {}, entity {}",
                        player.getUid(),
                        entity.getId(),
                        error);
            } finally {
                try {
                    if (reserved && spent < requested) {
                        int refunded =
                                (int)
                                        Math.min(
                                                Math.max(0, getMaxVolume()),
                                                (long) getCurrentVolume() + requested - spent);
                        if (!player.setProperty(PlayerProperty.PROP_CUR_SPRING_VOLUME, refunded)
                                || getCurrentVolume() != refunded)
                            logger.error(
                                    "Statue healing refund was not confirmed: UID {}, entity {}",
                                    player.getUid(),
                                    entity.getId());
                    }
                    if (spent > 0) {
                        player.setSpringLastUsed(System.currentTimeMillis() / 1000);
                        player.save();
                    }
                } finally {
                    springHealing = false;
                }
            }
            if (spent > 0) {
                player.sendPacket(
                        new PacketEntityFightPropChangeReasonNotify(
                                entity,
                                FightProperty.FIGHT_PROP_CUR_HP,
                                (float) spent / 100,
                                List.of(3),
                                PropChangeReason.PropChangeReason_PROP_CHANGE_STATUE_RECOVER,
                                ChangeHpReason.ChangeHpReason_CHANGE_HP_ADD_STATUE));
                player.sendPacket(
                        new PacketEntityFightPropUpdateNotify(
                                entity, FightProperty.FIGHT_PROP_CUR_HP));
            }
        }
    }

    protected boolean recoverAvatar(EntityAvatar entity, int amount) {
        return player.getTeamManager().healAvatar(entity.getAvatar(), 0, amount);
    }

    public void refillSpringVolume() {
        synchronized (player) {
            if (springHealing) return;
            long now = System.currentTimeMillis() / 1000;
            if (now <= 0) return;
            int maxVolume = getMaxVolume();
            if (maxVolume <= 0 || maxVolume > GlobalMaximumSpringVolume) {
                maxVolume = GlobalMaximumSpringVolume;
                setMaxVolume(maxVolume);
                if (getMaxVolume() != maxVolume) return;
            }
            int currentVolume = Math.max(0, Math.min(maxVolume, getCurrentVolume()));
            long last = getLastUsed();
            if (last <= 0) {
                setCurrentVolume(maxVolume);
                return;
            }
            if (now < last || currentVolume == maxVolume) return;
            long ticks = (now - last) / 15;
            if (ticks == 0) return;
            int updated =
                    (int)
                            Math.min(
                                    maxVolume,
                                    currentVolume + Math.min(100L, ticks) * maxVolume / 100);
            if (updated == currentVolume) return;
            if (player.setProperty(PlayerProperty.PROP_CUR_SPRING_VOLUME, updated)
                    && getCurrentVolume() == updated) {
                player.setSpringLastUsed(now - (now - last) % 15);
                player.save();
            }
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
