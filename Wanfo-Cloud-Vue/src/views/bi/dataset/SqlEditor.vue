<template><div ref="host" class="sql-code-editor" role="group" aria-label="SQL 编辑器"></div></template>
<script setup lang="ts">
import {onMounted,onBeforeUnmount,ref,watch} from 'vue'
import {EditorState,Compartment} from '@codemirror/state'
import {EditorView,keymap,lineNumbers,highlightActiveLine,drawSelection} from '@codemirror/view'
import {defaultKeymap,history,historyKeymap,indentWithTab} from '@codemirror/commands'
import {autocompletion,completionKeymap,startCompletion} from '@codemirror/autocomplete'
import {syntaxHighlighting,defaultHighlightStyle} from '@codemirror/language'
import {sql,type SQLNamespace} from '@codemirror/lang-sql'
const props=defineProps<{modelValue:string;schema:SQLNamespace;defaultSchema?:string;disabled?:boolean}>()
const emit=defineEmits<{(e:'update:modelValue',value:string):void;(e:'execute'):void}>()
const host=ref<HTMLElement>();let editor:EditorView|undefined
const language=new Compartment(),editable=new Compartment()
const languageExtension=()=>sql({schema:props.schema,defaultSchema:props.defaultSchema,upperCaseKeywords:true})
onMounted(()=>{if(!host.value)return;editor=new EditorView({parent:host.value,state:EditorState.create({doc:props.modelValue,extensions:[lineNumbers(),drawSelection(),highlightActiveLine(),history(),syntaxHighlighting(defaultHighlightStyle),language.of(languageExtension()),editable.of(EditorState.readOnly.of(!!props.disabled)),autocompletion(),keymap.of([{key:'Mod-Enter',run:()=>{if(!props.disabled)emit('execute');return true}},{key:'Ctrl-Space',run:startCompletion},...completionKeymap,...defaultKeymap,...historyKeymap,indentWithTab]),EditorView.updateListener.of(update=>{if(update.docChanged)emit('update:modelValue',update.state.doc.toString())}),EditorView.theme({'&':{height:'360px',fontSize:'13px'},'.cm-scroller':{fontFamily:'Consolas, monospace',overflow:'auto'},'.cm-content':{padding:'12px 0'},'.cm-gutters':{backgroundColor:'var(--el-fill-color-lighter)',color:'var(--el-text-color-placeholder)',borderRight:'1px solid var(--el-border-color-light)'},'&.cm-focused':{outline:'none'},'.cm-tooltip':{backgroundColor:'var(--el-bg-color)',border:'1px solid var(--el-border-color)'},'.cm-activeLine':{backgroundColor:'var(--el-fill-color-light)'},'.cm-completionIcon':{paddingRight:'6px'}})]})})})
watch(()=>props.modelValue,value=>{if(editor && value!==editor.state.doc.toString())editor.dispatch({changes:{from:0,to:editor.state.doc.length,insert:value}})})
watch(()=>[props.schema,props.defaultSchema],()=>editor?.dispatch({effects:language.reconfigure(languageExtension())}),{deep:true})
watch(()=>props.disabled,value=>editor?.dispatch({effects:editable.reconfigure(EditorState.readOnly.of(!!value))}))
onBeforeUnmount(()=>editor?.destroy())
</script>
<style scoped>.sql-code-editor{border:1px solid var(--el-border-color);border-radius:6px;overflow:hidden;background:var(--el-bg-color);color:var(--el-text-color-primary)}.sql-code-editor :deep(.cm-completionLabel){font-family:Consolas,monospace}.sql-code-editor :deep(.cm-tooltip-autocomplete){max-height:240px}</style>
