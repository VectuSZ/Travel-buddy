<script setup lang="ts">
import {routeSummary} from '~/utils/countries'
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
const expanded=ref(false)
const activeFilters=computed(()=>keys.filter(key=>key!=='size' && route.query[key]).length)
async function clearFilters(){
 for(const key of keys) form[key]=''
 await apply()
}
async function apply(){expanded.value=false;await navigateTo({path:route.path,query:Object.fromEntries(keys.filter(k=>form[k]).map(k=>[k,form[k]]))})}
function pageLink(page:number){return {path:route.path,query:{...route.query,page:String(page)}}}
usePublicHead('trips','legal.list','/pl/wyprawy','/en/trips')
useHead({meta:()=>[{name:'robots',content:Object.keys(route.query).length?'noindex,follow':'index,follow'}]})
const interactive=ref(false)
onMounted(()=>{interactive.value=true})
</script>
<template>
<h1>{{ t('trips') }}</h1>
<button type="button" :disabled="!interactive" aria-controls="trip-filters" :aria-expanded="expanded" @click="expanded=!expanded">{{ t('search.filters') }}{{ activeFilters ? ' ('+activeFilters+')' : '' }}</button>
<form v-if="expanded" id="trip-filters" @submit.prevent="apply">
<CountryPicker v-model="form.country" name="country" :label="t('search.country')" />
<label v-for="key in (['from','to','budgetMin','budgetMax'] as const)" :key="key">{{ t('search.'+key) }}<input v-model="form[key]" :name="key" :type="key==='from'||key==='to'?'date':'number'" step="0.01"></label>
<label>{{ t('profile.styles') }}<select v-model="form.style" name="style"><option value="">{{ t('search.any') }}</option><option v-for="code in styleCodes" :key="code" :value="code">{{ t('profile.styleLabels.'+code) }}</option></select></label>
<label>{{ t('search.size') }}<select v-model="form.size" name="size"><option value="">20</option><option value="1">1</option><option value="10">10</option><option value="50">50</option></select></label>
<button type="submit">{{ t('search.apply') }}</button> <button type="button" @click="clearFilters">{{ t('search.clear') }}</button>
</form>
<p v-if="status==='pending'" role="status">{{ t('loading') }}</p>
<p v-else-if="error" role="alert">{{ t(error.statusCode===400?'search.invalid':'error') }}</p>
<template v-else-if="data">
<p>{{ t('search.total') }}: {{ data.total }}</p>
<p v-if="!data.items.length">{{ t('empty') }}</p>
<article v-for="trip in data.items" :key="trip.id">
<h2><NuxtLink :to="tripUrl(trip,locale)" :lang="trip.contentLanguage">{{ trip.title }}</NuxtLink></h2>
<p class="route-summary">{{ routeSummary(trip.locations,locale) }}</p>
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

<style scoped>
.route-summary{display:-webkit-box;-webkit-line-clamp:3;-webkit-box-orient:vertical;overflow:hidden}
</style>
