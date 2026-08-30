# Schema migrations

Numbered, ordered, applied identically by every node. Task 0.7 / defence risk R12.

## Why this exists

`ddl-auto=update` adds tables and nullable columns, and quietly declines to do
anything else. It will not relax a `NOT NULL`, will not add a check constraint to
a populated table, and will not drop anything. That is not a conservative
migration strategy — it is a strategy that leaves each node's schema shaped by
the accident of what that node happened to run and in what order.

For a system whose central claim is that N independent branch databases hold the
same data, that is indefensible, and a panel is entitled to say so. B66, B77 and
B85 were all the same failure: a schema change the application believed in and
the database had never made.

## Rules

1. **Never edit an applied migration.** Flyway checksums them; changing one makes
   every node that already ran it refuse to start. Add `V{n+1}` instead.
2. **One concern per file**, named for what it does, not for when it was written.
3. **Every entity change gets a migration in the same commit.** `ddl-auto=validate`
   will fail the boot if you forget, which is the point.
4. **Migrations run before the app serves traffic**, so they must be safe on a
   populated database. Adding a NOT NULL column means: add nullable, backfill,
   then set NOT NULL — three statements, one file.

## Turning it on and off

Env-driven, so it is reversible without a rebuild:

| | Flyway | Hibernate |
|---|---|---|
| Target | `FLYWAY_ENABLED=true` | `DDL_AUTO=validate` |
| Fallback | `FLYWAY_ENABLED=false` | `DDL_AUTO=update` |

## V1 is a baseline, not a design

`V1__baseline.sql` is a dump of the schema as `ddl-auto` left it on 30 August
2026. It is not tidy and it is not meant to be read as an intended design — it is
the honest starting point, so that everything after it is deliberate.
