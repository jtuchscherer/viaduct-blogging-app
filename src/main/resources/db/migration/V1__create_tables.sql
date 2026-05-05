CREATE TABLE IF NOT EXISTS users (
    id          UUID        PRIMARY KEY,
    username    VARCHAR(100) NOT NULL,
    email       VARCHAR(255) NOT NULL,
    name        VARCHAR(255) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    salt        VARCHAR(255) NOT NULL,
    is_admin    BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT users_username_unique UNIQUE (username)
);

CREATE TABLE IF NOT EXISTS posts (
    id          UUID        PRIMARY KEY,
    title       VARCHAR(500) NOT NULL,
    content     TEXT        NOT NULL,
    author_id   UUID        NOT NULL REFERENCES users(id),
    post_type   VARCHAR(50) NOT NULL DEFAULT 'BLOG_POST',
    created_at  TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS comments (
    id          UUID        PRIMARY KEY,
    content     TEXT        NOT NULL,
    post_id     UUID        NOT NULL REFERENCES posts(id),
    author_id   UUID        NOT NULL REFERENCES users(id),
    created_at  TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS likes (
    id          UUID        PRIMARY KEY,
    post_id     UUID        NOT NULL REFERENCES posts(id),
    user_id     UUID        NOT NULL REFERENCES users(id),
    created_at  TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT likes_post_user_unique UNIQUE (post_id, user_id)
);

CREATE TABLE IF NOT EXISTS checked_list_items (
    id          VARCHAR(36) PRIMARY KEY,
    post_id     VARCHAR(36) NOT NULL,
    text        TEXT        NOT NULL,
    checked     BOOLEAN     NOT NULL DEFAULT FALSE,
    position    INTEGER     NOT NULL DEFAULT 0,
    created_at  VARCHAR(50) NOT NULL
);

CREATE TABLE IF NOT EXISTS post_views (
    post_id     VARCHAR(36) PRIMARY KEY,
    view_count  BIGINT      NOT NULL DEFAULT 0
);
