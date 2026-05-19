import { useState, useEffect } from 'react';

interface AIHealth {
  ollamaReachable: boolean;
  chatModel: string;
  embeddingModel: string;
}

/**
 * Fetches /health/ai once on mount. Returns null while loading.
 * Used to conditionally show/hide AI controls.
 */
export function useAIHealth(): AIHealth | null {
  const [health, setHealth] = useState<AIHealth | null>(null);

  useEffect(() => {
    const apiUrl = import.meta.env.VITE_API_URL ?? 'http://localhost:8080';
    fetch(`${apiUrl}/health/ai`)
      .then((r) => r.json())
      .then((data) => setHealth(data))
      .catch(() => setHealth({ ollamaReachable: false, chatModel: '', embeddingModel: '' }));
  }, []);

  return health;
}
