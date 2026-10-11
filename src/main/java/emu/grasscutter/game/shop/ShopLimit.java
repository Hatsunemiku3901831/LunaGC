package emu.grasscutter.game.shop;

import dev.morphia.annotations.Entity;

@Entity
public class ShopLimit {
    private int shopGoodId;
    private int hasBought;
    private int hasBoughtInPeriod = 0;
    private int nextRefreshTime = 0;
    private int activityScheduleId;

    public int getActivityScheduleId() {
        return activityScheduleId;
    }

    /** Legacy limits bind once without granting extra buys; a new replay resets its period. */
    public void bindActivitySchedule(int scheduleId) {
        if (scheduleId <= 0) throw new IllegalArgumentException("Invalid activity shop schedule");
        if (activityScheduleId != 0 && activityScheduleId != scheduleId) {
            hasBoughtInPeriod = 0;
            nextRefreshTime = 0;
        }
        activityScheduleId = scheduleId;
    }

    public int getShopGoodId() {
        return shopGoodId;
    }

    public void setShopGoodId(int shopGoodId) {
        this.shopGoodId = shopGoodId;
    }

    public int getHasBought() {
        return hasBought;
    }

    public void setHasBought(int hasBought) {
        this.hasBought = hasBought;
    }

    public int getNextRefreshTime() {
        return nextRefreshTime;
    }

    public void setNextRefreshTime(int nextRefreshTime) {
        this.nextRefreshTime = nextRefreshTime;
    }

    public int getHasBoughtInPeriod() {
        return hasBoughtInPeriod;
    }

    public void setHasBoughtInPeriod(int hasBoughtInPeriod) {
        this.hasBoughtInPeriod = hasBoughtInPeriod;
    }
}
