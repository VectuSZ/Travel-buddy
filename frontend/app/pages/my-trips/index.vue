<script setup lang="ts">
const {t}=useI18n()
useHead({title:()=>t('owner.mine'),meta:[{name:'robots',content:'noindex'}]})
</script>
<template><h1>{{ t('owner.mine') }}</h1><ClientOnly><MyTrips /><template #fallback><p>{{ t('loading') }}</p></template></ClientOnly></template>
