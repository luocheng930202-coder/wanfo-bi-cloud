import {computed,onMounted,onBeforeUnmount,onActivated,onDeactivated,ref,watch,type Ref} from 'vue'
import {createRefreshLoop} from '@/utils/biRefresh'

export function useBiRefresh(seconds:Ref<number>,task:()=>Promise<unknown>,busy:()=>boolean){
  const active=ref(true),visible=ref(!document.hidden),lastRefresh=ref('')
  const available=computed(()=>active.value && visible.value)
  const loop=createRefreshLoop(async()=>{await task();lastRefresh.value=new Date().toLocaleTimeString()},busy)
  const stop=watch(seconds,value=>loop.configure(value),{immediate:true})
  function visibility(){visible.value=!document.hidden;loop.visibility(available.value)}
  onMounted(()=>{document.addEventListener('visibilitychange',visibility);visibility()})
  onActivated(()=>{active.value=true;visibility()})
  onDeactivated(()=>{active.value=false;loop.visibility(false)})
  onBeforeUnmount(()=>{stop();loop.dispose();document.removeEventListener('visibilitychange',visibility)})
  const refreshStatus=computed(()=>!seconds.value?'自动刷新已关闭':!available.value?'自动刷新已暂停':`每${seconds.value}秒自动刷新${lastRefresh.value?' · 最近尝试 '+lastRefresh.value:''}`)
  return {available,refreshStatus}
}
