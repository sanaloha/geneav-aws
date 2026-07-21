-- Global session invalidation on password change.
--
-- Sessions live in memory (HttpSession), so there is no session store to purge.
-- Instead each session records when it was authenticated, and a session issued
-- before this timestamp is rejected on its next request. That logs out every
-- other browser after a password reset.
--
-- Nullable on purpose: existing sessions predate the stamp, and a NULL here
-- means "never changed", so a deploy does not sign everybody out.

ALTER TABLE account ADD COLUMN credentials_changed_at TIMESTAMP WITH TIME ZONE;
