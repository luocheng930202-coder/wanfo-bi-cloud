package com.wanfo.bi.service;

import com.wanfo.bi.domain.chart.ChartStyleValidator;
import com.wanfo.bi.domain.chart.ChartVisualStyle;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import static org.junit.jupiter.api.Assertions.*;

class ChartStyleValidatorTest {
    @Test void oldDefinitionsKeepTheirDefaults() {
        ChartStyleValidator.validate(null,"bar");
        ChartStyleValidator.validate(new ChartVisualStyle(),"ring");
    }
    @Test void rejectsInvalidRangesIncludingImplicitDefaults() {
        var s=new ChartVisualStyle();s.setAreaOpacity(Double.NaN);
        assertThrows(IllegalArgumentException.class,()->ChartStyleValidator.validate(s,"area"));
        s.setAreaOpacity(0.2);s.setBarMaxWidth(999);
        assertThrows(IllegalArgumentException.class,()->ChartStyleValidator.validate(s,"bar"));
        s.setBarMaxWidth(36);s.setPieInnerRadius(80);
        assertThrows(IllegalArgumentException.class,()->ChartStyleValidator.validate(s,"ring"));
        s.setPieInnerRadius(null);s.setPieOuterRadius(40);
        assertThrows(IllegalArgumentException.class,()->ChartStyleValidator.validate(s,"ring"));
        // A pie has no inner ring; old ring settings must not invalidate its outer radius.
        assertDoesNotThrow(()->ChartStyleValidator.validate(s,"pie"));
        s.setGaugeMin(20d);s.setGaugeMax(10d);
        assertThrows(IllegalArgumentException.class,()->ChartStyleValidator.validate(s,"gauge"));
    }
    @Test void persistsShapeOptionsWithoutLosingUserChrome() {
        var s=new ChartVisualStyle();s.setTitle("销售额");s.setBarRadius(6);s.setLineWidth(4);
        s.setShowPoints(false);s.setRingShowTotal(true);s.setPieInnerRadius(42);
        s.setGaugeStyle("progress");s.setGaugeMin(-100d);s.setGaugeMax(200d);
        var json=new ObjectMapper();
        var restored=json.readValue(json.writeValueAsString(s),ChartVisualStyle.class);
        assertEquals("销售额",restored.getTitle());assertEquals(6,restored.getBarRadius());
        assertEquals(4,restored.getLineWidth());assertFalse(restored.getShowPoints());
        assertTrue(restored.getRingShowTotal());assertEquals(42,restored.getPieInnerRadius());
        assertEquals("progress",restored.getGaugeStyle());assertEquals(200d,restored.getGaugeMax());
        assertDoesNotThrow(()->ChartStyleValidator.validate(restored,"ring"));
    }
}
