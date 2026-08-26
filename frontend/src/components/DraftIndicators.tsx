/**
 * The two ways the UI says "this post is not published yet".
 *
 * A banner where the post is the whole page, a badge where it is one row in a list. They live
 * together because they are one idea in two sizes, and because the test ids they carry are the
 * contract the e2e suite reads.
 */

/**
 * Shown on a draft's own page, to whoever is entitled to see it.
 *
 * No author check: only the author and admins can load a draft at all, so anyone reading this has
 * already been let through.
 */
export function DraftBanner() {
  return (
    <div className="draft-banner" data-testid="draft-banner" role="status">
      <strong>Draft</strong> — only you can see this. Publish it when you are ready.
    </div>
  );
}

/** Marks one draft among a list of the author's posts. */
export function DraftBadge() {
  return (
    <span className="draft-badge" data-testid="draft-badge">
      Draft
    </span>
  );
}
