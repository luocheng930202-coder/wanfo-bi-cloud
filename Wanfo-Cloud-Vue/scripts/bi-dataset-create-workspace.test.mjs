import assert from 'node:assert/strict'
import fs from 'node:fs/promises'
import ts from 'typescript'
import * as vue from 'vue'
import {test} from 'node:test'
const source=await fs.readFile(new URL('../src/views/bi/dataset/DatasetCreateWorkspace.vue',import.meta.url),'utf8')
const script=source.match(/<script setup lang="ts">([\s\S]*?)<\/script>/)[1].split('\n').filter(line=>!line.startsWith('import ')).join('\n')
const compiled=ts.transpileModule(script,{compilerOptions:{target:ts.ScriptTarget.ES2022,module:ts.ModuleKind.ES2022}}).outputText
const parameterSource=await fs.readFile(new URL('../src/utils/biSqlParameters.ts',import.meta.url),'utf8')
const parameterCode=ts.transpileModule(parameterSource,{compilerOptions:{target:ts.ScriptTarget.ES2022,module:ts.ModuleKind.ES2022}}).outputText
const parameterHelpers=await import('data:text/javascript;base64,'+Buffer.from(parameterCode).toString('base64'))
function harness(api={}){
 const calls=[],hooks={}
 const deps={...parameterHelpers,ref:vue.ref,nextTick:vue.nextTick,computed:vue.computed,watch:vue.watch,useRoute:()=>({query:{}}),useRouter:()=>({replace:async path=>{assert.equal(await hooks.leave(),true);calls.push(path)},push:()=>{}}),onMounted:()=>{},onBeforeUnmount:()=>{},onBeforeRouteLeave:fn=>hooks.leave=fn,checkPermi:()=>true,ElMessage:{success:()=>{},warning:()=>{}},ElMessageBox:{confirm:async()=>{}},...api}
 const keys=Object.keys(deps)
 const state=Function(...keys,compiled+';return {editRevision,editing,loadEditing,prepareEdit,confirmEdit,pendingDraft,editImpact,impactVisible,initialState,draftParameters,draftParameterValues,sqlParameters,sqlParameterValues,sourceId,sources,catalog,table,fields,preview,previewError,busy,saving,name,activeTab,derivedFields,derivedIndex,applyDerived,sqlVisible,sqlDraft,sqlText,sqlError,sqlLoading,applySql,joins,totalFields,joinIndex,editingJoin,rightColumns,leftKeys,confirmJoin,removeJoin,changeTable,runPreview,save}')(...keys.map(k=>deps[k]))
 state.sources.value=[{id:1,name:'test',schemaName:'test',type:'mysql'}];state.sourceId.value=1;state.catalog.value='demo'
 return {...state,calls,hooks}
}
const defer=()=>{let resolve;const promise=new Promise(r=>resolve=r);return {promise,resolve}}
test('late metadata response cannot replace fields from a newer table',async()=>{
 const first=defer(),second=defer()
 const h=harness({listColumns:(_s,t)=>t==='first'?first.promise:second.promise})
 h.table.value='first';const a=h.changeTable();h.table.value='second';const b=h.changeTable()
 second.resolve({data:[{name:'current',type:'int',suggestAs:'metric'}]});await b
 first.resolve({data:[{name:'obsolete',type:'varchar'}]});await a
 assert.deepEqual(h.fields.value.map(f=>f.name),['current']);assert.equal(h.busy.value,false)
})
test('changing selected fields discards in-flight preview results',async()=>{
 const pending=defer(),h=harness({previewTable:()=>pending.promise})
 h.table.value='orders';h.fields.value=[{name:'amount',selected:true,showName:'金额',dimFlag:'0'}];await vue.nextTick()
 const query=h.runPreview();h.fields.value[0].selected=false;await vue.nextTick()
 pending.resolve({data:{ok:true,rows:[{amount:10}],columns:['amount'],costMs:1}});await query
 assert.equal(h.preview.value,null);assert.equal(h.previewError.value,'')
})
test('creation persists configured labels and navigates after save without leave prompt',async()=>{
 let payload
 const h=harness({createWorkspaceDataset:async body=>{payload=body;return {data:{id:42}}}})
 h.name.value=' 测试数据集 ';h.table.value='orders';h.fields.value=[{name:'amount',selected:true,showName:' 金额 ',dimFlag:'0'},{name:'hidden',selected:false,showName:'排除',dimFlag:'1'}]
 await h.save()
 assert.equal(payload.name,'测试数据集');assert.deepEqual(payload.fields,[{name:'amount',showName:'金额',dimFlag:'0'}]);assert.deepEqual(h.calls,['/bi-admin/dataset-workspace/42/view']);assert.equal(h.saving.value,false)
})

