<script setup lang="ts">
const {t}=useI18n()
useHead({title:()=>t('applications.mine'),meta:[{name:'robots',content:'noindex'}]})
</script>
<template><h1>{{ t('applications.mine') }}</h1><ClientOnly><ApplicationsPanel /></ClientOnly></template>
