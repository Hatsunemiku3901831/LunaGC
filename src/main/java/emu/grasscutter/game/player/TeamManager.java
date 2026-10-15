package emu.grasscutter.game.player;

import static emu.grasscutter.config.Configuration.GAME_OPTIONS;

import dev.morphia.annotations.*;
import emu.grasscutter.*;
import emu.grasscutter.data.GameData;
import emu.grasscutter.data.binout.config.ConfigLevelEntity;
import emu.grasscutter.data.binout.config.fields.ConfigAbilityData;
import emu.grasscutter.data.excels.avatar.AvatarSkillDepotData;
import emu.grasscutter.game.avatar.Avatar;
import emu.grasscutter.game.avatar.TeamSkillLevelBonuses;
import emu.grasscutter.game.entity.*;
import emu.grasscutter.game.props.*;
import emu.grasscutter.game.world.*;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.*;
import emu.grasscutter.net.proto.EnterTypeOuterClass.EnterType;
import emu.grasscutter.net.proto.MotionStateOuterClass.MotionState;
import emu.grasscutter.net.proto.PlayerDieTypeOuterClass.PlayerDieType;
import emu.grasscutter.net.proto.RetcodeOuterClass.Retcode;
import emu.grasscutter.net.proto.GrantReasonOuterClass.GrantReason;
import emu.grasscutter.server.event.entity.EntityCreationEvent;
import emu.grasscutter.server.event.player.*;
import emu.grasscutter.server.packet.send.*;
import emu.grasscutter.utils.Utils;
import it.unimi.dsi.fastutil.ints.*;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import java.util.*;
import java.util.function.BooleanSupplier;
import java.util.stream.Stream;
import lombok.*;

@Entity
public final class TeamManager extends BasePlayerDataManager {
    // Both from MoonPhaseConstValueExcelConfigData. Verdant Dew is "MoonOvergrow" in the configs.
    public static final int MOONSIGN_MAX_LEVEL = 2;
    public static final String VERDANT_DEW = "MoonOvergrowPoint_All";
    public static final int VERDANT_DEW_CAP = 3;

    @Transient private final List<EntityAvatar> avatars;
    @Transient @Getter private final Set<EntityBaseGadget> gadgets;
    @Transient @Getter private final IntSet teamResonances;
    @Transient @Getter private final IntSet teamResonancesConfig;
    @Transient @Getter @Setter private Set<String> teamAbilityEmbryos;
    @Transient private transient final TeamSkillLevelBonuses skillLevelBonuses = new TeamSkillLevelBonuses();

    @Getter private LinkedHashMap<Integer, TeamInfo> teams;
    private int currentTeamIndex;
    @Getter @Setter private int currentCharacterIndex;
    @Transient @Getter @Setter private TeamInfo mpTeam;
    @Transient @Getter @Setter private EntityTeam entity;

    @Transient private int useTemporarilyTeamIndex = -1;
    @Transient private List<TeamInfo> temporaryTeam;
    @Transient @Setter private boolean usingTrialTeam;
    @Transient @Getter @Setter private TeamInfo trialAvatarTeam;
    @Transient private boolean trialTeamIncludesOwnedAvatars;

    // Only grant IDs survive a reconnect; borrowed Avatar objects never become owned avatars.
    @Getter private Map<Integer, Integer> questTrialAvatarIds = new LinkedHashMap<>();

    @Transient @Getter @Setter private Map<Integer, Avatar> trialAvatars;

    // A scene party overlays the regular, temporary and quest parties without modifying them.
    @Transient @Getter private Avatar tpsSceneAvatar;
    @Transient private TeamInfo tpsSceneTeam;
    @Transient private Scene tpsScene;
    // Persist only the selected slot so a reconnect in the mode can still restore it on exit.
    private Integer tpsPreviousIndex;

    public boolean isUsingTrialTeam() {
        return usingTrialTeam || tpsSceneAvatar != null;
    }

    public Collection<Avatar> getAllTrialAvatars() {
        var result = new ArrayList<>(trialAvatars.values());
        if (tpsSceneAvatar != null) result.add(tpsSceneAvatar);
        return result;
    }

    public Avatar getAvatarForTeam(int avatarId) {
        if (tpsSceneAvatar != null && tpsSceneAvatar.getAvatarId() == avatarId) return tpsSceneAvatar;
        boolean temporary = temporaryTeam != null && useTemporarilyTeamIndex >= 0
                && useTemporarilyTeamIndex < temporaryTeam.size();
        var trial = usingTrialTeam && !temporary ? trialAvatars.get(avatarId) : null;
        return trial != null ? trial : player.getAvatars().getAvatarById(avatarId);
    }

    public void beginTpsSceneTeam(Scene scene, Avatar avatar) {
        if (tpsSceneAvatar != null) throw new IllegalStateException("TPS scene party already active");
        if (tpsPreviousIndex == null) tpsPreviousIndex = currentCharacterIndex;
        tpsScene = scene;
        tpsSceneAvatar = avatar;
        tpsSceneTeam = new TeamInfo();
        tpsSceneTeam.addAvatar(avatar);
        currentCharacterIndex = 0;
    }

    public boolean endTpsSceneTeam(Scene scene) {
        if (tpsSceneAvatar == null || tpsScene != scene) return false;
        var avatar = tpsSceneAvatar;
        avatar.getEquips().values().forEach(emu.grasscutter.game.tps.TpsWeaponSystem::removeWeaponEntity);
        tpsSceneAvatar = null;
        tpsSceneTeam = null;
        tpsScene = null;
        restoreTpsSelection();
        if (player.hasSentLoginPackets()) player.sendPacket(new PacketAvatarDelNotify(List.of(avatar.getGuid())));
        return true;
    }

    public void restoreTpsSelection() {
        if (tpsSceneAvatar != null || tpsPreviousIndex == null) return;
        currentCharacterIndex = Math.max(0, Math.min(tpsPreviousIndex, getCurrentTeamInfo().size() - 1));
        tpsPreviousIndex = null;
    }

    @Transient @Getter @Setter
    private int previousIndex = -1;

    public TeamManager() {
        this.mpTeam = new TeamInfo();
        this.avatars = Collections.synchronizedList(new ArrayList<>());
        this.gadgets = new HashSet<>();
        this.teamResonances = new IntOpenHashSet();
        this.teamResonancesConfig = new IntOpenHashSet();
        this.teamAbilityEmbryos = new HashSet<>();
        this.trialAvatars = new LinkedHashMap<>();
        this.trialAvatarTeam = new TeamInfo();
    }

    @Transient
    private transient emu.grasscutter.game.quest.QuestTeamWipe questTeamWipe;

    public synchronized emu.grasscutter.game.quest.QuestTeamWipe getQuestTeamWipe() {
        if (questTeamWipe == null)
            questTeamWipe = new emu.grasscutter.game.quest.QuestTeamWipe(this);
        return questTeamWipe;
    }

