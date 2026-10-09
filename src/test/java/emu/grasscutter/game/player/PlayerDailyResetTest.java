package emu.grasscutter.game.player;

import static emu.grasscutter.config.Configuration.GAME_OPTIONS;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.Gson;

import emu.grasscutter.data.GameData;
import emu.grasscutter.data.excels.ItemData;
import emu.grasscutter.game.Account;
import emu.grasscutter.game.activity.ActivityManager;
import emu.grasscutter.game.battlepass.BattlePassManager;
import emu.grasscutter.game.dailytask.DailyTaskManager;
import emu.grasscutter.game.inventory.*;
import emu.grasscutter.game.mail.*;
import emu.grasscutter.game.props.ActionReason;
import emu.grasscutter.game.props.WatcherTriggerType;
import emu.grasscutter.net.packet.BasePacket;

import org.junit.jupiter.api.*;

import java.time.*;
import java.util.*;

class PlayerDailyResetTest {
    private TimeZone originalZone;
    private boolean birthdayEnabled;
    private ItemData originalPrimogem;

    static Instant time(String text) {
        return OffsetDateTime.parse(text).toInstant();
    }

    static void field(Object owner, Class<?> type, String name, Object value) throws Exception {
        var field = type.getDeclaredField(name);
        field.setAccessible(true);
        field.set(owner, value);
    }

    static class TestPlayer extends Player {
        Instant now;
        int grants, saves, loginTriggers, dayNotifications;
        boolean rejectGrant, rejectMail;
        boolean online = true;
        final List<Mail> birthdayMail = new ArrayList<>();
        final BattlePassManager pass =
                new BattlePassManager(this) {
                    @Override
                    protected Instant missionNow() {
                        return now;
                    }

                    @Override
                    public void save() {}

                    @Override
                    public void triggerMission(WatcherTriggerType type) {
                        loginTriggers++;
                    }
                };
        final Inventory inventory =
                new Inventory(this) {
                    @Override
                    public boolean addItem(GameItem item, ActionReason reason) {
                        if (rejectGrant) return false;
                        grants += item.getCount();
                        return true;
                    }
                };
        final MailHandler mails =
                new MailHandler(this) {
                    @Override
                    public boolean deliver(Mail mail) {
                        if (rejectMail) return false;
                        birthdayMail.add(mail);
                        return true;
                    }
                };
        final DailyTaskManager commissions =
                new DailyTaskManager(this) {
                    @Override
                    public int resetDailyTasksForNewDay() {
                        return 4;
                    }

                    @Override
                    public int resetDailyTasksForNewDay(Instant instant) {
                        commissionTime = instant;
                        return 4;
                    }
                };
        Instant commissionTime;

        TestPlayer() throws Exception {
            setAccount(new Account());
            field(this, Player.class, "dailyTaskManager", commissions);
            field(
                    this,
                    Player.class,
                    "activityManager",
                    new ActivityManager(this) {
                        @Override
                        public void refreshActivities() {}

                        @Override
                        public void onDailyReset(long instant) {
                            dayNotifications++;
                        }
                    });
        }

        void tick(Instant instant) {
            now = instant;
            doDailyReset(instant);
        }

        @Override
        public BattlePassManager getBattlePassManager() {
            return pass;
        }

        @Override
        public Inventory getInventory() {
            return inventory == null ? super.getInventory() : inventory;
        }

        @Override
        public MailHandler getMailHandler() {
            return mails == null ? super.getMailHandler() : mails;
        }

        @Override
        public String getNickname() {
            return "Daily refresh test";
        }

        @Override
        public void sendPacket(BasePacket packet) {}

        @Override
        public void save() {
            saves++;
        }

        @Override
        public boolean isOnline() {
            return online;
        }

        @Override
        protected LocalDate moonCardToday() {
            return now.atZone(ZoneId.of("Asia/Shanghai")).minusHours(4).toLocalDate();
        }
    }

    @BeforeEach
    void setup() throws Exception {
        Class.forName("emu.grasscutter.Grasscutter");
        originalZone = TimeZone.getDefault();
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Shanghai"));
        birthdayEnabled = GAME_OPTIONS.birthdayMail.enabled;
        GAME_OPTIONS.birthdayMail.enabled = true;
        originalPrimogem =
                GameData.getItemDataMap()
                        .put(
                                201,
                                new Gson()
                                        .fromJson(
                                                "{\"id\":201,\"itemType\":\"ITEM_VIRTUAL\"}",
                                                ItemData.class));
    }

