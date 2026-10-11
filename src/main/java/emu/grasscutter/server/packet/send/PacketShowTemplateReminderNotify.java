package emu.grasscutter.server.packet.send;

import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.ShowTemplateReminderNotifyOuterClass.ShowTemplateReminderNotify;

/** The scene-wide reminder used by the original timed reinforcement scripts. */
public final class PacketShowTemplateReminderNotify extends BasePacket {
    public PacketShowTemplateReminderNotify(int id, int[] parameters) {
        super(PacketOpcodes.ShowTemplateReminderNotify);
        var message = ShowTemplateReminderNotify.newBuilder().setTemplateReminderId(id);
        for (int parameter : parameters) message.addParamList(parameter);
        setData(message.build());
    }
}
