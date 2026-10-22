import type {ChartDefinition,ChartResult} from '@/api/bi/chart'
export function comparisonDefinition(definition:ChartDefinition,result:ChartResult|null):ChartDefinition {
  const comparison=result?.timeComparison
  if(!comparison || comparison.status!=='complete' || comparison.mode!==definition.query.timeComparison)return definition
  const metrics=definition.query.metrics.flatMap(metric=>{const key=metric.alias || metric.agg.toLowerCase()+'_'+metric.field;const field=comparison.fields.find(f=>f.key===key);return field?[metric,{...metric,alias:field.baseline}]:[metric]})
  return {...definition,query:{...definition.query,metrics}}
}
export function comparisonLabels(result:ChartResult|null,labels:Record<string,string>):Record<string,string>{
  const out={...labels,...result?.columnLabels},comparison=result?.timeComparison
  for(const f of comparison?.fields||[]){const name=labels[f.key]||f.key;out[f.baseline]=name+' · '+comparison!.label+'基期';out[f.difference]=name+' · 增长额';out[f.rate]=name+' · 增长率'}return out
}
export function comparisonRate(result:ChartResult|null,key:string,value:unknown):string|undefined {
  if(!result?.timeComparison?.fields.some(f=>f.rate===key))return undefined
  if(value==null || value==='')return '—'
  const n=Number(value);return Number.isFinite(n)?(n*100).toFixed(2)+'%':String(value)
}
