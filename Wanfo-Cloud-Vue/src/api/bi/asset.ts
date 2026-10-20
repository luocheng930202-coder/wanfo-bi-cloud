import request from '@/utils/request'
import type {GrantPrincipal,ResourceGrant} from './datasource'
import type {BiResult as AjaxResult} from './response'
export interface Asset {id:number;name:string;byteSize:number;createBy?:string;resourceActions?:string[];image?:string}
export const listAssets=(page=1,query=''):Promise<AjaxResult<{items:Asset[];total:number}>>=>request({url:'/bi/asset',params:{page,query}})
export const getAsset=(id:number):Promise<AjaxResult<Asset>>=>request({url:`/bi/asset/${id}`})
export const uploadAsset=(name:string,image:string):Promise<AjaxResult<{id:number}>>=>request({url:'/bi/asset',method:'post',data:{name,image}})
export const deleteAsset=(id:number):Promise<AjaxResult>=>request({url:`/bi/asset/${id}`,method:'delete'})

export const renameAsset=(id:number,name:string,expectedName:string):Promise<AjaxResult>=>request({url:`/bi/asset/${id}`,method:'put',data:{name,expectedName}})
export const searchAssetPrincipals=(query=''):Promise<AjaxResult<GrantPrincipal[]>>=>request({url:'/bi/asset/principals',params:{query}})
export const assetGrants=(id:number):Promise<AjaxResult<ResourceGrant[]>>=>request({url:`/bi/asset/${id}/grants`})
export const grantAsset=(id:number,principalId:number,action:string):Promise<AjaxResult>=>request({url:`/bi/asset/${id}/grants`,method:'post',data:{principalId,action}})
export const revokeAssetGrant=(id:number,principalId:number,action:string):Promise<AjaxResult>=>request({url:`/bi/asset/${id}/grants/${principalId}/${action}`,method:'delete'})

export const uploadStagingAsset=(image:string,name=''):Promise<AjaxResult<{id:number;image:string;name:string;byteSize:number;expireHours:number}>>=>request({url:'/bi/asset/staging',method:'post',data:{name,image}})
export const releaseStagingAssets=(ids:number[]):Promise<AjaxResult<{released:number}>>=>request({url:'/bi/asset/staging/release',method:'post',data:{ids}})
export const promoteStagingAsset=(id:number,name=''):Promise<AjaxResult<{id:number}>>=>request({url:`/bi/asset/staging/${id}/promote`,method:'post',data:{name}})
