import com.fairycrane.handycode.UrlPolicy;

public final class UrlPolicyTest {
    private static int checked;
    private static void expect(boolean expected, String url) {
        checked++;
        if (UrlPolicy.isAllowed(url) != expected)
            throw new AssertionError("Unexpected URL policy result: " + url);
    }
    public static void main(String[] args) {
        for (String url : new String[]{UrlPolicy.TAOBAO_ENTRY, UrlPolicy.TAOBAO_PARCELS_ENTRY,
                "https://login.m.taobao.com/login.htm", "https://login.taobao.com:443/",
                "HTTPS://LOGIN.TAOBAO.COM/", "https://memberprod.alipay.com/",
                "https://www.cainiao.com/", "https://login.alibaba.com/", "https://login.tmall.com/"})
            expect(true, url);
        for (String url : new String[]{null, "", "http://login.taobao.com/", "javascript:alert(1)",
                "file:///etc/passwd", "content://taobao.com/", "intent://taobao.com/",
                "taobao://m.taobao.com/", "https://taobao.com.evil.example/", "https://eviltaobao.com/",
                "https://taobao.com@evil.example/", "https://evil.example@taobao.com/",
                "https://login.taobao.com:444/", "https://taobao.com\\@evil.example/",
                "https://taobao.com./", "https://taobao.com%2eevil.example/",
                "https://taobao.com/\n", " https://taobao.com/", "https:///taobao.com/",
                "https://login.taobao.com:0/"})
            expect(false, url);
        // Pinduoduo is an external app shortcut, never an in-app WebView host.
        for (String url : new String[]{"https://pinduoduo.com/", "https://passport.pinduoduo.com/",
                "https://mobile.yangkeduo.com/", "https://m.pinduoduo.net/mdkd/package?tab=ID_CODE",
                "pinduoduo://com.xunmeng.pinduoduo/mdkd/package?tab=ID_CODE"})
            expect(false, url);
        String addPhone = UrlPolicy.TAOBAO_ENTRY.replace("identity-code", "add-friend-phone-ssr");
        if (!UrlPolicy.isTaobaoAddPhonePage(addPhone + "?skeleton=true")
                || UrlPolicy.isTaobaoAddPhonePage(UrlPolicy.TAOBAO_ENTRY)
                || UrlPolicy.isTaobaoAddPhonePage(addPhone.replace("pages-fast.m.taobao.com", "login.taobao.com")))
            throw new AssertionError("The nested phone window must start at the official add-phone form.");
        for (String idUrl : new String[]{
                UrlPolicy.TAOBAO_ENTRY,
                UrlPolicy.TAOBAO_ENTRY + "/",
                UrlPolicy.TAOBAO_ENTRY + "?spm=test",
                UrlPolicy.TAOBAO_ENTRY.replace("pages-fast.m.taobao.com", "pages.m.taobao.com")}) {
            checked++;
            if (!UrlPolicy.isTaobaoIdentityEntry(idUrl))
                throw new AssertionError("Expected identity entry match: " + idUrl);
        }
        for (String nonId : new String[]{null, UrlPolicy.TAOBAO_PARCELS_ENTRY,
                "https://login.m.taobao.com/login.htm",
                "https://pages-fast.m.taobao.com.evil.example/wow/z/uniapp/1011717/last-mile-fe/end-collect-platform/identity-code"}) {
            checked++;
            if (UrlPolicy.isTaobaoIdentityEntry(nonId))
                throw new AssertionError("Unexpected identity entry match: " + nonId);
        }
        for (String authUrl : new String[]{
                "https://login.m.taobao.com/login.htm",
                "https://passport.taobao.com/ac/password_find.htm",
                "https://login.tmall.com/",
                "https://aq.taobao.com/durex/validate",
                "https://sec.taobao.com/query.htm",
                "https://h5api.m.taobao.com/h5/mtop.test/1.0/_____tmd_____/punish"}) {
            checked++;
            if (!UrlPolicy.isTaobaoAuthUrl(authUrl))
                throw new AssertionError("Expected Taobao auth/verify match: " + authUrl);
        }
        for (String nonAuth : new String[]{null, UrlPolicy.TAOBAO_ENTRY, UrlPolicy.TAOBAO_PARCELS_ENTRY,
                "https://login.taobao.com.evil.example/"}) {
            checked++;
            if (UrlPolicy.isTaobaoAuthUrl(nonAuth))
                throw new AssertionError("Unexpected Taobao auth/verify match: " + nonAuth);
        }
        System.out.println("PASS: " + checked + " Taobao navigation allowlist cases.");
    }
}
