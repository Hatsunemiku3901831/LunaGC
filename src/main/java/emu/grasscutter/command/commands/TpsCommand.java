package emu.grasscutter.command.commands;

import emu.grasscutter.command.*;
import emu.grasscutter.data.GameData;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.tps.TpsWeaponSystem;
import java.util.*;

@Command(
        label = "tps",
        usage = {"give [<itemId>]", "wear [<itemId> ...]", "accessory [<accessoryId>]", "refill"},
        permission = "server.tps",
        permissionTargeted = "server.tps.others")
public final class TpsCommand implements CommandHandler {
    @Override
    public void execute(Player sender, Player targetPlayer, List<String> args) {
        if (targetPlayer == null || args.isEmpty()) {
            sendUsageMessage(sender);
            return;
        }
        try {
            switch (args.get(0).toLowerCase(Locale.ROOT)) {
                case "give" -> {
                    if (args.size() > 2) {
                        sendUsageMessage(sender);
                        return;
                    }
                    var ids =
                            args.size() == 2
                                    ? List.of(Integer.parseInt(args.get(1)))
                                    : GameData.getTpsWeaponDataMap().keySet().intStream().sorted().boxed().toList();
                    int added = 0;
                    for (int id : ids) {
                        if (GameData.getTpsWeaponDataMap().containsKey(id)
                                && targetPlayer.getInventory().addItem(id)) added++;
                    }
                    CommandHandler.sendMessage(sender, "TPS weapons available: " + added);
                }
                case "wear" -> {
                    var current = TpsWeaponSystem.currentAvatar(targetPlayer);
                    if (current == null) {
                        CommandHandler.sendMessage(sender, "No active avatar.");
                        return;
                    }
                    var guids = new ArrayList<Long>();
                    for (String arg : args.subList(1, args.size())) {
                        var item = TpsWeaponSystem.findOwnedWeapon(targetPlayer, Integer.parseInt(arg));
                        if (item == null) {
                            CommandHandler.sendMessage(sender, "TPS weapon not owned: " + arg);
                            return;
                        }
                        guids.add(item.getGuid());
                    }
                    int result = TpsWeaponSystem.wear(targetPlayer, current.getAvatar().getGuid(), guids);
                    CommandHandler.sendMessage(sender, "TPS equip retcode: " + result);
                }
                case "accessory" -> {
                    if (args.size() > 2) {
                        sendUsageMessage(sender);
                        return;
                    }
                    var ids =
                            args.size() == 2
                                    ? List.of(Integer.parseInt(args.get(1)))
                                    : GameData.getTpsWeaponAccessoryDataMap()
                                            .keySet()
                                            .intStream()
                                            .sorted()
                                            .boxed()
                                            .toList();
                    long added =
                            ids.stream().filter(id -> TpsWeaponSystem.unlockAccessory(targetPlayer, id)).count();
                    CommandHandler.sendMessage(sender, "TPS accessories unlocked: " + added);
                }
                case "refill" -> {
                    if (args.size() != 1) {
                        sendUsageMessage(sender);
                        return;
                    }
                    TpsWeaponSystem.refillAmmunition(targetPlayer);
                    CommandHandler.sendMessage(sender, "TPS ammunition refilled.");
                }
                default -> sendUsageMessage(sender);
            }
        } catch (NumberFormatException e) {
            sendUsageMessage(sender);
        }
    }
}
