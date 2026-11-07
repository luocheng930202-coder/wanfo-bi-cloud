package com.wanfo.bi.query;

import java.util.*;
import java.math.BigDecimal;
import com.wanfo.bi.domain.BiDatasetFieldDO;
import com.wanfo.bi.domain.dataset.DatasetDerivedField;

/** Compiles user grouping rules through the same scalar whitelist as calculated fields. */
public final class DatasetDerivedFields {
    private DatasetDerivedFields(){}
    public static List<BiDatasetFieldDO> compile(List<BiDatasetFieldDO> base,List<DatasetDerivedField> operations){
        if(operations==null || operations.isEmpty())return List.of();
        if(base.size()+operations.size()>200)throw new IllegalArgumentException("数据集最多包含 200 个字段");
        List<BiDatasetFieldDO> available=new ArrayList<>(base),result=new ArrayList<>();
        Set<String> names=new HashSet<>();base.forEach(f->names.add(f.getOriginName().toLowerCase(Locale.ROOT)));
        int order=base.stream().map(BiDatasetFieldDO::getOrderNum).filter(Objects::nonNull).mapToInt(Integer::intValue).max().orElse(-1)+1;
        for(var operation:operations){
            if(operation==null || operation.originName()==null || !operation.originName().matches("[A-Za-z_][A-Za-z0-9_]{0,63}") || operation.originName().matches("r[0-9]*_.*") || !names.add(operation.originName().toLowerCase(Locale.ROOT)))throw new IllegalArgumentException("派生字段名须唯一，使用字母数字下划线，且不能使用关联字段前缀");
            String formula;
            if("group".equals(operation.kind()))formula=groupFormula(operation,available);
            else if("calc".equals(operation.kind()))formula=operation.formula();
            else throw new IllegalArgumentException("不支持的派生字段类型");
            String label=operation.showName()==null || operation.showName().isBlank()?operation.originName():operation.showName().trim();
            if(label.length()>200)throw new IllegalArgumentException("字段显示名称不能超过 200 字");
            if(operation.dimFlag()!=null && !Set.of("0","1").contains(operation.dimFlag()))throw new IllegalArgumentException("字段角色无效");
            BiDatasetFieldDO field=new BiDatasetFieldDO();field.setOriginName(operation.originName());field.setShowName(label);
            field.setOriginType(CalcFieldTypes.infer(formula,available));field.setExtFlag("1");field.setExtFormula(formula);field.setDimFlag("group".equals(operation.kind())?"1":"0".equals(operation.dimFlag())?"0":"1");field.setGroupName("group".equals(operation.kind())?"分组字段":"计算字段");field.setOrderNum(order++);field.setStatus("0");
            available.add(field);result.add(field);
        }
        return result;
    }
    private static String groupFormula(DatasetDerivedField operation,List<BiDatasetFieldDO> available){
        var source=available.stream().filter(f->f.getOriginName().equals(operation.sourceField())).findFirst().orElseThrow(()->new IllegalArgumentException("请选择有效的分组来源字段"));
        if(operation.groups()==null || operation.groups().isEmpty() || operation.groups().size()>10)throw new IllegalArgumentException("请配置 1～10 个分组");
        String reference="\""+source.getOriginName().replace("\"","\"\"")+"\"";
        boolean numeric=source.getOriginType()!=null && source.getOriginType().toUpperCase(Locale.ROOT).matches(".*(INT|DECIMAL|NUMERIC|NUMBER|DOUBLE|FLOAT|REAL|MONEY).*");
        Set<String> values=new HashSet<>();StringBuilder expression=new StringBuilder("CASE ");
        for(var group:operation.groups()){
            if(group==null || group.label()==null || group.label().isBlank() || group.label().length()>100 || group.values()==null || group.values().isEmpty() || group.values().size()>50)throw new IllegalArgumentException("每个分组须包含名称和 1～50 个值");
            List<String> checks=new ArrayList<>();
            for(String value:group.values()){
                if(value==null || value.length()>200)throw new IllegalArgumentException("分组值无效或过长");
                String literal=numeric?new BigDecimal(value).stripTrailingZeros().toPlainString():quote(value);
                if(!values.add(literal))throw new IllegalArgumentException("同一个值不能属于多个分组");
                checks.add(reference+" = "+literal);
            }
            expression.append("WHEN (").append(String.join(" OR ",checks)).append(") THEN ").append(quote(group.label())).append(' ');
        }
        String other=operation.otherLabel()==null?"其他":operation.otherLabel();if(other.length()>100)throw new IllegalArgumentException("其他分组名称过长");
        return expression.append("ELSE ").append(quote(other)).append(" END").toString();
    }
    private static String quote(String value){return "'"+value.replace("'","''")+"'";}
}
