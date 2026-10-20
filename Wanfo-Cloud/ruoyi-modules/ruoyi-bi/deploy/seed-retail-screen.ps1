# Load demo-retail-screen.sql into MySQL (docker mysql-dev, root/root)
$ErrorActionPreference = 'Stop'
$sql = Join-Path $PSScriptRoot 'demo-retail-screen.sql'
if (-not (Test-Path $sql)) { throw "Missing $sql" }
docker cp $sql mysql-dev:/tmp/demo-retail-screen.sql | Out-Null
docker exec mysql-dev sh -c "mysql -uroot -proot --default-character-set=utf8mb4 bi_phase_a_test < /tmp/demo-retail-screen.sql"
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
Write-Host 'Seeded bi_phase_a_test: dim_store, dim_product, fact_order, dim_target, ops_retail_note, v_retail_wide' -ForegroundColor Green
docker exec mysql-dev mysql -uroot -proot -N -e "SELECT 'stores',COUNT(*) FROM bi_phase_a_test.dim_store UNION ALL SELECT 'products',COUNT(*) FROM bi_phase_a_test.dim_product UNION ALL SELECT 'orders',COUNT(*) FROM bi_phase_a_test.fact_order UNION ALL SELECT 'targets',COUNT(*) FROM bi_phase_a_test.dim_target;"
