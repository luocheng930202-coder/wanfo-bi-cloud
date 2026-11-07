package com.wanfo.bi.service;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;

/** Classifies source-comment edits separately from structural schema changes. */
public final class AssetMetadataDiff {
    private AssetMetadataDiff() {}

    public static boolean commentOnlyColumn(Map<String, Object> before, Map<String, Object> after) {
        var keys = new LinkedHashSet<String>();
        if (before != null) keys.addAll(before.keySet());
        if (after != null) keys.addAll(after.keySet());
        boolean commentDiff = false;
        for (String key : keys) {
            Object left = before == null ? null : before.get(key);
            Object right = after == null ? null : after.get(key);
            if (Objects.equals(left, right)) continue;
            if (!"comment".equals(key)) return false;
            commentDiff = true;
        }
        return commentDiff;
    }

    public static String impactSeverity(boolean structural, boolean commentOnly, boolean liveApi, boolean joinSql, boolean metrics) {
        if (structural || (liveApi && !commentOnly)) return "BLOCKING";
        if (!commentOnly && (joinSql || metrics)) return "REVIEW";
        return "COMPATIBLE";
    }
}
