<script setup lang="ts">
import {styleCodes} from '~/utils/profileCodes'
import {tripUrl} from '~/utils/tripUrl'
import type {TripResults} from '../../shared/trip'
const {t,locale}=useI18n(),route=useRoute()
const keys=['country','from','to','budgetMin','budgetMax','style','size'] as const
const form=reactive(Object.fromEntries(keys.map(k=>[k,''])) as Record<typeof keys[number],string>)
function sync(){for(const key of keys)form[key]=typeof route.query[key]==='string'?route.query[key] as string:''}
watch(()=>route.query,sync,{immediate:true})
const query=computed(()=>Object.fromEntries([...keys,'page'].filter(k=>route.query[k]!==undefined).map(k=>[k,route.query[k]])))
const {data,status,error}=await useFetch<TripResults>('/api/v1/trips',{query})
if(error.value&&import.meta.server)setResponseStatus(error.value.statusCode===400?400:502)
async function apply(){await navigateTo({path:route.path,query:Object.fromEntries(keys.filter(k=>form[k]).map(k=>[k,form[k]]))})}
function pageLink(page:number){return {path:route.path,query:{...route.query,page:String(page)}}}
useHead({title:()=>t('trips')+' — Travel Buddy',meta:()=>[{name:'robots',content:Object.keys(route.query).length?'noindex,follow':'index,follow'}]})
</script>
<template>
<h1>{{ t('trips') }}</h1>
<form @submit.prevent="apply">
<label>{{ t('search.country') }}<input v-model="form.country" name="country" maxlength="2" placeholder="PT" @input="form.country=form.country.toUpperCase()"></label>
<label v-for="key in (['from','to','budgetMin','budgetMax'] as const)" :key="key">{{ t('search.'+key) }}<input v-model="form[key]" :name="key" :type="key==='from'||key==='to'?'date':'number'" step="0.01"></label>
<label>{{ t('profile.styles') }}<select v-model="form.style" name="style"><option value="">{{ t('search.any') }}</option><option v-for="code in styleCodes" :key="code" :value="code">{{ t('profile.styleLabels.'+code) }}</option></select></label>
<label>{{ t('search.size') }}<select v-model="form.size" name="size"><option value="">20</option><option value="1">1</option><option value="10">10</option><option value="50">50</option></select></label>
<button type="submit">{{ t('search.apply') }}</button> <NuxtLink :to="route.path">{{ t('search.clear') }}</NuxtLink>
</form>
<p v-if="status==='pending'" role="status">{{ t('loading') }}</p>
<p v-else-if="error" role="alert">{{ t(error.statusCode===400?'search.invalid':'error') }}</p>
<template v-else-if="data">
<p>{{ t('search.total') }}: {{ data.total }}</p>
<p v-if="!data.items.length">{{ t('empty') }}</p>
<article v-for="trip in data.items" :key="trip.id">
<h2><NuxtLink :to="tripUrl(trip,locale)" :lang="trip.contentLanguage">{{ trip.title }}</NuxtLink></h2>
<p>{{ trip.locations.map(l=>[l.countryCode,l.city].filter(Boolean).join(' — ')).join(' → ') }}</p>
<p>{{ trip.startDate }} — {{ trip.endDate }}</p>
<p v-if="trip.dateMode==='FLEXIBLE'">{{ t('search.flexible') }}</p>
<p>{{ trip.budgetMin }}–{{ trip.budgetMax }} PLN · {{ t('search.perPerson') }}</p>
<p :lang="trip.contentLanguage">{{ trip.description }}</p>
</article>
<nav :aria-label="t('search.pagination')">
<NuxtLink v-if="data.page>1" :to="pageLink(data.page-1)">{{ t('search.previous') }}</NuxtLink>
<span> {{ data.page }} / {{ Math.max(1,Math.ceil(data.total/data.size)) }} </span>
<NuxtLink v-if="data.page*data.size<data.total" :to="pageLink(data.page+1)">{{ t('search.next') }}</NuxtLink>
</nav>
</template>
</template>
