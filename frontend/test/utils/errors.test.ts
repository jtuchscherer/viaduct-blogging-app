import { describe, it, expect } from 'vitest'
import { isNotFoundError } from '../../src/utils/errors'

/**
 * A draft the viewer may not see comes back from the backend as a not-found error, not as a null
 * node, so the pages that read a single post have to tell "this does not exist (for you)" apart
 * from "something broke". Getting that wrong either leaks the existence of a draft or shows a raw
 * exception string to a reader who simply followed a stale link.
 */
describe('isNotFoundError', () => {
  it('recognises the backend NotFoundException message', () => {
    expect(isNotFoundError(new Error('Post not found: 0b1f'))).toBe(true)
  })

  it('recognises the message graphql-java wraps around it', () => {
    // The default exception handler prefixes the field path, so the raw text is not an exact match.
    expect(
      isNotFoundError(new Error('Exception while fetching data (/node) : Post not found: 0b1f')),
    ).toBe(true)
  })

  it('reads the messages of a combined GraphQL error rather than its summary', () => {
    expect(
      isNotFoundError({
        message: 'One error occurred',
        errors: [{ message: 'Post not found: 0b1f' }],
      }),
    ).toBe(true)
  })

  it('is false for an unrelated failure', () => {
    expect(isNotFoundError(new Error('Failed to fetch'))).toBe(false)
  })

  it('is false when only some of several errors are not-found', () => {
    // Reporting "not found" here would hide the authorisation failure from the reader.
    expect(
      isNotFoundError({
        message: 'Two errors occurred',
        errors: [{ message: 'Post not found: 0b1f' }, { message: 'You are not authorized' }],
      }),
    ).toBe(false)
  })

  it('is false when the error carries no message at all', () => {
    expect(isNotFoundError({ errors: [] })).toBe(false)
  })

  it('is false when there is no error', () => {
    expect(isNotFoundError(undefined)).toBe(false)
    expect(isNotFoundError(null)).toBe(false)
  })
})
