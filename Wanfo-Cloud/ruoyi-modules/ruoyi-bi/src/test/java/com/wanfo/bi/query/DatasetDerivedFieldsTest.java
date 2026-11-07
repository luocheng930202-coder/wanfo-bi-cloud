package com.wanfo.bi.query;
import java.util.*;
import com.wanfo.bi.domain.BiDatasetFieldDO;
import com.wanfo.bi.domain.dataset.DatasetDerivedField;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class DatasetDerivedFieldsTest {
 private List<BiDatasetFieldDO> base(){var amount=new BiDatasetFieldDO();amount.setOriginName("amount");amount.setOriginType("DECIMAL(12,2)");return List.of(amount);}
 @Test void calculatedAndGroupingFieldsCanReferencePreviousDerivedResults(){
  var result=DatasetDerivedFields.compile(base(),List.of(
   new DatasetDerivedField("calc","doubled","双倍金额","0","[amount] * 2",null,null,null),
   new DatasetDerivedField("group","band","分组","1",null,"doubled",List.of(new DatasetDerivedField.Group("普通",List.of("10","20"))),"其他"),
   new DatasetDerivedField("calc","band_upper","分类大写","1","UPPER([band])",null,null,null)));
  assertEquals(3,result.size());assertEquals("1",result.get(1).getDimFlag());assertEquals("分组字段",result.get(1).getGroupName());assertTrue(result.get(2).getOriginType().contains("VARCHAR"));
 }
 @Test void duplicateNamesAndForwardReferencesAreRejected(){
  assertThrows(IllegalArgumentException.class,()->DatasetDerivedFields.compile(base(),List.of(new DatasetDerivedField("calc","amount","重复","0","1",null,null,null))));
  assertThrows(IllegalArgumentException.class,()->DatasetDerivedFields.compile(base(),List.of(new DatasetDerivedField("calc","first","前向","0","[later]+1",null,null,null))));
 }
 @Test void overlappingNumericGroupsAreRejectedEvenWhenTextRepresentationsDiffer(){
  var definition=new DatasetDerivedField("group","band","分类","1",null,"amount",List.of(new DatasetDerivedField.Group("A",List.of("1")),new DatasetDerivedField.Group("B",List.of("1.0"))),"其他");
  assertThrows(IllegalArgumentException.class,()->DatasetDerivedFields.compile(base(),List.of(definition)));
 }
}
