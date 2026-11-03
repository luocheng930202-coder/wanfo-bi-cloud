/** Creates a separate, editable demo using normal APIs. No embedded credentials or business-table changes.
 * Seed demo-school-canteen.sql into the selected MySQL datasource before running.
 * WANFO_DEMO_USERNAME / WANFO_DEMO_PASSWORD, or the ignored Wanfo-Cloud/.local-login.json.
 * Optional WANFO_DEMO_DATASOURCE_ID (default 58). Existing named demo resources are reused.
 */
import fs from 'node:fs/promises'
import path from 'node:path'
import {fileURLToPath} from 'node:url'
const root=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'../../..')
const api=process.env.WANFO_DEMO_API || 'http://127.0.0.1:8080'
const credentials=process.env.WANFO_DEMO_USERNAME?{username:process.env.WANFO_DEMO_USERNAME,password:process.env.WANFO_DEMO_PASSWORD}:JSON.parse(await fs.readFile(path.join(root,'.local-login.json'),'utf8'))
const login=await(await fetch(api+'/auth/login',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({username:credentials.username,password:credentials.password})})).json()
if(!login.data?.access_token)throw Error('Local demo login failed')
async function request(url,method='GET',data){
 const response=await fetch(api+url,{method,headers:{Authorization:'Bearer '+login.data.access_token,'Content-Type':'application/json'},...(data?{body:JSON.stringify(data)}:{})})
 const value=await response.json();if(value.code!==200)throw Error(`${method} ${url}: ${value.msg || value.code}`)
 return value.data
}
const prefix='智慧食堂演示 · '
const datasets=await request('/bi/dataset/list'),charts=await request('/bi/chart/saved')
const datasourceId=Number(process.env.WANFO_DEMO_DATASOURCE_ID || 58)
const tables={
 overview:['demo_canteen_overview_v1',['id','purchase_wan','meal_visits','schools','acceptance_pct','safety_score']],
 categories:['demo_canteen_categories_v1',['id','category','amount_wan']],
 costs:['demo_canteen_costs_v1',['id','category','amount_wan']],
 meals:['demo_canteen_meals_v1',['id','day_label','meal_visits']],
 regions:['demo_canteen_regions_v1',['id','region_code','region_name','schools']],
 mealTypes:['demo_canteen_meal_types_v1',['id','meal_type','visits']],
 devices:['demo_canteen_devices_v1',['id','device_name','location_name','status_label','temperature_c']],
 receipts:['demo_canteen_receipts_v1',['id','school','event_time','category','amount_yuan','status_label']]
}
const datasetIds={}
const fieldNames={purchase_wan:'采购总额（万元）',meal_visits:'用餐人次',schools:'学校数量',acceptance_pct:'验收合格率',safety_score:'安全评分',category:'品类',amount_wan:'金额（万元）',day_label:'日期',region_code:'地区',region_name:'地区名称',meal_type:'餐次',visits:'就餐人次',device_name:'设备',location_name:'学校',status_label:'状态',temperature_c:'温度℃',school:'学校',event_time:'时间',amount_yuan:'金额（元）'}
for(const [key,[sourceTable,fields]] of Object.entries(tables)){
 let row=datasets.find(d=>d.name===prefix+key && d.datasourceId===datasourceId && d.sourceTable===sourceTable)
 if(!row)row=await request('/bi/dataset/table','POST',{name:prefix+key,datasourceId,sourceTable,fields,cacheEnabled:'1'})
 datasetIds[key]=row.id
 const detail=await request(`/bi/dataset/${row.id}`)
 for(const field of detail.fields || [])if(fieldNames[field.originName] && field.showName!==fieldNames[field.originName])await request(`/bi/dataset/${row.id}/fields/${field.id}`,'PUT',{showName:fieldNames[field.originName]})
 const policy=await request(`/bi/dataset/${row.id}/cache`)
 if(!policy.enabled || policy.ttl!==300)await request(`/bi/dataset/${row.id}/cache`,'PUT',{enabled:true,ttl:300})
}
const chartIds={}
const metric=(field,alias=field,agg='SUM')=>({field,alias,agg})
const specs=[
 ['purchase','overview','table',[],[metric('purchase_wan','采购金额')]],
 ['visits','overview','table',[],[metric('meal_visits','用餐人次')]],
 ['schools','overview','table',[],[metric('schools','学校数量')]],
 ['acceptance','overview','table',[],[metric('acceptance_pct','合格率')]],
 ['categories','categories','bar',['category'],[metric('amount_wan','采购金额（万元）')],{barMaxWidth:35,barRadius:5,showLabels:true,showLegend:false}],
 ['costs','costs','ring',['category'],[metric('amount_wan','费用（万元）')],{ringShowTotal:true,showLabels:false,pieInnerRadius:65,pieOuterRadius:85,pieLabelPosition:'inside',legendPosition:'bottom'}],
 ['meals','meals','area',['day_label'],[metric('meal_visits','就餐人次')],{showLegend:false,showPoints:true,lineWidth:3,areaOpacity:0.24}],
 ['regions','regions','map',['region_code'],[metric('schools','学校数量')],{mapBaseStyle:'dark',mapProjection:'mercator',mapLowColor:'#16495F',mapHighColor:'#32DAC0',mapBorderColor:'#32A8C0',mapShowLabels:true,mapLabelContent:'name',mapNoDataColor:'#102D42'},{mapScope:'320000',mapValueMode:'code',mapFields:{city:'region_code'}}],
 ['mealTypes','mealTypes','ring',['meal_type'],[metric('visits','就餐人次')],{ringShowTotal:true,showLabels:false,pieInnerRadius:62,pieOuterRadius:82,legendPosition:'bottom',pieLabelPosition:'inside'}],
 ['devices','devices','table',['device_name','location_name','status_label'],[metric('temperature_c','温度℃','AVG')],{tableRowHeight:36,tableBorder:false,tableStripe:false,tableHeaderColor:'#7BA6BF',tableHeaderBackground:'#0A2032',tableColumns:[{key:'设备',width:110},{key:'位置',width:130},{key:'状态',width:80},{key:'温度℃',width:90}]}],
 ['safety','overview','gauge',[],[metric('safety_score','安全评分')],{gaugeMin:0,gaugeMax:100,gaugeStyle:'progress',showLegend:false}],
 ['receipts','receipts','table',['school','category','status_label'],[metric('amount_yuan','金额（元）')],{tableRowHeight:30,tableBorder:false,tableStripe:false,tableHeaderColor:'#7BA6BF',tableHeaderBackground:'#0A2032',tableColumns:[{key:'学校',width:130},{key:'品类',width:60},{key:'状态',width:70},{key:'金额（元）',width:100}]}]
]
for(const [key,dataset,type,dimensions,metrics,style={},advanced={}] of specs){
 let row=charts.find(c=>c.name===prefix+key)
 const definition={schemaVersion:1,name:prefix+key,type,query:{datasetId:datasetIds[dataset],dimensions:dimensions.map(field=>({field,alias:({school:'学校',category:'品类',status_label:'状态',device_name:'设备',location_name:'位置',day_label:'日期',meal_type:'餐次',region_code:'地区'})[field] || field})),metrics,orderBys:key==='meals'?[{field:'day_label',order:'ASC'}]:[],limit:100},style:{showTitle:false,showLegend:true,palette:'ocean',...style},advanced:{smoothLine:true,...advanced}}
 if(row){const current=await request(`/bi/chart/saved/${row.id}`);row=await request(`/bi/chart/saved/${row.id}`,'PUT',{definition,version:current.version})}
 else row=await request('/bi/chart/saved','POST',{definition})
 chartIds[key]=row.id
 const result=await request(`/bi/chart/saved/${row.id}/data`,'POST',{})
 if(!result.rows?.length)throw Error('Demo chart has no data: '+key)
 console.log(`Verified chart ${key}: ${result.rows.length} rows`)
}
const style={showTitle:true,titleAlign:'left'}
const panel=(key,title,type,x,y,w,h,extra={})=>({key,title,componentType:type,x,y,w,h,locked:false,hidden:false,style:{...style},...extra})
const format=(suffix,decimals=1,scale='none')=>({mode:'number',scale,decimals,grouping:true,fixed:false,prefix:'',suffix,nullText:'—'})
const panels=[
 panel('brand','品牌标识','image',20,24,68,68,{style:{showTitle:false,titleAlign:'left',transparent:true},content:{image:'data:image/svg+xml;base64,'+Buffer.from((await fs.readFile(path.join(root,'../Wanfo-Cloud-Vue/src/assets/icons/svg/chart.svg'),'utf8')).replace('<path','<path fill="#43DCE6"')).toString('base64')}}),
 panel('heading','智慧校园食堂运行驾驶舱','text',88,20,1224,52,{style:{showTitle:false,titleAlign:'left',fontSize:34,textColor:'#DDF5FF',transparent:true},content:{text:'智慧校园食堂运行驾驶舱'}}),
 panel('subheading','英文副标题','text',90,72,1144,28,{style:{showTitle:false,titleAlign:'left',fontSize:12,textColor:'#6E9AB4',transparent:true},content:{text:'SCHOOL CANTEEN  ·  DIGITAL TWIN OVERVIEW     /     校园餐饮数据中枢'}}),
 panel('clock','系统时间','clock',1490,30,398,54,{style:{showTitle:false,titleAlign:'center',fontSize:17,textColor:'#9DBCCD',transparent:true},content:{text:'YYYY-MM-DD HH:mm:ss'}}),
 ...[
  ['purchase','食材采购总额','万元',1,'none'],['visits','学生用餐人次','人次',1,'wan'],['schools','覆盖学校数量','所',0,'none'],['acceptance','食材验收合格率','%',1,'none']
 ].map(([key,title,unit,decimals,scale],i)=>panel(key,title,'kpi',32+i*400,120,380,148,{chartId:chartIds[key],style:{...style,fontSize:42,textColor:i===3?'#43E0BD':'#DDF5FF',numberFormat:format(unit,decimals,scale)}})),
 panel('notice','演示说明','text',1632,120,256,136,{style:{showTitle:false,titleAlign:'left',fontSize:14,textColor:'#80A7BC',transparent:true},content:{text:'●  江苏省 · 校园食堂\n独立演示数据 / 非真实业务\n静态样本，不自动更新'}}),
 panel('categories','01  食材采购结构 · 万元','chart',32,280,420,242,{chartId:chartIds.categories}),
 panel('costs','02  费用支出分析 · 万元','chart',32,538,420,242,{chartId:chartIds.costs}),
 panel('meals','03  学生用餐趋势 · 近 7 日','chart',32,796,420,242,{chartId:chartIds.meals}),
 panel('campus','校园空间态势 · 三维食堂结构示意','scene3d',472,280,976,492,{content:{scene3d:{schemaVersion:1,mode:'building',cameraPreset:'orbit',fallbackMode:'poster',rotateEnabled:true,zoomEnabled:true}}}),
 panel('regions','04  区域食堂分布 · 江苏','chart',472,792,478,246,{chartId:chartIds.regions}),
 panel('mealTypes','07  就餐类型分布','chart',1468,572,420,232,{chartId:chartIds.mealTypes}),
 panel('devices','05  食堂设备运行监测','chart',970,792,478,246,{chartId:chartIds.devices}),
 panel('safety','06  食品安全综合评分','chart',1468,280,420,276,{chartId:chartIds.safety}),
 panel('receipts','08  最新采购与验收','chart',1468,820,420,218,{chartId:chartIds.receipts}),
 panel('footer','页脚说明','text',32,1047,1856,28,{style:{showTitle:false,titleAlign:'left',fontSize:11,textColor:'#63879D',transparent:true},content:{text:'WANFO BI CLOUD  /  WISDOM CAMPUS                         演示数据 · 建筑为程序化结构示意，非实景数字孪生模型                         本布局所有区块均可在大屏编辑器中独立调整'}})
]
const definition={schemaVersion:1,name:'智慧校园食堂运行驾驶舱 · 新版编辑器演示',kind:'screen',layout:'absolute',scene:{width:1920,height:1080,fit:'contain',refreshSeconds:0},appearance:{visualPreset:'tech-blue-v1',theme:'dark',background:'#07111F',cardBackground:'#0B1D2E',textColor:'#D9EFFB',borderColor:'#1A4358',palette:'ocean',titleSize:17,radius:4,decimals:1,grouping:true},interaction:{controls:[],links:[]},panels}
const dashboards=await request('/bi/dashboard')
const existing=dashboards.find(d=>d.name===definition.name)
let saved
if(existing){const current=await request(`/bi/dashboard/${existing.id}/draft`);saved=await request(`/bi/dashboard/${existing.id}`,'PUT',{definition,version:current.version})}
else saved=await request('/bi/dashboard','POST',{definition})
const preview=await request(`/bi/dashboard/${saved.id}/preview`,'POST',{definition,version:saved.version,input:{filters:{},links:{}}})
if(Object.keys(preview.payloads).length!==specs.length)throw Error('Incomplete demo payloads')
const workspaceRoot=path.resolve(root,'..')
await fs.mkdir(path.join(workspaceRoot,'.local/runtime'),{recursive:true})
await fs.writeFile(path.join(workspaceRoot,'.local/runtime/canteen-demo-definition.json'),JSON.stringify({id:saved.id,version:saved.version,definition},null,2))
console.log(JSON.stringify({dashboardId:saved.id,version:saved.version,panels:panels.length,verifiedQueries:Object.keys(preview.payloads).length,editUrl:`http://localhost:5173/bi-admin/dashboard-workspace/${saved.id}/edit`}))
