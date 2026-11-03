import {computed,onMounted,onBeforeUnmount,onActivated,onDeactivated,ref,watch,type Ref} from 'vue'
import {createRefreshLoop,refreshRetrySeconds} from '@/utils/biRefresh'

export function useBiRefresh(seconds:Ref<number>,task:()=>Promise<unknown>,busy:()=>boolean){
  const active=ref(true),visible=ref(!document.hidden),lastRefresh=ref(''),failures=ref(0)
  const available=computed(()=>active.value && visible.value)
  const loop=createRefreshLoop(async()=>{try{const result=await task();failures.value=result===false?failures.value+1:0;return result}catch(error){failures.value++;throw error}finally{lastRefresh.value=new Date().toLocaleTimeString()}},busy)
  const stop=watch(seconds,value=>{failures.value=0;loop.configure(value)},{immediate:true})
  function visibility(){visible.value=!document.hidden;loop.visibility(available.value)}
  onMounted(()=>{document.addEventListener('visibilitychange',visibility);visibility()})
  onActivated(()=>{active.value=true;visibility()})
  onDeactivated(()=>{active.value=false;loop.visibility(false)})
  onBeforeUnmount(()=>{stop();loop.dispose();document.removeEventListener('visibilitychange',visibility)})
  const refreshStatus=computed(()=>!seconds.value?'自动刷新已关闭':!available.value?'自动刷新已暂停':`${failures.value?'自动刷新连续失败 '+failures.value+' 次，'+refreshRetrySeconds(seconds.value,failures.value)+' 秒后重试':'每'+seconds.value+'秒自动刷新'}${lastRefresh.value?' · 最近尝试 '+lastRefresh.value:''}`)
  return {available,refreshStatus}
}
