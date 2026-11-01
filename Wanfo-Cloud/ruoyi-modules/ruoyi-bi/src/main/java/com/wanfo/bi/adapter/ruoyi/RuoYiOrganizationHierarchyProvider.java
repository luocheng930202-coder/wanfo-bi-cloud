package com.wanfo.bi.adapter.ruoyi;

import java.util.List;
import javax.sql.DataSource;
import com.wanfo.bi.security.OrganizationHierarchyProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Resolves organization principal IDs through the RuoYi directory and its active department tree. */
@Component
@ConditionalOnProperty(name="bi.security.mode", havingValue="ruoyi", matchIfMissing=true)
public class RuoYiOrganizationHierarchyProvider implements OrganizationHierarchyProvider
{
    private final JdbcTemplate jdbc;
    public RuoYiOrganizationHierarchyProvider(DataSource source) { this.jdbc = new JdbcTemplate(source); }

    @Override
    public List<Long> activeSubtree(Long organizationPrincipalId)
    {
        if (organizationPrincipalId == null) return List.of();
        var rows = jdbc.queryForList("SELECT dept_id FROM sys_dept WHERE dept_id = ("
                + "SELECT CAST(external_id AS UNSIGNED) FROM bi_principal WHERE id=? AND source='ruoyi' AND principal_type='organization'"
                + ") AND status='0' AND del_flag='0'", organizationPrincipalId);
        if (rows.size() != 1) return List.of();
        long root = ((Number) rows.get(0).get("dept_id")).longValue();
        return jdbc.query("SELECT p.id FROM bi_principal p JOIN sys_dept d ON d.dept_id=CAST(p.external_id AS UNSIGNED) "
                + "WHERE p.source='ruoyi' AND p.principal_type='organization' AND d.status='0' AND d.del_flag='0' "
                + "AND (d.dept_id=? OR FIND_IN_SET(?,d.ancestors)>0) ORDER BY d.dept_id",
                (rs, n) -> rs.getLong(1), root, root);
    }
}
