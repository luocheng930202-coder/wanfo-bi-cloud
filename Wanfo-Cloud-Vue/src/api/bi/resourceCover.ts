import request from '@/utils/request'

export function saveResourceCover(kind: 'chart' | 'dashboard', id: number, version: number, dataUrl: string) {
  return request({ url: `/bi/resource-cover/${kind}/${id}`, method: 'post', data: { version, dataUrl } })
}

export function fetchResourceCover(kind: 'chart' | 'dashboard', id: number, version: number): Promise<Blob> {
  return request({ url: `/bi/resource-cover/${kind}/${id}`, params: { v: version }, responseType: 'blob', skipErrorMessage: true } as Parameters<typeof request>[0])
}
