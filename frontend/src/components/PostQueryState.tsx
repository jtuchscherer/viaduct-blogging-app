import type { ReactNode } from 'react';
import { isNotFoundError } from '../utils/errors';

/**
 * The loading / not-found / error rendering shared by the two pages that read a single post
 * through `node(id)`: the detail page and the edit page.
 *
 * Shared rather than copied because the not-found branch is a promise about drafts, not a
 * convenience. A draft the viewer may not see comes back as a not-found error, and the raw error
 * text would confirm that the post exists. If one of the two pages were to lose that branch, that
 * page alone would leak — so both read the decision from here.
 *
 * Returns null when the query has a post to render, which is the caller's cue to carry on.
 */
export function renderPostQueryState({
  loading,
  error,
  missing,
}: {
  loading: boolean;
  error: unknown;
  missing: boolean;
}): ReactNode | null {
  if (loading) {
    return <div className="container"><p>Loading post...</p></div>;
  }

  // A post the viewer may not see and a post that is absent read the same, deliberately.
  if (isNotFoundError(error)) {
    return <div className="container"><p>Post not found</p></div>;
  }

  // Before `missing`, not after: a failed query leaves no data either, and folding the two
  // together would report every genuine failure as a missing post.
  if (error) {
    return (
      <div className="container">
        <div className="error-message">Error: {(error as Error).message}</div>
      </div>
    );
  }

  if (missing) {
    return <div className="container"><p>Post not found</p></div>;
  }

  return null;
}