    public TeamManager(Player player) {
        this();
        this.setPlayer(player);

        this.teams = new LinkedHashMap<>();
        this.currentTeamIndex = 1;
        for (int i = 1; i <= GameConstants.DEFAULT_TEAMS; i++) {
            this.teams.put(i, new TeamInfo());
        }
    }

    public AbilityControlBlockOuterClass.AbilityControlBlock getAbilityControlBlock() {
        return player.getWidgets().extend(getBaseAbilityControlBlock());
    }

    public AbilityControlBlockOuterClass.AbilityControlBlock getBaseAbilityControlBlock() {
        AbilityControlBlockOuterClass.AbilityControlBlock.Builder abilityControlBlock =
            AbilityControlBlockOuterClass.AbilityControlBlock.newBuilder();
        int embryoId = 0;

        if (GameConstants.defaultTeamAbilityStrings().length > 0) {
            List<String> teamAbilties =
                Arrays.stream(GameConstants.defaultTeamAbilityStrings()).toList();
            for (String skill : teamAbilties) {
                AbilityEmbryoOuterClass.AbilityEmbryo emb =
                    AbilityEmbryoOuterClass.AbilityEmbryo.newBuilder()
                        .setAbilityId(++embryoId)
                        .setAbilityNameHash(Utils.abilityHash(skill))
                        .setAbilityOverrideNameHash(GameConstants.DEFAULT_ABILITY_NAME)
                        .build();
                abilityControlBlock.addAbilityEmbryoList(emb);
            }
        }

        var scene = player.getScene();
        if (scene != null) {
            String configName = scene.getSceneData().getLevelEntityConfig();
            if (configName != null && !configName.isEmpty()) {
                ConfigLevelEntity levelConfig = GameData.getConfigLevelEntityDataMap().get(configName);
                if (levelConfig != null && levelConfig.getTeamAbilities() != null) {
                    for (ConfigAbilityData ability : levelConfig.getTeamAbilities()) {
                        String skill = ability.getAbilityName();
                        if (skill == null || skill.isEmpty()) {
                            Grasscutter.getLogger().warn("{} 中存在空能力名称", configName);
                            continue;
                        }
                        AbilityEmbryoOuterClass.AbilityEmbryo emb =
                            AbilityEmbryoOuterClass.AbilityEmbryo.newBuilder()
                                .setAbilityId(++embryoId)
                                .setAbilityNameHash(Utils.abilityHash(skill))
                                .setAbilityOverrideNameHash(GameConstants.DEFAULT_ABILITY_NAME)
                                .build();
                        abilityControlBlock.addAbilityEmbryoList(emb);
                    }
                }
            }
        }

        return abilityControlBlock.build();
    }

    public World getWorld() {
        return this.getPlayer().getWorld();
    }

    public int getTeamId(TeamInfo team) {
        for (int i = 1; i <= this.teams.size(); i++) {
            if (this.teams.get(i).equals(team)) {
                return i;
            }
        }
        return -1;
    }

    public int getCurrentTeamId() {

        return currentTeamIndex;
    }

    private void setCurrentTeamId(int currentTeamIndex) {
        this.currentTeamIndex = currentTeamIndex;
    }

    public long getCurrentCharacterGuid() {
        var currentAvatarEntity = this.getCurrentAvatarEntity();
        if (currentAvatarEntity == null){

            Avatar mainCharacter = new Avatar(this.getPlayer().getMainCharacterId());
            return mainCharacter.getGuid();
        }

        return currentAvatarEntity.getAvatar().getGuid();
    }

    public TeamInfo getCurrentTeamInfo() {
        if (tpsSceneTeam != null) return tpsSceneTeam;
        if (temporaryTeam != null && useTemporarilyTeamIndex >= 0
                && useTemporarilyTeamIndex < temporaryTeam.size())
            return temporaryTeam.get(useTemporarilyTeamIndex);
        if (this.usingTrialTeam) return this.getTrialAvatarTeam();
        return this.getRegularTeamInfo();
    }

    private TeamInfo getRegularTeamInfo() {
        // The index and the list are both transient and set by different packets - the abyss picks
        // teams in TowerTeamSelectReq and uses one in TowerEnterLevelReq - so a client that enters
        // without selecting first, or that restarts a floor, can leave the index set with no list
        // behind it. Falling through to the normal team is the right answer there; dereferencing is
        // not.
        if (temporaryTeam != null
                && useTemporarilyTeamIndex >= 0
                && useTemporarilyTeamIndex < temporaryTeam.size()) {
            return temporaryTeam.get(useTemporarilyTeamIndex);
        }
        if (this.getPlayer().isInMultiplayer()) {
            return this.getMpTeam();
        }
        return this.getTeams().get(this.currentTeamIndex);
    }

    public TeamInfo getCurrentSinglePlayerTeamInfo() {
        return this.getTeams().get(this.currentTeamIndex);
    }

    public List<EntityAvatar> getActiveTeam() {
        return avatars;
    }

    /** Copy under the list's own monitor; scene ticks must not acquire the team manager monitor. */
    public List<EntityAvatar> getActiveTeamSnapshot() {
        synchronized (avatars) {
            return List.copyOf(avatars);
        }
    }

    public List<EntityAvatar> getActiveTeam(boolean fix) {
        if (!fix) return this.getActiveTeam();

        var avatars = this.getActiveTeam();
        var avatarIds = new HashSet<Long>();
        for (var entityAvatar : new ArrayList<>(avatars)) {
            if (avatarIds.contains(entityAvatar.getAvatar().getGuid())) {
                avatars.remove(entityAvatar);
            } else {
                avatarIds.add(entityAvatar.getAvatar().getGuid());
            }
        }

        return avatars;
    }

    public EntityAvatar getCurrentAvatarEntity() {

        if (this.getActiveTeam().isEmpty()) {

            this.currentCharacterIndex = 0;
            Avatar mainCharacter = new Avatar(this.player.getMainCharacterId());
            this.avatars.add(mainCharacter.getAsEntity());
            return mainCharacter.getAsEntity();
        }

        if (this.currentCharacterIndex >= this.getActiveTeam().size()) {
            this.currentCharacterIndex = 0;
        }

        EntityAvatar currentCharacter;

        try {
            currentCharacter = this.getActiveTeam().get(this.currentCharacterIndex);
        } catch (Exception e) {
            currentCharacter = this.getActiveTeam().get(0);
        }

        return currentCharacter;
    }

    public boolean isSpawned() {
        return this.getPlayer().getScene() != null
            && this.getPlayer()
            .getScene()
            .getEntities()
            .containsKey(this.getCurrentAvatarEntity().getId());
    }

    public int getMaxTeamSize() {
        if (this.getPlayer().isInMultiplayer()) {
            int max = GAME_OPTIONS.avatarLimits.multiplayerTeam;
            if (this.getPlayer().getWorld().getHost() == this.getPlayer()) {
                return Math.max(1, (int) Math.ceil(max / (double) this.getWorld().getPlayerCount()));
            }
            return Math.max(1, (int) Math.floor(max / (double) this.getWorld().getPlayerCount()));
        }

        return GAME_OPTIONS.avatarLimits.singlePlayerTeam;
    }

