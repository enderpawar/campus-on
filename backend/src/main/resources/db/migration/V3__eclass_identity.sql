-- ERD users identity. Keep V1/V2 history and existing event/feed owner IDs.
ALTER TABLE member RENAME TO users;
ALTER TABLE users RENAME COLUMN id TO user_id;
ALTER TABLE users RENAME COLUMN name TO user_name;
ALTER TABLE users ALTER COLUMN user_name TYPE VARCHAR(100);
ALTER TABLE users ALTER COLUMN user_name DROP NOT NULL;
ALTER TABLE users RENAME COLUMN department TO department_name;
ALTER TABLE users ALTER COLUMN department_name TYPE VARCHAR(200);
ALTER TABLE users ADD COLUMN external_ref VARCHAR(64);
ALTER TABLE users ADD COLUMN user_role VARCHAR(20) NOT NULL DEFAULT 'STUDENT';
ALTER TABLE users ADD COLUMN account_status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE';
ALTER TABLE users ADD COLUMN last_synced_at TIMESTAMP(6) WITH TIME ZONE;
ALTER TABLE users ADD COLUMN updated_at TIMESTAMP(6) WITH TIME ZONE;
ALTER TABLE users ADD COLUMN legacy_user_key VARCHAR(64);
-- Old local-email identities cannot be mapped to academic IDs without evidence.
-- Preserve their owned data, disable authentication, and invalidate old JWTs.
UPDATE users SET external_ref = 'legacy:' || CAST(user_id AS VARCHAR(30)),
                 account_status = 'SUSPENDED', token_version = token_version + 1;
ALTER TABLE users ALTER COLUMN external_ref SET NOT NULL;
ALTER TABLE users ADD CONSTRAINT uq_users_school_external UNIQUE (school_id, external_ref);
ALTER TABLE users ADD CONSTRAINT ck_users_role CHECK (user_role IN ('STUDENT', 'PROFESSOR', 'TA', 'STAFF'));
ALTER TABLE users ADD CONSTRAINT ck_users_status CHECK (account_status IN ('ACTIVE', 'SUSPENDED', 'WITHDRAWN'));
ALTER TABLE users DROP CONSTRAINT uq_member_email;
ALTER TABLE users DROP COLUMN email;
ALTER TABLE users DROP COLUMN password_hash;
-- Optional upstream names may be absent; retain the nonblank check when supplied.

CREATE TABLE eclass_connections (
  user_id BIGINT PRIMARY KEY,
  encrypted_session_cookie VARCHAR(24000) NOT NULL,
  encrypted_password VARCHAR(8000),
  sync_state VARCHAR(20) NOT NULL DEFAULT 'PENDING',
  last_synced_at TIMESTAMP(6) WITH TIME ZONE,
  last_error_code VARCHAR(50),
  updated_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
  CONSTRAINT fk_eclass_connection_user FOREIGN KEY (user_id) REFERENCES users(user_id)
);
CREATE INDEX ix_eclass_connection_updated ON eclass_connections(updated_at);
