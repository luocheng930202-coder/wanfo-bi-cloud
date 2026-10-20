import {spawnSync} from 'node:child_process'
import {fileURLToPath} from 'node:url'
import path from 'node:path'
import fs from 'node:fs'
import {diagnosticsFrom,isBiDiagnostic} from './typecheck-diagnostics.mjs'
const root=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'..')
const result=spawnSync(process.execPath,[path.join(root,'node_modules/vue-tsc/bin/vue-tsc.js'),'--noEmit','--pretty','false'],{cwd:root,encoding:'utf8',maxBuffer:64*1024*1024,timeout:180000})
const output=(result.stdout||'')+(result.stderr||'')
const cache=path.join(root,'node_modules/.cache/bi-typecheck.log');fs.mkdirSync(path.dirname(cache),{recursive:true});fs.writeFileSync(cache,output)
const diagnostics=diagnosticsFrom(output)
const bi=diagnostics.filter(isBiDiagnostic)
if(result.error || result.status!==0 && diagnostics.length===0){console.error(output || result.error || 'Type checker did not complete');process.exit(1)}
console.log(`BI diagnostics: ${bi.length}; full-project diagnostics: ${diagnostics.length}. Full log: ${cache}`)
if(diagnostics.length)console.log('Full-project typecheck has NOT passed; this command gates only BI-owned sources and retains all host diagnostics in the log.')
if(bi.length){console.error(bi.map(d=>d.text).join('\n'));process.exit(1)}
