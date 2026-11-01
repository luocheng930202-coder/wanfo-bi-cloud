package com.wanfo.bi.mapper;

import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface TaskPoolLaneMapper {
    @Select("""
            SELECT lane,paused,max_concurrent AS maxConcurrent,priority,weight,
                   update_by AS updateBy,update_time AS updateTime
            FROM dp_task_pool_lane ORDER BY priority ASC, lane
            """)
    List<Map<String, Object>> list();

    @Select("""
            SELECT lane,paused,max_concurrent AS maxConcurrent,priority,weight,
                   update_by AS updateBy,update_time AS updateTime
            FROM dp_task_pool_lane WHERE lane=#{lane}
            """)
    Map<String, Object> find(@Param("lane") String lane);

    @Insert("""
            INSERT INTO dp_task_pool_lane(lane,paused,max_concurrent,priority,weight,update_by)
            VALUES(#{lane},#{paused},#{maxConcurrent},#{priority},#{weight},#{user})
            ON DUPLICATE KEY UPDATE paused=VALUES(paused),max_concurrent=VALUES(max_concurrent),
                priority=VALUES(priority),weight=VALUES(weight),
                update_by=VALUES(update_by),update_time=NOW(3)
            """)
    int upsert(Map<String, Object> row);

    @Update("""
            UPDATE dp_task_pool_lane SET paused=#{paused},max_concurrent=#{maxConcurrent},
                priority=#{priority},weight=#{weight},
                update_by=#{user},update_time=NOW(3)
            WHERE lane=#{lane}
            """)
    int update(Map<String, Object> row);
}
