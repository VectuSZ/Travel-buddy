<script setup lang="ts">
const {t,locale}=useI18n(),route=useRoute(),{write}=useTripApi()
const form=reactive({reporterName:'',reporterEmail:'',category:'',description:'',goodFaith:false})
const token=ref(''),error=ref(''),busy=ref(false),done=ref(false),ready=ref(false),signedIn=ref(false)
const config=ref<{local:boolean;siteKey:string}|null>(null)
const widget=ref<HTMLElement|null>(null)
type Turnstile={render:(el:HTMLElement,options:Record<string,unknown>)=>string;reset:(id:string)=>void;remove:(id:string)=>void}
let widgetId=''
function turnstile(){return (window as unknown as {turnstile?:Turnstile}).turnstile}
function fail(e:unknown){const x=e as {statusCode?:number;data?:{code?:string}};error.value=x.data?.code||(x.statusCode===429?'RATE_LIMITED':'ERROR')}
async function verify(){
 try{token.value=(await write<{token:string}>('/api/v1/reports/challenge',{})).token}catch(e){fail(e)}
}
onMounted(async()=>{
 try{
  config.value=await $fetch<{local:boolean;siteKey:string}>('/api/v1/reports/config',{retry:0,cache:'no-store'})
  try{await $fetch('/api/v1/auth/session',{retry:0,cache:'no-store'});signedIn.value=true}catch{}
  ready.value=true
  if(!config.value?.local){
   await nextTick()
   const script=document.createElement('script');script.src='https://challenges.cloudflare.com/turnstile/v0/api.js?render=explicit'
   script.onload=()=>{if(widget.value)widgetId=turnstile()!.render(widget.value,{'sitekey':config.value!.siteKey,action:'report',callback:(value:string)=>{token.value=value},'expired-callback':()=>{token.value=''}})}
   script.onerror=()=>{error.value='CHALLENGE_UNAVAILABLE'};document.head.appendChild(script)
  }
 }catch(e){fail(e)}
})
onBeforeUnmount(()=>{if(widgetId)turnstile()?.remove(widgetId)})
async function send(){
 if(busy.value)return
 busy.value=true;error.value=''
 try{
  const trip=typeof route.query.trip==='string'?route.query.trip:null,user=typeof route.query.user==='string'?route.query.user:null
  await write('/api/v1/reports',{...form,targetTripId:trip,targetUserId:user,targetUrl:trip?(locale.value==='en'?'/en/trips/':'/trips/')+trip:(locale.value==='en'?'/en/profiles/':'/profiles/')+user,token:token.value})
  done.value=true
 }catch(e){fail(e);token.value='';if(widgetId)turnstile()?.reset(widgetId)}finally{busy.value=false}
}
</script>
<template>
<p v-if="!ready&&!error">{{ t('loading') }}</p>
<p v-if="error" role="alert">{{ t('moderation.errors.'+(['INVALID_REPORT','TARGET_NOT_FOUND','RATE_LIMITED','CHALLENGE_INVALID','CHALLENGE_UNAVAILABLE'].includes(error)?error:'ERROR')) }}</p>
<p v-if="done" role="status">{{ t('moderation.received') }}</p>
<form v-else-if="ready" @submit.prevent="send"><fieldset :disabled="busy">
<p>{{ t('moderation.hint') }}</p>
<template v-if="!signedIn"><label>{{ t('moderation.name') }}<input v-model="form.reporterName" name="reporterName" required maxlength="120"></label><label>{{ t('moderation.email') }}<input v-model="form.reporterEmail" name="reporterEmail" type="email" required maxlength="254"></label></template>
<label>{{ t('moderation.category') }}<select v-model="form.category" name="category" required><option value="">{{ t('owner.choose') }}</option><option v-for="c in ['SPAM','HARASSMENT','FRAUD','UNSAFE','OTHER']" :key="c" :value="c">{{ t('moderation.'+c) }}</option></select></label>
<label>{{ t('moderation.description') }}<textarea v-model="form.description" name="reportDescription" required maxlength="4000" rows="5" /></label>
<label><input v-model="form.goodFaith" type="checkbox" name="goodFaith" required>{{ t('moderation.faith') }}</label>
<template v-if="config?.local"><p>{{ t('moderation.local') }}</p><button type="button" @click="verify">{{ t('moderation.verify') }}</button><span v-if="token"> ✓</span></template><div ref="widget" />
<button type="submit" :disabled="!token">{{ t('moderation.submit') }}</button>
</fieldset></form>
</template>