    public boolean canAddAvatarsToTeam(TeamInfo team, int avatars) {
        return team.size() + avatars <= this.getMaxTeamSize();
    }

    public boolean canAddAvatarToTeam(TeamInfo team) {
        return this.canAddAvatarsToTeam(team, 1);
    }

    public boolean canAddAvatarsToCurrentTeam(int avatars) {
        if (this.useTemporarilyTeamIndex != -1 || tpsSceneAvatar != null) {
            return false;
        }
        return this.canAddAvatarsToTeam(this.getCurrentTeamInfo(), avatars);
    }

    public boolean canAddAvatarToCurrentTeam() {
        return this.canAddAvatarsToCurrentTeam(1);
    }

    public boolean addAvatarsToTeam(TeamInfo team, Collection<Avatar> avatars) {
        if (!this.canAddAvatarsToTeam(team, avatars.size())) {
            return false;
        }

        team.getAvatars().addAll(avatars.stream().map(a -> a.getAvatarId()).toList());

        if (this.getPlayer().isInMultiplayer()) {
            if (team.equals(this.getMpTeam())) {

                this.updateTeamEntities(new PacketChangeMpTeamAvatarRsp(this.getPlayer(), team));
            }
        } else {

            this.getPlayer().sendPacket(new PacketAvatarTeamUpdateNotify(this.getPlayer()));

            int teamId = this.getTeamId(team);
            if (teamId != -1) {

                if (teamId == this.getCurrentTeamId()) {
                    this.updateTeamEntities(new PacketSetUpAvatarTeamRsp(this.getPlayer(), teamId, team));
                } else {
                    this.getPlayer().sendPacket(new PacketSetUpAvatarTeamRsp(this.getPlayer(), teamId, team));
                }
            }
        }

        return true;
    }

    public boolean addAvatarToTeam(TeamInfo team, Avatar avatar) {
        return this.addAvatarsToTeam(team, Collections.singleton(avatar));
    }

    public boolean addAvatarsToCurrentTeam(Collection<Avatar> avatars) {
        if (this.useTemporarilyTeamIndex != -1 || tpsSceneAvatar != null) {
            return false;
        }
        return this.addAvatarsToTeam(this.getCurrentTeamInfo(), avatars);
    }

    public boolean addAvatarToCurrentTeam(Avatar avatar) {
        return this.addAvatarsToCurrentTeam(Collections.singleton(avatar));
    }

    private void updateTeamResonances() {
        this.getTeamResonances().clear();
        this.getTeamResonancesConfig().clear();

        for (var data : emu.grasscutter.game.avatar.TeamResonances.active(player)) {
            this.teamResonances.add(data.getId());
            var config = GameData.getOpenConfigEntries().get(data.getOpenConfig());
            if (config != null && config.getAddAbilities() != null)
                for (var name : config.getAddAbilities())
                    this.teamResonancesConfig.add(Utils.abilityHash(name));
        }
    }

    /** Nod-Krai characters in the party, capped: 1 is Nascent Gleam, 2 Ascendant Gleam. */
    public int getMoonsignLevel() {
        long count = this.getActiveTeam().stream()
            .filter(e -> PacketPlayerEnterSceneInfoNotify.getMoonphaseIds().contains(e.getAvatar().getAvatarId()))
            .count();

        return (int) Math.min(count, MOONSIGN_MAX_LEVEL);
    }

    /** Seeds the dew only; from the first Lunar-Bloom onwards the client's own count wins. */
    public void sendMoonsignState() {
        int level = this.getMoonsignLevel();
        int teamEntityId = this.getEntity().getId();

        this.getPlayer().sendPacket(new PacketServerGlobalValueChangeNotify(
            teamEntityId, "SGV_MoonPhaseLevel", (float) level));

        if (level > 0) {
            this.getEntity().getGlobalAbilityValues().put(VERDANT_DEW, (float) VERDANT_DEW_CAP);
            this.getPlayer().sendPacket(new PacketServerGlobalValueChangeNotify(
                teamEntityId, VERDANT_DEW, (float) VERDANT_DEW_CAP));
        }

        this.getPlayer().sendPacket(new PacketTeamMoonPhaseChangeNotify(level));
    }

    public void updateTeamProperties() {
        var skillChanges = this.prepareTeamSkillLevelBonuses();
        var resonanceChanges = this.prepareTeamResonances(true);
        this.getWorld()
            .broadcastPacket(
                new PacketSceneTeamUpdateNotify(
                    this.getPlayer()));

        skillChanges.forEach(change -> change.finish(true));
        resonanceChanges.forEach(Runnable::run);

        this.getActiveTeam().stream()
            .map(EntityAvatar::getAvatar)
            .forEach(Avatar::sendSkillExtraChargeMap);

        this.sendMoonsignState();

        long hexenzirkelCount = this.getActiveTeam().stream()
            .filter(e -> PacketPlayerEnterSceneInfoNotify.getHexenzirkelIds().contains(e.getAvatar().getAvatarId()))
            .count();
        this.getPlayer().sendPacket(new PacketServerGlobalValueChangeNotify(
            this.getEntity().getId(), "SGV_HexenzirkelLevel", (float) hexenzirkelCount));
        this.getPlayer().sendPacket(new PacketTeamHexenzirkelChangeNotify((int) hexenzirkelCount));
    }

    private List<TeamSkillLevelBonuses.Change> prepareTeamSkillLevelBonuses() {
        return skillLevelBonuses.prepare(player, this::getSelectedPartySnapshot);
    }

    public List<Avatar> getSelectedPartySnapshot() {
        var selected = new ArrayList<Avatar>();
        var team = getCurrentTeamInfo();
        if (team != null) {
            var active = getActiveTeamSnapshot();
            for (var id : new ArrayList<>(team.getAvatars())) {
                var avatar = id == null ? null : getAvatarForTeam(id);
                if (avatar == null && id != null)
                    avatar = active.stream().map(EntityAvatar::getAvatar)
                            .filter(a -> a.getAvatarId() == id && a.getPlayer() == player).findFirst().orElse(null);
                selected.add(avatar);
            }
        }
        return selected;
    }

    private List<Runnable> prepareTeamResonances(boolean notify) {
        var changes = new ArrayList<Runnable>();
        for (var member : emu.grasscutter.game.avatar.TeamResonances.participants(player)) {
            var manager = member.getTeamManager();
            var previous = new IntOpenHashSet(manager.getTeamResonancesConfig());
            manager.updateTeamResonances();
            boolean layoutChanged = !previous.equals(manager.getTeamResonancesConfig());
            changes.add(() -> member.getAbilityManager().refreshTeamResonanceAbilities(previous.size(), layoutChanged, notify));
        }
        return changes;
    }

