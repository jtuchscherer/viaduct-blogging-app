interface PaginationControlsProps {
  /** Zero-based offset of the current page window */
  offset: number;
  pageSize: number;
  totalCount: number;
  /** Label for the empty-state message, e.g. "posts", "users" */
  entityName: string;
  onPrev: () => void;
  onNext: () => void;
}

/**
 * Shared pagination strip used by all admin list pages.
 *
 * Shows a "Showing X–Y of Z" summary alongside Previous / Next buttons,
 * and an empty-state label when totalCount is 0.
 */
export default function PaginationControls({
  offset,
  pageSize,
  totalCount,
  entityName,
  onPrev,
  onNext,
}: PaginationControlsProps) {
  const currentPage = Math.floor(offset / pageSize) + 1;
  const totalPages = Math.ceil(totalCount / pageSize);
  const rangeStart = totalCount === 0 ? 0 : offset + 1;
  const rangeEnd = Math.min(offset + pageSize, totalCount);

  return (
    <div className="admin-pagination">
      <span data-testid="admin-page-info">
        {totalCount === 0
          ? `No ${entityName}`
          : `Showing ${rangeStart}–${rangeEnd} of ${totalCount}`}
      </span>
      <div className="admin-pagination-controls">
        <button
          data-testid="btn-prev-page"
          className="btn-page"
          onClick={onPrev}
          disabled={offset === 0}
        >
          Previous
        </button>
        <span className="admin-page-number">
          Page {currentPage} of {totalPages || 1}
        </span>
        <button
          data-testid="btn-next-page"
          className="btn-page"
          onClick={onNext}
          disabled={offset + pageSize >= totalCount}
        >
          Next
        </button>
      </div>
    </div>
  );
}
