<script setup lang="ts">
const { locale } = useI18n()
const switchLocalePath = useSwitchLocalePath()
const localePath = useLocalePath()
useHead({ htmlAttrs: { lang: locale } })
</script>
<template><div><header><NuxtLink :to="localePath('/')">Travel Buddy</NuxtLink><nav aria-label="Language"><NuxtLink :to="switchLocalePath('pl')">PL</NuxtLink> · <NuxtLink :to="switchLocalePath('en')">EN</NuxtLink></nav></header><div class="auth-nav"><ClientOnly><AuthNav /></ClientOnly></div><main><NuxtPage /></main></div></template>
<style>
body{margin:0;background:#f5f7f4;color:#183a32;font-family:system-ui,sans-serif;line-height:1.6}header,main{max-width:900px;margin:auto;padding:24px}header{display:flex;justify-content:space-between}a{color:#146554}article{background:white;border:1px solid #d9e3de;border-radius:12px;padding:24px;margin:16px 0}h1{line-height:1.2}button{padding:12px;cursor:pointer}a:focus-visible,button:focus-visible{outline:3px solid #bd7815}p{overflow-wrap:anywhere}

fieldset{border:0;padding:0;margin:0;min-width:0}
.auth-nav{max-width:900px;margin:auto;padding:0 24px}form{max-width:480px}label{display:block;margin:16px 0}input:not([type=checkbox]){display:block;box-sizing:border-box;width:100%;padding:12px;border:1px solid #82998f;border-radius:6px;font:inherit}input[type=checkbox]{width:20px;height:20px}button{font:inherit;border:1px solid #146554;border-radius:6px;background:white;color:#146554}button:disabled{opacity:.6}header{gap:12px;flex-wrap:wrap}
</style>
