<script setup lang="ts">
import {languageCodes,styleCodes} from '~/utils/profileCodes'
import type {OwnedTrip,TripFields} from '../../shared/owned-trip'
const {t,locale}=useI18n(),path=useLocalePath(),route=useRoute()
const {write}=useTripApi()
const id=ref(typeof route.query.id==='string'?route.query.id:''),version=ref(0),state=ref('DRAFT')
const loading=ref(true),loaded=ref(false),busy=ref(false),error=ref(''),saved=ref(false)
const errors=ref<Record<string,string>>({})
const form=reactive({title:'',itinerary:'',lookingFor:'',contentLanguage:'',dateMode:'',dateFrom:'',dateTo:'',durationDays:'' as string|number,targetPeople:'' as string|number,budgetMin:'' as string|number,budgetMax:'' as string|number,styles:[] as string[],locations:[] as {countryCode:string;city:string}[]})
const textFields=['title','itinerary','lookingFor'] as const
const numberFields=['durationDays','targetPeople','budgetMin','budgetMax'] as const
const countries=computed(()=>{
 const names=new Intl.DisplayNames([locale.value],{type:'region'})
 return countryCodes.split(' ').map(code=>({code,name:names.of(code)||code})).sort((a,b)=>a.name.localeCompare(b.name,locale.value))
})
const countryCodes='AD AE AF AG AI AL AM AO AQ AR AS AT AU AW AX AZ BA BB BD BE BF BG BH BI BJ BL BM BN BO BQ BR BS BT BV BW BY BZ CA CC CD CF CG CH CI CK CL CM CN CO CR CU CV CW CX CY CZ DE DJ DK DM DO DZ EC EE EG EH ER ES ET FI FJ FK FM FO FR GA GB GD GE GF GG GH GI GL GM GN GP GQ GR GS GT GU GW GY HK HM HN HR HT HU ID IE IL IM IN IO IQ IR IS IT JE JM JO JP KE KG KH KI KM KN KP KR KW KY KZ LA LB LC LI LK LR LS LT LU LV LY MA MC MD ME MF MG MH MK ML MM MN MO MP MQ MR MS MT MU MV MW MX MY MZ NA NC NE NF NG NI NL NO NP NR NU NZ OM PA PE PF PG PH PK PL PM PN PR PS PT PW PY QA RE RO RS RU RW SA SB SC SD SE SG SH SI SJ SK SL SM SN SO SR SS ST SV SX SY SZ TC TD TF TG TH TJ TK TL TM TN TO TR TT TV TW TZ UA UG UM US UY UZ VA VC VE VG VI VN VU WF WS YE YT ZA ZM ZW'
function fill(data:OwnedTrip){
 id.value=data.id;version.value=data.version;state.value=data.status
 for(const key of textFields)form[key]=data[key]||''
 for(const key of ['contentLanguage','dateMode','dateFrom','dateTo'] as const)form[key]=data[key]||''
 for(const key of numberFields)form[key]=data[key]??''
 form.styles=[...data.styles];form.locations=data.locations.map(l=>({countryCode:l.countryCode,city:l.city||''}))
}
function handle(e:unknown){
 const problem=e as {statusCode?:number;data?:{code?:string}}
 const code=problem.data?.code||''
 if(problem.statusCode===401){error.value='session';useAuth().account.value=null}
 else if(code==='TRIP_VERSION_CONFLICT')error.value='conflict'
 else if(code==='INVALID_PROFILE_REQUIRED')error.value='profile'
 else if(code==='INVALID_CONTACT_REQUIRED')error.value='contact'
 else if(problem.statusCode===429)error.value='limit'
 else if(problem.statusCode===404)error.value='missing'
 else if(problem.statusCode===409)error.value='stateConflict'
 else{
  error.value='error'
  const field=code.replace('INVALID_','')
  const aliases:Record<string,string[]>={DATES:['dateFrom','dateTo','durationDays'],BUDGET:['budgetMin','budgetMax'],COUNTRY:['locations'],CITY:['locations'],LOCATIONS:['locations'],STYLES:['styles'],TARGET_PEOPLE:['targetPeople'],CONTENT_LANGUAGE:['contentLanguage'],DATE_MODE:['dateMode']}
  for(const key of aliases[field]||[field])errors.value[key]='invalid'
 }
}
async function load(){
 loading.value=true;error.value=''
 try{
  if(id.value)fill(await $fetch<OwnedTrip>('/api/v1/me/trips/'+id.value,{cache:'no-store',retry:0}))
  else await $fetch('/api/v1/auth/session',{cache:'no-store',retry:0})
  loaded.value=true;errors.value={};saved.value=false
 }catch(e){handle(e)}finally{loading.value=false}
}
function payload():TripFields{
 const num=(v:string|number)=>v===''?null:Number(v)
 return {title:form.title.trim()||null,itinerary:form.itinerary.trim()||null,lookingFor:form.lookingFor.trim()||null,contentLanguage:form.contentLanguage||null,dateMode:form.dateMode||null,dateFrom:form.dateFrom||null,dateTo:form.dateTo||null,durationDays:form.dateMode==='FLEXIBLE'?num(form.durationDays):null,targetPeople:num(form.targetPeople),budgetMin:num(form.budgetMin),budgetMax:num(form.budgetMax),styles:[...form.styles],locations:form.locations.map(l=>({countryCode:l.countryCode,city:l.city.trim()||null}))}
}
function validate(complete:boolean){
 const p=payload();errors.value={}
 if(complete){
  for(const key of ['title','itinerary','lookingFor','contentLanguage','dateMode','dateFrom','dateTo','targetPeople','budgetMin','budgetMax'] as const)if(p[key]===null)errors.value[key]='required'
  if(p.dateMode==='FLEXIBLE'&&p.durationDays===null)errors.value.durationDays='required'
  if(!p.styles.length)errors.value.styles='required'
  if(!p.locations.length)errors.value.locations='required'
 }
 for(const key of textFields)if(/[<>]/.test(form[key]))errors.value[key]='invalid'
 if(p.locations.some(l=>!l.countryCode || /[<>]/.test(l.city||'')))errors.value.locations='invalid'
 if(p.targetPeople!==null && (!Number.isInteger(p.targetPeople)||p.targetPeople<2||p.targetPeople>50))errors.value.targetPeople='peopleError'
 if(p.dateFrom&&p.dateTo&&p.dateFrom>p.dateTo)errors.value.dateTo='datesError'
 if(p.durationDays!==null && (!Number.isInteger(p.durationDays)||p.durationDays<1||p.durationDays>32767||(p.dateFrom&&p.dateTo&&p.durationDays>(Date.parse(p.dateTo)-Date.parse(p.dateFrom))/86400000+1)))errors.value.durationDays='durationError'
 for(const key of ['budgetMin','budgetMax'] as const)if(p[key]!==null && (!Number.isFinite(p[key])||p[key]!<0||p[key]!>99999999.99||Math.abs(p[key]!*100-Math.round(p[key]!*100))>0.00001))errors.value[key]='budgetError'
 if(p.budgetMin!==null&&p.budgetMax!==null&&p.budgetMin>p.budgetMax)errors.value.budgetMax='budgetError'
 return !Object.keys(errors.value).length
}
async function save(publish=false){
 if(busy.value || error.value==='conflict')return
 error.value='';saved.value=false
 if(!validate(publish||state.value==='OPEN')){error.value='fields';return}
 busy.value=true
 try{
  const data=await write<OwnedTrip>(id.value?'/api/v1/trips/'+id.value:'/api/v1/trips',id.value?{...payload(),version:version.value}:payload(),id.value?'PATCH':'POST')
  fill(data)
  await navigateTo({path:path('/my-trips/edit'),query:{id:data.id}},{replace:true})
  if(publish)fill(await write<OwnedTrip>('/api/v1/trips/'+id.value+'/publish',{version:version.value}))
  saved.value=true
 }catch(e){handle(e)}finally{busy.value=false}
}
async function close(){
 if(busy.value || !window.confirm(t('owner.confirmClose')))return
 busy.value=true;error.value=''
 try{fill(await write<OwnedTrip>('/api/v1/trips/'+id.value+'/close',{version:version.value}));saved.value=true}
 catch(e){handle(e)}finally{busy.value=false}
}
function move(index:number,delta:number){
 const next=index+delta
 if(next<0||next>=form.locations.length)return
 const [item]=form.locations.splice(index,1);if(item)form.locations.splice(next,0,item)
}
onMounted(load)
</script>
<template>
 <NuxtLink :to="path('/my-trips')">{{ t('owner.mine') }}</NuxtLink>
 <p v-if="loading" role="status">{{ t('loading') }}</p>
 <div v-if="error" role="alert">{{ t('owner.'+error) }}
  <NuxtLink v-if="error==='session'" :to="path('/login')">{{ t('auth.login') }}</NuxtLink>
  <NuxtLink v-if="error==='profile'||error==='contact'" :to="path('/account')+(error==='profile'?'#profile':'#contacts')">{{ t('owner.fixAccount') }}</NuxtLink>
  <button v-if="error==='conflict'||error==='stateConflict'" type="button" :disabled="busy" @click="load">{{ t('owner.reload') }}</button>
  <button v-if="!loaded&&error==='error'" type="button" @click="load">{{ t('retry') }}</button>
 </div>
 <template v-if="loaded">
 <p>{{ t('owner.'+state) }}</p><p v-if="saved" role="status">{{ t('owner.saved') }}</p>
 <NuxtLink v-if="id&&state!=='DRAFT'" :to="path('/trips/'+id)">{{ t('owner.public') }}</NuxtLink>
 <form novalidate @submit.prevent="save(false)"><fieldset :disabled="busy||state==='CLOSED'||loading">
 <label v-for="key in textFields" :key="key">{{ t('owner.'+key) }}
  <input v-if="key==='title'" v-model="form[key]" :name="key" maxlength="120">
  <textarea v-else v-model="form[key]" :name="key" :maxlength="key==='itinerary'?10000:2000" rows="4"/>
  <span v-if="errors[key]" class="field-error">{{ t('owner.'+errors[key]) }}</span>
 </label>
 <fieldset><legend>{{ t('owner.locations') }}</legend>
 <div v-for="(location,index) in form.locations" :key="index" class="location">
 <label>{{ t('owner.country') }}<select v-model="location.countryCode" name="countryCode"><option value="">{{ t('owner.choose') }}</option><option v-for="country in countries" :key="country.code" :value="country.code">{{ country.name }}</option></select></label>
 <label>{{ t('owner.city') }}<input v-model="location.city" name="city" maxlength="120"></label>
 <button type="button" :disabled="index===0" @click="move(index,-1)">{{ t('owner.up') }}</button>
 <button type="button" :disabled="index===form.locations.length-1" @click="move(index,1)">{{ t('owner.down') }}</button>
 <button type="button" @click="form.locations.splice(index,1)">{{ t('owner.remove') }}</button>
 </div>
 <button type="button" :disabled="form.locations.length>=50" @click="form.locations.push({countryCode:'',city:''})">{{ t('owner.addLocation') }}</button>
 <p v-if="errors.locations" class="field-error">{{ t('owner.'+errors.locations) }}</p>
 </fieldset>
 <label>{{ t('owner.contentLanguage') }}<select v-model="form.contentLanguage" name="contentLanguage"><option value="">{{ t('owner.choose') }}</option><option v-for="code in languageCodes" :key="code" :value="code">{{ t('profile.languageLabels.'+code) }}</option></select><span v-if="errors.contentLanguage" class="field-error">{{ t('owner.'+errors.contentLanguage) }}</span></label>
 <fieldset><legend>{{ t('profile.styles') }}</legend><label v-for="code in styleCodes" :key="code" class="choice"><input v-model="form.styles" type="checkbox" name="styles" :value="code">{{ t('profile.styleLabels.'+code) }}</label><p v-if="errors.styles" class="field-error">{{ t('owner.'+errors.styles) }}</p></fieldset>
 <label>{{ t('owner.dateMode') }}<select v-model="form.dateMode" name="dateMode"><option value="">{{ t('owner.choose') }}</option><option value="EXACT">{{ t('owner.EXACT') }}</option><option value="FLEXIBLE">{{ t('owner.FLEXIBLE') }}</option></select><span v-if="errors.dateMode" class="field-error">{{ t('owner.'+errors.dateMode) }}</span></label>
 <label v-for="key in (['dateFrom','dateTo'] as const)" :key="key">{{ t('owner.'+key) }}<input v-model="form[key]" :name="key" type="date"><span v-if="errors[key]" class="field-error">{{ t('owner.'+errors[key]) }}</span></label>
 <template v-for="key in numberFields" :key="key"><label v-if="key!=='durationDays'||form.dateMode==='FLEXIBLE'">{{ t('owner.'+key) }}<input v-model="form[key]" :name="key" type="number" :step="key.startsWith('budget')?'0.01':'1'"><small v-if="key==='targetPeople'">{{ t('owner.peopleHint') }}</small><span v-if="errors[key]" class="field-error">{{ t('owner.'+errors[key]) }}</span></label></template>
 <button type="submit" :disabled="error==='conflict'">{{ t(state==='DRAFT'?'owner.saveDraft':'owner.save') }}</button>
 <button v-if="state==='DRAFT'" type="button" :disabled="error==='conflict'" @click="save(true)">{{ t('owner.publish') }}</button>
 </fieldset></form>
 <button v-if="state==='OPEN'" type="button" :disabled="busy" @click="close">{{ t('owner.close') }}</button>
 </template>
</template>
<style scoped>textarea,select{box-sizing:border-box;width:100%;font:inherit;padding:10px}.choice{display:inline-flex;gap:6px;margin:8px}.field-error{display:block;color:#9b2525}.location{padding:12px;border:1px solid #ccc;margin:12px 0}button{margin:6px}small{display:block}</style>
