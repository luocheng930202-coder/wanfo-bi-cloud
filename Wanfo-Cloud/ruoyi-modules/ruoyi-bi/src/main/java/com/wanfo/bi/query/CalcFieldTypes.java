package com.wanfo.bi.query;

import java.util.*;
import com.wanfo.bi.domain.BiDatasetFieldDO;
import org.apache.calcite.rel.type.*;
import org.apache.calcite.schema.impl.AbstractTable;
import org.apache.calcite.sql.type.SqlTypeName;
import org.apache.calcite.tools.Frameworks;

/** Validate and infer scalar expressions against metadata, without querying business data. */
public final class CalcFieldTypes {
    private CalcFieldTypes() { }

    public static String infer(String formula, List<BiDatasetFieldDO> fields) {
        Map<String,String> physical=new LinkedHashMap<>();
        fields.stream().filter(f->!"1".equals(f.getExtFlag())).forEach(f->physical.put(f.getOriginName(),f.getOriginType()));
        String expression=CalcFormulaCompiler.compile(formula,physical.keySet());
        var dependencies=CalcFormulaCompiler.references(formula,physical.keySet());
        physical.keySet().retainAll(dependencies);
        var root=Frameworks.createRootSchema(true);
        root.add("input",new AbstractTable() {
            @Override public RelDataType getRowType(RelDataTypeFactory factory) {
                var builder=factory.builder();
                physical.forEach((name,type)->builder.add(name,factory.createTypeWithNullability(sqlType(factory,type),true)));
                return builder.build();
            }
        });
        try(var planner=Frameworks.getPlanner(Frameworks.newConfigBuilder().defaultSchema(root)
                .operatorTable(org.apache.calcite.sql.fun.SqlLibraryOperatorTableFactory.INSTANCE.getOperatorTable(org.apache.calcite.sql.fun.SqlLibrary.STANDARD,org.apache.calcite.sql.fun.SqlLibrary.MYSQL))
                .sqlValidatorConfig(org.apache.calcite.sql.validate.SqlValidator.Config.DEFAULT.withTypeCoercionEnabled(false)).build())) {
            var node=planner.parse("SELECT "+expression+" AS \"value\" FROM \"input\"");
            var validated=planner.validate(node);
            return planner.rel(validated).validatedRowType.getFieldList().get(0).getType().toString();
        } catch(Exception ex) {
            throw new IllegalArgumentException("计算表达式与字段类型不匹配，请检查公式和参数类型",ex);
        }
    }

    private static RelDataType sqlType(RelDataTypeFactory factory,String source) {
        String raw=source==null?"":source.toUpperCase(Locale.ROOT).trim();
        String name=raw.replaceAll("\\(.*", "").replace(" UNSIGNED", "").trim();
        SqlTypeName type=switch(name) {
            case "TINYINT","SMALLINT","MEDIUMINT","INT","INTEGER","INT2","INT4"->SqlTypeName.INTEGER;
            case "BIGINT","INT8","LARGEINT"->SqlTypeName.BIGINT;
            case "DECIMAL","NUMERIC","NUMBER","MONEY"->SqlTypeName.DECIMAL;
            case "FLOAT","DOUBLE","DOUBLE PRECISION","REAL"->SqlTypeName.DOUBLE;
            case "BOOL","BOOLEAN","BIT"->SqlTypeName.BOOLEAN;
            case "DATE"->SqlTypeName.DATE;
            case "DATETIME","TIMESTAMP","TIMESTAMP WITHOUT TIME ZONE"->SqlTypeName.TIMESTAMP;
            case "TIME"->SqlTypeName.TIME;
            case "CHAR","VARCHAR","TEXT","TINYTEXT","MEDIUMTEXT","LONGTEXT","STRING","NVARCHAR","NCHAR"->SqlTypeName.VARCHAR;
            default->throw new IllegalArgumentException("字段类型暂不支持计算："+name);
        };
        if(type==SqlTypeName.DECIMAL) {
            var match=java.util.regex.Pattern.compile("\\((\\d+)\\s*,\\s*(\\d+)\\)").matcher(raw);
            return match.find()?factory.createSqlType(type,Integer.parseInt(match.group(1)),Integer.parseInt(match.group(2))):factory.createSqlType(type,19,6);
        }
        return type==SqlTypeName.VARCHAR?factory.createSqlType(type,65535):factory.createSqlType(type);
    }
}
