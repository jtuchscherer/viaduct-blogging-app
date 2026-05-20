import type { AIHealth, RephraseTone } from '../types';

interface RephraseControlsProps {
  aiHealth: AIHealth | null;
  tone: RephraseTone;
  onToneChange: (tone: RephraseTone) => void;
  onRephrase: () => void;
  rephrasing: boolean;
  /** True while the surrounding form is submitting — disables all controls. */
  formLoading: boolean;
  contentEmpty: boolean;
}

/**
 * Tone selector + Rephrase button shown above the rich-text editor.
 * Renders nothing until the AI health check resolves.
 * Shows an "Ollama offline" hint and disables controls when Ollama is unreachable.
 */
export default function RephraseControls({
  aiHealth,
  tone,
  onToneChange,
  onRephrase,
  rephrasing,
  formLoading,
  contentEmpty,
}: RephraseControlsProps) {
  const aiAvailable = aiHealth?.ollamaReachable ?? false;
  const busy = formLoading || rephrasing;

  return (
    <div style={{ display: 'flex', gap: '0.5rem', alignItems: 'center' }}>
      {aiHealth !== null && !aiAvailable && (
        <span style={{ fontSize: '0.75rem', color: '#999', fontStyle: 'italic' }}>
          Ollama offline
        </span>
      )}
      <select
        value={tone}
        onChange={(e) => onToneChange(e.target.value as RephraseTone)}
        disabled={busy || !aiAvailable}
        style={{ fontSize: '0.85rem', padding: '0.25rem 0.5rem' }}
      >
        <option value="PROFESSIONAL">Professional</option>
        <option value="CASUAL">Casual</option>
        <option value="CONCISE">Concise</option>
      </select>
      <button
        type="button"
        onClick={onRephrase}
        disabled={busy || contentEmpty || !aiAvailable}
        className="btn-secondary"
        style={{ fontSize: '0.85rem', padding: '0.25rem 0.75rem' }}
      >
        {rephrasing ? 'Rephrasing…' : '✨ Rephrase'}
      </button>
    </div>
  );
}
