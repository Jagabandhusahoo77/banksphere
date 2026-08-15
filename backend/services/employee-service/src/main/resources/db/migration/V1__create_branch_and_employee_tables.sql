CREATE TABLE branches (
    id           UUID          PRIMARY KEY,
    branch_code  VARCHAR(20)   NOT NULL,
    branch_name  VARCHAR(150)  NOT NULL,
    ifsc         VARCHAR(11)   NOT NULL,
    status       VARCHAR(20)   NOT NULL DEFAULT 'ACTIVE',
    created_at   TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT uq_branches_branch_code UNIQUE (branch_code),
    CONSTRAINT chk_branches_status CHECK (status IN ('ACTIVE', 'INACTIVE'))
);

CREATE TABLE employees (
    id               UUID          PRIMARY KEY,
    employee_number  VARCHAR(20)   NOT NULL,
    username         VARCHAR(50)   NOT NULL,
    password_hash    VARCHAR(255)  NOT NULL,
    first_name       VARCHAR(100)  NOT NULL,
    last_name        VARCHAR(100)  NOT NULL,
    email            VARCHAR(254)  NOT NULL,
    branch_id        UUID          NOT NULL REFERENCES branches (id),
    status           VARCHAR(20)   NOT NULL DEFAULT 'ACTIVE',
    created_at       TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT uq_employees_employee_number UNIQUE (employee_number),
    CONSTRAINT uq_employees_username UNIQUE (username),
    CONSTRAINT chk_employees_status CHECK (status IN ('ACTIVE', 'INACTIVE', 'LOCKED'))
);

-- "Who works at this branch" is not a query this phase runs yet, but the
-- FK is enforced now (see future branch-scoped-authorization note in
-- ADR-006) so the index earns its keep from day one rather than being
-- retrofitted alongside the query that needs it.
CREATE INDEX idx_employees_branch_id ON employees (branch_id);

-- The login lookup (POST /api/v1/employees/auth/login) is this service's
-- single most common query, always by username.
CREATE INDEX idx_employees_username ON employees (username);

-- An employee may hold more than one Role (see Employee.java's javadoc) —
-- a plain join table against the fixed Role enum, not a full roles
-- reference table, since roles are code-defined and not administrable via
-- the database in this phase (see security/RolePermissions.java).
CREATE TABLE employee_roles (
    employee_id  UUID         NOT NULL REFERENCES employees (id) ON DELETE CASCADE,
    role         VARCHAR(30)  NOT NULL,
    PRIMARY KEY (employee_id, role),
    CONSTRAINT chk_employee_roles_role CHECK (role IN (
        'TELLER', 'KYC_OFFICER', 'LOAN_OFFICER', 'CARD_OFFICER',
        'OPERATIONS', 'BRANCH_MANAGER', 'ADMIN'
    ))
);
