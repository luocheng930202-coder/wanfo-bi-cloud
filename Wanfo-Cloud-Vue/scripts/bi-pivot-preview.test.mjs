import assert from 'node:assert/strict'
import fs from 'node:fs/promises'
import ts from 'typescript'
import { test } from 'node:test'

const opts = { compilerOptions: { target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.ES2022 } }
let pivotSrc = await fs.readFile(new URL('../src/utils/biPivotPreview.ts', import.meta.url), 'utf8')
pivotSrc = pivotSrc.split('\n').filter(line => !line.includes('@/utils/biEchartsPreview')).join('\n')
const metricStub = `export function metricResultKey(m){return m.alias || (m.agg==='NONE'?m.field:m.agg.toLowerCase()+'_'+m.field)}`
const bundle = metricStub + '\n' + ts.transpileModule(pivotSrc, opts).outputText
const { validatePivotConfig, buildPivotDataConfig, syncPivotQueryDimensions } = await import(
  'data:text/javascript;base64,' + Buffer.from(bundle).toString('base64')
)

const definition = {
  schemaVersion: 1,
  name: 'p',
  type: 'pivot',
  query: {
    datasetId: 1,
    dimensions: [{ field: 'city', alias: 'city' }, { field: 'cat', alias: 'cat' }],
    metrics: [{ field: 'amount', agg: 'SUM', alias: 'sum_amount' }],
    limit: 100
  },
  advanced: { pivotRowFields: ['city'], pivotColFields: ['cat'] }
}

test('validate and build pivot config', () => {
  assert.equal(validatePivotConfig(definition), null)
  const cfg = buildPivotDataConfig(definition, { columns: [], rows: [{ city: '杭州', cat: 'A', sum_amount: 9 }], rowCount: 1, costMs: 1 })
  assert.deepEqual(cfg.fields.rows, ['city'])
  assert.deepEqual(cfg.fields.columns, ['cat'])
  assert.deepEqual(cfg.fields.values, ['sum_amount'])
})

test('sync dimensions from pivot fields', () => {
  syncPivotQueryDimensions(definition)
  assert.deepEqual(definition.query.dimensions.map(d => d.field), ['city', 'cat'])
})
