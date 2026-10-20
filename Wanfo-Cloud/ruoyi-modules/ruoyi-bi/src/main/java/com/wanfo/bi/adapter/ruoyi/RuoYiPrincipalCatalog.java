package com.wanfo.bi.adapter.ruoyi;

import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import com.wanfo.bi.security.PrincipalCatalog;
import com.wanfo.bi.security.PrincipalDirectory;
import com.wanfo.bi.mapper.BiPrincipalMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Host directory reads remain inside the RuoYi adapter. No passwords or personal contact fields. */
@Component
@ConditionalOnProperty(name="bi.security.mode", havingValue="ruoyi", matchIfMissing=true)
public class RuoYiPrincipalCatalog implements PrincipalCatalog
{
    private final JdbcTemplate jdbc;
    private final PrincipalDirectory directory;
    private final BiPrincipalMapper principals;

    public RuoYiPrincipalCatalog(DataSource source, PrincipalDirectory directory, BiPrincipalMapper principals)
    {
        this.jdbc = new JdbcTemplate(source);
        this.directory = directory;
        this.principals = principals;
    }

    public List<Map<String, Object>> search(String query)
    {
        String pattern = "%" + (query == null ? "" : query.trim()) + "%";
        return jdbc.query("SELECT * FROM ("
                + "SELECT 'user' kind,user_id external_id,user_name label FROM sys_user WHERE status='0' AND del_flag='0' "
                + "UNION ALL SELECT 'role',role_id,role_name FROM sys_role WHERE status='0' AND del_flag='0' "
                + "UNION ALL SELECT 'organization',dept_id,dept_name FROM sys_dept WHERE status='0' AND del_flag='0'"
                + ") p WHERE label LIKE ? ORDER BY kind,external_id LIMIT 50", (rs, row) -> {
                    String kind = rs.getString("kind"), label = rs.getString("label");
                    Long id = directory.resolve("ruoyi", kind, rs.getString("external_id"), label);
                    principals.updateDisplayName(id, label);
                    return Map.<String, Object>of("id", id, "type", kind, "name", label);
                }, pattern);
    }
}
