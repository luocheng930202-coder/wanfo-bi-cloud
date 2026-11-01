-- Local/dev bootstrap for the isolated ClickHouse warehouse target.
-- This creates empty logical layers only. It does not create users, grant roles,
-- select partition keys, or set retention periods; those require workload review.
CREATE DATABASE IF NOT EXISTS wanfo_wh_staging;
CREATE DATABASE IF NOT EXISTS wanfo_wh_ods;
CREATE DATABASE IF NOT EXISTS wanfo_wh_dwd;
CREATE DATABASE IF NOT EXISTS wanfo_wh_dws;
CREATE DATABASE IF NOT EXISTS wanfo_wh_ads;
