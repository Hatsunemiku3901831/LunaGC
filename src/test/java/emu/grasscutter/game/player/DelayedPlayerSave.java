package emu.grasscutter.game.player;

import dev.morphia.Datastore;

import org.bson.Document;

/** Delay an actual Player replacement, then release it at the next guard metadata read. */
public final class DelayedPlayerSave implements PlayerSaveGuard.Storage {
    private final PlayerSaveGuard.Storage delegate;
    private Document delayedFilter, delayedDocument;
    private boolean delay, commitOnRead;
    private int replacements, advances, reads;

    public DelayedPlayerSave(Player player, Datastore store) throws Exception {
        delegate = new PlayerSaveGuard.MongoStorage(store);
        var field = Player.class.getDeclaredField("saveGuard");
        field.setAccessible(true);
        field.set(
                player, new PlayerSaveGuard(player, player.getSaveStamp(), true, ignored -> this));
    }

    public void delayNextReplacement() {
        delay = true;
    }

    public void commitBeforeNextCheck() {
        commitOnRead = true;
    }

    public int replacements() {
        return replacements;
    }

    public int advances() {
        return advances;
    }

    public int reads() {
        return reads;
    }

    public boolean release() {
        return delegate.replace(delayedFilter, delayedDocument);
    }

    @Override
    public Document read(int uid) {
        reads++;
        if (commitOnRead) {
            commitOnRead = false;
            if (!release()) throw new IllegalStateException("Delayed original did not commit");
        }
        return delegate.read(uid);
    }

    @Override
    public void insert(Document document) {
        delegate.insert(document);
    }

    @Override
    public boolean replace(Document filter, Document document) {
        replacements++;
        if (delay) {
            delay = false;
            delayedFilter = filter;
            delayedDocument = document;
            throw new IllegalStateException("Uncertain deferred Player replacement");
        }
        return delegate.replace(filter, document);
    }

    @Override
    public boolean advance(Document filter, PlayerSaveStamp stamp) {
        advances++;
        return delegate.advance(filter, stamp);
    }
}
