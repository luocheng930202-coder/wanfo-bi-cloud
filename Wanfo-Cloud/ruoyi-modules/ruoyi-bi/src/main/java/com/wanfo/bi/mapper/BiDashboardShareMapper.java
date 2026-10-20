package com.wanfo.bi.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Param;

import com.wanfo.bi.domain.BiDashboardShareDO;

public interface BiDashboardShareMapper
{
    @org.apache.ibatis.annotations.Select("SELECT l.id,l.share_id AS shareId,l.viewer_principal AS viewerPrincipal,l.event,l.create_time AS createTime FROM bi_share_access_log l JOIN bi_dashboard_share s ON s.id=l.share_id WHERE s.dashboard_id=#{dashboard} ORDER BY l.id DESC LIMIT 200")
    List<java.util.Map<String,Object>> accessLogs(Long dashboard);
    @org.apache.ibatis.annotations.Delete("DELETE FROM bi_share_password_attempt WHERE window_start<DATE_SUB(NOW(),INTERVAL 1 DAY) LIMIT 1000")
    int purgePasswordAttempts();
    @org.apache.ibatis.annotations.Delete("DELETE FROM bi_share_access_log WHERE create_time<DATE_SUB(NOW(),INTERVAL #{days} DAY) LIMIT 1000")
    int purgeAccessLogs(int days);
    @org.apache.ibatis.annotations.Insert("INSERT INTO bi_share_password_attempt(share_id,viewer_principal,attempts,window_start) VALUES(#{share},#{viewer},1,NOW()) ON DUPLICATE KEY UPDATE attempts=IF(window_start<DATE_SUB(NOW(),INTERVAL 10 MINUTE),1,attempts+1),window_start=IF(window_start<DATE_SUB(NOW(),INTERVAL 10 MINUTE),NOW(),window_start)")
    int passwordAttempt(@Param("share") Long share,@Param("viewer") Long viewer);
    @org.apache.ibatis.annotations.Select("SELECT COALESCE(MAX(attempts),0) FROM bi_share_password_attempt WHERE share_id=#{share} AND viewer_principal=#{viewer} AND window_start>=DATE_SUB(NOW(),INTERVAL 10 MINUTE)")
    int passwordAttempts(@Param("share") Long share,@Param("viewer") Long viewer);
    @org.apache.ibatis.annotations.Delete("DELETE FROM bi_share_password_attempt WHERE share_id=#{share} AND viewer_principal=#{viewer}")
    int clearPasswordAttempts(@Param("share") Long share,@Param("viewer") Long viewer);
    @org.apache.ibatis.annotations.Update("UPDATE bi_dashboard_share SET password_hash=#{replacement} WHERE id=#{id} AND password_hash=#{previous}")
    int upgradePassword(@Param("id") Long id,@Param("previous") String previous,@Param("replacement") String replacement);
    @org.apache.ibatis.annotations.Insert("INSERT INTO bi_share_access_log(share_id,viewer_principal,event) VALUES(#{share},#{viewer},#{event})")
    int auditAccess(@Param("share") Long share,@Param("viewer") Long viewer,@Param("event") String event);
    List<BiDashboardShareDO> listByDashboard(Long dashboardId);

    BiDashboardShareDO selectByToken(String token);

    BiDashboardShareDO selectById(Long id);

    int insert(BiDashboardShareDO row);

    int disable(@Param("id") Long id, @Param("dashboardId") Long dashboardId);
}
