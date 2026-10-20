import 'vue'
import type {parseTime,resetForm,handleTree,addDateRange,selectDictLabel,selectDictLabels} from '@/utils/ruoyi'
import type {download} from '@/utils/request'
import type {useDict} from '@/utils/dict'
import type {getConfigKey} from '@/api/system/config'
import type tab from '@/plugins/tab'
import type auth from '@/plugins/auth'
import type cache from '@/plugins/cache'
import type modal from '@/plugins/modal'
import type fileDownload from '@/plugins/download'
/** Augment the real Vue module; never replace its exports with an ambient declaration. */
declare module 'vue' {
 interface ComponentCustomProperties {
  parseTime:typeof parseTime
  resetForm:typeof resetForm
  handleTree:typeof handleTree
  addDateRange:typeof addDateRange
  selectDictLabel:typeof selectDictLabel
  selectDictLabels:typeof selectDictLabels
  download:typeof download
  useDict:typeof useDict
  getConfigKey:typeof getConfigKey
  $tab:typeof tab
  $auth:typeof auth
  $cache:typeof cache
  $modal:typeof modal
  $download:typeof fileDownload
 }
}
