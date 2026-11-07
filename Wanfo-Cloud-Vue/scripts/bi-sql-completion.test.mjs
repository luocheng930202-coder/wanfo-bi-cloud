import {test} from 'node:test'
import assert from 'node:assert/strict'
import {EditorState} from '@codemirror/state'
import {CompletionContext} from '@codemirror/autocomplete'
import {sql,schemaCompletionSource,keywordCompletionSource,StandardSQL} from '@codemirror/lang-sql'
const config={schema:{demo:{orders:['id','amount','school_id'],schools:['id','name']}},defaultSchema:'demo',upperCaseKeywords:true}
async function suggestions(doc,pos=doc.length){const state=EditorState.create({doc,extensions:[sql(config)]});return (await schemaCompletionSource(config)(new CompletionContext(state,pos,true)))?.options || []}
test('SQL editor completes tables from the selected logical source',async()=>{const options=await suggestions('SELECT * FROM demo.ord');assert.ok(options.some(o=>o.label==='orders'))})
test('SQL editor completes columns using table aliases',async()=>{const doc='SELECT o.am FROM orders o';const options=await suggestions(doc,doc.indexOf('am')+2);assert.ok(options.some(o=>o.label==='amount'))})
test('SQL editor completes SQL keywords in uppercase',async()=>{const state=EditorState.create({doc:'SEL',extensions:[sql(config)]});const result=await keywordCompletionSource(StandardSQL,true)(new CompletionContext(state,3,true));assert.ok(result.options.some(o=>o.label==='SELECT'))})
