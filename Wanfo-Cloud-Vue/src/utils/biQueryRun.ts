export function queryWaitMessage(elapsedMs:number):string {
  const seconds=Math.max(0,Math.floor(elapsedMs/1000))
  return seconds>=5?`查询耗时较长，已等待 ${seconds} 秒；可以继续等待或停止等待。`:`正在查询，已等待 ${seconds} 秒…`
}

/** One active browser request; late completions cannot release a newer run. */
export function createQueryRun(){
  let active:AbortController|undefined
  return {
    begin(){if(active && !active.signal.aborted)return undefined;active=new AbortController();return active},
    cancel(){active?.abort();active=undefined},
    finish(controller:AbortController){if(active!==controller)return false;active=undefined;return true},
    isCurrent(controller:AbortController){return active===controller && !controller.signal.aborted}
  }
}
