import { useState, useEffect } from 'react';
import type { AIHealth } from '../types';

// Module-level promise — one fetch per browser session regardless of how many
// components call this hook. Resets to null on error so a subsequent mount retries.
let healthPromise: Promise<AIHealth> | null = null;

function fetchAIHealth(): Promise<AIHealth> {
  if (!healthPromise) {
    const apiUrl = import.meta.env.VITE_API_URL ?? 'http://localhost:8080';
    healthPromise = fetch(`${apiUrl}/health/ai`)
      .then((r) => r.json() as Promise<AIHealth>)
      .catch(() => {
        healthPromise = null; // allow retry after transient failure
        return { ollamaReachable: false, chatModel: '', embeddingModel: '' } satisfies AIHealth;
      });
  }
  return healthPromise;
}

/**
 * Fetches /health/ai once per browser session and returns the result.
 * Returns null while the request is in-flight.
 * Multiple components sharing this hook share a single fetch — no duplicate requests.
 */
export function useAIHealth(): AIHealth | null {
  const [health, setHealth] = useState<AIHealth | null>(null);

  useEffect(() => {
    fetchAIHealth().then(setHealth);
  }, []);

  return health;
}
