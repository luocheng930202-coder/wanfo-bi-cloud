export interface SceneDatum {label:string;value:number}
function numeric(value:unknown):number|undefined {
  if(typeof value==='number')return Number.isFinite(value)?value:undefined
  if(typeof value!=='string' || !value.trim())return
  const text=value.trim()
  if(!/^[+-]?(?:\d+(?:\.\d*)?|\.\d+)(?:[eE][+-]?\d+)?$/.test(text) && !/^[+-]?\d{1,3}(?:,\d{3})+(?:\.\d+)?$/.test(text))return
  const parsed=Number(text.replaceAll(',',''))
  return Number.isFinite(parsed)?parsed:undefined
}
export function sceneData(rows:Record<string,unknown>[],dimension:string|undefined,metric:string|undefined,bound:boolean,limit=5) {
  const records:SceneDatum[]=[]
  if(!bound)return {state:'unbound' as const,records,invalid:0,omitted:0}
  if(!metric)return {state:'unconfigured' as const,records,invalid:0,omitted:0}
  let invalid=0,valid=0
  for(const [index,row] of rows.entries()){
    const value=numeric(row[metric])
    if(value===undefined){invalid++;continue}
    valid++
    if(records.length<limit)records.push({label:String((dimension?row[dimension]:undefined) ?? ('第'+(index+1)+'项')).slice(0,80),value})
  }
  return {state:records.length?'ready' as const:rows.length?'invalid' as const:'empty' as const,records,invalid,omitted:valid-records.length}
}
export function sceneBarGeometry(value:number,maximum:number){
  const height=Math.abs(value)/Math.max(Math.abs(maximum),Math.abs(value),1)*4.3
  const end=Math.sign(value)*height
  return {height,centerY:end/2,endY:end}
}
