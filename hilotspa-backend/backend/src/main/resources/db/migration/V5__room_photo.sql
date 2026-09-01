-- Task: the client now picks a room, so a room needs a face.
--
-- A FILENAME, not an id: ids regenerate on every reseed and would orphan every
-- picture. Same contract as massage.image_name (V3). Nullable because a room
-- without a photo is normal and renders a neutral tile.
ALTER TABLE room ADD COLUMN IF NOT EXISTS image_name varchar(255);
