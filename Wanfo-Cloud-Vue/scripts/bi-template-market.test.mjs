import assert from 'node:assert/strict'
import fs from 'node:fs/promises'
import { test } from 'node:test'

const indexPath = new URL('../../Wanfo-Cloud/ruoyi-modules/ruoyi-bi/src/main/resources/bi/template-market/index.json', import.meta.url)

test('builtin market index references classpath layouts', async () => {
  const index = JSON.parse(await fs.readFile(indexPath, 'utf8'))
  assert.equal(index.version, 1)
  assert.ok(Array.isArray(index.templates) && index.templates.length >= 2)
  for (const entry of index.templates) {
    assert.match(entry.id, /^[a-z0-9-]+$/)
    assert.ok(entry.resource.startsWith('classpath:bi/template-market/'))
    const layout = JSON.parse(await fs.readFile(new URL('../../Wanfo-Cloud/ruoyi-modules/ruoyi-bi/src/main/resources/' + entry.resource.slice('classpath:'.length), import.meta.url), 'utf8'))
    assert.ok(['datacenter-screen-template', 'datacenter-dashboard-template'].includes(layout.format))
    assert.ok(!JSON.stringify(layout).includes('chartId'))
  }
})
