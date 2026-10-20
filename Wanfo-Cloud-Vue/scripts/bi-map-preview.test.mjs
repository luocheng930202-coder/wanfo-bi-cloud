import assert from 'node:assert/strict'
import fs from 'node:fs/promises'
import ts from 'typescript'
import { test } from 'node:test'

const opts = { compilerOptions: { target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.ES2022 } }
let geoSrc = await fs.readFile(new URL('../src/utils/biMapGeo.ts', import.meta.url), 'utf8')
geoSrc = geoSrc.replace(/import request from '@\/utils\/request'[\s\S]*?^/m, '').replace(/import \* as echarts[\s\S]*?^/m, '')
geoSrc = geoSrc.replace(/export async function fetchMapGeo[\s\S]*?^}/m, '').replace(/export async function ensureMapRegistered[\s\S]*?^}/m, '')
let previewSrc = await fs.readFile(new URL('../src/utils/biMapPreview.ts', import.meta.url), 'utf8')
previewSrc = previewSrc.split('\n').filter(line => !line.includes('@/')).join('\n')
const metricStub = `export function metricResultKey(m){return m.alias || (m.agg==='NONE'?m.field:m.agg.toLowerCase()+'_'+m.field)}`
const bundle = metricStub + '\n' + ts.transpileModule(geoSrc, opts).outputText + '\n' + ts.transpileModule(previewSrc, opts).outputText
const { normalizeProvinceName, validateMapConfig } = await import(
  'data:text/javascript;base64,' + Buffer.from(bundle).toString('base64')
)

test('normalize province aliases', () => {
  assert.equal(normalizeProvinceName('广东'), '广东省')
  assert.equal(normalizeProvinceName('内蒙古'), '内蒙古自治区')
  assert.equal(normalizeProvinceName('北京市'), '北京市')
})

test('map chart config', () => {
  const definition = {
    schemaVersion: 1,
    name: 'm',
    type: 'map',
    query: {
      datasetId: 1,
      dimensions: [{ field: 'province', alias: 'province' }],
      metrics: [{ field: 'amount', agg: 'SUM', alias: 'sum_amount' }],
      limit: 100
    },
    advanced: { mapScope: 'china-provinces' }
  }
  assert.equal(validateMapConfig(definition), null)
  assert.equal(normalizeProvinceName('浙江'), '浙江省')
})
