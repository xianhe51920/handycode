package com.fairycrane.handycode;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;

/** Official Taobao content and authentication hosts allowed inside WebView. */
public final class UrlPolicy {
    public static final String PDD_IDENTITY_APP_LINK = "pinduoduo://com.xunmeng.pinduoduo/mdkd/package?tab=ID_CODE";
    public static final String TAOBAO_ENTRY =
        "https://pages-fast.m.taobao.com/wow/z/uniapp/1011717/last-mile-fe/end-collect-platform/identity-code";
    public static final String TAOBAO_PARCELS_ENTRY =
        "https://pages-fast.m.taobao.com/wow/z/uniapp/1100333/last-mile-fe/m-end-school-tab/home";
    private static final String[] ALLOWED_HOSTS = {"taobao.com", "tmall.com", "alipay.com", "cainiao.com", "alibaba.com"};
    private UrlPolicy() {}

    public static boolean isAllowed(String raw) {
        URI uri = httpsUri(raw);
        if (uri == null) return false;
        String host = uri.getHost().toLowerCase(Locale.ROOT);
        for (String root : ALLOWED_HOSTS) {
            if (host.equals(root) || host.endsWith("." + root)) return true;
        }
        return false;
    }

    public static boolean isPddAllowed(String raw) {
        URI uri = httpsUri(raw);
        return uri != null && "m.pinduoduo.net".equalsIgnoreCase(uri.getHost());
    }

    public static boolean isPddLoginPage(String raw) {
        return isPddAllowed(raw) && "/login.html".equals(httpsUri(raw).getPath());
    }

    public static boolean opensPddIdentity(String current, String target, boolean mainFrame, boolean gesture) {
        return mainFrame && gesture && isPddLoginPage(current) && PDD_IDENTITY_APP_LINK.equals(target);
    }

    public static boolean isPddAppBundle(String raw) {
        URI uri = httpsUri(raw);
        return uri != null && "pfile.pddpic.com".equalsIgnoreCase(uri.getHost())
            && uri.getPath() != null && uri.getPath().matches("/mdkd/mdkd/_next/static/chunks/pages/_app-[0-9a-f]+\\.js");
    }

    public static String pddRefreshPart(String raw) {
        URI uri = httpsUri(raw);
        if (uri == null || !"pfile.pddpic.com".equalsIgnoreCase(uri.getHost()) || uri.getPath() == null) return null;
        if (uri.getPath().matches("/mdkd/mdkd/_next/static/chunks/1235-[0-9a-f]+\\.js")) return "identity";
        if (uri.getPath().matches("/mdkd/mdkd/_next/static/chunks/8905-[0-9a-f]+\\.js")) return "parcels";
        return null;
    }

    private static URI httpsUri(String raw) {
        if (raw == null || raw.isEmpty() || raw.length() > 8192) return null;
        for (int i = 0; i < raw.length(); i++) {
            if (Character.isISOControl(raw.charAt(i)) || Character.isWhitespace(raw.charAt(i))
                    || raw.charAt(i) == '\\') return null;
        }
        try {
            URI uri = new URI(raw);
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getRawUserInfo() != null
                    || (uri.getPort() != -1 && uri.getPort() != 443)) return null;
            String host = uri.getHost();
            return host == null ? null : uri;
        } catch (URISyntaxException error) {
            return null;
        }
    }
}
