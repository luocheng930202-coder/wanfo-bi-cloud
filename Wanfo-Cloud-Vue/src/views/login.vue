<template>
 <main class="wanfo-login">
  <div class="login-scene-frame"><div class="login-scene"><div class="login-scene-image" aria-hidden="true"></div><div class="scene-labels" aria-label="平台能力"><span style="left:18.36%;top:41.66%">数据接入</span><span style="left:27.45%;top:41.66%">数据加工</span><span style="left:37.98%;top:35.92%">资产治理</span><span style="left:49.64%;top:41.66%">数据分析</span><span style="left:58.97%;top:41.66%">可视化</span></div></div></div><div class="login-light-trail" aria-hidden="true"></div>
  <header class="login-brand"><img :src="brandIcon" alt="万佛数据平台标志"/><div>{{ title }}<small>WANFO DATA</small></div></header>
  <div class="login-layout">
   <section class="login-hero" aria-label="平台介绍"><h1>汇聚数据，<span>洞见价值</span></h1><p>连 接 · 治 理 · 分 析 · 展 示</p></section>
   <section class="login-panel" aria-labelledby="login-heading"><h2 id="login-heading">欢迎登录</h2><p class="login-subtitle">登录万佛数据平台，开启数据工作空间</p><div class="login-divider" aria-hidden="true"></div>
    <el-form ref="loginRef" :model="loginForm" :rules="loginRules" class="login-form" label-position="top" @submit.prevent="handleLogin">
     <el-form-item label="账号" prop="username"><el-input v-model="loginForm.username" name="username" autocomplete="username" placeholder="请输入账号" size="large"><template #prefix><svg-icon icon-class="user" class="input-icon"/></template></el-input></el-form-item>
     <el-form-item label="密码" prop="password"><el-input v-model="loginForm.password" name="password" type="password" autocomplete="current-password" placeholder="请输入密码" show-password size="large"><template #prefix><svg-icon icon-class="password" class="input-icon"/></template></el-input></el-form-item>
     <el-form-item v-if="captchaEnabled" label="验证码" prop="code"><div class="captcha-row"><el-input v-model="loginForm.code" name="code" autocomplete="off" placeholder="请输入验证码" size="large"><template #prefix><svg-icon icon-class="validCode" class="input-icon"/></template></el-input><button class="captcha-refresh" type="button" aria-label="刷新验证码" @click="getCode"><img v-if="codeUrl" :src="codeUrl" alt="登录验证码"/><span v-else>{{ captchaError?'加载失败，点击重试':'加载中…' }}</span></button></div></el-form-item>
     <el-checkbox v-model="loginForm.rememberMe" class="remember-account">记住账号</el-checkbox>
     <el-button :loading="loading" native-type="submit" type="primary" class="login-submit" size="large">{{ loading?'登录中…':'登 录' }}</el-button>
     <router-link v-if="register" class="register-link" to="/register">立即注册</router-link>
    </el-form><div class="login-security"><svg-icon icon-class="password"/><span>统一身份认证 · 权限控制</span></div>
   </section>
  </div><footer class="login-footer">WANFO DATA · 万佛数据平台</footer>
 </main>
</template>
<script setup lang="ts">
import brandIcon from '@/assets/bi/wanfo-brand-icon.png'
import { getCodeImg } from "@/api/login"
import Cookies from "js-cookie"
import useUserStore from '@/store/modules/user'
import type { FormInstance } from 'element-plus'
import type { CaptchaInfoResult } from '@/types/api/login'
import type { LoginForm } from '@/types/api/login'

const title = "万佛数据平台"
const userStore = useUserStore()
const route = useRoute()
const router = useRouter()
const loginRef=ref<FormInstance>()

const loginForm = ref<LoginForm>({
  username: "",
  password: "",
  rememberMe: false,
  code: "",
  uuid: ""
})

const loginRules = {
  username: [{ required: true, trigger: "blur", message: "请输入您的账号" }],
  password: [{ required: true, trigger: "blur", message: "请输入您的密码" }],
  code: [{ required: true, trigger: "change", message: "请输入验证码" }]
}

const codeUrl = ref("")
const captchaError=ref(false)
const loading = ref(false)
// 验证码开关
const captchaEnabled = ref(true)
// 注册开关
const register = ref(false)
const redirect = ref<string | undefined>(undefined)

watch(route, (newRoute: any) => {
    redirect.value = (newRoute.query && newRoute.query.redirect) as string | undefined
}, { immediate: true })

