# 行政区 GeoJSON

- `china-provinces.json`：中国省级边界，来源于 [阿里云 DataV 地理小工具](https://datav.aliyun.com/portal/school/atlas/area_selector) 开放接口（`areas_v3/bound/100000_full`），仅用于 BI 地图可视化，请遵循其使用说明。
- 通过 `GET /bi/geo/china-provinces` 由服务端下发，避免前端直连第三方 CDN。
