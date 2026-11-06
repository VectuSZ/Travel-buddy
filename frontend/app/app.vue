<script setup lang="ts">
const { locale } = useI18n()
const switchLocalePath = useSwitchLocalePath()
const localePath = useLocalePath()
useHead({ htmlAttrs: { lang: locale } })
</script>
<template><div><header><NuxtLink :to="localePath('/')">Travel Buddy</NuxtLink><nav aria-label="Language"><NuxtLink :to="switchLocalePath('pl')">PL</NuxtLink> · <NuxtLink :to="switchLocalePath('en')">EN</NuxtLink></nav></header><main><NuxtPage /></main></div></template>
<style>
body{margin:0;background:#f5f7f4;color:#183a32;font-family:system-ui,sans-serif;line-height:1.6}header,main{max-width:900px;margin:auto;padding:24px}header{display:flex;justify-content:space-between}a{color:#146554}article{background:white;border:1px solid #d9e3de;border-radius:12px;padding:24px;margin:16px 0}h1{line-height:1.2}button{padding:12px;cursor:pointer}a:focus-visible,button:focus-visible{outline:3px solid #bd7815}p{overflow-wrap:anywhere}
</style>
