<script setup lang="ts">
import type {Contacts} from '../../shared/profile'
interface Application {id:string;tripId:string;tripTitle:string;organizerId:string;applicantId:string;displayName:string;message:string;status:string}
const props=defineProps<{tripId?:string;organizer?:boolean;tripStatus?:string;remainingPlaces?:number}>()
const emit=defineEmits<{availabilityChanged:[]}>()
const {t}=useI18n(),path=useLocalePath(),{write}=useTripApi()
const rows=ref<Application[]>([]),message=ref(''),busy=ref(false),error=ref(''),ready=ref(false),ack=ref(false),contacts=ref<Contacts|null>(null)
async function load(){
 try{rows.value=await $fetch<Application[]>(props.organizer?'/api/v1/trips/'+props.tripId+'/applications':'/api/v1/me/applications',{cache:'no-store',retry:0});ready.value=true}
 catch(e){handle(e)}
}
function handle(e:unknown){const p=e as {statusCode?:number;data?:{code?:string}};error.value=p.statusCode===401?'session':p.data?.code||'error';contacts.value=null}
async function action(action:string,row?:Application){
 if(busy.value)return
 if(action==='accept'&&!window.confirm(t('applications.notice')))return
 busy.value=true;error.value='';contacts.value=null
 try{
  if(action==='apply')await write('/api/v1/trips/'+props.tripId+'/applications',{message:message.value})
  else await write('/api/v1/applications/'+row!.id+'/'+action,{})
  await load()
  emit('availabilityChanged')
 }catch(e){handle(e);emit('availabilityChanged')}finally{busy.value=false}
}
async function reveal(row:Application){
 contacts.value=null;error.value=''
 try{contacts.value=await $fetch<Contacts>('/api/v1/trips/'+row.tripId+'/contacts/'+(props.organizer?row.applicantId:row.organizerId),{cache:'no-store',retry:0})}catch(e){handle(e)}
}
const visible=computed(()=>props.tripId?rows.value.filter(a=>a.tripId===props.tripId):rows.value)
onMounted(load)
onBeforeUnmount(()=>{contacts.value=null})
</script>
<template><section>
<h2>{{ t(organizer?'applications.incoming':'applications.mine') }}</h2>
<p v-if="error" role="alert">{{ t('applications.errors.'+(['session','PROFILE_CONTACT_REQUIRED','TRIP_FULL','ALREADY_APPLIED','TRIP_CLOSED','OWN_TRIP','CONTACT_FORBIDDEN','APPLICATION_STATE','INVALID_MESSAGE','APPLICATION_LIMIT'].includes(error)?error:'error')) }} <NuxtLink v-if="error==='session'" :to="path('/login')">{{ t('auth.login') }}</NuxtLink><NuxtLink v-if="error==='PROFILE_CONTACT_REQUIRED'" :to="path('/account')">{{ t('auth.account') }}</NuxtLink></p>
<template v-if="ready">
<form v-if="tripId&&tripStatus==='OPEN'&&(remainingPlaces||0)>0&&!organizer&&!visible.length" @submit.prevent="action('apply')">
<p>{{ t('applications.notice') }}</p>
<label>{{ t('applications.message') }}<textarea v-model="message" name="applicationMessage" required maxlength="2000" /></label>
<label><input v-model="ack" type="checkbox" name="applicationConsent" required>{{ t('applications.ack') }}</label>
<button type="submit" :disabled="busy||!ack">{{ t('applications.apply') }}</button>
</form>
<article v-for="row in visible" :key="row.id">
<h3>{{ row.tripTitle }} — {{ row.displayName }}</h3><p>{{ t('applications.'+row.status) }}</p><p>{{ row.message }}</p>
<NuxtLink :to="path('/trips/'+row.tripId)">{{ t('owner.public') }}</NuxtLink>
<template v-if="organizer&&row.status==='PENDING'"><p>{{ t('applications.notice') }}</p><button :disabled="busy" @click="action('accept',row)">{{ t('applications.accept') }}</button><button :disabled="busy" @click="action('reject',row)">{{ t('applications.reject') }}</button></template>
<button v-if="!organizer&&['PENDING','ACCEPTED'].includes(row.status)" :disabled="busy" @click="action('withdraw',row)">{{ t('applications.withdraw') }}</button>
<button v-if="row.status==='ACCEPTED'" @click="reveal(row)">{{ t('applications.contact') }}</button>
</article>
<aside v-if="contacts"><h3>{{ t('applications.contact') }}</h3><p>{{ contacts.contactEmail }}</p><p>{{ contacts.instagramHandle }}</p><p>{{ contacts.whatsappE164 }}</p><button @click="contacts=null">{{ t('applications.hide') }}</button></aside>
</template></section></template>
