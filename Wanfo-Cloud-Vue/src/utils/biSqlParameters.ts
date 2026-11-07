import type {SqlParameterDefinition} from '@/api/bi/dataset'
const variable=/\$\[([A-Za-z_][A-Za-z0-9_]{0,63})\]/g
function quotedEnd(sql:string,start:number):number{
  const quote=sql[start]
  for(let i=start+1;i<sql.length;i++)if(sql[i]===quote){if(sql[i+1]===quote)i++;else return i+1}
  throw Error('SQL 字符串或标识符未闭合')
}
/** Discovery only: the server independently validates WHERE placement and binds values. */
export function discoverSqlParameters(sql:string):string[]{
  const names=new Set<string>()
  for(let i=0;i<sql.length;){
    if(sql.startsWith('--',i)){const end=sql.indexOf('\n',i);i=end<0?sql.length:end}
    else if(sql.startsWith('/*',i)){const end=sql.indexOf('*/',i+2);if(end<0)throw Error('SQL 注释未闭合');i=end+2}
    else if(sql.startsWith('$DE_PARAM{',i)){
      const start=i+10;let end=start
      for(;end<sql.length;end++){if(["'",'"','`'].includes(sql[end])){end=quotedEnd(sql,end)-1;continue}if(sql[end]==='{')throw Error('SQL 参数条件不能嵌套');if(sql[end]==='}')break}
      if(end===sql.length)throw Error('SQL 参数条件未闭合')
      const body=sql.slice(start,end),matches=[...body.matchAll(variable)]
      if(!matches.length || body.replace(variable,'').includes('$['))throw Error('请使用有效的 $[参数名]，例如 $[PARAM1]')
      matches.forEach(match=>names.add(match[1]));i=end+1
    }else if(["'",'"','`'].includes(sql[i]))i=quotedEnd(sql,i)
    else i++
  }
  if(names.size>50)throw Error('最多支持 50 个 SQL 参数')
  return [...names]
}
export function syncSqlParameters(sql:string,existing:SqlParameterDefinition[]):SqlParameterDefinition[]{
  return discoverSqlParameters(sql).map(name=>existing.find(p=>p.name===name) || {name,label:name,type:'TEXT',multiple:new RegExp(`\\bIN\\s*\\(\\s*\\$\\[${name}\\]\\s*\\)`,'i').test(sql),required:false,defaultValues:[],defaultScope:'ALL'})
}
export function parameterLines(value:string):string[]{return value.split(/\r?\n/).filter(line=>line.length>0)}
