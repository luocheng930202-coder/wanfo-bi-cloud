import assert from 'node:assert/strict'
import fs from 'node:fs/promises'
import ts from 'typescript'
import {test} from 'node:test'
let bundle=`export function metricResultKey(m){return m.alias || (m.agg==='NONE'?m.field:m.agg.toLowerCase()+'_'+m.field)}\nexport function defaultMapScope(){return 'china-provinces'}\nexport function normalizeProvinceName(value){return value}\n`
for(const file of ['biTheme.ts','biMapRegions.ts','biPresentation.ts','biChartPresentation.ts','biMapPreview.ts']){
 const source=(await fs.readFile(new URL('../src/utils/'+file,import.meta.url),'utf8')).split('\n').filter(l=>!l.startsWith('import ')).join('\n')
 bundle+=ts.transpileModule(source,{compilerOptions:{target:ts.ScriptTarget.ES2022,module:ts.ModuleKind.ES2022}}).outputText+'\n'
}
const {mapScopeCode,geoDisplayLevel,geoTree,matchGeoFeature,mapMatchWarning,buildMapPreviewOption,validateMapConfig,mapPieceColor,mapDrillNames}=await import('data:text/javascript;base64,'+Buffer.from(bundle).toString('base64'))
const catalog=JSON.parse(await fs.readFile(new URL('../../Wanfo-Cloud/ruoyi-modules/ruoyi-bi/src/main/resources/bi/geo/regions-index.json',import.meta.url),'utf8'))
const features=[{adcode:330100,name:'杭州市'},{adcode:330500,name:'湖州市'}]
function definition(mode='code'){return {schemaVersion:1,type:'map',name:'地图',advanced:{mapScope:'330000',mapValueMode:mode},query:{datasetId:1,dimensions:[{field:'city'}],metrics:[{field:'amount',agg:'SUM',alias:'value'}]}}}
test('catalog covers country, province, city and county, including municipality shortcuts',()=>{
 assert.ok(catalog.regions.length>3000);assert.equal(geoDisplayLevel(catalog,'100000'),'province')
 assert.equal(geoDisplayLevel(catalog,'330000'),'city');assert.equal(geoDisplayLevel(catalog,'330100'),'county')
 assert.equal(geoDisplayLevel(catalog,'110000'),'county');assert.equal(geoDisplayLevel(catalog,'460000'),'city')
 const tree=geoTree(catalog);assert.equal(tree[0].value,'100000');assert.ok(tree[0].children.find(n=>n.value==='330000').children.find(n=>n.value==='330100').children.find(n=>n.value==='330106'))
})
test('scope IDs cannot become URLs or paths and name matching is scoped and unambiguous',()=>{
 assert.equal(mapScopeCode('china-provinces'),'100000');assert.equal(mapScopeCode('330100'),'330100')
 for(const value of ['../application','https://example.com','330100/../../'])assert.throws(()=>mapScopeCode(value))
 assert.equal(matchGeoFeature(features,330100,'code').name,'杭州市');assert.equal(matchGeoFeature(features,'杭州','name').adcode,330100)
 assert.equal(matchGeoFeature(features,'西湖区','name'),undefined)
 assert.equal(matchGeoFeature([{adcode:'1',name:'鼓楼区'},{adcode:'2',name:'鼓楼区'}],'鼓楼区','name'),undefined)
})
test('map plots only matching region values, preserves raw event identity and reports mismatches',()=>{
 const d=definition(),result={columns:['city','value'],rows:[{city:'330100',value:12},{city:'999999',value:99}],rowCount:2,costMs:1}
 const option=buildMapPreviewOption({mapName:'test',definition:d,result,labels:{},geoFeatures:features,formatFor:()=>undefined})
 assert.deepEqual(option.series[0].data,[{name:'杭州市',value:12,rawRegion:'330100'}])
 assert.match(mapMatchWarning(d,result,features),/1 个地区/);assert.equal(result.rows[1].value,99)
})
test('empty or entirely unmatched results leave a neutral boundary and never invent regional zeros',()=>{
 const result={columns:['city','value'],rows:[],rowCount:0,costMs:1}
 const o=buildMapPreviewOption({mapName:'test',definition:definition(),result,labels:{},geoFeatures:features,formatFor:()=>undefined})
 assert.deepEqual(o.series[0].data,[]);assert.equal(o.visualMap.min,0);assert.equal(o.visualMap.max,1)
 assert.match(mapMatchWarning(definition(),result,features),/显示区域边界/)
})
test('different aliases for one geographic region must be unified before aggregation',()=>{
 const result={columns:['city','value'],rows:[{city:'杭州',value:10},{city:'杭州市',value:20}],rowCount:2,costMs:1}
 assert.throws(()=>buildMapPreviewOption({mapName:'test',definition:definition('name'),result,labels:{},geoFeatures:features,formatFor:()=>undefined}),/统一地区编码/)
})


