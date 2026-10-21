export const chartWorkspacePath=(id?:number,mode='view')=>id?`/bi-admin/chart-workspace/${id}/${mode}`:'/bi-admin/chart-workspace/new'
export const dashboardWorkspacePath=(id?:number,mode='edit')=>id?`/bi-admin/dashboard-workspace/${id}/${mode}`:'/bi-admin/dashboard-workspace/new'

export interface LibraryPosition { search:string; filter:string; display:'grid'|'list'; page:number; scroll:number }
const defaults=():LibraryPosition=>({search:'',filter:'all',display:'grid',page:1,scroll:0})
const key=(kind:string,actor:string)=>`bi:library-position:${actor}:${kind}`
export function readLibraryPosition(kind:string,actor:string):LibraryPosition {
  try {
    const raw=JSON.parse(sessionStorage.getItem(key(kind,actor))||'null')
    if(!raw)return defaults()
    return {
      search:typeof raw.search==='string'?raw.search.slice(0,200):'',
      filter:typeof raw.filter==='string'?raw.filter:'all',
      display:raw.display==='list'?'list':'grid',
      page:Number.isSafeInteger(raw.page) && raw.page>0?raw.page:1,
      scroll:typeof raw.scroll==='number' && Number.isFinite(raw.scroll)?Math.max(0,raw.scroll):0
    }
  } catch {return defaults()}
}
export function saveLibraryPosition(kind:string,actor:string,value:LibraryPosition):void {
  try{sessionStorage.setItem(key(kind,actor),JSON.stringify(value))}catch{/* Browsers may disable session storage. Navigation still works. */}
}
