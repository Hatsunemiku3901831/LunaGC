package emu.grasscutter.data.excels.quest;

import com.google.gson.annotations.SerializedName;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.data.*;
import emu.grasscutter.data.binout.MainQuestData;
import emu.grasscutter.data.common.ItemParamData;
import emu.grasscutter.game.quest.enums.*;

import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.*;

import javax.annotation.*;

@ResourceType(name = "QuestExcelConfigData.json")
@Getter
@ToString
public class QuestData extends GameResource {
    @Getter private int subId;
    @Getter private int mainId;
    @Getter private int order;
    @Getter private long descTextMapHash;

    @Getter private boolean finishParent;
    @Getter private boolean isRewind;

    @Getter private LogicType acceptCondComb;
    @Getter private LogicType finishCondComb;
    @Getter private LogicType failCondComb;

    @Getter private List<QuestAcceptCondition> acceptCond;
    @Getter private List<QuestContentCondition> finishCond;
    @Getter private List<QuestContentCondition> failCond;
    @Getter private List<QuestExecParam> beginExec;
    @Getter private List<QuestExecParam> finishExec;
    @Getter private List<QuestExecParam> failExec;
    @Getter private Guide guide;

    @Getter private List<Integer> trialAvatarList;
    @Getter private List<ItemParamData> gainItems;

    // Client chapters can contain objectives without the server's acceptance rules.
    private transient boolean automaticAcceptance = true;

    public static String questConditionKey(
            @Nonnull Enum<?> type, int firstParam, @Nullable String paramsStr) {
        return type.name() + firstParam + (paramsStr != null ? paramsStr : "");
    }

    // ResourceLoader not happy if you remove getId() ~~
    public int getId() {
        return subId;
    }

    public void onLoad() {
        if (this.acceptCond == null) this.automaticAcceptance = false;
        this.acceptCond =
                nonNull(this.acceptCond).stream()
                        .filter(p -> p != null && p.getType() != null)
                        .toList();
        this.finishCond =
                nonNull(this.finishCond).stream()
                        .filter(p -> p != null && p.getType() != null)
                        .toList();
        this.failCond =
                nonNull(this.failCond).stream()
                        .filter(p -> p != null && p.getType() != null)
                        .toList();

        this.beginExec =
                nonNull(this.beginExec).stream().filter(p -> p != null && p.type != null).toList();
        this.finishExec =
                nonNull(this.finishExec).stream().filter(p -> p != null && p.type != null).toList();
        this.failExec =
                nonNull(this.failExec).stream().filter(p -> p != null && p.type != null).toList();

        if (this.acceptCondComb == null) this.acceptCondComb = LogicType.LOGIC_NONE;

        if (this.finishCondComb == null) this.finishCondComb = LogicType.LOGIC_NONE;

        if (this.failCondComb == null) this.failCondComb = LogicType.LOGIC_NONE;

        if (this.gainItems == null) this.gainItems = Collections.emptyList();

        this.addToCache();
    }

    private static <T> List<T> nonNull(List<T> values) {
        return values == null ? List.of() : values;
    }

    public void applyFrom(MainQuestData.SubQuestData additionalData) {
        this.isRewind = additionalData.isRewind();
        this.finishParent = additionalData.isFinishParent();
    }

    private void addToCache() {
        if (!this.automaticAcceptance) {
            Grasscutter.getLogger().debug("No automatic acceptance rules for quest {}", getSubId());
            return;
        }

        var cacheMap = GameData.getBeginCondQuestMap();
        if (getAcceptCond().isEmpty()) {
            var list =
                    cacheMap.computeIfAbsent(
                            QuestData.questConditionKey(QuestCond.QUEST_COND_NONE, 0, null),
                            e -> new ArrayList<>());
            list.add(this);
        } else {
            this.getAcceptCond()
                    .forEach(
                            questCondition -> {
                                if (questCondition.getType() == null) {
                                    Grasscutter.getLogger()
                                            .warn("null accept type for quest {}", getSubId());
                                    return;
                                }

                                var key = questCondition.asKey();
                                var list = cacheMap.computeIfAbsent(key, e -> new ArrayList<>());
                                list.add(this);
                            });
        }
    }

    @Data
    @FieldDefaults(level = AccessLevel.PRIVATE)
    public static class QuestExecParam {
        @SerializedName(
                value = "_type",
                alternate = {"type"})
        QuestExec type;

        @SerializedName(
                value = "_param",
                alternate = {"param"})
        String[] param;

        @SerializedName(
                value = "_count",
                alternate = {"count"})
        String count;
    }

    public static class QuestAcceptCondition extends QuestCondition<QuestCond> {}

    public static class QuestContentCondition extends QuestCondition<QuestContent> {}

    @Data
    public static class QuestCondition<TYPE extends Enum<?> & QuestTrigger> {
        @SerializedName(
                value = "_type",
                alternate = {"type"})
        private TYPE type;

        @SerializedName(
                value = "_param",
                alternate = {"param"})
        private int[] param;

        @SerializedName(
                value = "_param_str",
                alternate = {"param_str"})
        private String paramStr = "";

        @SerializedName(
                value = "_count",
                alternate = {"count"})
        private int count;

        public String asKey() {
            return questConditionKey(getType(), getParam()[0], getParamStr());
        }
    }

    @Data
    public static class Guide {
        private String type;
        private List<String> param;
        private int guideScene;
    }
}
