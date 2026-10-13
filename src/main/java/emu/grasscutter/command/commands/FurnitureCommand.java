package emu.grasscutter.command.commands;

import emu.grasscutter.command.*;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.net.proto.RetcodeOuterClass.Retcode;

import java.util.List;

@Command(
        label = "furniture",
        usage = {"help"},
        targetRequirement = Command.TargetRequirement.NONE)
public final class FurnitureCommand implements CommandHandler {
    @Override
    public void execute(Player sender, Player targetPlayer, List<String> args) {
        if (sender == null) {
            CommandHandler.sendTranslatedMessage(null, "commands.generic.player_execute_error");
            return;
        }
        if (args.size() != 1 || !args.get(0).equalsIgnoreCase("help")) {
            sendUsageMessage(sender);
            return;
        }
        int result = sender.getFurnitureManager().helpMake();
        String message =
                switch (result) {
                    case 0 -> "help_success";
                    case Retcode.RET_FURNITURE_MAKE_NOT_IN_CORRECT_HOME_VALUE -> "visit_required";
                    case Retcode.RET_FURNITURE_MAKE_NO_COUNT_VALUE -> "daily_limit";
                    case Retcode.RET_FURNITURE_MAKE_ACCELERATE_LIMIT_VALUE -> "acceleration_limit";
                    case Retcode.RET_FURNITURE_MAKE_NO_MAKE_DATA_VALUE,
                            Retcode.RET_FURNITURE_MAKE_IS_FINISH_VALUE -> "no_jobs";
                    default -> "help_error";
                };
        CommandHandler.sendTranslatedMessage(sender, "commands.furniture." + message);
    }
}
