package com.wanfo.bi.domain.form;

/** Single writable column on a data entry form. */
public class DataFormField
{
    private String field;
    private String label;
    private Boolean required;
    /** text | number | date | textarea */
    private String widget;

    public String getField() { return field; }
    public void setField(String field) { this.field = field; }
    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }
    public Boolean getRequired() { return required; }
    public void setRequired(Boolean required) { this.required = required; }
    public String getWidget() { return widget; }
    public void setWidget(String widget) { this.widget = widget; }
}
