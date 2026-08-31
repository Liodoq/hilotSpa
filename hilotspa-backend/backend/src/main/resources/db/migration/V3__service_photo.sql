-- Task 2.40 - a treatment carries the FILENAME of its photograph.
--
-- The screens used to build the image path from the service's id. Ids are
-- regenerated every time the database is rebuilt, so the spa's real photographs
-- would have needed renaming after every reset, and the files would have been
-- called things like "a3f4b2c1-9d8e-4f10-....jpg" that nobody could match to a
-- treatment. A filename survives a rebuild and a human can read it.
--
-- Nullable on purpose: most treatments have no photograph yet, and the screens
-- fall back to a plain tinted block rather than a broken image. That is honest
-- about a photo the spa has not supplied.

ALTER TABLE massage
  ADD COLUMN IF NOT EXISTS image_name varchar(255);
