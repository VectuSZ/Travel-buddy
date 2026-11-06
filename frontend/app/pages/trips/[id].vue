<script setup lang="ts">
import type { Trip } from '../../../shared/trip'
const route = useRoute()
const { t } = useI18n()
const localePath = useLocalePath()
const { data, status, error, refresh } = await useFetch<Trip>(() => '/api/v1/trips/' + route.params.id)
if (error.value && import.meta.server) setResponseStatus(error.value.statusCode === 404 ? 404 : 502)
useHead({ title: () => (data.value?.title || t('trips')) + ' — Travel Buddy' })
</script>
<template><NuxtLink :to="localePath('/')">{{ t('back') }}</NuxtLink><p v-if="status === 'pending'" role="status">{{ t('loading') }}</p><div v-else-if="error" role="alert">{{ t(error.statusCode === 404 ? 'missing' : 'error') }} <button @click="refresh()">{{ t('retry') }}</button></div><article v-else-if="data"><h1 :lang="data.contentLanguage">{{ data.title }}</h1><p>{{ data.country }} · {{ data.startDate }} — {{ data.endDate }}</p><p :lang="data.contentLanguage">{{ data.description }}</p><small>{{ t('language') }}: {{ data.contentLanguage.toUpperCase() }}</small></article></template>
