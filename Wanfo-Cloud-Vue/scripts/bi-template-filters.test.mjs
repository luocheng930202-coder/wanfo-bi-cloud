import assert from 'node:assert/strict'
import fs from 'node:fs/promises'
import ts from 'typescript'
import {test} from 'node:test'
const source=await fs.readFile(new URL('../src/utils/biTemplateFilters.ts',import.meta.url),'utf8')
const code=ts.transpileModule(source,{compilerOptions:{module:ts.ModuleKind.ES2022}}).outputText
const {templateFilterControls:build}=await import('data:text/javascript;base64,'+Buffer.from(code).toString('base64'))
const slots=[{key:'purchase',label:'采购'},{key:'meal',label:'用餐'}]
const fields={purchase:[{name:'purchase_date',type:'date'},{name:'school_id',type:'varchar'}],meal:[{name:'meal_time',type:'date'},{name:'school_code',type:'varchar'}]}
const mappings={purchase:{date:'purchase_date',school:'school_id'},meal:{date:'meal_time',school:'school_code'}}
test('shared filters map different dataset fields to newly generated panel keys without default restrictions',()=>{
  const before=JSON.stringify({fields,mappings})
  const controls=build(slots,['new-1','new-2'],{date:true,school:true},mappings,fields)
  assert.equal(controls.length,2)
  assert.equal(controls[0].type,'date');assert.equal(controls[1].type,'enum')
  assert.deepEqual(controls[0].targets,[{panel:'new-1',field:'purchase_date'},{panel:'new-2',field:'meal_time'}])
  assert.equal(controls[0].defaultValue,null)
  assert.equal(JSON.stringify({fields,mappings}),before)
})
test('incomplete, invisible, incompatible and stale panel mappings block creation',()=>{
  assert.throws(()=>build(slots,['a','b'],{date:true},{purchase:{date:'purchase_date'}},fields),/用餐/)
  assert.throws(()=>build(slots,['a','b'],{date:true},{...mappings,meal:{date:'secret'}},fields),/可见字段/)
  assert.throws(()=>build(slots,['a','b'],{date:true},{...mappings,meal:{date:'school_code'}},fields),/DATE/)
  assert.throws(()=>build(slots,['a'],{date:true},mappings,fields),/一致/)
  assert.throws(()=>build(slots,['a','a'],{date:true},mappings,fields),/一致/)
  assert.throws(()=>build(slots,['a','b'],{date:true},mappings,{...fields,meal:[{name:'meal_time',type:'timestamp'}]}),/DATE/)
  assert.throws(()=>build(slots,['a','b'],{school:true},mappings,{...fields,meal:[{name:'school_code',type:'int'}]}),/类型须一致/)
})
test('disabled controls do not force mappings or introduce filters',()=>{
  assert.deepEqual(build(slots,[],{}, {},{}),[])
  assert.deepEqual(build(slots,['a','b'],{date:false,school:true},mappings,fields).map(c=>c.key),['template-school'])
})
test('defaults preserve text identifiers and valid date ranges without mutating their input',()=>{
  const defaults={date:['2026-01-01','2026-01-31'],school:'00123'}
  const controls=build(slots,['a','b'],{date:true,school:true},mappings,fields,defaults)
  assert.equal(controls[1].defaultValue,'00123')
  assert.deepEqual(controls[0].defaultValue,defaults.date)
  assert.notEqual(controls[0].defaultValue,defaults.date)
})
test('invalid calendar dates, inverted and partial ranges cannot be saved or previewed',()=>{
  for(const value of [['2026-02-30','2026-03-01'],['2026-02-02','2026-02-01'],['2026-01-01',null],'2026-01-01']) {
    assert.throws(()=>build(slots,['a','b'],{date:true},mappings,fields,{date:value}),/起止日期/)
  }
})
test('numeric codes accept exact numeric strings and reject unsafe or incompatible values',()=>{
  const numeric={purchase:[{name:'school_id',type:'int'}],meal:[{name:'school_code',type:'int'}]}
  assert.equal(build(slots,['a','b'],{school:true},mappings,numeric,{school:'12345678901234567890'})[0].defaultValue,'12345678901234567890')
  for(const value of ['not-a-number','1'.repeat(31),'1.1234567890123',['a','b']])assert.throws(()=>build(slots,['a','b'],{school:true},mappings,numeric,{school:value}))
  assert.throws(()=>build(slots,['a','b'],{school:true},mappings,fields,{school:123}),/文本/)
})
