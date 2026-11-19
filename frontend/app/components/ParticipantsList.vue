<script setup lang="ts">
import type {PublicProfile} from '../../shared/profile'
const props=defineProps<{tripId:string;capacity:number;remainingPlaces:number}>(),{t}=useI18n()
const {data,refresh}=await useFetch<PublicProfile[]>(()=>'/api/v1/trips/'+props.tripId+'/participants')
watch(()=>props.remainingPlaces,()=>refresh())
</script>
<template><section><h2>{{ t('applications.participants') }}</h2><p>{{ capacity - remainingPlaces }} / {{ capacity }} <span v-if="remainingPlaces===0">{{ t('applications.full') }}</span></p><ul><li v-for="person in data" :key="person.userId">{{ person.displayName }}</li></ul></section></template>
