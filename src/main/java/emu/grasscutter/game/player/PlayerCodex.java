package emu.grasscutter.game.player;

import dev.morphia.annotations.*;

import emu.grasscutter.data.GameData;
import emu.grasscutter.data.excels.codex.CodexAnimalData;
import emu.grasscutter.database.DatabaseHelper;
import emu.grasscutter.game.entity.*;
import emu.grasscutter.game.inventory.BookUnlocks;
import emu.grasscutter.game.inventory.GameItem;
import emu.grasscutter.server.packet.send.PacketCodexDataUpdateNotify;

import lombok.*;

import java.util.*;

@Entity
public class PlayerCodex {
    @Transient private Player player;
    @Transient private transient boolean reliquaryMigrationPending;

    // itemId is not codexId!
    @Getter private Set<Integer> unlockedWeapon;
    @Getter private Map<Integer, Integer> unlockedAnimal;
    @Getter private Set<Integer> unlockedMaterial;
    @Getter private Set<Integer> unlockedBook;
    @Getter private Set<Integer> unlockedTip;
    @Getter private Set<Integer> unlockedView;
    @Getter private Set<Integer> unlockedReliquary;
    @Getter private Set<Integer> unlockedReliquarySuitCodex;

    public PlayerCodex() {
        this.unlockedWeapon = new HashSet<>();
        this.unlockedAnimal = new HashMap<>();
        this.unlockedMaterial = new HashSet<>();
        this.unlockedBook = new HashSet<>();
        this.unlockedTip = new HashSet<>();
        this.unlockedView = new HashSet<>();
        this.unlockedReliquary = new HashSet<>();
        this.unlockedReliquarySuitCodex = new HashSet<>();
    }

    public PlayerCodex(Player player) {
        this();
        this.player = player;
    }

    public void setPlayer(Player player) {
        this.player = player;
        this.reliquaryMigrationPending |= this.fixReliquaries();
    }

    /** Read-only binding may normalize a copy; only the login owner persists its migration. */
    void onLogin() {
        onLogin(() -> DatabaseHelper.saveGameSync(player));
    }

    void onLogin(Runnable save) {
        reliquaryMigrationPending |= fixReliquaries();
        if (!reliquaryMigrationPending) return;
        if (player == null || player.getCodex() != this)
            throw new IllegalStateException("Codex migration has no owning login player");
        save.run();
        reliquaryMigrationPending = false;
    }

    public boolean unlockBook(int codexId) {
        var book = GameData.getCodexBookDataMap().get(codexId);
        if (player == null
                || book == null
                || book.getId() != codexId
                || book.getMaterialId() <= 0
                || BookUnlocks.definition(GameData.getItemDataMap().get(book.getMaterialId()))
                        != book
                || !this.getUnlockedBook().add(book.getMaterialId())) return false;
        player.save();
        player.sendPacket(new PacketCodexDataUpdateNotify(5, codexId));
        return true;
    }

    public void checkAddedItem(GameItem item) {
        val itemData = item.getItemData();
        val itemId = item.getItemId();
        switch (itemData.getItemType()) {
            case ITEM_WEAPON -> {
                Optional.ofNullable(GameData.getCodexWeaponDataIdMap().get(itemId))
                        .ifPresent(
                                codexData -> {
                                    if (this.getUnlockedWeapon().add(itemId)) {
                                        this.player.save();
                                        this.player.sendPacket(
                                                new PacketCodexDataUpdateNotify(
                                                        2, codexData.getId()));
                                    }
                                });
            }
            case ITEM_MATERIAL -> {
                switch (itemData.getMaterialType()) {
                        // Is this check even needed?
                    case MATERIAL_FOOD,
                            MATERIAL_WIDGET,
                            MATERIAL_EXCHANGE,
                            MATERIAL_AVATAR_MATERIAL,
                            MATERIAL_NOTICE_ADD_HP -> {
                        Optional.ofNullable(GameData.getCodexMaterialDataIdMap().get(itemId))
                                .ifPresent(
                                        codexData -> {
                                            if (this.getUnlockedMaterial().add(itemId)) {
                                                this.player.save();
                                                this.player.sendPacket(
                                                        new PacketCodexDataUpdateNotify(
                                                                4, codexData.getId()));
                                            }
                                        });
                    }
                    default -> {}
                }
            }
            case ITEM_RELIQUARY -> {
                val reliquaryId = (itemId / 10) * 10; // Normalize to 0-substat form
                if (this.getUnlockedReliquary().add(reliquaryId)) checkUnlockedSuits(reliquaryId);
            }
            default -> {}
        }
    }

    public void checkAnimal(GameEntity target, CodexAnimalData.CountType countType) {
        if (target instanceof EntityMonster) {
            val monsterId = ((EntityMonster) target).getMonsterData().getId();
            val codexAnimal = GameData.getCodexAnimalDataMap().get(monsterId);
            if (codexAnimal == null) return;

            val animalCountType = codexAnimal.getCountType();
            if (animalCountType != countType && animalCountType != null) return;

            this.getUnlockedAnimal().merge(monsterId, 1, (i, j) -> i + 1);

            player.save();
            this.player.sendPacket(new PacketCodexDataUpdateNotify(3, monsterId));
        }
    }

    public void checkUnlockedSuits(int reliquaryId) {
        GameData.getCodexReliquaryArrayList().stream()
                .filter(x -> !this.getUnlockedReliquarySuitCodex().contains(x.getId()))
                .filter(x -> x.containsId(reliquaryId))
                .filter(x -> this.getUnlockedReliquary().containsAll(x.getIds()))
                .forEach(
                        x -> {
                            int id = x.getId();
                            this.getUnlockedReliquarySuitCodex().add(id);
                            this.player.save();
                            this.player.sendPacket(new PacketCodexDataUpdateNotify(8, id));
                        });
    }

    private boolean fixReliquaries() {
        boolean changed = false;
        if (unlockedWeapon == null) {
            unlockedWeapon = new HashSet<>();
            changed = true;
        }
        if (unlockedAnimal == null) {
            unlockedAnimal = new HashMap<>();
            changed = true;
        }
        if (unlockedMaterial == null) {
            unlockedMaterial = new HashSet<>();
            changed = true;
        }
        if (unlockedBook == null) {
            unlockedBook = new HashSet<>();
            changed = true;
        }
        if (unlockedTip == null) {
            unlockedTip = new HashSet<>();
            changed = true;
        }
        if (unlockedView == null) {
            unlockedView = new HashSet<>();
            changed = true;
        }
        if (unlockedReliquary == null) {
            unlockedReliquary = new HashSet<>();
            changed = true;
        }
        if (unlockedReliquarySuitCodex == null) {
            unlockedReliquarySuitCodex = new HashSet<>();
            changed = true;
        }
        // Migrate older database entries which were using non-canonical forms of itemIds
        val newReliquaries = new HashSet<Integer>();
        this.unlockedReliquary.forEach(
                i -> {
                    if (i != null) newReliquaries.add((i / 10) * 10);
                });
        if (!newReliquaries.equals(unlockedReliquary)) {
            this.unlockedReliquary = newReliquaries;
            changed = true;
        }
        for (var row : GameData.getCodexReliquaryArrayList())
            if (unlockedReliquary.containsAll(row.getIds()))
                changed |= unlockedReliquarySuitCodex.add(row.getId());
        return changed;
    }
}