test('incomplete relationship cannot create a partially configured dataset',async()=>{
 let created=false
 const h=harness({createWorkspaceDataset:async()=>{created=true;return {data:{id:42}}}})
 h.name.value='test';h.table.value='orders';h.fields.value=[{name:'school_id',selected:true,showName:'学校',dimFlag:'1'}]
 h.joins.value=[{joinType:'LEFT',rightTable:'schools',leftKey:'school_id',rightKey:'',rightFields:['name']}]
 await h.save();assert.equal(created,false);assert.equal(h.saving.value,false)
})
test('save submits configured relationships with base fields in a single request',async()=>{
 let calls=0,model
 const h=harness({createWorkspaceDataset:async(_body,joins)=>{calls++;model=joins;return {data:{id:42}}}})
 h.name.value='test';h.table.value='orders';h.fields.value=[{name:'school_id',selected:true,showName:'学校',dimFlag:'1'}]
 h.joins.value=[{joinType:'LEFT',rightTable:'schools',leftKey:'school_id',rightKey:'id',rightFields:['name']}]
 await h.save();assert.equal(calls,1);assert.deepEqual(model.joins,h.joins.value)
})
test('removing an upstream relationship also removes dependent later relationships',async()=>{
 const h=harness();h.joins.value=[{rightTable:'a',rightFields:['id']},{rightTable:'b',rightFields:['id']}]
 await h.removeJoin(0);assert.equal(h.joins.value.length,0)
})

test('SQL metadata resolves fields even when the query returns no rows',async()=>{
 const h=harness({previewSqlNode:async()=>({data:{ok:true,normalizedSql:'SELECT id FROM demo.orders',columns:['id'],rows:[],costMs:2,fieldMetadata:[{name:'id',type:'INTEGER',suggestAs:'metric'}]}})})
 h.sqlVisible.value=true;h.sqlDraft.value='SELECT id FROM demo.orders'
 await h.applySql()
 assert.equal(h.table.value,'自定义 SQL');assert.equal(h.fields.value[0].name,'id');assert.equal(h.fields.value[0].dimFlag,'0');assert.deepEqual(h.preview.value.rows,[]);assert.equal(h.sqlVisible.value,false)
})
test('a failed SQL edit preserves the last successfully parsed node',async()=>{
 const h=harness({previewSqlNode:async()=>{throw Error('invalid SQL')}})
 h.sqlText.value='SELECT id FROM demo.orders';h.table.value='自定义 SQL';h.fields.value=[{name:'id',selected:true,showName:'ID',dimFlag:'1'}]
 h.sqlVisible.value=true;h.sqlDraft.value='DELETE FROM demo.orders'
 await h.applySql();assert.equal(h.sqlText.value,'SELECT id FROM demo.orders');assert.equal(h.fields.value[0].name,'id');assert.equal(h.sqlError.value,'invalid SQL')
})
test('SQL creation uses the atomic workspace endpoint with its source definition',async()=>{
 let saved
 const h=harness({createWorkspaceDataset:async(dataset,model)=>{saved={dataset,model};return {data:{id:42}}}})
 h.name.value='SQL dataset';h.sqlText.value='SELECT id FROM demo.orders';h.table.value='自定义 SQL';h.fields.value=[{name:'id',selected:true,showName:'ID',dimFlag:'1'}]
 await h.save();assert.equal(saved.model.sourceSql,'SELECT id FROM demo.orders');assert.deepEqual(saved.dataset.fields,[{name:'id',showName:'ID',dimFlag:'1'}])
})

test('calculated and group definitions are submitted together with the new dataset',async()=>{
 let model
 const h=harness({createWorkspaceDataset:async(_dataset,value)=>{model=value;return {data:{id:42}}}})
 h.name.value='派生验收';h.table.value='orders';h.fields.value=[{name:'amount',selected:true,showName:'金额',dimFlag:'0'}]
 h.applyDerived({kind:'calc',originName:'doubled',showName:'双倍',dimFlag:'0',formula:'[amount]*2'})
 h.applyDerived({kind:'group',originName:'band',showName:'金额分类',sourceField:'doubled',groups:[{label:'常规',values:['10']}],otherLabel:'其他'})
 await h.save();assert.equal(model.derivedFields.length,2);assert.equal(model.derivedFields[1].groups[0].label,'常规')
})
test('editing a derived field replaces its definition without appending a duplicate',()=>{
 const h=harness();h.applyDerived({kind:'calc',originName:'n',formula:'1'});h.derivedIndex.value=0;h.applyDerived({kind:'calc',originName:'n',formula:'2'})
 assert.equal(h.derivedFields.value.length,1);assert.equal(h.derivedFields.value[0].formula,'2')
})

