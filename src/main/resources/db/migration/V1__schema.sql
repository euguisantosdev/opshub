CREATE TABLE users (
    id            BIGSERIAL PRIMARY KEY,
    username      VARCHAR(60)  NOT NULL UNIQUE,
    display_name  VARCHAR(120) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    role          VARCHAR(20)  NOT NULL,
    active        BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMPTZ  NOT NULL
);

CREATE TABLE monitored_services (
    id                   BIGSERIAL PRIMARY KEY,
    name                 VARCHAR(80)  NOT NULL UNIQUE,
    description          VARCHAR(255) NOT NULL DEFAULT '',
    criticality          VARCHAR(10)  NOT NULL,
    failure_threshold    INT          NOT NULL,
    status               VARCHAR(12)  NOT NULL,
    consecutive_failures INT          NOT NULL DEFAULT 0,
    fault                VARCHAR(12)  NOT NULL DEFAULT 'NONE',
    last_check_at        TIMESTAMPTZ,
    version              BIGINT       NOT NULL DEFAULT 0
);

CREATE TABLE tickets (
    id             BIGSERIAL PRIMARY KEY,
    title          VARCHAR(160)  NOT NULL,
    description    VARCHAR(4000) NOT NULL DEFAULT '',
    category       VARCHAR(30)   NOT NULL,
    priority       VARCHAR(10)   NOT NULL,
    level          VARCHAR(2)    NOT NULL DEFAULT 'N1',
    status         VARCHAR(12)   NOT NULL DEFAULT 'NOVO',
    origin         VARCHAR(12)   NOT NULL DEFAULT 'MANUAL',
    requester_id   BIGINT        NOT NULL REFERENCES users (id),
    assignee_id    BIGINT        REFERENCES users (id),
    service_id     BIGINT        REFERENCES monitored_services (id),
    created_at     TIMESTAMPTZ   NOT NULL,
    paused_at      TIMESTAMPTZ,
    paused_seconds BIGINT        NOT NULL DEFAULT 0,
    resolved_at    TIMESTAMPTZ,
    root_cause     VARCHAR(2000),
    version        BIGINT        NOT NULL DEFAULT 0
);

CREATE TABLE ticket_events (
    id        BIGSERIAL PRIMARY KEY,
    ticket_id BIGINT        NOT NULL REFERENCES tickets (id) ON DELETE CASCADE,
    at        TIMESTAMPTZ   NOT NULL,
    actor_id  BIGINT        NOT NULL REFERENCES users (id),
    kind      VARCHAR(20)   NOT NULL,
    note      VARCHAR(2000) NOT NULL DEFAULT ''
);

CREATE TABLE health_checks (
    id         BIGSERIAL PRIMARY KEY,
    service_id BIGINT       NOT NULL REFERENCES monitored_services (id) ON DELETE CASCADE,
    at         TIMESTAMPTZ  NOT NULL,
    ok         BOOLEAN      NOT NULL,
    latency_ms INT          NOT NULL,
    message    VARCHAR(255) NOT NULL DEFAULT ''
);

CREATE TABLE runbooks (
    id           BIGSERIAL PRIMARY KEY,
    name         VARCHAR(80)  NOT NULL UNIQUE,
    action       VARCHAR(20)  NOT NULL,
    description  VARCHAR(255) NOT NULL DEFAULT '',
    max_attempts INT          NOT NULL
);

CREATE TABLE runbook_executions (
    id         BIGSERIAL PRIMARY KEY,
    ticket_id  BIGINT       NOT NULL REFERENCES tickets (id) ON DELETE CASCADE,
    runbook_id BIGINT       NOT NULL REFERENCES runbooks (id),
    attempt    INT          NOT NULL,
    success    BOOLEAN      NOT NULL,
    at         TIMESTAMPTZ  NOT NULL,
    output     VARCHAR(255) NOT NULL DEFAULT ''
);

CREATE INDEX ix_tickets_status_priority ON tickets (status, priority);
CREATE INDEX ix_tickets_requester ON tickets (requester_id);
CREATE INDEX ix_tickets_service ON tickets (service_id);
CREATE INDEX ix_events_ticket ON ticket_events (ticket_id, id);
CREATE INDEX ix_checks_service_at ON health_checks (service_id, at DESC);
CREATE INDEX ix_runs_ticket ON runbook_executions (ticket_id, id);
