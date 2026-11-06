<script setup lang="ts">
const { t } = useI18n()
useHead({ title: () => t('auth.reset') + ' — Travel Buddy', meta: [{ name: 'robots', content: 'noindex' }] })
</script>
<template><AuthForm mode="reset" /></template>
