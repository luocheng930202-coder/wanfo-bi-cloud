package com.wanfo.bi.domain.dataset;

import java.util.List;

/** Ordered workspace operation; group values use exact equality and first-match precedence. */
public record DatasetDerivedField(String kind,String originName,String showName,String dimFlag,String formula,
        String sourceField,List<Group> groups,String otherLabel) {
    public record Group(String label,List<String> values){}
}