    public void refreshTeamResonances(boolean notify) {
        var changes = prepareTeamResonances(notify);
        if (notify && player.hasSentLoginPackets() && getWorld() != null)
            getWorld().broadcastPacket(new PacketSceneTeamUpdateNotify(player));
        changes.forEach(Runnable::run);
    }

    public void refreshTeamSkillLevelBonuses(boolean notify) {
        this.prepareTeamSkillLevelBonuses().forEach(change -> change.finish(notify));
    }

    public void updateTeamEntities(BasePacket responsePacket) {

        if (this.getCurrentTeamInfo().getAvatars().size() <= 0) {
            return;
        }

        var currentEntity = this.getCurrentAvatarEntity();
        var existingAvatars = new Int2ObjectOpenHashMap<EntityAvatar>();
        var prevSelectedAvatarIndex = -1;

        for (EntityAvatar entity : this.getActiveTeam()) {
            existingAvatars.put(entity.getAvatar().getAvatarId(), entity);
        }

        this.getActiveTeam().clear();

        for (int i = 0; i < this.getCurrentTeamInfo().getAvatars().size(); i++) {
            var avatarId = (int) this.getCurrentTeamInfo().getAvatars().get(i);
            var avatar = this.getAvatarForTeam(avatarId);
            if (avatar == null) continue;
            EntityAvatar entity;
            if (existingAvatars.containsKey(avatarId) && existingAvatars.get(avatarId).getAvatar() == avatar) {
                entity = existingAvatars.get(avatarId);
                existingAvatars.remove(avatarId);
                if (entity == currentEntity) {
                    prevSelectedAvatarIndex = i;
                }
            } else {
                var player = this.getPlayer();
                entity =
                    EntityCreationEvent.call(
                        EntityAvatar.class,
                        new Class<?>[] {Scene.class, Avatar.class},
                        new Object[] {player.getScene(), avatar});
            }

            this.getActiveTeam().add(entity);
        }

        for (var entity : existingAvatars.values()) {
            if (entity == currentEntity) {
                entity.getAvatar().save();
                continue;
            }
            this.getPlayer().getScene().removeEntity(entity);
            entity.getAvatar().save();
        }

        if (prevSelectedAvatarIndex == -1) {

            prevSelectedAvatarIndex =
                Math.min(this.currentCharacterIndex, this.getActiveTeam().size() - 1);
        }
        this.currentCharacterIndex = prevSelectedAvatarIndex;

        this.updateTeamProperties();

        if (responsePacket != null) {
            this.getPlayer().sendPacket(responsePacket);
        }

        checkCurrentAvatarIsAlive(currentEntity);
    }

    public void checkCurrentAvatarIsAlive(EntityAvatar currentEntity) {
        if (currentEntity == null) {
            currentEntity = this.getCurrentAvatarEntity();
        }

        if (!this.getActiveTeam().get(this.currentCharacterIndex).isAlive()) {

            int replaceIndex = getDeadAvatarReplacement();
            if (0 <= replaceIndex && replaceIndex < this.getActiveTeam().size()) {
                this.currentCharacterIndex = replaceIndex;
            } else {

                this.currentCharacterIndex = 0;
                this.reviveAvatar(this.getCurrentAvatarEntity().getAvatar());
            }
        }

        var newAvatarEntity = this.getCurrentAvatarEntity();
        if (currentEntity != null && newAvatarEntity != null && currentEntity != newAvatarEntity) {

            var event =
                new PlayerSwitchAvatarEvent(
                    this.getPlayer(), currentEntity.getAvatar(), newAvatarEntity.getAvatar());
            if (!event.call()) return;

            this.getPlayer().getScene().replaceEntity(currentEntity, newAvatarEntity);
        }
    }

    public synchronized void setupAvatarTeam(int teamId, List<Long> list) {
        if (tpsSceneAvatar != null) return;

        if (list.isEmpty()
            || list.size() > this.getMaxTeamSize()
            || this.getPlayer().isInMultiplayer()) {
            return;
        }

        List<Integer> team = new ArrayList<>();
        team.add(teamId);

        TeamInfo teamInfo = this.getTeams().get(team.size());
        if (teamInfo == null) {
            return;
        }

        LinkedHashSet<Avatar> newTeam = new LinkedHashSet<>();
        for (Long aLong : list) {
            Avatar avatar = this.getPlayer().getAvatars().getAvatarByGuid(aLong);
            if (avatar == null || newTeam.contains(avatar)) {

                return;
            }
            newTeam.add(avatar);
        }

        teamInfo.getAvatars().clear();
        this.addAvatarsToTeam(teamInfo, newTeam);
    }

    public void setupMpTeam(List<Long> list) {
        if (tpsSceneAvatar != null) return;

        if (list.size() == 0
            || list.size() > this.getMaxTeamSize()
            || !this.getPlayer().isInMultiplayer()) {
            return;
        }

        TeamInfo teamInfo = this.getMpTeam();

        LinkedHashSet<Avatar> newTeam = new LinkedHashSet<>();
        for (Long aLong : list) {
            Avatar avatar = this.getPlayer().getAvatars().getAvatarByGuid(aLong);
            if (avatar == null || newTeam.contains(avatar)) {

                return;
            }
            newTeam.add(avatar);
        }

        teamInfo.getAvatars().clear();
        this.addAvatarsToTeam(teamInfo, newTeam);
    }

    public void setupTrialAvatars(boolean save) {
        if (this.usingTrialTeam) return;
        this.setPreviousIndex(tpsSceneAvatar == null ? this.getCurrentCharacterIndex() : tpsPreviousIndex);
        this.trialTeamIncludesOwnedAvatars = save;
        this.trialAvatarTeam = new TeamInfo();
        if (save) this.trialAvatarTeam.copyFrom(this.getRegularTeamInfo());
        else if (tpsSceneAvatar == null) {
            var scene = this.getPlayer().getScene();
            if (scene != null) this.getActiveTeam().forEach(entity ->
                scene.removeEntity(entity, VisionTypeOuterClass.VisionType.VisionType_VISION_REMOVE));
            this.getActiveTeam().clear();
        }
        this.usingTrialTeam = true;
    }

    public void trialAvatarTeamPostUpdate(int newCharacterIndex) {
        if (tpsSceneAvatar != null || this.getActiveTeam().isEmpty()) return;
        this.setCurrentCharacterIndex(Math.min(newCharacterIndex, this.getActiveTeam().size() - 1));

        this.updateTeamProperties();
        if (this.getPlayer().getScene() != null)
            this.getPlayer().getScene().addEntity(this.getCurrentAvatarEntity());
    }

