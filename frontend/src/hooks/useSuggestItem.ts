import { gql } from '@apollo/client';
import { useMutation } from '@apollo/client/react';

const SUGGEST_CHECKLIST_ITEM = gql`
  mutation SuggestChecklistItem($existingItems: [String!]!) {
    suggestChecklistItem(existingItems: $existingItems) {
      suggestedText
    }
  }
`;

interface SuggestItemResult {
  suggest: (existingItems: string[]) => Promise<string | null>;
  suggesting: boolean;
}

/**
 * Wraps the suggestChecklistItem mutation.
 * Returns suggest() to call the mutation and suggesting to reflect in-flight state.
 */
export function useSuggestItem(): SuggestItemResult {
  const [suggestMutation, { loading }] = useMutation<{
    suggestChecklistItem: { suggestedText: string };
  }>(SUGGEST_CHECKLIST_ITEM);

  const suggest = async (existingItems: string[]): Promise<string | null> => {
    const result = await suggestMutation({ variables: { existingItems } });
    return result.data?.suggestChecklistItem.suggestedText ?? null;
  };

  return { suggest, suggesting: loading };
}
