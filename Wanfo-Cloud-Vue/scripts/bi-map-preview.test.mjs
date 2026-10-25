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
const runtimeStub = `export const BI_TYPE={chart:12,title:16};export function resolveBiTheme(){return {text:'#111827',fontFamily:'Arial'}};export function chartAppearance(){return {}};export function paletteFromStyle(){return ['#2563eb']};export function formatMetric(v){return String(v)}`
const regionSource=(await fs.readFile(new URL('../src/utils/biMapRegions.ts',import.meta.url),'utf8')).split('\n').filter(line=>!line.startsWith('import ')).join('\n')
geoSrc=geoSrc.split('\n').filter(line=>!line.startsWith('import ')).join('\n');previewSrc=previewSrc.split('\n').filter(line=>!line.startsWith('import ')).join('\n')
const bundle = ts.transpileModule(regionSource,opts).outputText+'\n'+metricStub + '\n'+runtimeStub+'\n' + ts.transpileModule(geoSrc, opts).outputText + '\n' + ts.transpileModule(previewSrc, opts).outputText
const { normalizeProvinceName, validateMapConfig, buildMapPreviewOption } = await import(
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
  definition.style={mapBaseStyle:'dark',mapProjection:'mercator'}
  assert.equal(validateMapConfig(definition),null)
  definition.style.mapProjection='conic';assert.match(validateMapConfig(definition),/投影/)
  assert.equal(normalizeProvinceName('浙江'), '浙江省')
})

test('map preview wires local projection and base style into the geo component',()=>{
  const definition={type:'map',name:'区域',query:{dimensions:[{field:'province'}],metrics:[{field:'amount',agg:'SUM',alias:'sum_amount'}]},advanced:{mapScope:'china-provinces'},style:{mapProjection:'mercator',mapBaseStyle:'dark'}}
  const result={rows:[{province:'浙江省',sum_amount:12}]}
  const option=buildMapPreviewOption({mapName:'province',definition,result,labels:{},formatFor:()=>undefined})
  assert.equal(option.backgroundColor,'#151b26')
  assert.equal(typeof option.geo.projection.project,'function')
  assert.equal(option.series[0].geoIndex,0)
})
