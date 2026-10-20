/** One timer, no backlog. A slow request always settles before the next delay starts. */
export function createRefreshLoop(task:()=>Promise<unknown>, busy:()=>boolean, clock={
  set:(fn:()=>void,ms:number)=>setTimeout(fn,ms),
  clear:(id:ReturnType<typeof setTimeout>)=>clearTimeout(id)
}) {
  let timer:ReturnType<typeof setTimeout>|undefined, seconds=0, visible=true, disposed=false, running=false
  function clear(){if(timer!==undefined)clock.clear(timer);timer=undefined}
  function schedule(ms:number){clear();if(!disposed && visible && seconds>0)timer=clock.set(tick,ms)}
  async function tick(){
    timer=undefined
    if(disposed || !visible || !seconds || running)return
    if(busy()){schedule(1000);return}
    running=true
    try{await task()}catch{/* The caller renders the error; the next cycle retries. */}
    finally{running=false;schedule(seconds*1000)}
  }
  return {
    configure(value:number){seconds=Number.isInteger(value) && value>=10 && value<=3600?value:0;clear();if(!running)schedule(seconds*1000)},
    visibility(value:boolean){if(visible===value)return;visible=value;clear();if(value && !running)schedule(0)},
    dispose(){disposed=true;clear()}
  }
}
