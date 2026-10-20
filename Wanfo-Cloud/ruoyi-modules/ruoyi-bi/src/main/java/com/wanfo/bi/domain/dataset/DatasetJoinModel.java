package com.wanfo.bi.domain.dataset;
import java.util.List;
/** Single-table join extension stored in bi_dataset_table.model_json. */
public class DatasetJoinModel {
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
