-- Phase 28: draft / publish.
--
-- Both post types share the posts table, so one status column covers blog posts and checklists.
--
-- The DEFAULT and the UPDATE below are what keep an existing blog live through this migration:
-- every row that predates drafts must come out PUBLISHED. Getting this wrong unpublishes the
-- entire site, so the backfill is explicit rather than relying on the column default alone.

ALTER TABLE posts ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'PUBLISHED';

-- Null while a draft. Cleared again on unpublish, so it means "published at, if currently
-- published" rather than "first published at".
ALTER TABLE posts ADD COLUMN published_at TIMESTAMP NULL;

-- Existing posts were all visible, so they are published, and their creation time is the best
-- available publication time.
UPDATE posts SET status = 'PUBLISHED' WHERE status IS NULL OR status = '';
UPDATE posts SET published_at = created_at WHERE status = 'PUBLISHED' AND published_at IS NULL;

-- Feeds filter on status and order published posts by published_at.
CREATE INDEX idx_posts_status ON posts (status);
CREATE INDEX idx_posts_status_published_at ON posts (status, published_at);
