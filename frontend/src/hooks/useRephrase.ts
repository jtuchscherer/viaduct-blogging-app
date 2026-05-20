import { useState } from 'react';
import { gql } from '@apollo/client';
import { useMutation } from '@apollo/client/react';
import type { RephraseTone } from '../types';

// ── GraphQL ───────────────────────────────────────────────────────────────────

export const REPHRASE_CONTENT = gql`
  mutation RephraseContent($content: String!, $tone: RephraseTone) {
    rephraseContent(content: $content, tone: $tone) {
      rephrasedContent
    }
  }
`;

interface RephraseContentData {
  rephraseContent: { rephrasedContent: string };
}

// ── Hook ──────────────────────────────────────────────────────────────────────

export interface UseRephraseResult {
  tone: RephraseTone;
  setTone: (tone: RephraseTone) => void;
  /** Increment this value as the `key` prop on RichTextEditor to force a remount after rephrase. */
  contentKey: number;
  rephrasing: boolean;
  handleRephrase: () => void;
}

/**
 * Manages rephrase tone selection and executes the rephraseContent mutation.
 *
 * @param content  Current editor content — passed as the mutation variable.
 * @param setContent  Called with the rephrased text on success, triggering an editor update.
 * @param onError  Called with a human-readable message if the mutation fails.
 */
export function useRephrase(
  content: string,
  setContent: (content: string) => void,
  onError: (message: string) => void,
): UseRephraseResult {
  const [tone, setTone] = useState<RephraseTone>('PROFESSIONAL');
  const [contentKey, setContentKey] = useState(0);

  const [rephraseContent, { loading: rephrasing }] = useMutation<RephraseContentData>(
    REPHRASE_CONTENT,
    {
      onCompleted: (data) => {
        setContent(data.rephraseContent.rephrasedContent);
        setContentKey((k) => k + 1);
      },
      onError: (err) => onError(`Rephrase failed: ${err.message}`),
    },
  );

  const handleRephrase = () => {
    rephraseContent({ variables: { content, tone } });
  };

  return { tone, setTone, contentKey, rephrasing, handleRephrase };
}
