<script setup lang="ts">
import { languageCodes, styleCodes } from '../../shared/profile'
import type { Me } from '../../shared/profile'
const { t } = useI18n()
const localePath = useLocalePath()
const me = ref<Me | null>(null)
const profile = reactive({ displayName:'', bio:'', languages:[] as string[], travelStyles:[] as string[], version:0 })
const contacts = reactive({ contactEmail:'', instagramHandle:'', whatsappE164:'' })
const profileBusy = ref(false), contactsBusy = ref(false)
const profileError = ref(''), contactsError = ref(''), loadError = ref(false)
const savedProfile = ref(false), savedContacts = ref(false)
const ageEntered = ref<number | undefined>()
const conflict = computed(() => profileError.value === 'PROFILE_VERSION_CONFLICT')
const fields = ['contactEmail','instagramHandle','whatsappE164'] as const
const fieldCodes = {contactEmail:'INVALID_CONTACT_EMAIL',instagramHandle:'INVALID_INSTAGRAM',whatsappE164:'INVALID_WHATSAPP'}
async function handle(error: unknown, target: typeof profileError) {
 const e = error as { statusCode?:number; data?:{code?:string} }
 if (e.statusCode === 401) { useAuth().account.value = null; await navigateTo(localePath('/login')); return }
 const code = e.data?.code || 'UNKNOWN'
 target.value = ['INVALID_DISPLAY_NAME','INVALID_AGE','INVALID_BIO','INVALID_LANGUAGES','INVALID_TRAVEL_STYLES','INVALID_CONTACT_EMAIL','INVALID_INSTAGRAM','INVALID_WHATSAPP','PROFILE_VERSION_CONFLICT','INVALID_VERSION','INVALID_INPUT'].includes(code) ? code : 'UNKNOWN'
}
function fillProfile(data: Me) {
 if(data.profile) { Object.assign(profile,data.profile); ageEntered.value=data.profile.age }
 else { Object.assign(profile,{displayName:'',bio:'',languages:[],travelStyles:[],version:0}); ageEntered.value=undefined }
}
async function load(initial = false) {
 loadError.value=false
 try {
  const data = await $fetch<Me>('/api/v1/me',{cache:'no-store',retry:0})
  me.value=data; fillProfile(data); profileError.value=''; savedProfile.value=false
  if(initial) for(const f of fields) contacts[f]=data.contacts[f] || ''
 } catch(e) { loadError.value=true; await handle(e,profileError) }
}
onMounted(() => load(true))
async function save(kind:'profile'|'contacts') {
 const busy = kind==='profile'?profileBusy:contactsBusy
 const error = kind==='profile'?profileError:contactsError
 if(busy.value) return
 error.value=''; savedProfile.value=false; savedContacts.value=false
 if(kind==='profile') {
  if(profile.displayName.trim().length<2 || profile.displayName.trim().length>60) { error.value='INVALID_DISPLAY_NAME'; return }
  if(!Number.isInteger(ageEntered.value) || ageEntered.value!<18 || ageEntered.value!>120) { error.value='INVALID_AGE'; return }
  if(!profile.languages.length || profile.languages.length>10) { error.value='INVALID_LANGUAGES'; return }
  if(!profile.travelStyles.length) { error.value='INVALID_TRAVEL_STYLES'; return }
 }
 busy.value=true
 try {
  const csrf = await $fetch<{token:string}>('/api/v1/auth/csrf',{cache:'no-store',retry:0})
  const body = kind==='profile' ? {displayName:profile.displayName,age:ageEntered.value,bio:profile.bio,languages:profile.languages,travelStyles:profile.travelStyles,version:profile.version} : {...contacts}
  const data = await $fetch<Me>('/api/v1/me/'+kind,{method:kind==='profile'?'PATCH':'PUT',body,headers:{'X-CSRF-TOKEN':csrf.token},retry:0})
  me.value=data
  if(kind==='profile') { fillProfile(data); savedProfile.value=true }
  else { for(const f of fields) contacts[f]=data.contacts[f] || ''; savedContacts.value=true }
 } catch(e) { await handle(e,error) } finally { busy.value=false }
}
</script>
<template>
 <p v-if="!me && !loadError" role="status">{{ t('loading') }}</p>
 <p v-if="loadError" role="alert">{{ t('error') }} <button @click="load(true)">{{ t('retry') }}</button></p>
 <div v-if="me">
  <p role="status">{{ t(me.readyToParticipate?'profile.ready':!me.profileComplete?'profile.missingProfile':'profile.missingContact') }}</p>
  <NuxtLink v-if="me.profile" :to="localePath('/profiles/'+me.userId)">{{ t('profile.view') }}</NuxtLink>
  <section><h2>{{ t('profile.public') }}</h2><p>{{ t('profile.publicHint') }}</p>
   <form @submit.prevent="save('profile')"><fieldset :disabled="profileBusy">
    <label>{{ t('profile.displayName') }}<input v-model="profile.displayName" name="displayName" required minlength="2" maxlength="60"><span v-if="profileError==='INVALID_DISPLAY_NAME'">{{ t('profile.errors.INVALID_DISPLAY_NAME') }}</span></label>
    <label>{{ t('profile.age') }}<input v-model.number="ageEntered" name="age" type="number" required min="18" max="120" step="1"><span v-if="profileError==='INVALID_AGE'">{{ t('profile.errors.INVALID_AGE') }}</span></label>
    <fieldset><legend>{{ t('profile.languages') }}</legend><label v-for="code in languageCodes" :key="code" class="choice"><input v-model="profile.languages" type="checkbox" :value="code" name="languages">{{ t('profile.languageLabels.'+code) }}</label><p v-if="profileError==='INVALID_LANGUAGES'">{{ t('profile.errors.INVALID_LANGUAGES') }}</p></fieldset>
    <fieldset><legend>{{ t('profile.styles') }}</legend><label v-for="code in styleCodes" :key="code" class="choice"><input v-model="profile.travelStyles" type="checkbox" :value="code" name="travelStyles">{{ t('profile.styleLabels.'+code) }}</label><p v-if="profileError==='INVALID_TRAVEL_STYLES'">{{ t('profile.errors.INVALID_TRAVEL_STYLES') }}</p></fieldset>
    <label>{{ t('profile.bio') }}<textarea v-model="profile.bio" name="bio" maxlength="1000" rows="4"/><span v-if="profileError==='INVALID_BIO'">{{ t('profile.errors.INVALID_BIO') }}</span></label>
    <p v-if="profileError" role="alert">{{ t('profile.errors.'+profileError) }}</p>
    <button v-if="conflict" type="button" @click="load()">{{ t('profile.reload') }}</button>
    <p v-if="savedProfile" role="status">{{ t('profile.saved') }}</p>
    <button type="submit">{{ profileBusy?t('loading'):t('profile.saveProfile') }}</button>
   </fieldset></form>
  </section>
  <section><h2>{{ t('profile.contacts') }}</h2><p>{{ t('profile.contactsHint') }}</p><p>{{ t('profile.phoneHint') }}</p>
   <form @submit.prevent="save('contacts')"><fieldset :disabled="contactsBusy">
    <label v-for="f in fields" :key="f">{{ t('profile.'+f) }}<input v-model="contacts[f]" :name="f" :type="f==='contactEmail'?'email':'text'" :maxlength="f==='contactEmail'?254:f==='instagramHandle'?31:16"><span v-if="contactsError===fieldCodes[f]">{{ t('profile.errors.'+fieldCodes[f]) }}</span></label>
    <p v-if="contactsError" role="alert">{{ t('profile.errors.'+contactsError) }}</p><p v-if="savedContacts" role="status">{{ t('profile.saved') }}</p>
    <button type="submit">{{ contactsBusy?t('loading'):t('profile.saveContacts') }}</button>
   </fieldset></form>
  </section>
 </div>
</template>
<style scoped>.choice{display:inline-flex;align-items:center;gap:6px;margin:6px 12px 6px 0}textarea{box-sizing:border-box;width:100%;font:inherit;padding:12px}section{margin-top:32px}span{display:block;color:#9b2525}</style>