    public void addAvatarToTrialTeam(Avatar trialAvatar) {
        this.trialAvatarTeam.getAvatars().removeIf(id -> id == trialAvatar.getAvatarId());
        this.trialAvatarTeam.addAvatar(trialAvatar);
        this.getTrialAvatars().put(trialAvatar.getAvatarId(), trialAvatar);
        if (tpsSceneAvatar != null) return;

        this.getActiveTeam()
            .forEach(
                x ->
                    this.getPlayer()
                        .getScene()
                        .removeEntity(x, VisionTypeOuterClass.VisionType.VisionType_VISION_REMOVE));

        this.getActiveTeam().removeIf(x -> x.getAvatar().getAvatarId() == trialAvatar.getAvatarId());

        var entity = this.createTrialAvatarEntity(trialAvatar);
        if (entity != null) this.getActiveTeam().add(entity);
    }

    public long getTrialAvatarGuid(int trialAvatarId) {
        return this.getTrialAvatars().values().stream()
            .filter(avatar -> avatar.getTrialAvatarId() == trialAvatarId)
            .map(Avatar::getGuid)
            .findFirst()
            .orElse(0L);
    }

    public void unsetTrialAvatarTeam() {

        var index = this.getPreviousIndex();
        if (index < 0) index = 0;

        this.trialAvatarTeamPostUpdate(index);

        this.setPreviousIndex(-1);
    }

    public void removeTrialAvatarTeam() {
        this.removeTrialAvatarTeam(
            this.getTrialAvatars().values().stream().map(Avatar::getTrialAvatarId).toList());
    }

    public void removeTrialAvatarTeam(int avatarId) {
        this.removeTrialAvatarTeam(List.of(avatarId));
    }

    public void removeTrialAvatarTeam(List<Integer> trialAvatarIds) {
        boolean removed = this.getTrialAvatars().values()
            .removeIf(avatar -> trialAvatarIds.contains(avatar.getTrialAvatarId()));
        if (!removed) return;
        this.usingTrialTeam = !this.getTrialAvatars().isEmpty();
        this.trialAvatarTeam = new TeamInfo();
        if (this.usingTrialTeam) {
            if (this.trialTeamIncludesOwnedAvatars)
                this.trialAvatarTeam.copyFrom(this.getRegularTeamInfo());
            for (var avatar : this.getTrialAvatars().values()) {
                this.trialAvatarTeam.getAvatars().removeIf(id -> id == avatar.getAvatarId());
                this.trialAvatarTeam.addAvatar(avatar);
            }
        }
        if (tpsSceneAvatar != null && !usingTrialTeam) {
            tpsPreviousIndex = Math.max(0, previousIndex);
            previousIndex = -1;
        }
        this.refreshTrialTeamEntities();
    }

    private EntityAvatar createTrialAvatarEntity(Avatar avatar) {
        if (player.getScene() == null) return null;
        return EntityCreationEvent.call(EntityAvatar.class,
            new Class<?>[] {Scene.class, Avatar.class}, new Object[] {player.getScene(), avatar});
    }

    private void refreshTrialTeamEntities() {
        if (tpsSceneAvatar != null) return;
        var scene = this.getPlayer().getScene();
        if (scene == null) {
            this.getActiveTeam().clear();
            var index = this.isUsingTrialTeam() ? this.getCurrentCharacterIndex() : this.getPreviousIndex();
            this.setCurrentCharacterIndex(Math.max(0, Math.min(index, this.getCurrentTeamInfo().size() - 1)));
            if (!this.isUsingTrialTeam()) this.setPreviousIndex(-1);
            return;
        }
        if (scene != null) this.getActiveTeam().forEach(entity ->
            scene.removeEntity(entity, VisionTypeOuterClass.VisionType.VisionType_VISION_REMOVE));
        this.getActiveTeam().clear();
        for (var id : this.getCurrentTeamInfo().getAvatars()) {
            var avatar = this.getAvatarForTeam(id);
            if (avatar != null) this.getActiveTeam().add(this.createTrialAvatarEntity(avatar));
        }
        if (this.isUsingTrialTeam()) this.trialAvatarTeamPostUpdate(this.getCurrentCharacterIndex());
        else this.unsetTrialAvatarTeam();
    }

    public void setupTemporaryTeam(List<List<Long>> guidList) {
        trySetupTemporaryTeam(guidList);
    }

    public boolean trySetupTemporaryTeam(List<List<Long>> guidList) {
        if (tpsSceneAvatar != null || guidList == null || guidList.isEmpty()) return false;
        var teams = new ArrayList<TeamInfo>(guidList.size());
        var selected = new HashSet<Integer>();

        for (int i = 0; i < guidList.size(); i++) {
            var guids = guidList.get(i);
            String rejected = null;
            List<Integer> avatarIds = null;

            if (guids == null || guids.isEmpty()) {
                rejected = "it is empty";
            } else if (guids.size() > this.getMaxTeamSize()) {
                rejected = guids.size() + " avatars, over the limit of " + this.getMaxTeamSize();
            } else {
                var newTeam = new LinkedHashSet<Avatar>();
                for (Long guid : guids) {
                    Avatar avatar =
                            guid == null
                                    ? null
                                    : this.getPlayer().getAvatars().getAvatarByGuid(guid);
                    if (avatar == null) {
                        rejected = "no avatar with guid " + guid;
                        break;
                    }
                    if (!newTeam.add(avatar) || !selected.add(avatar.getAvatarId())) {
                        rejected = "avatar " + avatar.getAvatarId() + " is listed twice";
                        break;
                    }
                }
                if (rejected == null) {
                    avatarIds = newTeam.stream().map(Avatar::getAvatarId).toList();
                }
            }

            if (rejected != null) {
                Grasscutter.getLogger().warn("Temporary team {} rejected: {}", i + 1, rejected);
                return false;
            } else {
                teams.add(new TeamInfo(new ArrayList<>(avatarIds)));
            }
        }

        this.temporaryTeam = teams;
        return true;
    }

    public void useTemporaryTeam(int index) {
        tryUseTemporaryTeam(index);
    }

    public List<List<Long>> getTemporaryTeamGuids() {
        var current = this.temporaryTeam;
        if (current == null) return List.of();
        var result = new ArrayList<List<Long>>(current.size());
        for (var team : current) {
            if (team == null || team.size() == 0) return List.of();
            var guids = new ArrayList<Long>(team.size());
            for (var id : team.getAvatars()) {
                var avatar = id == null ? null : this.getPlayer().getAvatars().getAvatarById(id);
                if (avatar == null) return List.of();
                guids.add(avatar.getGuid());
            }
            result.add(List.copyOf(guids));
        }
        return List.copyOf(result);
    }

    public List<List<Integer>> getTemporaryTeamAvatarIds() {
        var guids = getTemporaryTeamGuids();
        return guids.stream()
                .map(
                        team ->
                                team.stream()
                                        .map(
                                                guid ->
                                                        player.getAvatars()
                                                                .getAvatarByGuid(guid)
                                                                .getAvatarId())
                                        .toList())
                .toList();
    }

    public int getTemporaryTeamIndex() {
        return useTemporarilyTeamIndex;
    }

