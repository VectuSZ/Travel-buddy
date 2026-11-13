<script setup lang="ts">
import type { PublicProfile } from '../../../shared/profile'
const { t } = useI18n()
const route = useRoute()
const { data, error, status } = await useFetch<PublicProfile>(() => '/api/v1/profiles/'+route.params.id)
if(error.value && import.meta.server) setResponseStatus(error.value.statusCode===404?404:502)
useHead({title:() => data.value?.displayName || t('profile.public'),meta:[{name:'robots',content:'noindex'}]})
</script>
<template><p v-if="status==='pending'">{{ t('loading') }}</p><p v-else-if="error" role="alert">{{ t(error.statusCode===404?'profile.notFound':'error') }}</p><article v-else-if="data"><h1>{{ data.displayName }}</h1><p>{{ t('profile.age') }}: {{ data.age }}</p><p style="white-space:pre-wrap">{{ data.bio }}</p><p>{{ t('profile.languages') }}: {{ data.languages.map(c=>t('profile.languageLabels.'+c)).join(', ') }}</p><p>{{ t('profile.styles') }}: {{ data.travelStyles.map(c=>t('profile.styleLabels.'+c)).join(', ') }}</p></article></template>
