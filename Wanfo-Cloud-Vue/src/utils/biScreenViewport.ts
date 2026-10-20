export function rulerTicks(length:number,scale:number){
  const raw=80/scale,base=10**Math.floor(Math.log10(raw))
  const step=[1,2,5,10].map(n=>n*base).find(n=>n>=raw) || base*10
  return Array.from({length:Math.floor(length/step)+1},(_,i)=>i*step)
}
export function guidePosition(client:number,origin:number,scale:number,limit:number){
  return Math.max(0,Math.min(limit,Math.round((client-origin)/scale)))
}
export function snapAxis(start:number,length:number,limit:number,guides:number[],tolerance:number,resize=false){
  const targets=guides.filter(n=>Number.isInteger(n) && n>=0 && n<=limit)
  let delta=0,distance=tolerance+1
  for(const edge of resize?[start+length]:[start,start+length/2,start+length]){
    for(const guide of targets){const d=guide-edge;if(Math.abs(d)<distance && Math.abs(d)<=tolerance){delta=d;distance=Math.abs(d)}}
  }
  return resize?Math.max(20,Math.min(limit-start,Math.round(length+delta))):Math.max(0,Math.min(limit-length,Math.round(start+delta)))
}