    public boolean tryUseTemporaryTeam(int index) {
        if (tpsSceneAvatar != null) return false;
        int available = this.temporaryTeam == null ? 0 : this.temporaryTeam.size();
        if (index < 0 || index >= available) {
            // getCurrentTeamInfo falls back to the overworld team here, which is exactly what the
            // abyss looks like when the selection never landed. Do not let that happen quietly.
            Grasscutter.getLogger()
                    .warn(
                            "Asked to use temporary team {} but only {} are set up; the current"
                                    + " team stays",
                            index + 1,
                            available);
            return false;
        }
        if (!hasUsableTemporaryTeam(index)) return false;
        var scene = this.getPlayer().getScene();
        // Not getCurrentAvatarEntity(): on an empty team that builds a fresh main character and
        // files it in the avatar list, which is not something reading the current avatar should do.
        var previous =
                scene == null || this.getActiveTeam().isEmpty()
                        ? null
                        : this.getActiveTeam()
                                .get(
                                        Math.max(
                                                0,
                                                Math.min(
                                                        this.currentCharacterIndex,
                                                        this.getActiveTeam().size() - 1)));

        this.useTemporarilyTeamIndex = index;
        this.updateTeamEntities(null);

        if (scene == null) return true;

        // updateTeamEntities keeps whichever avatar was selected even when the new team has no slot
        // for it, so the half that just finished would otherwise stay standing in the chamber.
        if (previous != null && !this.getActiveTeam().contains(previous)) {
            scene.removeEntity(previous);
        }

        // Nothing else puts the new half into the scene: the chamber does not reload across the
        // swap, and the team packet alone only tells the client who is on the team. Without an
        // avatar entity actually in the world the client has nothing to drive - no skills, no
        // burst, no switching - and the monsters have nothing to aim at.
        scene.spawnPlayer(this.getPlayer());
        return true;
    }

    private boolean hasUsableTemporaryTeam(int index) {
        if (tpsSceneAvatar != null
                || temporaryTeam == null
                || index < 0
                || index >= temporaryTeam.size()) return false;
        var chosen = temporaryTeam.get(index);
        return chosen != null
                && chosen.size() > 0
                && chosen.size() <= getMaxTeamSize()
                && chosen.getAvatars().stream()
                        .allMatch(id -> id != null && player.getAvatars().hasAvatar(id));
    }

    /** Select the entry party without changing entities in the scene being left. */
    public boolean enterSceneWithTemporaryTeam(int index, BooleanSupplier enter) {
        if (enter == null) return false;
        synchronized (player.getSceneTransferLock()) {
            if (!hasUsableTemporaryTeam(index)) return false;
            var source = player.getScene();
            var world = player.getWorld();
            int previousTeam = useTemporarilyTeamIndex;
            int previousAvatar = currentCharacterIndex;
            boolean entered = false;
            useTemporarilyTeamIndex = index;
            currentCharacterIndex = 0;
            try {
                entered = enter.getAsBoolean();
                return entered;
            } finally {
                if (!entered && player.getScene() == source && player.getWorld() == world) {
                    useTemporarilyTeamIndex = previousTeam;
                    currentCharacterIndex = previousAvatar;
                }
            }
        }
    }

    public boolean cleanTemporaryTeam() {
        if (tpsSceneAvatar != null) return false;

        if (useTemporarilyTeamIndex < 0) {
            return false;
        }

        this.useTemporarilyTeamIndex = -1;
        this.temporaryTeam = null;
        this.updateTeamEntities(null);
        return true;
    }

    public synchronized void setCurrentTeam(int teamId) {
        if (tpsSceneAvatar != null) return;

        if (this.getPlayer().isInMultiplayer()) {
            return;
        }

        TeamInfo teamInfo = this.getTeams().get(teamId);
        if (teamInfo == null || teamInfo.getAvatars().size() == 0) {
            return;
        }

        this.setCurrentTeamId(teamId);
        this.updateTeamEntities(new PacketChooseCurAvatarTeamRsp(teamId));
    }

    public synchronized void setTeamName(int teamId, String teamName) {

        TeamInfo teamInfo = this.getTeams().get(teamId);
        if (teamInfo == null) {
            return;
        }

        teamInfo.setName(teamName);

        this.getPlayer().sendPacket(new PacketChangeTeamNameRsp(teamId, teamName));
    }

    public synchronized void changeAvatar(long guid) {
        EntityAvatar oldEntity = this.getCurrentAvatarEntity();
        if (oldEntity == null || guid == oldEntity.getAvatar().getGuid()) {
            return;
        }

        EntityAvatar newEntity = null;
        int index = -1;
        for (int i = 0; i < this.getActiveTeam().size(); i++) {
            if (guid == this.getActiveTeam().get(i).getAvatar().getGuid()) {
                index = i;
                newEntity = this.getActiveTeam().get(i);
            }
        }

        if (index < 0 || newEntity == oldEntity) {
            return;
        }

        var event =
            new PlayerSwitchAvatarEvent(this.getPlayer(), oldEntity.getAvatar(), newEntity.getAvatar());
        if (!event.call()) return;

        newEntity = event.getNewAvatarEntity();

        this.setCurrentCharacterIndex(index);

        oldEntity.setMotionState(MotionState.MotionState_MOTION_STANDBY);

        this.getPlayer().getScene().replaceEntity(oldEntity, newEntity);
        this.getPlayer().sendPacket(new PacketChangeAvatarRsp(guid));
    }

    public void applyVoidDamage() {
        this.getActiveTeam()
            .forEach(
                entity -> {
                    entity.damage(entity.getFightProperty(FightProperty.FIGHT_PROP_MAX_HP) * .1f);
                    player.sendPacket(new PacketAvatarLifeStateChangeNotify(entity.getAvatar()));
                });
    }

    public void onAvatarDie(long dieGuid) {
        if (dieGuid == 0) return;
        var scene = this.player.getScene();
        var activeTeam = this.getActiveTeamSnapshot();
        if (scene == null || activeTeam.isEmpty()) return;
        int currentIndex = this.currentCharacterIndex;
        if (currentIndex < 0 || currentIndex >= activeTeam.size()) currentIndex = 0;
        EntityAvatar deadAvatar = activeTeam.get(currentIndex);
        if (deadAvatar == null
                || deadAvatar.getScene() != scene
                || deadAvatar.isAlive()
                || (deadAvatar.getId() != dieGuid && deadAvatar.getAvatar().getGuid() != dieGuid))
            return;
        this.currentCharacterIndex = currentIndex;

        PlayerDieType dieType = deadAvatar.getKilledType();
        int killedBy = deadAvatar.getKilledBy();

        if (dieType == PlayerDieType.PlayerDieType_PLAYER_DIE_DRAWN) {

            this.getPlayer().sendPacket(new PacketWorldPlayerDieNotify(dieType, killedBy));
        } else {

            int replaceIndex = -1;
            for (int index = 0; index < activeTeam.size(); index++) {
                if (activeTeam.get(index).isAlive()) {
                    replaceIndex = index;
                    break;
                }
            }
            if (replaceIndex >= 0) {

                this.setCurrentCharacterIndex(replaceIndex);
                scene.addEntity(activeTeam.get(replaceIndex));
            } else {

                this.getPlayer().sendPacket(new PacketWorldPlayerDieNotify(dieType, killedBy));

                PlayerTeamDeathEvent event = new PlayerTeamDeathEvent(this.getPlayer(), deadAvatar);
                event.call();
            }
        }

        this.getPlayer().sendPacket(new PacketAvatarDieAnimationEndRsp(deadAvatar.getId(), 0));
    }

