interface Account { email: string }
export function useAuth() {
  const account = useState<Account | null>('account', () => null)
  const ready = useState('auth-ready', () => false)
  async function load() {
    if (import.meta.server) return
    try { account.value = await $fetch<Account>('/api/v1/auth/session', { cache: 'no-store', retry: 0 }) }
    catch (e: unknown) {
      account.value = null
      if ((e as { statusCode?: number }).statusCode !== 401) throw e
    } finally { ready.value = true }
  }
  async function post(path: string, body: unknown = {}) {
    const csrf = await $fetch<{ token: string }>('/api/v1/auth/csrf', { cache: 'no-store', retry: 0 })
    return $fetch('/api/v1/auth/' + path, { method: 'POST', body: body as Record<string, unknown>,
      headers: { 'X-CSRF-TOKEN': csrf.token }, retry: 0 })
  }
  async function logout() { await post('logout'); account.value = null }
  return { account, ready, load, post, logout }
}
