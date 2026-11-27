-- Country of the connection as Cloudflare resolves it (two-letter code, e.g. TH, ES). The locale tells
-- the phone's language, not where it is (en_US is used everywhere). The IP itself is never stored.
ALTER TABLE installs ADD COLUMN country TEXT;
