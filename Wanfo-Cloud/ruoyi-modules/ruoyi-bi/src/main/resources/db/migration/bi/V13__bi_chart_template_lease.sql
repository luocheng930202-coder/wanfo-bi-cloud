-- Ephemeral charts created while binding layout templates; reclaimed on cancel/failure/expire.
CREATE TABLE bi_chart_template_lease (
 chart_id bigint NOT NULL PRIMARY KEY,
 owner_principal bigint NOT NULL,
 create_by varchar(64) NOT NULL,
 create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
 expire_time datetime NOT NULL,
 KEY idx_chart_lease_expire(expire_time),
 KEY idx_chart_lease_owner(owner_principal,expire_time)
);
