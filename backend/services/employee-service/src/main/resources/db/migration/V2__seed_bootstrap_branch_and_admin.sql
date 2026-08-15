-- Bootstrap problem: employee registration is deliberately admin-only (no
-- public sign-up — see ADR-006, Decision 6), so without a seeded first
-- admin there would be no way to create the second employee at all. This
-- mirrors the project's existing local-dev-only-default-credentials
-- convention (docker-compose's own "banksphere"/"banksphere_local_dev" —
-- see CLAUDE.md) and must be rotated (or this employee deactivated and
-- replaced) before any real/shared deployment.
--
-- Password: "ChangeMe123!" — hashed with the exact BCryptPasswordEncoder
-- (no-arg, strength 10) this service itself uses at runtime, generated
-- out-of-band and pasted here as a literal (Flyway migrations cannot call
-- application code) and round-trip-verified against that same encoder
-- before being committed.
INSERT INTO branches (id, branch_code, branch_name, ifsc, status, created_at, updated_at)
VALUES (
    '00000000-0000-0000-0000-000000000001',
    'HQ001',
    'BankSphere Head Office',
    'BANK0000001',
    'ACTIVE',
    now(),
    now()
);

INSERT INTO employees (id, employee_number, username, password_hash, first_name, last_name, email, branch_id, status, created_at, updated_at)
VALUES (
    '00000000-0000-0000-0000-000000000002',
    'EMP000001',
    'admin',
    '$2a$10$opHxKatFcXNAe1nGUQuWn.2Wd0npE8pmAc0rV.vbybIO.XLCbmgc2',
    'System',
    'Administrator',
    'admin@banksphere.example',
    '00000000-0000-0000-0000-000000000001',
    'ACTIVE',
    now(),
    now()
);

INSERT INTO employee_roles (employee_id, role)
VALUES ('00000000-0000-0000-0000-000000000002', 'ADMIN');