function handleLogin(): void {
  loginRef.value?.validate((valid: boolean) => {
    if (valid) {
      loading.value = true
      // 只记住账号；密码不写入浏览器持久化存储。
      Cookies.remove("password")
      if (loginForm.value.rememberMe) {
        Cookies.set("username", loginForm.value.username, { expires: 30 })
        Cookies.set("rememberMe", String(loginForm.value.rememberMe), { expires: 30 })
      } else {
        // 否则移除
        Cookies.remove("username")
        Cookies.remove("password")
        Cookies.remove("rememberMe")
      }
      // 调用action的登录方法
      userStore.login(loginForm.value).then(() => {
        const query = route.query
        const otherQueryParams = Object.keys(query).reduce((acc: Record<string, any>, cur) => {
          if (cur !== "redirect") {
            acc[cur] = query[cur]
          }
          return acc
        }, {})
        router.push({ path: redirect.value || "/", query: otherQueryParams })
      }).catch(() => {
        loading.value = false
        // 重新获取验证码
        if (captchaEnabled.value) {
          getCode()
        }
      })
    }
  })
}

function getCode(): void {
  captchaError.value=false
  getCodeImg().then(res => {
    captchaEnabled.value = res.captchaEnabled === undefined ? true : res.captchaEnabled
    if (captchaEnabled.value) {
      codeUrl.value = "data:image/gif;base64," + res.img
      loginForm.value.uuid = res.uuid
    }
  }).catch(()=>{captchaError.value=true;codeUrl.value=""})
}

function getCookie(): void {
  const username = Cookies.get("username")
  Cookies.remove("password")
  const rememberMe = Cookies.get("rememberMe")
  loginForm.value = {
    username: username === undefined ? loginForm.value.username : username,
    password: "",
    rememberMe: rememberMe === "true",
    code: "",
    uuid: ""
  }
}

