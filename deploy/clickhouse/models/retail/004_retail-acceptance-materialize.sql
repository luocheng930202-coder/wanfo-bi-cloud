-- Local synthetic acceptance of the fixed retail-v1 DWS aggregation and atomic publish.
-- The application service performs the same validations and SQL with a run-scoped stage;
-- this fixture is only for verifying the ClickHouse target schema, permissions and metrics.

DROP TABLE IF EXISTS wanfo_wh_dws.__dp_acceptance_retail_stage;

SELECT throwIf((SELECT count() FROM wanfo_wh_dwd.fact_retail_order_line
                WHERE tenant_id='' OR order_id='' OR line_no=0 OR org_id='' OR is_deleted NOT IN (0,1)) != 0,
               'invalid acceptance facts');
SELECT throwIf((SELECT count() FROM (
                    SELECT tenant_id,order_id,line_no
                    FROM wanfo_wh_dwd.fact_retail_order_line
                    GROUP BY tenant_id,order_id,line_no HAVING count()>1)) != 0,
               'duplicate acceptance fact keys');
SELECT throwIf((SELECT count() FROM (
                    SELECT f.tenant_id,f.order_id,f.line_no,
                           countIf(o.org_id!='') AS org_matches,
                           countIf(p.product_id!='') AS product_matches
                    FROM wanfo_wh_dwd.fact_retail_order_line f
                    LEFT JOIN wanfo_wh_dwd.dim_retail_org o
                      ON o.tenant_id=f.tenant_id AND o.org_id=f.org_id
                     AND o.valid_from<=coalesce(f.order_at,toDateTime64(f.order_date,3,'Asia/Shanghai'))
                     AND (o.valid_to IS NULL OR o.valid_to>coalesce(f.order_at,toDateTime64(f.order_date,3,'Asia/Shanghai')))
                    LEFT JOIN wanfo_wh_dwd.dim_retail_product p
                      ON p.tenant_id=f.tenant_id AND p.product_id=f.product_id
                     AND p.valid_from<=coalesce(f.order_at,toDateTime64(f.order_date,3,'Asia/Shanghai'))
                     AND (p.valid_to IS NULL OR p.valid_to>coalesce(f.order_at,toDateTime64(f.order_date,3,'Asia/Shanghai')))
                    WHERE f.is_deleted=0
                    GROUP BY f.tenant_id,f.order_id,f.line_no
                    HAVING org_matches!=1 OR product_matches!=1)) != 0,
               'acceptance dimension history mismatch');

CREATE TABLE wanfo_wh_dws.__dp_acceptance_retail_stage
AS wanfo_wh_dws.agg_retail_sales_monthly;

INSERT INTO wanfo_wh_dws.__dp_acceptance_retail_stage
    (tenant_id,month_start,org_id,province_code,city_code,county_code,channel,category_id,
     sales_amount,net_quantity,order_count,source_max_updated_at,generated_at,load_run_id)
SELECT f.tenant_id,toDate(toStartOfMonth(f.order_date)),f.org_id,o.province_code,o.city_code,o.county_code,
       f.channel,p.category_id,CAST(sum(f.sales_amount),'Decimal(20,2)'),CAST(sum(f.quantity),'Decimal(20,3)'),
       uniqExact(f.order_id),max(f.source_updated_at),now64(3,'UTC'),toUInt64(999999)
FROM wanfo_wh_dwd.fact_retail_order_line f
INNER JOIN wanfo_wh_dwd.dim_retail_org o
  ON o.tenant_id=f.tenant_id AND o.org_id=f.org_id
 AND o.valid_from<=coalesce(f.order_at,toDateTime64(f.order_date,3,'Asia/Shanghai'))
 AND (o.valid_to IS NULL OR o.valid_to>coalesce(f.order_at,toDateTime64(f.order_date,3,'Asia/Shanghai')))
INNER JOIN wanfo_wh_dwd.dim_retail_product p
  ON p.tenant_id=f.tenant_id AND p.product_id=f.product_id
 AND p.valid_from<=coalesce(f.order_at,toDateTime64(f.order_date,3,'Asia/Shanghai'))
 AND (p.valid_to IS NULL OR p.valid_to>coalesce(f.order_at,toDateTime64(f.order_date,3,'Asia/Shanghai')))
WHERE f.is_deleted=0
GROUP BY f.tenant_id,toDate(toStartOfMonth(f.order_date)),f.org_id,o.province_code,o.city_code,
         o.county_code,f.channel,p.category_id;

SELECT throwIf((SELECT count() FROM (
                    SELECT tenant_id,month_start,org_id,channel,category_id
                    FROM wanfo_wh_dws.__dp_acceptance_retail_stage
                    GROUP BY tenant_id,month_start,org_id,channel,category_id HAVING count()>1)) != 0,
               'duplicate acceptance aggregate keys');
SELECT throwIf((SELECT sum(sales_amount) FROM wanfo_wh_dwd.fact_retail_order_line WHERE is_deleted=0)
                != (SELECT sum(sales_amount) FROM wanfo_wh_dws.__dp_acceptance_retail_stage),
               'sales reconciliation failed');
SELECT throwIf((SELECT sum(quantity) FROM wanfo_wh_dwd.fact_retail_order_line WHERE is_deleted=0)
                != (SELECT sum(net_quantity) FROM wanfo_wh_dws.__dp_acceptance_retail_stage),
               'quantity reconciliation failed');

EXCHANGE TABLES wanfo_wh_dws.agg_retail_sales_monthly
              AND wanfo_wh_dws.__dp_acceptance_retail_stage;
DROP TABLE wanfo_wh_dws.__dp_acceptance_retail_stage;

SELECT count() AS aggregate_rows,
       sum(sales_amount) AS total_sales,
       sum(net_quantity) AS total_net_quantity,
       sum(order_count) AS summed_group_order_counts,
       min(month_start) AS first_month,
       max(month_start) AS last_month
FROM wanfo_wh_dws.agg_retail_sales_monthly
WHERE tenant_id='wanfo_demo';
