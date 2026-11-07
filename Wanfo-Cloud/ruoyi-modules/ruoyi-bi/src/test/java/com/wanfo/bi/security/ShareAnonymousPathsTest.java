package com.wanfo.bi.security;

import com.wanfo.common.core.utils.ShareAnonymousPaths;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ShareAnonymousPathsTest {
    @Test void onlyPublisherShareReadsSkipGatewayLogin() {
        assertTrue(ShareAnonymousPaths.allows("/bi/share/abc/view"));
        assertTrue(ShareAnonymousPaths.allows("/bi/share/abc/panels/p1/data"));
        assertTrue(ShareAnonymousPaths.allows("/bi/share/abc/controls/c1/options?revision=1"));
        assertFalse(ShareAnonymousPaths.allows("/bi/share/abc"));
        assertFalse(ShareAnonymousPaths.allows("/bi/dashboard/1/shares"));
        assertFalse(ShareAnonymousPaths.allows("/bi/share/abc/panels/p1/data/extra"));
        assertTrue(ShareAnonymousPaths.allows("/bi/geo/regions"));
        assertTrue(ShareAnonymousPaths.allows("/bi/geo/china-provinces"));
        assertFalse(ShareAnonymousPaths.allows("/bi/geo"));
        assertFalse(ShareAnonymousPaths.allows("/bi/geo/regions/extra"));
    }
}
