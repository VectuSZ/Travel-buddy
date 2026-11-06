<script setup lang="ts">
import type { Trip } from '../../shared/trip'
const { t } = useI18n()
const localePath = useLocalePath()
const { data, status, error, refresh } = await useFetch<Trip[]>('/api/v1/trips')
useHead({title: () => t('trips') + ' — Travel Buddy'})
</script>
<template><h1>{{ t('trips') }}</h1><p>{{ t('intro') }}</p><p v-if="status === 'pending'" role="status">{{ t('loading') }}</p><div v-else-if="error" role="alert">{{ t('error') }} <button @click="refresh()">{{ t('retry') }}</button></div><p v-else-if="!data?.length">{{ t('empty') }}</p><section v-else><article v-for="trip in data" :key="trip.id"><h2><NuxtLink :to="localePath('/trips/' + trip.id)">{{ trip.title }}</NuxtLink></h2><p>{{ trip.country }} · {{ trip.startDate }} — {{ trip.endDate }}</p><p :lang="trip.contentLanguage">{{ trip.description }}</p></article></section></template>
