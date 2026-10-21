import type {DashboardAppearance} from '@/api/bi/dashboard'
/** Shared typography and neutral colors; persisted appearance and series colors take precedence. */
export const BI_FONT_FAMILY='Inter, -apple-system, BlinkMacSystemFont, "Segoe UI", "PingFang SC", "Microsoft YaHei", sans-serif'
export const BI_TYPE={body:14,chart:12,caption:11,title:14,pageTitle:30} as const
export function resolveBiTheme(appearance?:DashboardAppearance){
 const dark=appearance?.theme==='dark'
 return {fontFamily:BI_FONT_FAMILY,fontSize:BI_TYPE.chart,text:appearance?.textColor || (dark?'#E8EDF5':'#182230'),muted:appearance?.textColor || (dark?'#A0AEC0':'#667085'),border:appearance?.borderColor || (dark?'#303C50':'#E6EBF3'),surface:appearance?.cardBackground || (dark?'#1C2432':'#FFFFFF'),background:appearance?.background || (dark?'#121A27':'#F6F8FC')}
}
