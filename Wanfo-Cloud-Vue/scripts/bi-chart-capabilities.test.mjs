import assert from 'node:assert/strict'
import fs from 'node:fs/promises'
import ts from 'typescript'
import { test } from 'node:test'

async function load(file) {
  const source = await fs.readFile(new URL('../src/utils/'+file, import.meta.url), 'utf8')
  const compiled = ts.transpileModule(source, { compilerOptions: { target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.ES2022 } }).outputText
  return import('data:text/javascript;base64,'+Buffer.from(compiled).toString('base64'))
}
const { CHART_TYPES } = await load('biChartTypes.ts')
const { CHART_CAPABILITIES: capabilities, allowedAggregations } = await load('biChartCapabilities.ts')

test('every persisted chart type has an explicit data role and capability contract', () => {
  assert.deepEqual(Object.keys(capabilities).sort(), [...CHART_TYPES].sort())
  for (const type of CHART_TYPES) {
    assert.ok(capabilities[type].metric)
    assert.ok(capabilities[type].hint)
  }
})

test('non-cartesian charts never advertise axis controls, smoothing applies only to line renderers', () => {
  for (const type of ['pie','ring','funnel','radar','gauge','table','pivot','map']) assert.equal(capabilities[type].axis, false)
  for (const type of CHART_TYPES) assert.equal(capabilities[type].smooth, ['line','area','combo'].includes(type))
  assert.equal(capabilities.gauge.dimension, null)
  assert.equal(capabilities.table.legend, false)
  assert.match(capabilities.scatter.hint, /X、Y/)
})

test('text and date fields cannot select numeric aggregations, raw values belong to detail tables', () => {
  for (const field of ['varchar','timestamp','date']) {
    assert.deepEqual(allowedAggregations(field,'bar'), ['COUNT','COUNT_DISTINCT'])
    assert.deepEqual(allowedAggregations(field,'table'), ['NONE','COUNT','COUNT_DISTINCT'])
  }
  assert.ok(allowedAggregations('decimal(20,2)','bar').includes('SUM'))
  assert.ok(!allowedAggregations('bigint','gauge').includes('NONE'))
})
