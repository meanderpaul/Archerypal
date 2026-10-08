-- Coarse author location (rounded to ~7 miles) on public posts for the regional feed.

ALTER TABLE posts ADD COLUMN latitude REAL;
ALTER TABLE posts ADD COLUMN longitude REAL;
