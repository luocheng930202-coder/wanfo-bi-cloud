package com.wanfo.bi.service;

import com.wanfo.bi.mapper.BiReportSubscriptionMapper;
import com.wanfo.bi.mapper.BiResourceMapper;
import com.wanfo.bi.mapper.QualityAlertMapper;
import com.wanfo.bi.mapper.QualityTaskMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Quality alert fan-out with window dedupe and SLA escalation.
 * Notifications carry dataset/rule/issue identifiers only — never source row samples.
 */
@Service
public class QualityAlertService {
    private static final Logger LOGGER = LoggerFactory.getLogger(QualityAlertService.class);

    private final QualityAlertMapper alerts;
    private final QualityTaskMapper issues;
    private final BiResourceMapper resources;
    private final BiReportSubscriptionMapper notifications;
    private final int dedupeMinutes;

    public QualityAlertService(QualityAlertMapper alerts, QualityTaskMapper issues, BiResourceMapper resources,
            BiReportSubscriptionMapper notifications,
            @Value("${bi.quality.alert-dedupe-minutes:60}") int dedupeMinutes) {
        this.alerts = alerts;
        this.issues = issues;
        this.resources = resources;
        this.notifications = notifications;
        this.dedupeMinutes = Math.max(5, Math.min(dedupeMinutes, 7 * 24 * 60));
    }

    /** L0 alert after a new quality issue is detected. */
    public void notifyIssueDetected(long datasetId, long ruleId, long issueId, String severity, String ruleCode) {
        sendIfOutsideWindow(datasetId, ruleId, issueId, severity, ruleCode, 0, "质量规则未通过");
    }

    @Scheduled(fixedDelayString = "${bi.quality.sla-escalate-ms:300000}", initialDelayString = "${bi.quality.sla-escalate-ms:300000}")
    public void escalateOverdueClaims() {
        List<Map<String, Object>> overdue = alerts.listOverdueClaimed();
        for (Map<String, Object> row : overdue) {
            try {
                long datasetId = number(row.get("datasetId"));
                long ruleId = number(row.get("ruleId"));
                long issueId = number(row.get("id"));
                String severity = String.valueOf(row.getOrDefault("severity", "MEDIUM"));
                String ruleCode = String.valueOf(row.getOrDefault("ruleCode", ruleId));
                sendIfOutsideWindow(datasetId, ruleId, issueId, severity, ruleCode, 1, "质量问题认领已超 SLA");
                issues.insertIssueHistory(issueId, "system", "ESCALATED", "SLA 逾期告警已升级（无明细）");
            } catch (Exception failure) {
                LOGGER.warn("Quality SLA escalation skipped for issue {}", row.get("id"), failure);
            }
        }
    }

    private void sendIfOutsideWindow(long datasetId, long ruleId, long issueId, String severity, String ruleCode,
            int escalationLevel, String titlePrefix) {
        String key = dedupeKey(datasetId, ruleId, severity);
        Map<String, Object> existing = alerts.findDedupe(key);
        if (existing != null && withinWindow(existing.get("lastSentAt"))
                && number(existing.get("escalationLevel")) >= escalationLevel) {
            return;
        }
        String title = bounded(titlePrefix + " · 数据集#" + datasetId + " · 规则 " + ruleCode, 200);
        String body = bounded("issueId=" + issueId + " ruleId=" + ruleId + " severity=" + severity
                + " escalation=" + escalationLevel + "。未包含源行样例。", 1000);
        Set<Long> recipients = recipients(datasetId, escalationLevel);
        for (Long userId : recipients) {
            Map<String, Object> notice = new LinkedHashMap<>();
            notice.put("userId", userId);
            notice.put("subscriptionId", null);
            notice.put("title", title);
            notice.put("body", body);
            notifications.addNotification(notice);
            Map<String, Object> log = new LinkedHashMap<>();
            log.put("issueId", issueId);
            log.put("datasetId", datasetId);
            log.put("channel", "inbox");
            log.put("recipientUserId", userId);
            log.put("escalationLevel", escalationLevel);
            log.put("title", title);
            log.put("body", body);
            alerts.insertLog(log);
        }
        if (recipients.isEmpty()) {
            Map<String, Object> log = new LinkedHashMap<>();
            log.put("issueId", issueId);
            log.put("datasetId", datasetId);
            log.put("channel", "inbox");
            log.put("recipientUserId", null);
            log.put("escalationLevel", escalationLevel);
            log.put("title", title);
            log.put("body", bounded(body + "（无可用收件人）", 1000));
            alerts.insertLog(log);
        }
        Map<String, Object> dedupe = new LinkedHashMap<>();
        dedupe.put("dedupeKey", key);
        dedupe.put("datasetId", datasetId);
        dedupe.put("ruleId", ruleId);
        dedupe.put("severity", severity == null ? "MEDIUM" : severity.toUpperCase(Locale.ROOT));
        dedupe.put("lastIssueId", issueId);
        dedupe.put("escalationLevel", escalationLevel);
        alerts.upsertDedupe(dedupe);
    }

    private Set<Long> recipients(long datasetId, int escalationLevel) {
        LinkedHashSet<Long> users = new LinkedHashSet<>();
        Long owner = resources.owner("dataset", datasetId);
        if (owner != null && owner > 0) users.add(owner);
        for (Long steward : resources.granted("dataset", datasetId, "grant"))
            if (steward != null && steward > 0) users.add(steward);
        if (escalationLevel >= 1)
            for (Long viewer : resources.granted("dataset", datasetId, "query"))
                if (viewer != null && viewer > 0) users.add(viewer);
        return users;
    }

    private boolean withinWindow(Object lastSentAt) {
        Instant last = toInstant(lastSentAt);
        if (last == null) return false;
        return Duration.between(last, Instant.now()).toMinutes() < dedupeMinutes;
    }

    private static Instant toInstant(Object value) {
        if (value == null) return null;
        if (value instanceof Instant instant) return instant;
        if (value instanceof LocalDateTime local) return local.toInstant(ZoneOffset.systemDefault().getRules().getOffset(local));
        if (value instanceof java.sql.Timestamp ts) return ts.toInstant();
        if (value instanceof java.util.Date date) return date.toInstant();
        try { return Instant.parse(String.valueOf(value)); }
        catch (Exception ignored) {
            try { return LocalDateTime.parse(String.valueOf(value).replace(' ', 'T')).toInstant(ZoneOffset.ofHours(8)); }
            catch (Exception ignoredAgain) { return null; }
        }
    }

    static String dedupeKey(long datasetId, long ruleId, String severity) {
        String raw = datasetId + ":" + ruleId + ":" + (severity == null ? "MEDIUM" : severity.toUpperCase(Locale.ROOT));
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private static long number(Object value) {
        return value instanceof Number number ? number.longValue() : 0L;
    }

    private static String bounded(String value, int max) {
        if (value == null) return "";
        return value.substring(0, Math.min(max, value.length()));
    }
}