test('successful preview switches from field management to the data results',async()=>{
 const h=harness({previewTable:async()=>({data:{ok:true,rows:[{amount:10}],columns:['amount'],costMs:1}})})
 h.table.value='orders';h.fields.value=[{name:'amount',selected:true,showName:'金额',dimFlag:'0'}];await vue.nextTick();h.activeTab.value='fields'
 await h.runPreview();assert.equal(h.activeTab.value,'preview');assert.equal(h.preview.value.rows.length,1)
})

test('SQL parameter definitions persist with the template and temporary values stay separate',async()=>{
 let request,saved
 const h=harness({previewSqlNode:async data=>{request=data;return {data:{ok:true,normalizedSql:data.sql,columns:['name'],rows:[{name:'alice'}],fieldMetadata:[{name:'name',type:'VARCHAR',suggestAs:'dimension'}]}}},createWorkspaceDataset:async(dataset,model,values)=>{saved={dataset,model,values};return {data:{id:42}}}})
 h.sqlVisible.value=true;h.sqlDraft.value="SELECT name FROM t WHERE $DE_PARAM{name='$[P]'}"
 h.draftParameters.value=[{name:'P',label:'Name',type:'TEXT',multiple:false,required:false,defaultValues:['default'],defaultScope:'ALL'}]
 h.draftParameterValues.value={P:['temporary'],OLD:['stale']};await h.applySql()
 assert.deepEqual(request.parameterValues,{P:['temporary']});assert.equal(h.sqlText.value,h.sqlDraft.value)
 h.name.value='parameter dataset';await h.save()
 assert.deepEqual(saved.model.sqlParameters[0].defaultValues,['default']);assert.equal(saved.model.sourceSql,h.sqlDraft.value)
 assert.deepEqual(saved.values,{P:['temporary']});assert.equal(JSON.stringify(saved.model).includes('temporary'),false)
})

test('editing restores SQL template, joins, derived rules and parameter defaults',async()=>{
 const h=harness({useRoute:()=>({params:{id:'229'},query:{}}),getDatasetWorkspace:async()=>({data:{dataset:{name:'Saved',datasourceId:1,sourceCatalog:'demo',remark:'note',groupId:3},baseFields:[{originName:'amount',originType:'DECIMAL',showName:'Amount',dimFlag:'0'}],fields:[],revision:'revision-one',model:{sourceSql:'SELECT amount FROM orders',joins:[{joinType:'LEFT',rightTable:'school',leftKey:'amount',rightKey:'id',rightFields:['name']}],derivedFields:[{kind:'calc',originName:'double_amount',formula:'[amount]*2'}],sqlParameters:[{name:'P',type:'TEXT',multiple:false,required:false,defaultValues:['a'],defaultScope:'ALL'}]}}}),listCatalogs:async()=>({data:['demo']}),listTables:async()=>({data:[]})})
 await h.loadEditing();assert.equal(h.name.value,'Saved');assert.equal(h.editRevision.value,'revision-one');assert.equal(h.sqlText.value,'SELECT amount FROM orders');assert.equal(h.joins.value.length,1);assert.equal(h.derivedFields.value.length,1);assert.deepEqual(h.sqlParameters.value[0].defaultValues,['a']);assert.equal(h.fields.value[0].showName,'Amount')
})
test('editing checks impact first and updates original id only after confirmation',async()=>{
 let updated,created=false
 const h=harness({useRoute:()=>({params:{id:'229'},query:{}}),checkDatasetWorkspace:async(id,data)=>{assert.equal(id,229);assert.equal(data.revision,'r1');return {data:{blockers:[],warnings:['changed'],changedFields:[],references:[],token:'checked'}}},updateDatasetWorkspace:async(id,data)=>{updated={id,data};return {data:{id}}},createWorkspaceDataset:async()=>{created=true}})
 h.editRevision.value='r1';h.name.value='Saved';h.table.value='orders';h.fields.value=[{name:'amount',selected:true,showName:'Amount',dimFlag:'0'}]
 await h.save();assert.equal(created,false);assert.equal(updated,undefined);assert.equal(h.impactVisible.value,true)
 await h.confirmEdit();assert.equal(updated.id,229);assert.equal(updated.data.impactToken,'checked');assert.deepEqual(h.calls,['/bi-admin/dataset-workspace/229/view'])
})
test('blocked impact cannot submit changes',async()=>{
 let updated=false;const h=harness({useRoute:()=>({params:{id:'229'},query:{}}),updateDatasetWorkspace:async()=>{updated=true}})
 h.editImpact.value={blockers:['chart references amount'],warnings:[],references:[],token:'x'};h.pendingDraft.value={};await h.confirmEdit();assert.equal(updated,false)
})
