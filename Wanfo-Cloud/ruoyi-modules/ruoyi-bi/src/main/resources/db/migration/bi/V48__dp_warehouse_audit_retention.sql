-- Warehouse-scoped audit retention policy (inherits global until customized via API).
INSERT INTO dp_audit_retention_policy(policy_key, live_retention_days, updated_by)
VALUES ('warehouse', 365, 'system')
ON DUPLICATE KEY UPDATE policy_key = policy_key;
