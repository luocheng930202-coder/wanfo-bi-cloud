import assert from 'node:assert/strict'
import {diagnosticsFrom,isBiDiagnostic} from './typecheck-diagnostics.mjs'
const data=diagnosticsFrom(`src/views/bi/dashboard/Test.vue(1,2): error TS2322: broken
src/layout/Host.vue(3,4): error TS2339: import("D:/project/src/views/bi/dashboard/Test.vue")
D:\\project\\src\\api\\bi\\asset.ts(5,6): error TS18048: missing
src/utils/biPresentation.ts(7,8): error TS2322: broken
  Type detail from src/views/bi/dashboard/Test.vue`)
assert.equal(data.length,4);assert.equal(data.filter(isBiDiagnostic).length,3);assert.equal(isBiDiagnostic(data[1]),false)
console.log('3 diagnostic ownership assertions passed')
