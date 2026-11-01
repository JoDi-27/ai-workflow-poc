-- The engine's schema. Flyway creates it from spring.flyway.schemas; the engine core's V2 adds the
-- tables from docs/engine/schema-draft.sql (spec 017).
COMMENT ON SCHEMA engine IS 'The engine''s state, events, and evidence (docs/engine/design.md section 4)';
