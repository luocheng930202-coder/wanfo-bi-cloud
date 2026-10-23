package com.wanfo.bi.mapper;

import org.apache.ibatis.annotations.*;

@Mapper
public interface BiSourceSnapshotMapper {
    record Row(Long datasourceId,String kind,String configCipher,String columnsJson,String rowsJson,int rowCount,long version,String fileName,String refreshedAt){}
    @Select("SELECT datasource_id AS datasourceId,kind,config_cipher AS configCipher,columns_json AS columnsJson,rows_json AS rowsJson,row_count AS rowCount,version,file_name AS fileName,CAST(refreshed_at AS CHAR) AS refreshedAt FROM bi_source_snapshot WHERE datasource_id=#{id}")
    Row get(Long id);
    @Insert("INSERT INTO bi_source_snapshot(datasource_id,kind,config_cipher,columns_json,rows_json,row_count,version,file_name) VALUES(#{datasourceId},#{kind},#{configCipher},#{columnsJson},#{rowsJson},#{rowCount},1,#{fileName})")
    int insert(Row row);
    @Update("UPDATE bi_source_snapshot SET config_cipher=#{configCipher},columns_json=#{columnsJson},rows_json=#{rowsJson},row_count=#{rowCount},file_name=#{fileName},version=version+1,refreshed_at=CURRENT_TIMESTAMP WHERE datasource_id=#{datasourceId} AND version=#{version}")
    int replace(Row row);
}
