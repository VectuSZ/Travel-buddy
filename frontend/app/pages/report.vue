<script setup lang="ts">
const {t}=useI18n()
useHead({title:()=>t('moderation.report'),meta:[{name:'robots',content:'noindex'}]})
</script>
<template><h1>{{ t('moderation.report') }}</h1><ClientOnly><ReportForm /></ClientOnly></template>
