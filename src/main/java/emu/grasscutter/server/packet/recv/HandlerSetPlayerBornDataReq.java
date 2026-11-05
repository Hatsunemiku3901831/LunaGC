package emu.grasscutter.server.packet.recv;

import static emu.grasscutter.config.Configuration.*;

import com.google.protobuf.InvalidProtocolBufferException;
import emu.grasscutter.*;
import emu.grasscutter.command.commands.SendMailCommand.MailBuilder;
import emu.grasscutter.data.GameData;
import emu.grasscutter.database.DatabaseHelper;
import emu.grasscutter.game.avatar.Avatar;
import emu.grasscutter.game.AccountLoginAdmission;
import emu.grasscutter.game.mail.Mail;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.SetPlayerBornDataReqOuterClass.SetPlayerBornDataReq;
import emu.grasscutter.server.game.GameSession;
import emu.grasscutter.server.packet.send.PacketSetPlayerBornDataRsp;
import java.util.Arrays;

@Opcodes(PacketOpcodes.SetPlayerBornDataReq)
public class HandlerSetPlayerBornDataReq extends PacketHandler {

    @Override
    public void handle(GameSession session, byte[] header, byte[] payload) throws Exception {
        AccountLoginAdmission.step(session, () -> handleAdmitted(session, payload));
    }

    private void handleAdmitted(GameSession session, byte[] payload) throws Exception {
        SetPlayerBornDataReq req;
        try {
            req = SetPlayerBornDataReq.parseFrom(payload);
        } catch (InvalidProtocolBufferException malformed) {
            session.send(new PacketSetPlayerBornDataRsp(-1));
            return;
        }
        Player player = session.getPlayer();
        int error = validateRequest(player, req);
        if (error != 0) {
            session.send(new PacketSetPlayerBornDataRsp(error));
            return;
        }

        try {
            createCharacter(player, req);
        } catch (RuntimeException failed) {
            session.send(new PacketSetPlayerBornDataRsp(-1));
            // A partially saved creation must be reloaded, never retried on this in-memory player.
            session.close();
            throw failed;
        }
        player.onLogin();
        session.send(new PacketSetPlayerBornDataRsp(0));
        sendWelcomeMail(player);
    }

    private static int validateRequest(Player player, SetPlayerBornDataReq req) {
        if (player.getAvatars().getAvatarCount() != 0 || player.getMainCharacterId() != 0) return 116;
        int avatarId = req.getAvatarId();
        if (avatarId != GameConstants.MAIN_CHARACTER_MALE
                && avatarId != GameConstants.MAIN_CHARACTER_FEMALE) return -1;
        // Basic server input policy; the full target client nickname validator is still unverified.
        if (req.getNickName().isBlank()
                || req.getNickName().codePoints().anyMatch(Character::isISOControl)) return -1;
        int baseDepot = avatarId == GameConstants.MAIN_CHARACTER_MALE ? 501 : 701;
        int depot = GAME_OPTIONS.questing.enabled ? baseDepot
                : (avatarId == GameConstants.MAIN_CHARACTER_MALE ? 504 : 704);
        if (!GameData.getAvatarDataMap().containsKey(avatarId)
                || !GameData.getAvatarSkillDepotDataMap().containsKey(baseDepot)
                || !GameData.getAvatarSkillDepotDataMap().containsKey(depot)) return -1;
        return 0;
    }

    private static void createCharacter(Player player, SetPlayerBornDataReq req) {
        int avatarId = req.getAvatarId();
        Avatar mainCharacter = new Avatar(avatarId);
        if (!GAME_OPTIONS.questing.enabled) {
            mainCharacter.setSkillDepotData(GameData.getAvatarSkillDepotDataMap()
                    .get(avatarId == GameConstants.MAIN_CHARACTER_MALE ? 504 : 704));
        }
        player.setNickname(req.getNickName());
        player.addAvatar(mainCharacter, false);
        player.setMainCharacterId(avatarId);
        player.setHeadImage(avatarId);
        player.getTeamManager().getCurrentSinglePlayerTeamInfo().getAvatars().add(avatarId);

        // addAvatar/setHeadImage also enqueue writes. Confirm the final objects before onLogin.
        for (var item : mainCharacter.getEquips().values()) DatabaseHelper.saveGameSync(item);
        DatabaseHelper.saveGameSync(mainCharacter);
        DatabaseHelper.saveGameSync(player);
    }

    private static void sendWelcomeMail(Player player) {
        var welcomeMail = GAME_INFO.joinOptions.welcomeMail;
        MailBuilder mailBuilder = new MailBuilder(player.getUid(), new Mail());
        mailBuilder.mail.mailContent.title = welcomeMail.title;
        mailBuilder.mail.mailContent.sender = welcomeMail.sender;
        // Please credit Grasscutter if changing something here. We don't condone commercial use of the
        // project.
        mailBuilder.mail.mailContent.content =
                welcomeMail.content
                        + "\n<type=\"browser\" text=\"GitHub\" href=\"https://github.com/Grasscutters/Grasscutter\"/>";
        mailBuilder.mail.itemList.addAll(Arrays.asList(welcomeMail.items));
        mailBuilder.mail.importance = 1;
        player.sendMail(mailBuilder.mail);
    }
}
