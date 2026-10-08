-- Stop retaining Google emails in plaintext; linking uses google_sub only.
UPDATE google_account_links SET email = NULL WHERE email IS NOT NULL;
