/** Matches the backend's NotFoundException messages, e.g. "Post not found: <id>". */
const NOT_FOUND = /not found/i;

/** A GraphQL error as it reaches the client, whatever wrapper Apollo puts around it. */
interface ErrorWithMessages {
  message?: string;
  errors?: { message?: string }[];
}

/**
 * Collects the individual GraphQL error messages, preferring them over the wrapper's summary.
 *
 * Apollo's `CombinedGraphQLErrors` carries a summary in `message` ("One error occurred") and the
 * real messages in `errors`, so reading `message` alone would tell us nothing about the cause.
 */
function messagesOf(error: ErrorWithMessages): string[] {
  if (Array.isArray(error.errors)) {
    return error.errors.map((e) => e.message ?? '').filter(Boolean);
  }
  return error.message ? [error.message] : [];
}

/**
 * Whether a failed query means "this does not exist, for you" rather than "something broke".
 *
 * A draft the viewer may not see is reported by the backend as a not-found error, identical to a
 * post that never existed, so the response cannot be used to work out whether a draft exists. The
 * UI has to keep that promise: showing the raw message would leak both the fact that the post is
 * there and the internals of the field that refused it.
 *
 * Every message must be a not-found for this to be true. With a mix, something else also went
 * wrong — an authorisation failure, say — and reporting "not found" would hide it.
 */
export function isNotFoundError(error: unknown): boolean {
  if (!error || typeof error !== 'object') return false;
  const messages = messagesOf(error as ErrorWithMessages);
  return messages.length > 0 && messages.every((m) => NOT_FOUND.test(m));
}
