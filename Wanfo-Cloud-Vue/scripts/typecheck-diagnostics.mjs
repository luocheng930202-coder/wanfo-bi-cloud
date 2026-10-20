export function diagnosticsFrom(output){return output.split(/\r?\n/).flatMap(text=>{const match=/^(.+?)\((\d+),(\d+)\): error (TS\d+):/.exec(text);return match?[{file:match[1].replaceAll('\\','/'),text}]:[]})}
export function isBiDiagnostic(diagnostic){return /(?:^|\/)src\/(?:views\/bi\/|api\/bi\/|utils\/bi[^/]*\.ts$|composables\/useBi[^/]*\.ts$)/.test(diagnostic.file)}
