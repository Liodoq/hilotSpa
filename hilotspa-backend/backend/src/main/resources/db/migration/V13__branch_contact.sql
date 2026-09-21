-- V13 - a telephone number per branch (3.37).
--
-- The reminder email tells a client to ring the branch if they cannot come,
-- and until now there was no number to give them. SPA_PHONE exists, but it is
-- one environment variable per NODE, and a node holds both branches' data - so
-- an email about a Daraga visit sent from a node configured with Bulan's
-- number would print the wrong one. The number belongs to the branch, not to
-- the machine.
--
-- Nullable, and left empty by this migration. A blank number means "nobody has
-- told us yet", and the email simply omits the line rather than printing an
-- empty label. Inventing a number here would be worse than having none.

ALTER TABLE branch ADD COLUMN IF NOT EXISTS contact_number varchar(40);