    @AfterEach
    void restore() {
        TimeZone.setDefault(originalZone);
        GAME_OPTIONS.birthdayMail.enabled = birthdayEnabled;
        if (originalPrimogem == null) GameData.getItemDataMap().remove(201);
        else GameData.getItemDataMap().put(201, originalPrimogem);
    }

    TestPlayer spent(String last) throws Exception {
        var player = new TestPlayer();
        player.setLastDailyReset((int) time(last).getEpochSecond());
        player.setForgePoints(12);
        player.setResinBuyCount(5);
        player.setWeeklyBossChestNum(3);
        player.saves = 0;
        return player;
    }

    @Test
    void midnightDoesNotReissueThePlayersQuota() throws Exception {
        var player = spent("2026-10-09T12:00:00+08:00");
        player.tick(time("2026-10-10T00:00:00+08:00"));
        assertEquals(12, player.getForgePoints());
        assertEquals(5, player.getResinBuyCount());
        assertEquals(0, player.dayNotifications);
    }

    @Test
    void fourIsInclusiveAndReissuesQuotaOnce() throws Exception {
        var player = spent("2026-10-10T03:59:59+08:00");
        player.tick(time("2026-10-10T04:00:00+08:00"));
        assertEquals(300_000, player.getForgePoints());
        assertEquals(0, player.getResinBuyCount());
        assertEquals(1, player.dayNotifications);
        assertEquals(player.now, player.commissionTime);
        player.setForgePoints(123);
        player.setResinBuyCount(2);
        player.tick(time("2026-10-10T04:00:01+08:00"));
        assertEquals(123, player.getForgePoints());
        assertEquals(2, player.getResinBuyCount());
        assertEquals(1, player.dayNotifications);
    }

    @Test
    void utcContainerDoesNotMoveTheBoundary() throws Exception {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
        var player = spent("2026-10-10T03:59:59+08:00");
        player.tick(time("2026-10-10T04:00:00+08:00"));
        assertEquals(0, player.getResinBuyCount());
        assertEquals(300_000, player.getForgePoints());
    }

    @Test
    void mondayBeforeFourRetainsWeeklyBossDiscounts() throws Exception {
        var player = spent("2026-10-11T12:00:00+08:00");
        player.tick(time("2026-10-12T03:59:59+08:00"));
        assertEquals(3, player.getWeeklyBossChestNum());
    }

    @Test
    void mondayAtFourRefreshesTheWeeklyCounter() throws Exception {
        var player = spent("2026-10-12T03:59:59+08:00");
        player.tick(time("2026-10-12T04:00:00+08:00"));
        assertEquals(0, player.getWeeklyBossChestNum());
    }

    @Test
    void missingMondayStillRefreshesOnTuesday() throws Exception {
        var player = spent("2026-10-11T12:00:00+08:00");
        player.tick(time("2026-10-13T04:00:00+08:00"));
        assertEquals(0, player.getWeeklyBossChestNum());
    }

    @Test
    void clockRollbackDoesNotReissueQuota() throws Exception {
        var player = spent("2026-10-11T04:00:00+08:00");
        player.tick(time("2026-10-10T04:00:00+08:00"));
        assertEquals(5, player.getResinBuyCount());
        assertEquals(0, player.dayNotifications);
    }

    @Test
    void persistedResetPreventsAnotherIssueAfterRestart() throws Exception {
        var first = spent("2026-10-10T03:59:59+08:00");
        first.tick(time("2026-10-10T04:00:00+08:00"));
        var reloaded = spent("2026-10-10T04:00:00+08:00");
        reloaded.setLastDailyReset(first.getLastDailyReset());
        reloaded.tick(time("2026-10-10T12:00:00+08:00"));
        assertEquals(5, reloaded.getResinBuyCount());
        assertEquals(0, reloaded.dayNotifications);
    }

