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
}