test('map piece boundaries, missing colors and formatted labels preserve raw numbers',()=>{
 const d=definition();d.style={mapColorMode:'piecewise',mapBreaks:[0,100],mapLowColor:'#ffffff',mapHighColor:'#000000',mapNoDataColor:'#dddddd',mapBorderColor:'#123456',mapShowLabels:true,mapLabelContent:'nameValue',mapShowTooltip:false,showLegend:false}
 const result={columns:['city','value'],rows:[{city:'330100',value:100}],rowCount:1,costMs:1}
 const o=buildMapPreviewOption({mapName:'test',definition:d,result,labels:{value:'销售额'},geoFeatures:features,formatFor:()=>({mode:'number',scale:'none',decimals:2,prefix:'',suffix:'',nullText:'—'})})
 assert.equal(o.visualMap.type,'piecewise');assert.equal(o.visualMap.show,false)
 assert.deepEqual(o.visualMap.pieces.map(p=>[p.gte,p.lt]),[[undefined,0],[0,100],[100,undefined]])
 assert.deepEqual(o.visualMap.pieces.map(p=>p.color),['#ffffff','#808080','#000000'])
 assert.equal(o.series[0].data[0].value,100);assert.equal(o.series[0].itemStyle.areaColor,'#dddddd');assert.equal(o.tooltip.show,false)
 assert.match(o.series[0].label.formatter({name:'杭州市',value:100}),/杭州市\n100/)
 assert.match(o.tooltip.formatter({name:'湖州市',value:NaN}),/销售额: 无数据/)
 assert.equal(validateMapConfig(d),null)
 for(const limits of [[],[1,1],[2,1],[NaN],[Infinity],[1,2,3,4,5,6]]){d.style.mapBreaks=limits;assert.match(validateMapConfig(d),/分段/)}
})


test('piece overrides preserve interval ownership and nullable entries fall back to gradient',()=>{
 const d=definition();d.style={mapColorMode:'piecewise',mapBreaks:[0,100],mapLowColor:'#FFFFFF',mapHighColor:'#000000',mapPieceColors:['#22C55E',null,'#EF4444']}
 const result={columns:['city','value'],rows:[{city:'330100',value:100}],rowCount:1,costMs:1}
 const o=buildMapPreviewOption({mapName:'test',definition:d,result,labels:{},geoFeatures:features,formatFor:()=>undefined})
 assert.deepEqual(o.visualMap.pieces.map(p=>p.color),['#22C55E','#808080','#EF4444']);assert.equal(mapPieceColor(d.style,1),'#808080');assert.equal(o.visualMap.pieces[2].gte,100);assert.equal(o.series[0].data[0].value,100)
 assert.equal(validateMapConfig(d),null)
 for(const colors of [['#FFFFFF'],['url(x)',null,'#000000']]){d.style.mapPieceColors=colors;assert.match(validateMapConfig(d),/逐段颜色/)}
})
test('drill affordances only include plotted rows with available child metadata',()=>{
 const d=definition(),result={columns:['city','value'],rows:[{city:'330100',value:0},{city:'330500',value:null},{city:'999999',value:100}],rowCount:3,costMs:1,mapContext:{canDrillDown:true,drillableRegions:['330100','330500']}}
 assert.deepEqual(mapDrillNames(d,result,features),['杭州市']);result.mapContext.canDrillDown=false;assert.deepEqual(mapDrillNames(d,result,features),[])
 result.mapContext.canDrillDown=true;d.advanced.nullMode='zero';assert.deepEqual(mapDrillNames(d,result,features),['杭州市','湖州市'])
 result.mapContext.drillableRegions=[];assert.deepEqual(mapDrillNames(d,result,features),[])
})
test('map hover hints distinguish drill targets, leaf regions and no data without changing metrics',()=>{
 const o=buildMapPreviewOption({mapName:'test',definition:definition(),result:{rows:[],columns:[],rowCount:0,costMs:1},labels:{},geoFeatures:features,formatFor:()=>undefined,interaction:{enabled:true,names:['杭州市']}})
 assert.match(o.tooltip.formatter({name:'杭州市',value:10}),/点击下钻/);assert.match(o.tooltip.formatter({name:'湖州市',value:10}),/暂无可用下级/);assert.match(o.tooltip.formatter({name:'杭州市',value:NaN}),/当前无数据，无法下钻/)
 assert.equal(o.series[0].zoom,1);assert.deepEqual(o.series[0].scaleLimit,{min:0.5,max:8})
})