    public int getDeadAvatarReplacement() {
        int replaceIndex = -1;

        for (int i = 0; i < this.getActiveTeam().size(); i++) {
            EntityAvatar entity = this.getActiveTeam().get(i);
            if (entity.isAlive()) {
                replaceIndex = i;
                break;
            }
        }

        return replaceIndex;
    }

    public boolean reviveAvatar(Avatar avatar) {
        for (EntityAvatar entity : this.getActiveTeam()) {
            if (entity.getAvatar() == avatar) {
                if (!entity.revive(1f)) {
                    return false;
                }

                player.getSatiationManager().removeSatiationDirectly(entity.getAvatar(), 15000);
                return true;
            }
        }

        return false;
    }

    public boolean healAvatar(Avatar avatar, int healRate, int healAmount) {
        for (EntityAvatar entity : this.getActiveTeam()) {
            if (entity.getAvatar() == avatar) {
                if (!entity.isAlive()) {
                    return false;
                }

                entity.setFightProperty(
                    FightProperty.FIGHT_PROP_CUR_HP,
                    (float)
                        Math.min(
                            (entity.getFightProperty(FightProperty.FIGHT_PROP_CUR_HP)
                                + entity.getFightProperty(FightProperty.FIGHT_PROP_MAX_HP)
                                * (float) healRate
                                / 100.0
                                + (float) healAmount / 100.0),
                            entity.getFightProperty(FightProperty.FIGHT_PROP_MAX_HP)));
                this.getPlayer()
                    .sendPacket(
                        new PacketAvatarFightPropUpdateNotify(
                            entity.getAvatar(), FightProperty.FIGHT_PROP_CUR_HP));
                this.getPlayer().sendPacket(new PacketAvatarLifeStateChangeNotify(entity.getAvatar()));
                return true;
            }
        }
        return false;
    }

    public void respawnTeam() {
        Position reviveAt = this.getRespawnPosition();
        this.getPlayer()
            .getStaminaManager()
            .stopSustainedStaminaHandler();

        for (EntityAvatar entity : this.getActiveTeam()) {
            float restoredHp = entity.getFightProperty(FightProperty.FIGHT_PROP_MAX_HP) * .4f;
            if (!entity.revive(restoredHp)) {
                entity.setFightProperty(FightProperty.FIGHT_PROP_CUR_HP, restoredHp);
                this.getPlayer().sendPacket(
                    new PacketAvatarFightPropUpdateNotify(entity.getAvatar(), FightProperty.FIGHT_PROP_CUR_HP));
                this.getPlayer().sendPacket(new PacketAvatarLifeStateChangeNotify(entity.getAvatar()));
            }
            this.getPlayer().getSatiationManager().removeSatiationDirectly(entity.getAvatar(), 15000);
        }

        this.getPlayer()
            .sendPacket(
                new PacketPlayerEnterSceneNotify(
                    this.getPlayer(),
                    EnterType.EnterType_ENTER_SELF,
                    EnterReason.Revival,
                    this.getPlayer().getSceneId(),
                    reviveAt));
        this.getPlayer().sendPacket(new PacketEnterScenePeerNotify(this.getPlayer()));
        this.getPlayer().getPosition().set(reviveAt);
        // Each request handler sends its own response, including the dungeon revival path.
    }

    public Position getRespawnPosition() {
        return RespawnPositionHelper.find(this.getPlayer());
    }

    public void saveAvatars() {

        for (EntityAvatar entity : this.getActiveTeam()) {
            entity.getAvatar().save();
        }
    }

    public void onPlayerLogin() {
        this.refreshTeamSkillLevelBonuses(false);
        this.refreshTeamResonances(false);
    }

    public synchronized void addNewCustomTeam() {

        if (this.teams.size() == GameConstants.MAX_TEAMS) {
            player.sendPacket(new PacketAddBackupAvatarTeamRsp(Retcode.RET_FAIL));
            return;
        }

        int id = -1;
        for (int i = 5; i <= GameConstants.MAX_TEAMS; i++) {
            if (!this.teams.containsKey(i)) {
                id = i;
                break;
            }
        }

        this.teams.put(id, new TeamInfo());

        player.sendPacket(new PacketAvatarTeamAllDataNotify(player));
        player.sendPacket(new PacketAddBackupAvatarTeamRsp());
    }

    public synchronized void removeCustomTeam(int id) {

        if (!this.teams.containsKey(id)) {
            player.sendPacket(new PacketDelBackupAvatarTeamRsp(Retcode.RET_FAIL, id));
        }

        this.teams.remove(id);

        player.sendPacket(new PacketAvatarTeamAllDataNotify(player));
        player.sendPacket(new PacketDelBackupAvatarTeamRsp(id));
    }

    public void applyAbilities(Scene scene) {
        try {
            var levelEntityConfig = scene.getSceneData().getLevelEntityConfig();
            var config = GameData.getConfigLevelEntityDataMap().get(levelEntityConfig);
            if (config == null) return;

            var avatars = this.getPlayer().getAvatars();
            var avatarIds = scene.getSceneData().getSpecifiedAvatarList();
            var specifiedAvatarList = this.getActiveTeam();

            if (tpsSceneAvatar == null && avatarIds != null && avatarIds.size() > 0) {

                specifiedAvatarList.clear();
                // The list holds every variant of the forced character; the scene only admits
                // maxSpecifiedAvatarNum of them, so stop once that many are in the party.
                int maxSpecified = scene.getSceneData().getMaxSpecifiedAvatarNum();
                int limit = maxSpecified > 0 ? maxSpecified : Integer.MAX_VALUE;
                for (int id : avatarIds) {
                    if (specifiedAvatarList.size() >= limit) break;
                    var avatar = avatars.getAvatarById(id);
                    if (avatar == null) continue;

                    specifiedAvatarList.add(
                        EntityCreationEvent.call(
                            EntityAvatar.class,
                            new Class<?>[] {Scene.class, Avatar.class},
                            new Object[] {scene, avatar}));
                }
            }

            for (var entityAvatar : specifiedAvatarList) {
                var avatarData = entityAvatar.getAvatar().getAvatarData();
                if (avatarData == null) {
                    continue;
                }

                avatarData.buildEmbryo();
                if (config.getAvatarAbilities() == null) {
                    continue;

                }

                for (var abilities : config.getAvatarAbilities()) {
                    avatarData.getAbilities().add(Utils.abilityHash(abilities.getAbilityName()));
                }
            }
        } catch (Exception e) {
            Grasscutter.getLogger()
                .error(
                    "Error applying level entity config for scene {}", scene.getSceneData().getId(), e);
        }
    }

