<script setup lang="ts">
import type {PublicProfile} from '../../shared/profile'
const props=defineProps<{tripId:string;capacity:number}>(),{t}=useI18n()
const {data}=await useFetch<PublicProfile[]>(()=>'/api/v1/trips/'+props.tripId+'/participants')
</script>
<template><section><h2>{{ t('applications.participants') }}</h2><p>{{ data?.length || 0 }} / {{ capacity }} <span v-if="(data?.length || 0)>=capacity">{{ t('applications.full') }}</span></p><ul><li v-for="person in data" :key="person.userId">{{ person.displayName }}</li></ul></section></template>
