package com.wanfo.bi.domain.dataset;

import java.util.List;

/** Dataset relationship graph, serialized in bi_dataset_table.model_json. */
public class DatasetJoinModel {
    /** Legacy single-join fields remain readable for existing saved models and clients. */
    private String joinType;
    private String rightTable;
    private String leftKey;
    private String rightKey;
    private List<String> rightFields;
    private List<JoinStep> joins;

    public static class JoinStep {
        private String joinType;
        private String rightTable;
        private String leftKey;
        private String rightKey;
        private List<String> rightFields;

        public String getJoinType() { return joinType; }
        public void setJoinType(String joinType) { this.joinType = joinType; }
        public String getRightTable() { return rightTable; }
        public void setRightTable(String rightTable) { this.rightTable = rightTable; }
        public String getLeftKey() { return leftKey; }
        public void setLeftKey(String leftKey) { this.leftKey = leftKey; }
        public String getRightKey() { return rightKey; }
        public void setRightKey(String rightKey) { this.rightKey = rightKey; }
        public List<String> getRightFields() { return rightFields; }
        public void setRightFields(List<String> rightFields) { this.rightFields = rightFields; }
    }

    public List<JoinStep> normalizedJoins() {
        if (joins != null && !joins.isEmpty()) return joins;
        if (rightTable == null || rightTable.isBlank()) return List.of();
        JoinStep step = new JoinStep();
        step.setJoinType(joinType);
        step.setRightTable(rightTable);
        step.setLeftKey(leftKey);
        step.setRightKey(rightKey);
        step.setRightFields(rightFields);
        return List.of(step);
    }

    public static String fieldPrefix(int oneBasedIndex) {
        if (oneBasedIndex < 1 || oneBasedIndex > 8) throw new IllegalArgumentException("关联序号须为 1～8");
        return oneBasedIndex == 1 ? "r_" : "r" + oneBasedIndex + "_";
    }

    public String getJoinType() { return joinType; }
    public void setJoinType(String joinType) { this.joinType = joinType; }
    public String getRightTable() { return rightTable; }
    public void setRightTable(String rightTable) { this.rightTable = rightTable; }
    public String getLeftKey() { return leftKey; }
    public void setLeftKey(String leftKey) { this.leftKey = leftKey; }
    public String getRightKey() { return rightKey; }
    public void setRightKey(String rightKey) { this.rightKey = rightKey; }
    public List<String> getRightFields() { return rightFields; }
    public void setRightFields(List<String> rightFields) { this.rightFields = rightFields; }
    public List<JoinStep> getJoins() { return joins; }
    public void setJoins(List<JoinStep> joins) { this.joins = joins; }
}
