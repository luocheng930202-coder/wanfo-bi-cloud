import Cookies from 'js-cookie'

// Use an application-specific name: cookies are shared by localhost ports,
// so the generic RuoYi key can be overwritten by another local admin frontend.
const TokenKey = 'Wanfo-BI-Admin-Token'

const ExpiresInKey = 'Wanfo-BI-Expires-In'
const CookiePath = '/'

export function getToken(): string | undefined {
  return Cookies.get(TokenKey)
}

export function setToken(token: string): string | undefined {
  return Cookies.set(TokenKey, token, { path: CookiePath })
}

export function removeToken(): void {
  Cookies.remove(TokenKey, { path: CookiePath })
}

export function getExpiresIn(): string | number {
  return Cookies.get(ExpiresInKey) || -1
}

export function setExpiresIn(time: string | number): string | undefined {
  return Cookies.set(ExpiresInKey, time.toString(), { path: CookiePath })
}

export function removeExpiresIn(): void {
  Cookies.remove(ExpiresInKey, { path: CookiePath })
}
