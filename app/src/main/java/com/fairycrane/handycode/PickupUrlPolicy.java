package com.fairycrane.handycode;

import java.net.URI;
import java.util.Locale;

/** Experimental summary-provider navigation; existing JD identity policy stays unchanged. */
public final class PickupUrlPolicy {
    private PickupUrlPolicy() {}
    public static final String ORDERS = "https://trade.m.jd.com/order/orderlist_jdm.shtml?sceneval=2";
    public static final String MY_HOME = "https://my.m.jd.com/index.html?sceneval=2&sourceType=home_m";
    public static final String LOGIN = "https://plogin.m.jd.com/login/login?appid=300&returnurl=https%3A%2F%2Ftrade.m.jd.com%2Forder%2Forderlist_jdm.shtml%3Fsceneval%3D2";
    public static final String SF_ORDERS = "https://www.sf-express.com/chn/sc/waybill/list";
    public static final String DY_ORDERS = "https://haohuo.jinritemai.com/views/neworder/center";
    public static final String DY_LOGIN = "https://www.douyin.com/user/self";

    private static boolean within(String raw, String... roots) {
        if (raw == null || raw.length() > 8192) return false;
        for (char c : raw.toCharArray()) if (Character.isWhitespace(c) || Character.isISOControl(c) || c == '\\') return false;
        try {
            URI uri = new URI(raw);
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null || uri.getRawUserInfo() != null
                    || uri.getPort() != -1 && uri.getPort() != 443) return false;
            String host = uri.getHost().toLowerCase(Locale.ROOT);
            for (String root : roots) if (host.equals(root) || host.endsWith("." + root)) return true;
        } catch (Exception invalid) { return false; }
        return false;
    }
    public static boolean allowed(String raw) { return within(raw, "jd.com"); }
    public static boolean allowedSf(String raw) { return within(raw, "sf-express.com"); }
    public static boolean allowedDy(String raw) { return within(raw, "jinritemai.com", "snssdk.com", "douyin.com", "toutiao.com"); }
}
