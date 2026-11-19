// Add an identifier here when introducing an optional integration.
// Its loader must observe permits(id), including withdrawal of permission.
const integrations: readonly string[] = []
export function useOptionalCookies() {
  const choice = useCookie<'allow' | 'deny' | undefined>('optional-cookies-v1', { sameSite: 'lax' })
  return {
    available: integrations.length > 0, choice,
    set(value: 'allow' | 'deny') { if (integrations.length) choice.value = value },
    permits(id: string) { return integrations.includes(id) && choice.value === 'allow' }
  }
}
