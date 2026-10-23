import request from '@/utils/request'
import type {BiResult} from './response'
export interface QueryMonitorRow {requestId:string;instanceId:string;source:string;startedAt:number;durationMs:number;datasourceCount:number;status:string;slow:boolean}
export interface QueryMonitor {scope:'instance'|'cluster';instanceId:string;active:number;waiting:number;limits:{maxActive:number;perUser:number;perSource:number;maxWaiting:number;slowMillis:number};running:QueryMonitorRow[];recent:QueryMonitorRow[]}
export const queryMonitor=(signal?:AbortSignal):Promise<BiResult<QueryMonitor>>=>request({url:'/bi/query/monitor',signal})
export const cancelMonitoredQuery=(requestId:string):Promise<BiResult<{accepted:boolean}>>=>request({url:'/bi/query/monitor/cancel',method:'post',data:{requestId}})
