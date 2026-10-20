package com.wanfo.bi.domain.chart;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.Test;
import com.wanfo.bi.domain.query.QueryModel;
class ChartTypesTest {
    QueryModel pieQuery(){var q=new QueryModel();q.addDimension("city","c");q.addMetric("id","SUM","v");return q;}
    @Test void allowsExtendedTypes(){assertDoesNotThrow(()->ChartTypes.validateShape("area",pieQuery()));assertDoesNotThrow(()->ChartTypes.validateShape("ring",pieQuery()));}
    @Test void gaugeRequiresSingleMetricNoDim(){var q=new QueryModel();q.addMetric("id","SUM","v");assertDoesNotThrow(()->ChartTypes.validateShape("gauge",q));q.addDimension("city","c");assertThrows(IllegalArgumentException.class,()->ChartTypes.validateShape("gauge",q));}
    @Test void scatterRequiresTwoMetrics(){var q=pieQuery();assertThrows(IllegalArgumentException.class,()->ChartTypes.validateShape("scatter",q));q.addMetric("amount","SUM","a");assertDoesNotThrow(()->ChartTypes.validateShape("scatter",q));}
    @Test void comboNeedsTwoMetrics(){var q=pieQuery();assertThrows(IllegalArgumentException.class,()->ChartTypes.validateShape("combo",q));q.addMetric("amount","SUM","a");assertDoesNotThrow(()->ChartTypes.validateShape("combo",q));}
    @Test void mapRequiresRegionDimAndMetric(){var q=pieQuery();assertDoesNotThrow(()->ChartTypes.validateShape("map",q));q.addMetric("x","SUM","x2");assertThrows(IllegalArgumentException.class,()->ChartTypes.validateShape("map",q));}
}
