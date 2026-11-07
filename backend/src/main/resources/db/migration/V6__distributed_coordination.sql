-- Shared short transaction mutex; upstream HTTP never holds it.
CREATE TABLE auth_throttle_mutex (id INTEGER PRIMARY KEY);
INSERT INTO auth_throttle_mutex(id) VALUES (1);
CREATE TABLE auth_throttle_failure (
    bucket_key VARCHAR(130) PRIMARY KEY,
    failures INTEGER NOT NULL,
    ends_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE TABLE auth_throttle_reservation (
    attempt_id VARCHAR(36) NOT NULL,
    bucket_key VARCHAR(130) NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    PRIMARY KEY(attempt_id, bucket_key)
);
CREATE INDEX ix_auth_reservation_expiry ON auth_throttle_reservation(expires_at);
ALTER TABLE eclass_connections ADD COLUMN sync_lease_owner VARCHAR(36);
ALTER TABLE eclass_connections ADD COLUMN sync_lease_until TIMESTAMP WITH TIME ZONE;
ALTER TABLE eclass_connections ADD COLUMN reauth_claim_owner VARCHAR(36);
ALTER TABLE eclass_connections ADD COLUMN reauth_claim_until TIMESTAMP WITH TIME ZONE;
