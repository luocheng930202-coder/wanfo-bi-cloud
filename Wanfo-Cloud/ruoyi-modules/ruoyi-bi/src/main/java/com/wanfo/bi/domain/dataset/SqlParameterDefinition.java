package com.wanfo.bi.domain.dataset;
import java.util.List;
public record SqlParameterDefinition(String name,String label,String type,boolean multiple,boolean required,
        List<String> defaultValues,String defaultScope) {
    public SqlParameterDefinition publicView(){return new SqlParameterDefinition(name,label,type,multiple,required,List.of(),defaultScope);}
}
