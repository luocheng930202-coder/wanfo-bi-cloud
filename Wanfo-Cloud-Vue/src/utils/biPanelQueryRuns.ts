/** Independent panel requests. Canceling one never releases or aborts another. */
export function createPanelQueryRuns(){
  const runs=new Map<string,{controller:AbortController;started:number}>()
  return {
    begin(key:string,now=Date.now()){if(runs.has(key))return undefined;const run={controller:new AbortController(),started:now};runs.set(key,run);return run.controller},
    current(key:string,controller:AbortController){return runs.get(key)?.controller===controller && !controller.signal.aborted},
    finish(key:string,controller:AbortController){if(runs.get(key)?.controller!==controller)return false;runs.delete(key);return true},
    cancel(key:string){const run=runs.get(key);if(!run)return false;run.controller.abort();runs.delete(key);return true},
    cancelAll(){for(const run of runs.values())run.controller.abort();runs.clear()},
    elapsed(key:string,now=Date.now()){const run=runs.get(key);return run?Math.max(0,now-run.started):0},
    get size(){return runs.size}
  }
}