    @Test
    void birthdayIsCheckedAtMidnightEvenWhenGameDayHasNotChanged() throws Exception {
        var player = spent("2026-10-09T12:00:00+08:00");
        player.getBirthday().set(10, 10);
        // Simulate an old midnight reset already persisted before this version was installed.
        player.setLastDailyReset((int) time("2026-10-10T00:00:00+08:00").getEpochSecond());
        player.tick(time("2026-10-10T00:01:00+08:00"));
        assertEquals(1, player.birthdayMail.size());
        assertEquals(2026, player.getLastBirthdayMailYear());
        player.tick(time("2026-10-10T04:00:00+08:00"));
        player.tick(time("2026-10-10T23:59:59+08:00"));
        assertEquals(1, player.birthdayMail.size());
    }

    @Test
    void birthdayUsesChinaMidnightWhenMachineUsesUtc() throws Exception {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
        var player = spent("2026-10-09T12:00:00+08:00");
        player.getBirthday().set(10, 10);
        player.tick(time("2026-10-10T00:00:00+08:00"));
        assertEquals(1, player.birthdayMail.size());
        assertEquals(5, player.getResinBuyCount());
    }

    @Test
    void aMissedBirthdayDoesNotBackfillOnNextDay() throws Exception {
        var player = spent("2026-10-09T12:00:00+08:00");
        player.getBirthday().set(10, 10);
        player.tick(time("2026-10-11T00:00:00+08:00"));
        assertTrue(player.birthdayMail.isEmpty());
    }

    @Test
    void canceledBirthdayDeliveryCanRetryWithoutLosingTheYearlyGift() throws Exception {
        var player = spent("2026-10-09T12:00:00+08:00");
        player.getBirthday().set(10, 10);
        player.rejectMail = true;
        player.tick(time("2026-10-10T12:00:00+08:00"));
        assertTrue(player.birthdayMail.isEmpty());
        assertEquals(0, player.getLastBirthdayMailYear());
        player.rejectMail = false;
        player.tick(time("2026-10-10T12:00:01+08:00"));
        player.tick(time("2026-10-10T12:00:02+08:00"));
        assertEquals(1, player.birthdayMail.size());
        assertEquals(2026, player.getLastBirthdayMailYear());
    }

    @Test
    void onlineMoonCardIsAwardedAtFourOnceAndKeepsOriginalExpiry() throws Exception {
        var player = spent("2026-10-09T12:00:00+08:00");
        player.setMoonCard(true);
        player.setMoonCardDuration(30);
        player.setMoonCardStartTime(Date.from(time("2026-10-09T00:00:00+08:00")));
        player.now = time("2026-10-09T12:00:00+08:00");
        player.getTodayMoonCard();
        player.tick(time("2026-10-10T03:59:59+08:00"));
        assertEquals(90, player.grants);
        player.tick(time("2026-10-10T04:00:00+08:00"));
        player.tick(time("2026-10-10T04:00:01+08:00"));
        assertEquals(180, player.grants);
        assertEquals(30, player.getMoonCardDuration());
    }

    @Test
    void refusedMoonCardRewardCanRetryWithoutReissuingOtherQuota() throws Exception {
        var player = spent("2026-10-10T03:59:59+08:00");
        player.setMoonCard(true);
        player.setMoonCardDuration(30);
        player.setMoonCardStartTime(Date.from(time("2026-10-09T00:00:00+08:00")));
        player.rejectGrant = true;
        player.tick(time("2026-10-10T04:00:00+08:00"));
        assertEquals(0, player.grants);
        assertTrue(player.getMoonCardGetTimes().isEmpty());
        player.rejectGrant = false;
        player.setResinBuyCount(2);
        player.tick(time("2026-10-10T04:00:01+08:00"));
        player.tick(time("2026-10-10T04:00:02+08:00"));
        assertEquals(90, player.grants);
        assertEquals(2, player.getResinBuyCount());
        assertEquals(1, player.dayNotifications);
    }

    @Test
    void initialLoginKeepsMoonRewardAfterTheInventorySnapshot() throws Exception {
        var player = spent("2026-10-10T03:59:59+08:00");
        player.online = false;
        player.setMoonCard(true);
        player.setMoonCardDuration(30);
        player.setMoonCardStartTime(Date.from(time("2026-10-09T00:00:00+08:00")));
        player.tick(time("2026-10-10T04:00:00+08:00"));
        assertEquals(0, player.grants);
        assertEquals(0, player.dayNotifications);
        // The established onLogin call remains responsible for initial reward delivery.
        player.getTodayMoonCard();
        assertEquals(90, player.grants);
    }
}
