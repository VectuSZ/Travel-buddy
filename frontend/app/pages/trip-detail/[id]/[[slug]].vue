<script setup lang="ts">
import {countryName} from '~/utils/countries'
import {tripUrl} from '~/utils/tripUrl'
import type {Trip} from '../../../../shared/trip'
defineI18nRoute({paths:{pl:'/pl/wyprawy/[id]/[[slug]]',en:'/trips/[id]/[[slug]]'}})
const route=useRoute(),{t,locale}=useI18n()
const {data,error,refresh}=await useFetch<Trip & {remainingPlaces:number}>(()=>'/api/v1/trips/'+route.params.id)
if(error.value)throw createError({statusCode:error.value.statusCode===404?404:502,statusMessage:t('missing')})
if(data.value&&route.path!==tripUrl(data.value,locale.value))await navigateTo(tripUrl(data.value,locale.value),{redirectCode:301,replace:true})
const siteUrl=useRuntimeConfig().public.siteUrl
const canonical=computed(()=>siteUrl+ (data.value?tripUrl(data.value,data.value.contentLanguage==='en'?'en':'pl'):''))
useHead(()=>({title:(data.value?.title||t('trips'))+' — Travel Buddy',link:[{rel:'canonical',href:canonical.value}],meta:[{name:'description',content:data.value?.description.slice(0,160)||''},{property:'og:title',content:data.value?.title||''},{property:'og:description',content:data.value?.description.slice(0,160)||''},{property:'og:url',content:canonical.value},{property:'og:type',content:'article'}]}))
</script>
<template><NuxtLink :to="locale==='en'?'/en/trips':'/pl/wyprawy'">{{ t('back') }}</NuxtLink>
<NuxtLink v-if="data" :to="(locale==='en'?'/en/report':'/report')+'?trip='+data.id">{{ t('moderation.report') }}</NuxtLink><article v-if="data"><h1 :lang="data.contentLanguage">{{ data.title }}</h1><strong class="trip-status" :class="{available:data.status==='OPEN' && data.remainingPlaces>0}">{{ t(data.status==='OPEN' && data.remainingPlaces===0 ? 'applications.full' : 'owner.'+data.status) }}</strong>
<p>{{ data.locations.map(l=>[countryName(l.countryCode,locale),l.city].filter(Boolean).join(' — ')).join(' → ') }}</p>
<p>{{ data.startDate }} — {{ data.endDate }}</p><p v-if="data.dateMode==='FLEXIBLE'">{{ t('search.flexible') }}</p><p>{{ data.budgetMin }}–{{ data.budgetMax }} PLN · {{ t('search.perPerson') }}</p>
<p :lang="data.contentLanguage">{{ data.description }}</p></article><ParticipantsList v-if="data" :trip-id="data.id" :capacity="data.targetPeople" :remaining-places="data.remainingPlaces" /><ClientOnly><ApplicationsPanel v-if="data" :trip-id="data.id" :trip-status="data.status" :remaining-places="data.remainingPlaces" @availability-changed="refresh()" /></ClientOnly></template>

<style scoped>
h1{margin-bottom:.5rem}.trip-status{display:inline-block;padding:.25rem .8rem;border-radius:1rem;background:#e4e6e6;color:#353d3b}.available{background:#d8efdf;color:#17562b}
</style>
