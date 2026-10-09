package emu.grasscutter.game.ability;

import emu.grasscutter.game.entity.EntityAvatar;

import java.util.*;

/** Server view of the mode names in native client enum 70923; UI control stays client-owned. */
public final class AbilityPlayMode {
    public enum Type {
        Normal,
        Sneak,
        DvalinS01FlyState,
        Bomber,
        FindCat,
        WildSneak,
        GlidingChallengeState,
        LookAtCameraState,
        FleurFairFall,
        EndoraState,
        FleurFairBalloon,
        HideAndSeek,
        HideAndSeek_Ghost,
        Vehicle_Skiff,
        BounceConjuring,
        ActivityAimState,
        ElectricCoreState,
        Fishing,
        AvatarTransMove,
        AvatarTransMove_NoJump,
        Tail,
        WinterCampRace,
        Bartender,
        UGCForbidSkill,
        Blocking,
        BlackMud,
        LannaluoState,
        GrapplingHook,
        BoarHunt,
        BoarChuck,
        BoarKingChallenge,
        FungusFighterBattle,
        CharAmuseBaseball,
        CharAmuseFootball,
        HideAndSeek_GhostV2,
        ActivityBreakout,
        HideAndSeek_AliveV2,
        Vehicle_Sorush,
        LanternRiteV3Boat,
        PacMan,
        Vehicle_Foxcraft,
        CarpJump,
        PitchPot,
        ShootingStatue,
        TD_test,
        PartyLion,
        GotoRemusDash,
        ForbidenSkillAndAttack,
        MvM_Normal,
        MvM_Set,
        Fairybook,
        AirOceanCurrent,
        Vehicle_Natsaurus,
        Vehicle_Natsaurus_Flamingo,
        Natsaurus_SpecialSwim,
        HideAndSeek_V4_Repair,
        NyxState,
        NyxState_Xilonen,
        DreamExploreState,
        Vehicle_Natsaurus_Bisonsaurus,
        NyxState_Iansan,
        FlamingoPrimoChasing,
        Avatar_Chasca,
        Vehicle_Natsaurus_Shamansaurus,
        CharAumseSurfing,
        Panel_CloseAll,
        Panel_CloseAll_Natsaurus,
        SpecialFocusAttack,
        FishingJoy,
        InazumaBadminton,
        Avatar_Ifa,
        Vehicle_Natsaurus_PaintSeelie,
        Maglev,
        Vehicle_RobotVacuum,
        Maglev_SpecialSwimClimb,
        Vehicle_PlayerSaosaoRobo,
        MagnetRush,
        BeyondHall,
        BeyondCustomPlay,
        BeyondTeyvatPlay,
        Avatar_Nefer,
        Avatar_Durin,
        PitchPotNoMove,
        BeyondHallNotSnow,
        LunarisThrow,
        LunarisSlice,
        InstitudeExplore,
        InstitudeStealth,
        MoonMirrorShard,
        MagNStatue,
        FirmamentDeform,
        FirmamentRotate,
        FirmamentPaint,
        MapMark,
        FirmamentEscape,
        TradeShowStg,
        TradeShowTd,
        MothSpirit,
        SneakLE,
        Peittaa,
        ZeroGravity,
        Vehicle_ParamMove,
        Acitivity_OneShot,
        AquariumDiving,
        AquariumDivingInterior,
        TPS_Normal,
        RankedMatchPuzzle,
        JackFrostStealth,
        TPS_Defense,
        IceCreature,
        TPS_IceGun,
        IceCreatureZipLine,
        TPS_Grenade,
        RankedMatchPuzzle_InBattle,
        VaultDungeonRadio_Sneak,
        SnowballFight,
        Vehicle_VineManRun
    }

    private final EntityAvatar entity;
    private final Set<Type> active = EnumSet.noneOf(Type.class);
    private long version = -1;

    public AbilityPlayMode(EntityAvatar entity) {
        this.entity = entity;
    }

    private boolean current() {
        long next = entity.getAbilityLifecycleVersion();
        if (next != version) {
            active.clear();
            version = next;
        }
        return entity.isAbilityLifecycleCurrent(next);
    }

    public synchronized boolean change(Type from, Type to) {
        if (from == null || to == null || !current()) return false;
        // Native ChangePlayMode enters a non-Normal destination; otherwise it exits the source.
        if (to != Type.Normal) active.add(to);
        else if (from != Type.Normal) active.remove(from);
        return true;
    }

    public synchronized Set<Type> snapshot() {
        if (!current() || active.isEmpty()) return Set.of();
        return Collections.unmodifiableSet(EnumSet.copyOf(active));
    }
}
