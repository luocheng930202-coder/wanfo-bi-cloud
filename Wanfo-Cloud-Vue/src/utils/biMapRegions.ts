export interface GeoRegion {code:string;name:string;level:'country'|'province'|'city'|'district';parent:string|null;children:string[];aliases:string[];boundarySource:string}
export interface GeoCatalogIndex {version:string;source:string;regions:GeoRegion[];unavailableChildren:string[]}
export interface GeoFeature {adcode:string|number;name:string;childrenNum?:number;level?:string}
export function mapScopeCode(scope:string='china-provinces'):string {
  if(scope==='china-provinces')return '100000'
  if(!/^[0-9]{6}$/.test(scope))throw new Error('不支持的地图范围')
  return scope
}
export function geoDisplayLevel(catalog:GeoCatalogIndex,code:string):'province'|'city'|'county' {
  const node=catalog.regions.find(r=>r.code===code);if(!node)return 'province'
  const children=node.children.map(c=>catalog.regions.find(r=>r.code===c)).filter(Boolean)
  const level=children.some(r=>r?.level==='province')?'province':children.some(r=>r?.level==='city')?'city':children.length?'district':node.level
  return level==='district'?'county':level==='city'?'city':'province'
}
export interface GeoTreeNode {value:string;label:string;children?:GeoTreeNode[]}
export function geoTree(catalog:GeoCatalogIndex):GeoTreeNode[] {
  const nodes=new Map(catalog.regions.map(r=>[r.code,r]))
  function visit(code:string,seen:Set<string>):GeoTreeNode{const r=nodes.get(code)!;const next=new Set(seen).add(code);const children=r.children.filter(c=>nodes.has(c)&&!next.has(c)).map(c=>visit(c,next));return {value:code,label:r.name+(children.length?'':'（边界）'),...(children.length?{children}:{})}}
  return nodes.has('100000')?[visit('100000',new Set())]:[]
}
export function geoNameAliases(name:string):string[]{return [...new Set([name,name.replace(/(特别行政区|维吾尔自治区|壮族自治区|回族自治区|自治区|省|市|区|县)$/,'')])].filter(Boolean)}
export function matchGeoFeature(features:GeoFeature[],raw:unknown,mode:'name'|'code'):GeoFeature|undefined {
  const value=String(raw??'').trim();if(!value)return undefined
  const matches=features.filter(f=>mode==='code'?String(f.adcode)===value:geoNameAliases(f.name).includes(value))
  return matches.length===1?matches[0]:undefined
}
