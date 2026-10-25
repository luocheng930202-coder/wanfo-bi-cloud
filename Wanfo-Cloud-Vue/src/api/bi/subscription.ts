import request from '@/utils/request'
import type { BiResult } from './response'
export type SubscriptionFormat='csv'|'xlsx'|'pdf'
export interface ReportSubscription {id:number;name:string;sourceType:'chart'|'dashboard';sourceId:number;panelKey:string;format:SubscriptionFormat;intervalMinutes:number;enabled:boolean;nextRun:string;lastRun?:string;lastStatus:string;lastMessage:string;recipientCount:number;createTime:string}
export interface SubscriptionCreate {sourceType:'chart'|'dashboard';sourceId:number;panelKey?:string;format:SubscriptionFormat;name:string;recipients:string[];intervalMinutes:number}
export interface SubscriptionAudit {id:number;startedAt:string;finishedAt:string;status:string;attempt:number;rowCount:number;recipientCount:number;message:string}
export const listSubscriptions=():Promise<BiResult<ReportSubscription[]>>=>request({url:'/bi/subscription'})
export const createSubscription=(data:SubscriptionCreate):Promise<BiResult<{id:number;status:string}>>=>request({url:'/bi/subscription',method:'post',data})
export const updateSubscription=(id:number,data:{recipients?:string[];intervalMinutes:number;enabled:boolean}):Promise<BiResult<{updated:boolean}>>=>request({url:`/bi/subscription/${id}`,method:'put',data})
export const deleteSubscription=(id:number):Promise<BiResult<{deleted:boolean}>>=>request({url:`/bi/subscription/${id}`,method:'delete'})
export const subscriptionHistory=(id:number):Promise<BiResult<SubscriptionAudit[]>>=>request({url:`/bi/subscription/${id}/history`})
