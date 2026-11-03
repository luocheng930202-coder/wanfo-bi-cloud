import type {Decoration} from '@/api/bi/dashboard'

export interface BorderPreset {id:string;label:string;decoration:Decoration;w:number;h:number}
export interface LinePreset {id:string;label:string;decoration:Decoration;w:number;h:number}

export const borderPresets:BorderPreset[]=[
  {id:'cyan-frame',label:'科技青框',w:420,h:280,decoration:{color:'#38BDF8',stroke:2,pattern:'solid',radius:12,direction:'horizontal'}},
  {id:'gold-frame',label:'商务金框',w:420,h:280,decoration:{color:'#F59E0B',stroke:3,pattern:'solid',radius:4,direction:'horizontal'}},
  {id:'violet-dash',label:'紫色虚线框',w:400,h:260,decoration:{color:'#A78BFA',stroke:2,pattern:'dashed',radius:16,direction:'horizontal'}},
  {id:'mint-dot',label:'薄荷绿点框',w:400,h:260,decoration:{color:'#2DD4BF',stroke:2,pattern:'dotted',radius:8,direction:'horizontal'}},
  {id:'white-thin',label:'浅色细框',w:440,h:300,decoration:{color:'#E2E8F0',stroke:1,pattern:'solid',radius:0,direction:'horizontal'}},
  {id:'rose-thick',label:'玫红厚框',w:380,h:240,decoration:{color:'#F43F5E',stroke:4,pattern:'solid',radius:20,direction:'horizontal'}},
]

export const linePresets:LinePreset[]=[
  {id:'h-cyan',label:'水平青线',w:560,h:24,decoration:{color:'#38BDF8',stroke:2,pattern:'solid',radius:0,direction:'horizontal'}},
  {id:'h-gold',label:'水平金线',w:560,h:24,decoration:{color:'#F59E0B',stroke:3,pattern:'dashed',radius:0,direction:'horizontal'}},
  {id:'v-cyan',label:'垂直青线',w:24,h:320,decoration:{color:'#38BDF8',stroke:2,pattern:'solid',radius:0,direction:'vertical'}},
]
