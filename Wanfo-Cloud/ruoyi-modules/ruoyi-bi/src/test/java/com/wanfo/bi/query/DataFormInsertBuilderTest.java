package com.wanfo.bi.query;

import java.util.List;
import java.util.Map;
import com.wanfo.bi.domain.form.DataFormField;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DataFormInsertBuilderTest
{
    @Test void buildsParameterizedInsert()
    {
        DataFormField city = new DataFormField();
        city.setField("city");
        city.setLabel("城市");
        city.setRequired(true);
        city.setWidget("text");
        DataFormField amount = new DataFormField();
        amount.setField("amount");
        amount.setWidget("number");
        var plan = DataFormInsertBuilder.compile("poc_a", "orders", "`",
                List.of(city, amount), Map.of("city", "VARCHAR", "amount", "DECIMAL"),
                Map.of("city", "杭州", "amount", "12.5"));
        assertTrue(plan.sql().contains("INSERT INTO `poc_a`.`orders`"));
        assertEquals(2, plan.params().size());
        assertEquals("杭州", plan.normalized().get("city"));
    }

    @Test void requiredFieldMissingFails()
    {
        DataFormField city = new DataFormField();
        city.setField("city");
        city.setRequired(true);
        assertThrows(IllegalArgumentException.class, () -> DataFormInsertBuilder.compile(
                "poc_a", "orders", "`", List.of(city), Map.of("city", "VARCHAR"), Map.of()));
    }
}
