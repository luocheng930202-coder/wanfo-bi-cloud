/** A grid preview uses the same desktop 12-column renderer, then scales its whole surface. */
export function templateGridSize(panels: {y:number;h:number}[]) {
  const rows=Math.max(1,...panels.map(panel=>panel.y+panel.h))
  return {width:1200,height:rows*78+8}
}
export function fitTemplateViewport(width:number,height:number,nativeWidth:number,nativeHeight:number) {
  if(![width,height,nativeWidth,nativeHeight].every(value=>Number.isFinite(value) && value>0))return {scale:0,x:0,y:0}
  const scale=Math.min(width/nativeWidth,height/nativeHeight)
  return {scale,x:(width-nativeWidth*scale)/2,y:(height-nativeHeight*scale)/2}
}
