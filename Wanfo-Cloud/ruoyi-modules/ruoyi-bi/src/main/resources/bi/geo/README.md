# 行政区 GeoJSON

- `china-provinces.json`：中国省级边界，来源于 [阿里云 DataV 地理小工具](https://datav.aliyun.com/portal/school/atlas/area_selector) 开放接口（`areas_v3/bound/100000_full`），仅用于 BI 地图可视化，请遵循其使用说明。
- 通过 `GET /bi/geo/china-provinces` 由服务端下发，避免前端直连第三方 CDN。

## 省、市、区县资源包

- `regions-index.json`：地区名称、6 位行政区编码、别名与上下级索引；`regions/*.json.gz` 按范围压缩存储子区域边界。区县单独边界从其父范围中提取，不重复存储所有县的文件。
- 来源：[DataV.GeoAtlas](https://datav.aliyun.com/portal/school/atlas/atlas_aigc)，沿用现有 `areas_v3` 数据，来源页标明版本为 **2021.5**、仅供学习交流；这不是实时行政区划。数据资源不属于本项目 MIT 代码许可的授权范围，使用与更新须遵循来源说明。
- 本次快照有 3,238 个地区索引、362 个压缩边界包。未提供的下级不虚构边界；直辖市直接展示区县，部分省份的下一层含直辖县级地区。
- 客户端仅调用本项目 `/bi/geo/regions` 和 `/bi/geo/{目录内编码}`；后端只读取固定目录内资源，不拼接外部 URL，也不允许任意路径。更新边界时需同步更新父子目录和来源版本。
