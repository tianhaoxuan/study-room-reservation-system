ALTER TABLE `user`
    ADD COLUMN role VARCHAR(20) NOT NULL DEFAULT 'USER'
        COMMENT 'USER normal user, ADMIN administrator'
        AFTER status;

CREATE INDEX idx_user_role ON `user` (role);
