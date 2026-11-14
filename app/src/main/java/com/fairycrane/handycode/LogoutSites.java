package com.fairycrane.handycode;

/** Registrable sites for logout, indexed by the native tab order. */
public final class LogoutSites {
    private LogoutSites() {}
    public static String[] forPage(int page) {
        if (page == 1) return new String[]{"taobao.com", "tmall.com", "alipay.com", "cainiao.com", "alibaba.com"};
        if (page == 2) return new String[]{"pinduoduo.net"};
        if (page == 3) return new String[]{"jd.com"};
        throw new IllegalArgumentException("Unknown page");
    }
    public static String[] forPickupProvider(int provider) {
        if (provider == 0) return forPage(1);
        if (provider == 1) return forPage(2);
        if (provider == 2) return forPage(3);
        if (provider == 3) return new String[]{"sf-express.com"};
        if (provider == 4) return new String[]{"jinritemai.com", "snssdk.com", "douyin.com", "toutiao.com"};
        throw new IllegalArgumentException("Unknown pickup provider");
    }
}
