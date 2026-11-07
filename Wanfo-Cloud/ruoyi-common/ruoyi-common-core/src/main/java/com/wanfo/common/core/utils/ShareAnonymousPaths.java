package com.wanfo.common.core.utils;

/** Read paths a publisher-scope share may call without a login token. */
public final class ShareAnonymousPaths {
    private ShareAnonymousPaths() {}

    public static boolean allows(String url) {
        if (url == null || url.isBlank()) return false;
        String path = url;
        int query = path.indexOf('?');
        if (query >= 0) path = path.substring(0, query);
        return path.matches("^/bi/share/[^/]+/view$")
                || path.matches("^/bi/share/[^/]+/panels/[^/]+/data$")
                || path.matches("^/bi/share/[^/]+/controls/[^/]+/options$")
                || path.matches("^/bi/geo/[^/]+$");
    }
}
