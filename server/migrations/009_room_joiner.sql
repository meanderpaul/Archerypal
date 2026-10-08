-- Track challenged player on remote match rooms for completion validation

ALTER TABLE rooms ADD COLUMN joiner_friend_code TEXT;
