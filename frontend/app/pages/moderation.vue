<script setup lang="ts">
const {t}=useI18n()
useHead({title:()=>t('moderation.panel'),meta:[{name:'robots',content:'noindex'}]})
</script>
<template><h1>{{ t('moderation.panel') }}</h1><ClientOnly><ModerationPanel /></ClientOnly></template>
