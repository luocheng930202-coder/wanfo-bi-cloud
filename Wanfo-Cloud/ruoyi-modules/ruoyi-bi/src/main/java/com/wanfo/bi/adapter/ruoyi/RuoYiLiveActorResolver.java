package com.wanfo.bi.adapter.ruoyi;
import java.util.*;
import javax.sql.DataSource;
import com.wanfo.bi.security.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
@Component
@ConditionalOnProperty(name="bi.security.mode",havingValue="ruoyi",matchIfMissing=true)
public class RuoYiLiveActorResolver implements LiveActorResolver {
 private final JdbcTemplate jdbc;private final PrincipalDirectory directory;
 public RuoYiLiveActorResolver(DataSource source,PrincipalDirectory directory){jdbc=new JdbcTemplate(source);this.directory=directory;}
 public Snapshot resolve(BiActor reference){
  if(reference==null || !"ruoyi".equals(reference.source()))throw new BiAccessException(403,"身份来源不可用");
  var users=jdbc.queryForList("SELECT u.user_id,u.user_name,u.dept_id FROM bi_principal p JOIN sys_user u ON p.external_id=CAST(u.user_id AS CHAR) WHERE p.id=? AND p.source='ruoyi' AND p.principal_type='user' AND u.status='0' AND u.del_flag='0'",reference.userId());
  if(users.size()!=1)throw new BiAccessException(403,"发布者或任务用户已失效");
  var user=users.get(0);long external=((Number)user.get("user_id")).longValue();String name=String.valueOf(user.get("user_name"));Long dept=user.get("dept_id") instanceof Number n?n.longValue():null;
  Long organization=dept==null?null:directory.resolve("ruoyi","organization",dept.toString(),null);
  Set<Long> roles=new HashSet<>();
  for(var r:jdbc.queryForList("SELECT r.role_id,r.role_name FROM sys_user_role ur JOIN sys_role r ON ur.role_id=r.role_id WHERE ur.user_id=? AND r.status='0' AND r.del_flag='0'",external))roles.add(directory.resolve("ruoyi","role",r.get("role_id").toString(),String.valueOf(r.get("role_name"))));
  Set<String> permissions=new HashSet<>();
  if(external==1)permissions.add("*:*:*");else for(var r:jdbc.queryForList("SELECT DISTINCT m.perms FROM sys_user_role ur JOIN sys_role r ON r.role_id=ur.role_id JOIN sys_role_menu rm ON rm.role_id=r.role_id JOIN sys_menu m ON m.menu_id=rm.menu_id WHERE ur.user_id=? AND r.status='0' AND r.del_flag='0' AND m.status='0'",external)){Object p=r.get("perms");if(p instanceof String s && !s.isBlank())permissions.add(s);}
  return new Snapshot(new BiActor("ruoyi",reference.userId(),name,organization,roles),permissions);
 }
}
