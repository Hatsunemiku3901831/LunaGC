package emu.grasscutter.server.http.objects;

/** Session endpoint shape for an already successful passport verification. */
public final class SessionVerifyResponseJson {
    public String message;
    public int retcode;
    public SessionData data;

    public SessionVerifyResponseJson(VerifySTokenResponseJson verified) {
        message = verified.message;
        retcode = verified.retcode;
        data = new SessionData();
        data.user_info = verified.data.user_info;
        data.token = verified.data.tokens.get(0);
        data.ext_user_info = verified.data.ext_user_info;
    }

    public static class SessionData {
        public VerifySTokenResponseJson.UserInfoData user_info;
        public VerifySTokenResponseJson.TokenData token;
        public VerifySTokenResponseJson.ExtUserInfoData ext_user_info;
    }
}
