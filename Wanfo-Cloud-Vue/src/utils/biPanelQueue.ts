/** At most two requests per round. The caller owns errors and stale-response checks. */
export async function runPanelQueue<T>(items:readonly T[], task:(item:T)=>Promise<unknown>, current:()=>boolean=()=>true) {
  let next=0
  async function worker(){
    while(current() && next<items.length){
      const item=items[next++]
      await task(item)
    }
  }
  await Promise.all([worker(),worker()])
}
