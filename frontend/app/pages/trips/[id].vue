<script setup lang="ts">
import {tripUrl} from '~/utils/tripUrl'
import type {Trip} from '../../../shared/trip'
const route=useRoute(),{locale}=useI18n()
const {data,error}=await useFetch<Trip>(()=>'/api/v1/trips/'+route.params.id)
if(error.value)throw createError({statusCode:error.value.statusCode===404?404:502,statusMessage:'Trip unavailable'})
if(data.value)await navigateTo(tripUrl(data.value,locale.value),{redirectCode:301,replace:true})
</script>
<template><span /></template>
