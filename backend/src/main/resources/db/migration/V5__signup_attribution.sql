-- Where a signup came from.
--
-- Until now the only thing recorded about an account was that it existed, so a
-- rise in traffic could never be traced to the channel that caused it. These
-- columns hold the standard UTM parameters plus the external referrer and the
-- first page the visitor landed on, captured once at signup.
--
-- All nullable, and deliberately so:
--   * every account created before this migration has no attribution at all;
--   * a direct visit (typed URL, bookmark) genuinely has no source, and NULL is
--     the honest representation of that, not an empty string;
--   * attribution must never be able to fail a signup, so the API accepts a
--     request with none of these fields and stores NULLs.
--
-- Widths are generous rather than tight because the values are supplied by the
-- browser and the backend truncates to fit instead of rejecting: a campaign URL
-- someone hand-edited should cost us a trimmed string, not a lost customer.

ALTER TABLE account ADD COLUMN utm_source   VARCHAR(128);
ALTER TABLE account ADD COLUMN utm_medium   VARCHAR(128);
ALTER TABLE account ADD COLUMN utm_campaign VARCHAR(128);
ALTER TABLE account ADD COLUMN utm_term     VARCHAR(128);
ALTER TABLE account ADD COLUMN utm_content  VARCHAR(128);

-- Full URL of the external page that linked here, so a referring domain can be
-- derived without a separate column.
ALTER TABLE account ADD COLUMN referrer     VARCHAR(512);

-- Path only (no host, no query) of the first geneav page the visitor saw. Tells
-- you which content earned the signup when there is no UTM tag to go on.
ALTER TABLE account ADD COLUMN landing_path VARCHAR(512);
