interface MenuRoute {
  path: string
  hidden?: boolean
  alwaysShow?: boolean
  children?: MenuRoute[]
  [key: string]: unknown
}

/** Only simplify the host sidebar; retain registered routes and BI workspace navigation. */
export function compactBiAdminMenu(routes: MenuRoute[], parent = ''): MenuRoute[] {
  return routes.map(route => {
    if (/^(https?:)?\/\//.test(route.path)) return route
    const path = (route.path.startsWith('/') ? route.path : `${parent}/${route.path}`).replace(/\/{2,}/g, '/').replace(/\/$/, '')
    const item = { ...route }
    if (route.children) item.children = compactBiAdminMenu(route.children, path)
    if (path.startsWith('/bi-admin/') && path !== '/bi-admin/workbench') item.hidden = true
    if (path === '/bi-admin') {
      item.alwaysShow = false
      if (!item.children?.some(child => !child.hidden)) item.hidden = true
    }
    return item
  })
}
