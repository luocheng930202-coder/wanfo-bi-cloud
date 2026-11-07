package com.wanfo.bi.service;

import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import com.wanfo.bi.mapper.BiChartMapper;
import com.wanfo.bi.mapper.BiDashboardMapper;
import com.wanfo.bi.mapper.BiResourceCoverMapper;
import com.wanfo.bi.security.BiAccessException;
import com.wanfo.bi.security.BiResourceAuthorizer;
import org.springframework.stereotype.Service;

/** Snapshot of the preview on screen at save or publish. Opening the resource still queries with the viewer's permission. */
@Service
public class ResourceCoverService {
    private static final int MAX_BYTES = 512 * 1024;
    private final BiResourceCoverMapper covers;
    private final BiChartMapper charts;
    private final BiDashboardMapper dashboards;
    private final BiResourceAuthorizer resources;

    public ResourceCoverService(BiResourceCoverMapper covers, BiChartMapper charts, BiDashboardMapper dashboards, BiResourceAuthorizer resources) {
        this.covers = covers;
        this.charts = charts;
        this.dashboards = dashboards;
        this.resources = resources;
    }

    public Map<Long, Integer> versions(String kind, List<Long> ids) {
        if (ids == null || ids.isEmpty()) return Map.of();
        Map<Long, Integer> result = new HashMap<>();
        for (var row : covers.versions(kind, ids)) {
            Object id = row.get("resourceId");
            Object version = row.get("version");
            if (id instanceof Number n && version instanceof Number v) result.put(n.longValue(), v.intValue());
        }
        return result;
    }

    public record Image(String mediaType, byte[] body) {}

    public Image read(String kind, Long id, int version) {
        String normalized = normalize(kind);
        require(normalized, id, "view");
        var row = covers.find(normalized, id);
        if (row == null || !(row.get("version") instanceof Number stored) || stored.intValue() != version) return null;
        String data = String.valueOf(row.get("dataUrl"));
        int comma = data.indexOf(',');
        if (comma < 0) return null;
        byte[] body;
        try { body = Base64.getDecoder().decode(data.substring(comma + 1)); }
        catch (IllegalArgumentException ex) { return null; }
        return new Image(String.valueOf(row.get("mediaType")), body);
    }

    public void save(String kind, Long id, int version, String dataUrl) {
        String normalized = normalize(kind);
        require(normalized, id, "edit");
        int current = currentVersion(normalized, id);
        if (current != version) throw new BiAccessException(409, "资源版本已变化，请重新保存后再更新封面");
        Parsed parsed = parse(dataUrl);
        covers.save(normalized, id, version, parsed.media, dataUrl.trim());
    }

    private int currentVersion(String kind, Long id) {
        Integer version = "chart".equals(kind) ? versionOf(charts.get(id)) : versionOf(dashboards.get(id));
        if (version == null) throw new IllegalArgumentException("资源不存在");
        return version;
    }

    private static Integer versionOf(com.wanfo.bi.domain.BiChartDO row) { return row == null ? null : row.getVersion(); }
    private static Integer versionOf(com.wanfo.bi.domain.BiDashboardDO row) { return row == null ? null : row.getVersion(); }

    private void require(String kind, Long id, String action) {
        resources.require(kind, id, "view");
        if (!"view".equals(action)) resources.require(kind, id, action);
        boolean missing = "chart".equals(kind) ? charts.get(id) == null : dashboards.get(id) == null;
        if (missing) throw new IllegalArgumentException("资源不存在");
    }

    private static String normalize(String kind) {
        String value = kind == null ? "" : kind.trim().toLowerCase(Locale.ROOT);
        if (!Set.of("chart", "dashboard").contains(value)) throw new IllegalArgumentException("封面类型无效");
        return value;
    }

    private record Parsed(String media) {}

    private static Parsed parse(String dataUrl) {
        if (dataUrl == null) throw new IllegalArgumentException("封面不能为空");
        String value = dataUrl.trim();
        String media;
        if (value.startsWith("data:image/png;base64,")) media = "image/png";
        else if (value.startsWith("data:image/jpeg;base64,") || value.startsWith("data:image/jpg;base64,")) media = "image/jpeg";
        else if (value.startsWith("data:image/webp;base64,")) media = "image/webp";
        else throw new IllegalArgumentException("封面只接受 PNG、JPEG 或 WebP");
        int comma = value.indexOf(',');
        byte[] body;
        try { body = Base64.getDecoder().decode(value.substring(comma + 1)); }
        catch (IllegalArgumentException ex) { throw new IllegalArgumentException("封面内容无效"); }
        if (body.length < 16 || body.length > MAX_BYTES) throw new IllegalArgumentException("封面须小于 512KB");
        boolean png = body[0] == (byte) 0x89 && body[1] == 0x50 && body[2] == 0x4E && body[3] == 0x47;
        boolean jpeg = (body[0] & 0xFF) == 0xFF && (body[1] & 0xFF) == 0xD8 && (body[2] & 0xFF) == 0xFF;
        boolean webp = body[0] == 'R' && body[1] == 'I' && body[2] == 'F' && body[3] == 'F'
                && body.length > 12 && body[8] == 'W' && body[9] == 'E' && body[10] == 'B' && body[11] == 'P';
        boolean matches = switch (media) {
            case "image/png" -> png;
            case "image/jpeg" -> jpeg;
            case "image/webp" -> webp;
            default -> false;
        };
        if (!matches) throw new IllegalArgumentException("封面内容与类型不一致");
        return new Parsed(media);
    }
}
