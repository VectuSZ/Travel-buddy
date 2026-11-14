<script setup lang="ts">
import type {OwnedTrip} from '../../shared/owned-trip'
const {t}=useI18n(), path=useLocalePath()
const trips=ref<OwnedTrip[]>([]), loading=ref(true), error=ref(''),busy=ref('')
const {write}=useTripApi()
async function load(){
 loading.value=true;error.value=''
 try{trips.value=await $fetch<OwnedTrip[]>('/api/v1/me/trips',{cache:'no-store',retry:0})}
 catch(e){error.value=(e as {statusCode?:number}).statusCode===401?'session':'error'}
 finally{loading.value=false}
}
async function close(trip:OwnedTrip){
 if(busy.value || !window.confirm(t('owner.confirmClose')))return
 busy.value=trip.id;error.value=''
 try{const updated=await write<OwnedTrip>('/api/v1/trips/'+trip.id+'/close',{version:trip.version});trips.value=trips.value.map(v=>v.id===updated.id?updated:v)}
 catch(e){error.value=(e as {statusCode?:number}).statusCode===401?'session':(e as {statusCode?:number}).statusCode===409?'conflict':'error'}
 finally{busy.value=''}
}
onMounted(load)
</script>
<template>
 <p v-if="loading" role="status">{{ t('loading') }}</p>
 <p v-if="error" role="alert">{{ t('owner.'+error) }} <NuxtLink v-if="error==='session'" :to="path('/login')">{{ t('auth.login') }}</NuxtLink><button v-else @click="load">{{ t('retry') }}</button></p>
 <template v-if="!loading && error!=='session'">
 <NuxtLink :to="path('/my-trips/edit')">{{ t('owner.create') }}</NuxtLink>
 <p v-if="!trips.length">{{ t('owner.empty') }}</p>
 <article v-for="trip in trips" :key="trip.id"><h2>{{ trip.title || t('owner.untitled') }}</h2>
 <p>{{ t('owner.'+trip.status) }}</p>
 <NuxtLink :to="path('/my-trips/edit')+'?id='+trip.id">{{ t(trip.status==='CLOSED'?'owner.view':'owner.edit') }}</NuxtLink>
 <template v-if="trip.status!=='DRAFT'"> · <NuxtLink :to="path('/trips/'+trip.id)">{{ t('owner.public') }}</NuxtLink></template>
 <button v-if="trip.status==='OPEN'" :disabled="!!busy" @click="close(trip)">{{ t('owner.close') }}</button>
 </article>
 </template>
</template>
