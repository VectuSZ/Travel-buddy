<script setup lang="ts">
import {tripUrl} from '~/utils/tripUrl'
import type {Trip} from '../../../../shared/trip'
defineI18nRoute({paths:{pl:'/pl/wyprawy/[id]/[[slug]]',en:'/trips/[id]/[[slug]]'}})
const route=useRoute(),{t,locale}=useI18n()
const {data,error}=await useFetch<Trip>(()=>'/api/v1/trips/'+route.params.id)
if(error.value)throw createError({statusCode:error.value.statusCode===404?404:502,statusMessage:t('missing')})
if(data.value&&route.path!==tripUrl(data.value,locale.value))await navigateTo(tripUrl(data.value,locale.value),{redirectCode:301,replace:true})
const siteUrl=useRuntimeConfig().public.siteUrl
const canonical=computed(()=>siteUrl+ (data.value?tripUrl(data.value,data.value.contentLanguage==='en'?'en':'pl'):''))
useHead(()=>({title:(data.value?.title||t('trips'))+' — Travel Buddy',link:[{rel:'canonical',href:canonical.value}],meta:[{name:'description',content:data.value?.description.slice(0,160)||''},{property:'og:title',content:data.value?.title||''},{property:'og:description',content:data.value?.description.slice(0,160)||''},{property:'og:url',content:canonical.value},{property:'og:type',content:'article'}]}))
</script>
<template><NuxtLink :to="locale==='en'?'/en/trips':'/pl/wyprawy'">{{ t('back') }}</NuxtLink>
<article v-if="data"><h1 :lang="data.contentLanguage">{{ data.title }}</h1><p>{{ t('owner.'+data.status) }}</p>
<p>{{ data.locations.map(l=>[l.countryCode,l.city].filter(Boolean).join(' — ')).join(' → ') }}</p>
<p>{{ data.startDate }} — {{ data.endDate }}</p><p v-if="data.dateMode==='FLEXIBLE'">{{ t('search.flexible') }}</p><p>{{ data.budgetMin }}–{{ data.budgetMax }} PLN · {{ t('search.perPerson') }}</p>
<p :lang="data.contentLanguage">{{ data.description }}</p></article><ParticipantsList v-if="data" :trip-id="data.id" :capacity="data.targetPeople" /><ClientOnly><ApplicationsPanel v-if="data" :trip-id="data.id" :trip-status="data.status" /></ClientOnly></template>
