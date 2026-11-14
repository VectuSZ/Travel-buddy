<script setup lang="ts">
const {t}=useI18n()
useHead({title:()=>t('owner.edit'),meta:[{name:'robots',content:'noindex'}]})
</script>
<template><h1>{{ t('owner.edit') }}</h1><ClientOnly><TripEditor /><template #fallback><p>{{ t('loading') }}</p></template></ClientOnly></template>
