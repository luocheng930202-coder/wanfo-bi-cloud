package com.wanfo.bi.domain.chart;
import java.util.List;
public class ChartAdvancedOptions {
    public record MapFields(String province,String city,String county) { }
    private MapFields mapFields;
    private String mapValueMode;
    public MapFields getMapFields(){return mapFields;}
    public void setMapFields(MapFields value){mapFields=value;}
    public String getMapValueMode(){return mapValueMode;}
    public void setMapValueMode(String value){mapValueMode=value;}
    /** keep | zero | hide */
    private String nullMode;
    private Boolean smoothLine;
    /** Pivot row dimension field names (1–3). */
    private List<String> pivotRowFields;
    /** Pivot column dimension field names (1–2). */
    private List<String> pivotColFields;
    /** Map scope id, e.g. china-provinces */
    private String mapScope;
    /** Ordered drill dimension fields (2–5); first must match query.dimensions[0]. */
    private List<String> drillFields;
    public String getNullMode() { return nullMode; }
    public void setNullMode(String nullMode) { this.nullMode = nullMode; }
    public Boolean getSmoothLine() { return smoothLine; }
    public void setSmoothLine(Boolean smoothLine) { this.smoothLine = smoothLine; }
    public List<String> getPivotRowFields() { return pivotRowFields; }
    public void setPivotRowFields(List<String> pivotRowFields) { this.pivotRowFields = pivotRowFields; }
    public List<String> getPivotColFields() { return pivotColFields; }
    public void setPivotColFields(List<String> pivotColFields) { this.pivotColFields = pivotColFields; }
    public String getMapScope() { return mapScope; }
    public void setMapScope(String mapScope) { this.mapScope = mapScope; }
    public List<String> getDrillFields() { return drillFields; }
    public void setDrillFields(List<String> drillFields) { this.drillFields = drillFields; }
}
