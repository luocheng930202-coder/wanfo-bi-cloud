package com.wanfo.bi.service;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import tools.jackson.databind.json.JsonMapper;
class DashboardNumberFormatTest {
 DashboardPresentation.NumberFormat f(String mode,String scale,int digits,String prefix){return new DashboardPresentation.NumberFormat(mode,scale,digits,true,true,prefix,"元","暂无");}
 @Test void legacyStyleKeepsNoOverride(){assertNull(DashboardPresentation.normalize(new DashboardPresentation.PanelStyle(true,"left")).numberFormat());}
 @Test void formatSurvivesJsonAndNormalization(){var j=JsonMapper.builder().build();var value=new DashboardPresentation.PanelStyle(true,"center",f("number","wan",3,"¥"));assertEquals(value,DashboardPresentation.normalize(j.readValue(j.writeValueAsString(value),DashboardPresentation.PanelStyle.class)));}
 @Test void percentCannotScale(){assertThrows(IllegalArgumentException.class,()->DashboardPresentation.normalize(f("percent","wan",2,"")));}
 @Test void boundedDecimals(){assertThrows(IllegalArgumentException.class,()->DashboardPresentation.normalize(f("number","none",7,"")));}
 @Test void unknownModeAndScaleRejected(){assertThrows(IllegalArgumentException.class,()->DashboardPresentation.normalize(f("script","none",2,"")));assertThrows(IllegalArgumentException.class,()->DashboardPresentation.normalize(f("number","other",2,"")));}
 @Test void textBoundedAndControlRejected(){assertThrows(IllegalArgumentException.class,()->DashboardPresentation.normalize(f("number","none",2,"x".repeat(21))));assertThrows(IllegalArgumentException.class,()->DashboardPresentation.normalize(f("number","none",2,"\n")));}
}
