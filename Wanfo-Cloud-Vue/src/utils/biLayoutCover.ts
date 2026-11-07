import { toJpeg } from 'html-to-image'

const MAX_BYTES = 480 * 1024

/** Snapshot of the chart or board currently on screen. The list later shows this image and does not query again. */
export async function captureResourceCover(): Promise<string> {
  const root = [...document.querySelectorAll<HTMLElement>('.bi-cover-source')]
    .find(node => node.getClientRects().length > 0 && node.clientWidth > 40 && node.clientHeight > 40)
  const target = root ? coverTarget(root) : null
  if (!target) return ''
  await new Promise(requestAnimationFrame)
  const hide = document.createElement('style')
  hide.textContent = '.resize-handle,.container-mover,.query-feedback,.query-completed,.hidden-title,.guide,.ruler,.guide-list{display:none!important}.screen-panel.selected,.grid-stack-item-content.is-selected{outline:none!important;box-shadow:none!important}'
  document.head.appendChild(hide)
  try {
    const width = Math.max(target.clientWidth, 1)
    const height = Math.max(target.clientHeight, 1)
    let pixelRatio = Math.min(1, 1280 / Math.max(width, height))
    let quality = 0.86
    let data = await render(target, width, height, pixelRatio, quality)
    while (decodedBytes(data) > MAX_BYTES && (quality > 0.5 || pixelRatio > 0.34)) {
      if (quality > 0.5) quality = Math.round((quality - 0.12) * 100) / 100
      else pixelRatio *= 0.75
      data = await render(target, width, height, pixelRatio, quality)
    }
    return data.startsWith('data:image/jpeg') && decodedBytes(data) <= MAX_BYTES ? data : ''
  } catch {
    return ''
  } finally {
    hide.remove()
  }
}

function coverTarget(root: HTMLElement): HTMLElement | null {
  if (root.dataset.cover === 'chart') {
    return [...root.querySelectorAll<HTMLElement>('.chart-canvas, .pivot-canvas, .el-table')].find(visible) || null
  }
  const sheet = root.querySelector<HTMLElement>('.screen-sheet')
  if (sheet && visible(sheet)) return sheet
  const grid = root.querySelector<HTMLElement>('.grid-stack, .view-grid')
  if (grid && visible(grid)) return grid
  return visible(root) ? root : null
}

function visible(node: HTMLElement) {
  return node.clientWidth > 40 && node.clientHeight > 40 && getComputedStyle(node).display !== 'none'
}

function render(node: HTMLElement, width: number, height: number, pixelRatio: number, quality: number) {
  const background = getComputedStyle(node).backgroundColor
  return toJpeg(node, {
    width,
    height,
    quality,
    pixelRatio,
    cacheBust: true,
    skipFonts: true,
    backgroundColor: background && background !== 'rgba(0, 0, 0, 0)' && background !== 'transparent' ? background : '#ffffff',
    style: { transform: 'none', transformOrigin: 'top left', margin: '0', position: 'relative', left: '0', top: '0' },
    filter: (domNode) => {
      if (!(domNode instanceof Element)) return true
      const classes = domNode.classList
      if (classes.contains('resize-handle') || classes.contains('container-mover') || classes.contains('query-feedback') || classes.contains('query-completed') || classes.contains('hidden-title')) return false
      return !(classes.contains('el-button') && !!domNode.closest('.panel-header'))
    },
  })
}

function decodedBytes(dataUrl: string) {
  const comma = dataUrl.indexOf(',')
  if (comma < 0) return dataUrl.length
  return Math.floor((dataUrl.length - comma - 1) * 3 / 4)
}
