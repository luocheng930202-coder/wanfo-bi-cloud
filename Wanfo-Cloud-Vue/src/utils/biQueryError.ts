export function isBiQueryUrl(url:unknown):boolean{return typeof url==='string' && (/^\/bi\/chart\/(data|map-preview|saved\/[^/]+\/data)$/.test(url) || /^\/bi\/dashboard\/[^/]+\/(panels\/[^/]+\/data|controls\/[^/]+\/options)$/.test(url))}
const messages={
 QUERY_BUSY:'查询繁忙，请稍后重试，避免连续刷新。',
 QUERY_CANCELLED:'查询已取消，可重新查询。',
 SESSION_EXPIRED:'登录已过期，请重新登录后查询。',
 ACCESS_DENIED:'没有此数据的查询权限，请联系管理员核对资源及行列权限。',
 RESOURCE_UNAVAILABLE:'查询资源不存在或已不可用，请返回列表检查。',
 VERSION_CHANGED:'发布版本或图表版本已变化，请重新打开图表或刷新看板后查询。',
 QUERY_TIMEOUT:'查询超时，请缩小时间或筛选范围后重试；仍失败时联系管理员。',
 DATASOURCE_UNAVAILABLE:'暂时无法连接数据源，请联系管理员检查连接状态后重试。',
 QUERY_INVALID:'查询配置无效，请检查字段、指标和筛选配置；字段可能已发生变化。',
 NETWORK_UNAVAILABLE:'无法连接服务，请检查网络及服务状态后重试。',
 QUERY_FAILED:'查询未完成，请重试；仍失败时提供排查编号联系管理员。'
} as const
export class BiQueryError extends Error {
 constructor(public readonly kind:keyof typeof messages,public readonly requestId?:string){super(messages[kind]);this.name='BiQueryError'}
}
export function biQueryError(value:unknown):BiQueryError {
 if(value instanceof BiQueryError)return value
 const error=value && typeof value==='object'?value as Record<string,any>:{}
 const response=error.response || {},data=response.data || error
 const status=Number(data.code || response.status)
 let kind:keyof typeof messages='QUERY_FAILED'
 if(error.code==='ECONNABORTED' || error.code==='ETIMEDOUT' || status===504)kind='QUERY_TIMEOUT'
 else if(status===401)kind='SESSION_EXPIRED'
 else if(status===403)kind='ACCESS_DENIED'
 else if(status===404)kind='RESOURCE_UNAVAILABLE'
 else if(status===409)kind='VERSION_CHANGED'
 else if(status===400)kind='QUERY_INVALID'
 else if(status===429)kind='QUERY_BUSY'
 else if(status===499)kind='QUERY_CANCELLED'
 else if(typeof data.errorType==='string' && Object.prototype.hasOwnProperty.call(messages,data.errorType))kind=data.errorType as keyof typeof messages
 else if(error.code==='ERR_NETWORK')kind='NETWORK_UNAVAILABLE'
 // Accept only server-generated UUIDs; never render untrusted messages or request headers.
 const requestId=typeof data.requestId==='string' && /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(data.requestId)?data.requestId:undefined
 return new BiQueryError(kind,requestId)
}
export function queryErrorText(value:unknown):string{const error=biQueryError(value);return error.message+(error.requestId?' 排查编号：'+error.requestId:'')}