getCode()
getCookie()
</script>
<style scoped lang="scss">
.wanfo-login{--el-color-primary:#13d9ed;--el-color-primary-light-3:#50e3ef;--el-color-primary-dark-2:#0eacbf;--el-text-color-regular:#e0edf7;--el-text-color-primary:#f1f7fc;--el-text-color-placeholder:#9badc1;--el-border-color:#60778d;--el-fill-color-blank:#172c43;--el-bg-color-overlay:#10263c;position:relative;isolation:isolate;min-height:100dvh;background:#061426;color:#f1f7fc;overflow:hidden;font-family:Inter,'Segoe UI','Microsoft YaHei',sans-serif;display:flex;flex-direction:column;padding:30px 4vw 20px}.login-art{position:absolute;inset:0;z-index:-2;background:url('../assets/bi/wanfo-login-background.png') center / cover no-repeat;animation:ambient-data 9s ease-in-out infinite}.login-brand{display:flex;align-items:center;gap:14px;position:relative;z-index:1}.login-brand img{width:66px;height:66px;object-fit:contain}.login-brand>div{font-size:27px;font-weight:700;letter-spacing:1px}.login-brand small{display:block;font-size:13px;letter-spacing:5px;margin-top:5px;font-weight:600}.login-layout{display:grid;grid-template-columns:minmax(0,1.7fr) minmax(360px,1fr);gap:5vw;flex:1;align-items:center;padding:20px 0 36px}.login-hero{align-self:stretch;padding-top:45px}.login-hero h1{font-size:clamp(34px,3.6vw,68px);margin:0;font-weight:750;letter-spacing:-1.5px;white-space:nowrap}.login-hero h1 span{color:#59eafa}.login-hero>p{font-size:clamp(15px,1.7vw,25px);letter-spacing:5px;margin-top:20px}.platform-capabilities{display:flex;gap:16px;flex-wrap:wrap;margin-top:24px;color:#bcefff;font-size:12px;letter-spacing:1px}.platform-capabilities span{border:1px solid #32809b80;border-radius:5px;padding:6px 11px;background:#071c35aa}.login-panel{border:1px solid #61879d80;border-radius:20px;background:#0c2139df;box-shadow:inset 0 1px 0 #74e7fc33,0 16px 60px #0002;padding:42px 40px 28px;backdrop-filter:blur(12px);width:100%;max-width:540px;justify-self:end}.login-panel h2{font-size:40px;font-weight:700;letter-spacing:1px;margin:0}.login-subtitle{font-size:14px;color:#cad8e8;margin-top:12px;line-height:1.7}.login-divider{height:1px;background:#356078;margin:24px 0 25px}.login-form{width:100%;background:none;padding:0}.login-form :deep(.el-form-item){margin-bottom:23px}.login-form :deep(.el-form-item__label){color:#e5edf7;font-size:14px;line-height:20px;padding-bottom:9px}.login-form :deep(.el-input__wrapper){background:#23374dcc;box-shadow:0 0 0 1px #6d879a inset;min-height:46px;border-radius:8px;padding:0 14px}.login-form :deep(.el-input__wrapper.is-focus){box-shadow:0 0 0 1px #32e1f4 inset,0 0 0 3px #13d9ed18}.login-form :deep(.el-input__inner){color:#f0f7ff;font-size:14px}.input-icon{width:17px;height:17px;color:#d4e6f2;margin-right:6px}.captcha-row{display:flex;gap:13px;width:100%}.captcha-row>.el-input{min-width:0;flex:1}.captcha-refresh{width:118px;flex-shrink:0;height:46px;border:1px solid #5e7b94;border-radius:8px;background:#eff9ff;padding:0;overflow:hidden;cursor:pointer;color:#18445c;font:inherit;font-size:11px}.captcha-refresh img{display:block;width:100%;height:100%;object-fit:contain}.remember-account{margin:0 0 25px}.login-form :deep(.el-checkbox__label){color:#d7e5f1}.login-form :deep(.el-checkbox__inner){background:#19384d;border-color:#87a5b7}.login-form :deep(.el-checkbox__input.is-checked .el-checkbox__inner){background:#13d9ed;border-color:#13d9ed}.login-form :deep(.el-checkbox__input.is-checked .el-checkbox__inner:after){border-color:#073045}.login-submit{width:100%;height:52px;border:0;border-radius:8px;font-size:19px;font-weight:700;letter-spacing:8px;--el-button-text-color:#042a3d;--el-button-hover-text-color:#042a3d;--el-button-active-text-color:#042a3d}.login-security{display:flex;align-items:center;justify-content:center;gap:12px;color:#c4d9e8;margin-top:28px;padding-top:21px;border-top:1px solid #42678080;font-size:12px;letter-spacing:2px}.login-security svg{width:15px}.login-footer{text-align:center;font-size:11px;letter-spacing:3px;color:#c4d9e8;padding:5px 0;position:relative}.register-link{display:block;text-align:right;margin-top:14px;color:#54eafa;font-size:12px}.login-light-trail{position:absolute;pointer-events:none;z-index:-1;bottom:7%;left:-12%;width:140px;height:2px;background:#45e6ff;box-shadow:0 0 25px 8px #25d4e955;animation:data-transit 12s linear infinite;opacity:.6}@keyframes data-transit{from{transform:translateX(0);opacity:0}10%{opacity:.6}85%{opacity:.6}to{transform:translateX(100vw);opacity:0}}@keyframes ambient-data{0%,100%{opacity:.83}50%{opacity:1}}@media(min-width:1700px){.login-panel{padding:48px 46px 34px}.login-layout{gap:7vw}.login-panel h2{font-size:46px}.login-form :deep(.el-form-item){margin-bottom:27px}.login-hero{padding-top:50px}}@media(max-width:1100px){.wanfo-login{padding:24px 3vw 18px}.login-layout{grid-template-columns:minmax(0,1fr) minmax(350px,1fr);gap:25px}.login-hero h1{font-size:32px;white-space:normal;line-height:1.5}.login-panel{padding:30px 27px}.platform-capabilities{gap:8px}.login-brand>div{font-size:22px}.login-hero>p{font-size:16px;letter-spacing:1px}.login-panel h2{font-size:34px}}@media(max-width:760px){.wanfo-login{padding:22px 20px;overflow:auto}.login-layout{display:flex;flex-direction:column;gap:18px;padding:24px 0}.login-hero{padding:0;align-self:center;text-align:center}.login-hero h1{font-size:27px}.login-hero>p{font-size:13px;margin:8px 0 0;letter-spacing:2px}.platform-capabilities{display:none}.login-panel{max-width:450px;justify-self:auto;padding:28px 24px;border-radius:14px;background:#0b2039ed}.login-panel h2{font-size:29px}.login-subtitle{font-size:12px}.login-brand{gap:10px}.login-brand img{width:45px;height:45px}.login-brand>div{font-size:20px}.login-brand small{font-size:9px;letter-spacing:4px}.login-art{background-position:35% center;opacity:.5;animation:none}.login-footer{font-size:9px;letter-spacing:1px}}@media(prefers-reduced-motion:reduce){.login-art,.login-light-trail{animation:none}.login-light-trail{display:none}}
</style>
<style scoped>
.login-scene-frame{position:absolute;inset:0;z-index:-2;container-type:size;pointer-events:none;overflow:hidden}.login-scene{position:absolute;left:50%;top:50%;transform:translate(-50%,-50%);width:max(100cqw,177.6833cqh);height:max(56.28cqw,100cqh)}.login-scene-image{position:absolute;inset:0;background:url('../assets/bi/wanfo-login-background.png') center/100% 100% no-repeat;animation:ambient-data 9s ease-in-out infinite}.scene-labels span{position:absolute;transform:translate(-50%,-100%);margin-top:-12px;white-space:nowrap;color:#e2f8ff;border:1px solid #3898bf;border-radius:5px;background:#0a223be6;padding:5px 11px;font-size:clamp(11px,.9vw,16px);letter-spacing:1px;box-shadow:0 0 12px #25d4e91a}.scene-labels span:after{content:'';position:absolute;left:50%;top:100%;height:12px;width:1px;background:#32d8ec}@media(max-width:760px){.scene-labels{display:none}.login-scene-image{opacity:.5;animation:none}}@media(prefers-reduced-motion:reduce){.login-scene-image{animation:none}}
</style>
