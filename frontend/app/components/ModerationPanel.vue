<script setup lang="ts">
interface Report {id:string;targetTripId:string|null;targetUserId:string|null;targetLabel:string;targetContent:string;targetStatus:string;targetUrl:string;category:string;description:string;status:string;resolutionReason:string|null}
const {t}=useI18n(),{write}=useTripApi()
const status=ref('OPEN'),rows=ref<Report[]>([]),reasons=reactive<Record<string,string>>({}),error=ref(''),loading=ref(false),allowed=ref(false),busy=ref(false),saved=ref(false)
function fail(e:unknown){const p=e as {statusCode?:number;data?:{code?:string}};error.value=p.statusCode===403||p.statusCode===401?'ADMIN_REQUIRED':p.data?.code||'ERROR'}
async function load(){
 loading.value=true;error.value=''
 try{rows.value=await $fetch<Report[]>('/api/v1/admin/reports',{query:{status:status.value},retry:0,cache:'no-store'});allowed.value=true}catch(e){fail(e)}finally{loading.value=false}
}
async function act(row:Report,action:string){
 if(busy.value)return
 if(!reasons[row.id]?.trim()){error.value='REASON_REQUIRED';return}
 busy.value=true;error.value='';saved.value=false
 try{
  const url=['RESOLVED','DISMISSED'].includes(action)?'/api/v1/admin/reports/'+row.id+'/resolve':'/api/v1/admin/'+(row.targetTripId?'trips/':'users/')+(row.targetTripId||row.targetUserId)+'/'+action
  await write(url,['RESOLVED','DISMISSED'].includes(action)?{status:action,reason:reasons[row.id]}:{reason:reasons[row.id]})
  saved.value=true;await load()
 }catch(e){fail(e)}finally{busy.value=false}
}
onMounted(load)
</script>
<template>
<p v-if="loading" role="status">{{ t('loading') }}</p>
<p v-if="error" role="alert">{{ t('moderation.errors.'+(['ADMIN_REQUIRED','REASON_REQUIRED','INVALID_REPORT','REPORT_STATE','SELF_MODERATION','ACCOUNT_STATE'].includes(error)?error:'ERROR')) }}</p>
<p v-if="saved" role="status">{{ t('moderation.saved') }}</p>
<template v-if="allowed"><label>{{ t('moderation.status') }}<select v-model="status" @change="load"><option v-for="s in ['OPEN','RESOLVED','DISMISSED']" :key="s" :value="s">{{ t('moderation.'+s) }}</option></select></label>
<p v-if="!loading&&!rows.length">{{ t('moderation.empty') }}</p>
<article v-for="row in rows" :key="row.id"><h2>{{ row.targetLabel || row.targetTripId || row.targetUserId }}</h2><p>{{ t('moderation.'+row.category) }} · {{ t('moderation.'+row.status) }}</p><p>{{ row.description }}</p><details><summary>{{ t('moderation.target') }}</summary><p v-if="row.targetStatus">{{ t('moderation.'+row.targetStatus) }}</p><p>{{ row.targetContent }}</p></details><NuxtLink :to="row.targetUrl">{{ t('moderation.target') }}</NuxtLink><p>{{ row.resolutionReason }}</p>
<label>{{ t('moderation.reason') }}<textarea v-model="reasons[row.id]" name="reason" maxlength="4000" /></label>
<button v-for="action in (row.targetTripId?['hide','unhide']:['block','unblock'])" :key="action" :disabled="busy" @click="act(row,action)">{{ t('moderation.'+action) }}</button>
<template v-if="row.status==='OPEN'"><button v-for="action in ['RESOLVED','DISMISSED']" :key="action" :disabled="busy" @click="act(row,action)">{{ t('moderation.action'+action) }}</button></template>
</article></template>
</template>
