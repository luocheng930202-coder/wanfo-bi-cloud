package com.wanfo.bi.service;

import com.wanfo.bi.mapper.*;
import com.wanfo.bi.query.CalcFieldTypes;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.slf4j.LoggerFactory;

/** Idempotent metadata repair after Flyway; invalid legacy formulas stay restricted. */
@Component
public class CalculatedTypeRepair implements ApplicationRunner {
    private final BiDatasetTableMapper datasets;
    private final BiDatasetFieldMapper fields;
    public CalculatedTypeRepair(BiDatasetTableMapper datasets,BiDatasetFieldMapper fields){this.datasets=datasets;this.fields=fields;}
    @Override public void run(ApplicationArguments args) {
        int repaired=0;
        for(var dataset:datasets.selectAll(null)) {
            var definitions=fields.selectByDatasetId(dataset.getId());
            for(var field:definitions)if("1".equals(field.getExtFlag())&&"CALC".equals(field.getOriginType())) {
                try {repaired+=fields.repairCalculatedType(field.getId(),field.getExtFormula(),CalcFieldTypes.infer(field.getExtFormula(),definitions));}
                catch(IllegalArgumentException ex){LoggerFactory.getLogger(getClass()).warn("Calculated field {} type repair rejected; definition requires review",field.getId());}
            }
        }
        LoggerFactory.getLogger(getClass()).info("Repaired {} legacy calculated field types",repaired);
    }
}
