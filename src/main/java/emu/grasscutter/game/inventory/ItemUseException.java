package emu.grasscutter.game.inventory;

import emu.grasscutter.net.proto.RetcodeOuterClass.Retcode;

/** An expected item-use rejection that the request handler can report without retrying. */
public final class ItemUseException extends RuntimeException {
    private final Retcode retcode;

    public ItemUseException(Retcode retcode) {
        super(retcode.name());
        this.retcode = retcode;
    }

    public Retcode getRetcode() {
        return retcode;
    }
}