    public List<Integer> getTrialAvatarParam(int trialAvatarId) {
        var custom = GameData.getTrialAvatarCustomData().get(trialAvatarId);
        if (custom != null) {
            var params = custom.getTrialAvatarParamList();
            if (params == null || params.isEmpty() || params.get(0) == null) return List.of();
            try {
                var parsed = Stream.of(params.get(0).split(";")).map(String::trim).map(Integer::parseInt).toList();
                return validTrialParam(parsed) ? parsed : List.of();
            } catch (NumberFormatException ignored) {
                return List.of();
            }
        }
        var data = GameData.getTrialAvatarDataMap().get(trialAvatarId);
        var params = data == null ? null : data.getTrialAvatarParamList();
        return validTrialParam(params) ? params : List.of();
    }

    private boolean validTrialParam(List<Integer> params) {
        return params != null && params.size() >= 2 && params.get(0) != null && params.get(0) > 0
                && params.get(1) != null && params.get(1) > 0 && params.get(1) <= 100;
    }

    public boolean addTrialAvatar(int avatarId, int questMainId, GrantReason reason) {
        List<Integer> trialAvatarBasicParam = getTrialAvatarParam(avatarId);
        if (trialAvatarBasicParam.isEmpty() || !this.getPlayer().hasSentLoginPackets()) return false;
        if (!GameData.getAvatarDataMap().containsKey((int) trialAvatarBasicParam.get(0))) return false;

        var avatar = new Avatar(trialAvatarBasicParam.get(0));

        avatar.setTrialAvatarInfo(trialAvatarBasicParam.get(1), avatarId, reason, questMainId);
        avatar.setOwner(this.getPlayer());
        avatar.equipTrialItems(tpsSceneAvatar == null && player.getScene() != null);

        avatar.recalcStats(false, tpsSceneAvatar == null);

        this.getPlayer().sendPacket(new PacketAvatarAddNotify(avatar, false));

        this.addAvatarToTrialTeam(avatar);
        if (questMainId != 0) {
            this.questTrialAvatarIds.put(avatarId, questMainId);
            this.getPlayer().save();
        }
        return true;
    }

    public void addTrialAvatar(int avatarId, int questMainId) {
        this.addTrialAvatars(List.of(avatarId), questMainId, true);

        this.getPlayer().sendPacket(new PacketAvatarTeamUpdateNotify(this.getPlayer()));
    }

    public void addTrialAvatars(List<Integer> avatarIds) {
        this.addTrialAvatars(avatarIds, 0, false);
    }

    public void addTrialAvatars(List<Integer> avatarIds, boolean save) {
        this.addTrialAvatars(avatarIds, 0, save);
    }

    public void addTrialAvatars(List<Integer> trialAvatarIds, int questId, boolean save) {
        this.setupTrialAvatars(save);

        trialAvatarIds.forEach(
            trialAvatarId -> {
                var result =
                    this.addTrialAvatar(
                        trialAvatarId,
                        questId,
                        questId != 0
                            ? GrantReason.GRANT_REASON_BY_QUEST
                            : GrantReason.GRANT_REASON_BY_TRIAL_AVATAR_ACTIVITY);

                if (!result) throw new RuntimeException("Unable to add trial avatar to team.");
            });

        this.trialAvatarTeamPostUpdate(questId != 0 ? this.getActiveTeam().size() - 1 : 0);
    }

    public void removeTrialAvatar() {
        this.removeTrialAvatar(
            this.getTrialAvatars().values().stream()
                .map(Avatar::getTrialAvatarId)
                .toList());
    }

    public void removeTrialAvatar(int trialAvatarId) {
        this.removeTrialAvatar(List.of(trialAvatarId));
    }

    public void removeTrialAvatar(List<Integer> trialAvatarIds) {
        boolean changed = this.questTrialAvatarIds.keySet().removeAll(trialAvatarIds);
        var guids = trialAvatarIds.stream().map(this::getTrialAvatarGuid)
            .filter(guid -> guid != 0).distinct().toList();
        if (guids.isEmpty()) {
            if (changed) this.getPlayer().save();
            return;
        }
        this.getPlayer().sendPacket(new PacketAvatarDelNotify(guids));
        this.removeTrialAvatarTeam(trialAvatarIds);
        if (changed) this.getPlayer().save();
        this.getPlayer().sendPacket(new PacketAvatarTeamUpdateNotify(this.getPlayer()));
    }

    public void removeQuestTrialAvatars(int questId) {
        var ids = new HashSet<Integer>();
        this.questTrialAvatarIds.forEach((id, parent) -> { if (parent == questId) ids.add(id); });
        this.getTrialAvatars().values().stream().filter(a -> a.getFromParentQuestId() == questId)
            .map(Avatar::getTrialAvatarId).forEach(ids::add);
        this.removeTrialAvatar(new ArrayList<>(ids));
    }

    public void restoreQuestTrialAvatars() {
        var scene = this.getPlayer().getScene();
        for (var grant : new LinkedHashMap<>(this.questTrialAvatarIds).entrySet()) {
            var quest = this.getPlayer().getQuestManager().getMainQuests().get(grant.getValue());
            if (quest == null) continue;
            if (quest.isFinished() || scene == null || scene.getSceneType() != SceneType.SCENE_DUNGEON) {
                this.removeTrialAvatar(grant.getKey());
            } else if (this.getTrialAvatarGuid(grant.getKey()) == 0) {
                this.addTrialAvatar(grant.getKey(), grant.getValue());
            }
        }
    }

    public void repairPersistentTeams() {
        for (var team : this.getTeams().values()) {
            var ids = new ArrayList<>(new LinkedHashSet<>(team.getAvatars()));
            ids.removeIf(id -> !this.getPlayer().getAvatars().hasAvatar(id));
            if (ids.size() > GAME_OPTIONS.avatarLimits.singlePlayerTeam)
                ids.subList(GAME_OPTIONS.avatarLimits.singlePlayerTeam, ids.size()).clear();
            team.getAvatars().clear();
            team.getAvatars().addAll(ids);
        }
        var current = this.getCurrentSinglePlayerTeamInfo();
        if (current.getAvatars().isEmpty()) {
            var owned = this.getPlayer().getAvatars();
            var first = owned.getAvatarById(this.getPlayer().getMainCharacterId());
            if (first == null && owned.iterator().hasNext()) first = owned.iterator().next();
            if (first != null) current.addAvatar(first);
        }
        this.setCurrentCharacterIndex(Math.max(0, Math.min(this.getCurrentCharacterIndex(), current.size() - 1)));
    }
}
