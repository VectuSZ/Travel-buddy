export function useTripApi() {
 async function write<T>(url:string, body:unknown, method:'POST'|'PATCH'='POST') {
  const csrf=await $fetch<{token:string}>('/api/v1/auth/csrf',{cache:'no-store',retry:0})
  return $fetch<T>(url,{method,body:body as Record<string,unknown>,headers:{'X-CSRF-TOKEN':csrf.token},retry:0})
 }
 return {write}
}
