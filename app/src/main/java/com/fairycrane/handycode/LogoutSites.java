package com.fairycrane.handycode;

/** Registrable sites used by the two independent official login flows. */
public final class LogoutSites {
    private LogoutSites() {}
    public static String[] forPage(int page) {
        if (page == 0) return new String[]{"taobao.com", "tmall.com", "alipay.com", "cainiao.com", "alibaba.com"};
        if (page == 1) return new String[]{"pinduoduo.net"};
        throw new IllegalArgumentException("Unknown page");
    }
}
