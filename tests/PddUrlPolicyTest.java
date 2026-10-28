import com.fairycrane.handycode.UrlPolicy;

public final class PddUrlPolicyTest {
    private static int checked;
    private static void expect(boolean result, boolean actual) {
        checked++;
        if (result != actual) throw new AssertionError("PDD URL scope mismatch at case " + checked);
    }
    public static void main(String[] args) {
        String login = "https://m.pinduoduo.net/login.html?redirect=fixture";
        expect(true, UrlPolicy.opensPddIdentity(login, UrlPolicy.PDD_IDENTITY_APP_LINK, true, true));
        expect(false, UrlPolicy.opensPddIdentity(login, UrlPolicy.PDD_IDENTITY_APP_LINK, false, true));
        expect(false, UrlPolicy.opensPddIdentity(login, UrlPolicy.PDD_IDENTITY_APP_LINK, true, false));
        for (String page : new String[]{null,"https://m.pinduoduo.net/mdkd/package?tab=ID_CODE","https://m.pinduoduo.net/psnl_verification.html","https://m.pinduoduo.net.evil.example/login.html","https://name@m.pinduoduo.net/login.html","http://m.pinduoduo.net/login.html"})
            expect(false, UrlPolicy.opensPddIdentity(page, UrlPolicy.PDD_IDENTITY_APP_LINK, true, true));
        for (String target : new String[]{null,"pinduoduo://com.xunmeng.pinduoduo/","intent://anything","https://mobile.yangkeduo.com/",UrlPolicy.PDD_IDENTITY_APP_LINK+"&unexpected=1","pinduoduo://other.package/mdkd/package?tab=ID_CODE"})
            expect(false, UrlPolicy.opensPddIdentity(login, target, true, true));
        for (String url : new String[]{"https://m.pinduoduo.net/mdkd/package?tab=ID_CODE", "https://m.pinduoduo.net/login.html", "https://m.pinduoduo.net/psnl_verification.html", "https://m.pinduoduo.net:443/index.html"})
            expect(true, UrlPolicy.isPddAllowed(url));
        for (String url : new String[]{null,"http://m.pinduoduo.net/","https://m.pinduoduo.net.evil.example/","https://name@m.pinduoduo.net/","https://m.pinduoduo.net:444/","https://api.pinduoduo.com/","https://mobile.yangkeduo.com/","pinduoduo://com.xunmeng.pinduoduo/"})
            expect(false, UrlPolicy.isPddAllowed(url));
        expect(true, UrlPolicy.isPddAppBundle("https://pfile.pddpic.com/mdkd/mdkd/_next/static/chunks/pages/_app-21fd7eb3eb3caac5.js"));
        expect(true, "identity".equals(UrlPolicy.pddRefreshPart("https://pfile.pddpic.com/mdkd/mdkd/_next/static/chunks/1235-91966cd87947ff3c.js")));
        expect(true, "parcels".equals(UrlPolicy.pddRefreshPart("https://pfile.pddpic.com/mdkd/mdkd/_next/static/chunks/8905-86d1ed3163cf59c9.js")));
        for (String invalid : new String[]{"https://pfile.pddpic.com.evil.example/mdkd/mdkd/_next/static/chunks/1235-abc.js","https://name@pfile.pddpic.com/mdkd/mdkd/_next/static/chunks/1235-abc.js","https://pfile.pddpic.com:444/mdkd/mdkd/_next/static/chunks/1235-abc.js","http://pfile.pddpic.com/mdkd/mdkd/_next/static/chunks/1235-abc.js","https://pfile.pddpic.com/other/1235-abc.js","https://pfile.pddpic.com/mdkd/mdkd/_next/static/chunks/9999-abc.js","https://pfile.pddpic.com/mdkd/mdkd/_next/static/chunks/8905-abc.js/extra"})
            expect(true, UrlPolicy.pddRefreshPart(invalid) == null);
        for (String url : new String[]{"https://pfile.pddpic.com.evil.example/mdkd/mdkd/_next/static/chunks/pages/_app-abc.js","http://pfile.pddpic.com/mdkd/mdkd/_next/static/chunks/pages/_app-abc.js","https://name@pfile.pddpic.com/mdkd/mdkd/_next/static/chunks/pages/_app-abc.js","https://pfile.pddpic.com/mdkd/mdkd/_next/static/chunks/pages/package-abc.js","https://pfile.pddpic.com/other/_app-abc.js","https://pfile.pddpic.com/mdkd/mdkd/_next/static/chunks/pages/_app-abc.js/extra"})
            expect(false, UrlPolicy.isPddAppBundle(url));
        System.out.println("PASS: " + checked + " PDD navigation / public-bundle scope cases");
    }
}
