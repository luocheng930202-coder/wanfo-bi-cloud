import request from '@/utils/request'

function randomKey(){
 if(typeof crypto.randomUUID==='function')return crypto.randomUUID()
 const bytes=crypto.getRandomValues(new Uint8Array(16));bytes[6]=(bytes[6]&15)|64;bytes[8]=(bytes[8]&63)|128
 const hex=Array.from(bytes,b=>b.toString(16).padStart(2,'0')).join('');return `${hex.slice(0,8)}-${hex.slice(8,12)}-${hex.slice(12,16)}-${hex.slice(16,20)}-${hex.slice(20)}`
}
/** One cancellation ticket per request signal; no actor identity is supplied by the client. */
export function createQueryCancellation(send:(key:string)=>Promise<unknown>,uuid:()=>string=randomKey){
 const tickets=new WeakMap<AbortSignal,string>()
 return {headers(signal?:AbortSignal):Record<string,string>{
  if(!signal)return {}
  let key=tickets.get(signal)
  if(!key){key=uuid();tickets.set(signal,key);const ticket=key;const cancel=()=>{void Promise.resolve().then(()=>send(ticket)).catch(()=>{})};if(signal.aborted)cancel();else signal.addEventListener('abort',cancel,{once:true})}
  return {'X-BI-Query-Key':key}
 }}
}
const cancellation=createQueryCancellation(key=>request({url:'/bi/query/cancel',method:'post',data:{key},timeout:5000}))
export const queryCancellationHeaders=(signal?:AbortSignal)=>cancellation.headers(signal)
