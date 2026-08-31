-- The client may ask for a woman or a man, and the spa honours it.
--
-- Hilot is close, hands-on work. A client who cannot say who they are
-- comfortable being treated by will not come back, and in this province that is
-- a matter of dignity rather than a nicety. So the preference is recorded on the
-- assessment and enforced twice: when times are offered, and again when the
-- therapist is actually assigned. A preference the screen respects and the write
-- path ignores is worse than no preference at all - the client is promised a
-- woman and then meets a man.
--
-- Both columns are NULLABLE, and null carries meaning in each case:
--   therapist.sex             - not recorded. That therapist is offered only to
--                               clients with NO preference, never guessed at.
--   forms.therapist_preference - the client did not express one. Everyone is
--                               eligible. There is deliberately no
--                               NO_PREFERENCE value: an absent preference is
--                               absent, not a third kind of choice.

ALTER TABLE therapist
  ADD COLUMN IF NOT EXISTS sex varchar(255);

ALTER TABLE forms
  ADD COLUMN IF NOT EXISTS therapist_preference varchar(255);
