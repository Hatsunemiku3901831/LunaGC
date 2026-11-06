package emu.grasscutter.server.packet.recv;

import static emu.grasscutter.config.Configuration.ACCOUNT;

import emu.grasscutter.*;
import emu.grasscutter.database.DatabaseHelper;
import emu.grasscutter.game.Account;
import emu.grasscutter.game.AccountLoginAdmission;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.GetPlayerTokenReqOuterClass.GetPlayerTokenReq;
import emu.grasscutter.server.event.game.PlayerCreationEvent;
import emu.grasscutter.server.game.GameSession;
import emu.grasscutter.server.game.GameSession.SessionState;
import emu.grasscutter.server.packet.send.PacketGetPlayerTokenRsp;
import emu.grasscutter.utils.*;

import java.nio.ByteBuffer;
import java.security.Signature;

import javax.crypto.Cipher;

@Opcodes(PacketOpcodes.GetPlayerTokenReq)
public class HandlerGetPlayerTokenReq extends PacketHandler {

    @Override
    public void handle(GameSession session, byte[] header, byte[] payload) throws Exception {
        var request = GetPlayerTokenReq.parseFrom(payload);
        if (Grasscutter.getConfig().server.game.useXorEncryption && request.getKeyId() <= 0) {
            session.send(new PacketGetPlayerTokenRsp(session, -1));
            return;
        }
        var accountId = request.getAccountUid();
        var accountToken = request.getAccountToken();
        AccountLoginAdmission.begin(session, accountId, accountToken,
                account -> handleAuthenticated(session, request, account));
    }

    private void handleAuthenticated(GameSession session, GetPlayerTokenReq request, Account account) throws Exception {
        var accountId = account.getId();
        var clientRandKey = request.getClientRandKey();
        var keyId = request.getKeyId();

        session.setAccount(account);

        boolean kicked = false;
        var exists = session.getServer().getPlayerByAccountId(accountId);
        if (exists != null) {
            var existsSession = exists.getSession();
            if (existsSession != session) {
                exists.onLogout();
                existsSession.close();
                Grasscutter.getLogger()
                        .warn(
                                "Player {} was kicked due to duplicated login",
                                account.getUsername());
                kicked = true;
            }
        }

        if (!kicked) {

            if (ACCOUNT.maxPlayer > -1
                    && session.getServer().getPlayers().size() >= ACCOUNT.maxPlayer) {
                session.close();
                return;
            }
        }

        var event = new PlayerCreationEvent(session, Player.class);
        event.call();

        var player =
                DatabaseHelper.getOrCreatePlayer(
                        account,
                        event.getPlayerClass(),
                        () -> {
                            try {
                                return event.getPlayerClass()
                                        .getDeclaredConstructor(GameSession.class)
                                        .newInstance(session);
                            } catch (ReflectiveOperationException failed) {
                                throw new IllegalStateException(
                                        "Could not construct a new player", failed);
                            }
                        });

        session.setPlayer(player);

        if (session.getAccount().isBanActive()) {
            session.setState(SessionState.ACCOUNT_BANNED);
            session.send(
                    new PacketGetPlayerTokenRsp(session, 21));
            return;
        }

        player.loadFromDatabase();
        session.setState(SessionState.WAITING_FOR_LOGIN);
        if (!Grasscutter.getConfig().server.game.useXorEncryption) {
            session.send(new PacketGetPlayerTokenRsp(session));
            return;
        }

        var encryptSeed = session.getEncryptSeed();
        try {
            var cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
            cipher.init(Cipher.DECRYPT_MODE, Crypto.CUR_SIGNING_KEY);
            var clientSeedEncrypted = Utils.base64Decode(clientRandKey);
            var clientSeed = ByteBuffer.wrap(cipher.doFinal(clientSeedEncrypted)).getLong();
            var seedBytes = ByteBuffer.wrap(new byte[8]).putLong(encryptSeed ^ clientSeed).array();
            cipher.init(Cipher.ENCRYPT_MODE, Crypto.EncryptionKeys.get(keyId));
            var seedEncrypted = cipher.doFinal(seedBytes);
            var signature = Signature.getInstance("SHA256withRSA");
            signature.initSign(Crypto.CUR_SIGNING_KEY);
            signature.update(seedBytes);
            session.send(new PacketGetPlayerTokenRsp(session,
                    Utils.base64Encode(seedEncrypted), Utils.base64Encode(signature.sign())));
            session.setUseSecretKey(true);
        } catch (Exception failure) {
            Grasscutter.getLogger().error(
                    "GetPlayerTokenReq RSA failed (key_id={}, clientRandKey chars={}): {}",
                    keyId, clientRandKey.length(), failure.getClass().getSimpleName());
            // A refused exchange never installs a key or substitutes a fake signature.
            session.send(new PacketGetPlayerTokenRsp(session, -1));
        }
    }
}
