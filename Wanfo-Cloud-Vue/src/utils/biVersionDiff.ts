export interface VersionDifference {path:string;before:string;after:string}
/** Bounded structural comparison; large image/text content is summarized for the review table. */
export function versionDifferences(before:unknown,after:unknown,limit=200):VersionDifference[]{
  const result:VersionDifference[]=[]
  const text=(value:unknown)=>value===undefined?'（不存在）':JSON.stringify(value)?.slice(0,180) || String(value)
  function visit(a:unknown,b:unknown,path:string,depth:number){
    if(result.length>=limit || Object.is(a,b))return
    if(depth<16 && a!==null && b!==null && typeof a==='object' && typeof b==='object' && Array.isArray(a)===Array.isArray(b)){
      const left=a as Record<string,unknown>,right=b as Record<string,unknown>
      for(const key of new Set([...Object.keys(left),...Object.keys(right)])){visit(left[key],right[key],path?`${path}.${key}`:key,depth+1);if(result.length>=limit)break}
    }else result.push({path:path || 'definition',before:text(a),after:text(b)})
  }
  visit(before,after,'',0);return result
}
