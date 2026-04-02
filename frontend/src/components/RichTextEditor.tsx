import { useEffect, useState, useCallback } from 'react';
import { LexicalComposer } from '@lexical/react/LexicalComposer';
import { RichTextPlugin } from '@lexical/react/LexicalRichTextPlugin';
import { ContentEditable } from '@lexical/react/LexicalContentEditable';
import { HistoryPlugin } from '@lexical/react/LexicalHistoryPlugin';
import { ListPlugin } from '@lexical/react/LexicalListPlugin';
import { LinkPlugin } from '@lexical/react/LexicalLinkPlugin';
import { OnChangePlugin } from '@lexical/react/LexicalOnChangePlugin';
import { useLexicalComposerContext } from '@lexical/react/LexicalComposerContext';
import { LexicalErrorBoundary } from '@lexical/react/LexicalErrorBoundary';
import { HeadingNode, QuoteNode, $createHeadingNode, $isHeadingNode } from '@lexical/rich-text';
import {
  ListNode,
  ListItemNode,
  $isListNode,
  INSERT_UNORDERED_LIST_COMMAND,
  INSERT_ORDERED_LIST_COMMAND,
  REMOVE_LIST_COMMAND,
} from '@lexical/list';
import { LinkNode, AutoLinkNode } from '@lexical/link';
import { CodeNode, CodeHighlightNode, $isCodeNode, $createCodeNode } from '@lexical/code';
import { $generateHtmlFromNodes, $generateNodesFromDOM } from '@lexical/html';
import { $setBlocksType } from '@lexical/selection';
import {
  $getSelection,
  $isRangeSelection,
  $findMatchingParent,
  $isRootOrShadowRoot,
  FORMAT_TEXT_COMMAND,
  $getRoot,
  $createParagraphNode,
  type LexicalNode,
  type EditorState,
  type LexicalEditor,
} from 'lexical';

// ── Helpers ──────────────────────────────────────────────────────────────────

/** Wrap legacy plain-text content in <p> tags so Lexical can parse it. */
function normalizeToHtml(content: string): string {
  if (!content) return '';
  const trimmed = content.trim();
  if (trimmed.startsWith('<')) return trimmed; // already HTML
  return trimmed
    .split('\n')
    .map((line) => {
      const escaped = line
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;');
      return `<p>${escaped || '<br>'}</p>`;
    })
    .join('');
}

// ── Theme ─────────────────────────────────────────────────────────────────────

const editorTheme = {
  text: {
    bold: 'editor-bold',
    italic: 'editor-italic',
    underline: 'editor-underline',
    code: 'editor-inline-code',
  },
  heading: {
    h1: 'editor-h1',
    h2: 'editor-h2',
    h3: 'editor-h3',
  },
  list: {
    ul: 'editor-ul',
    ol: 'editor-ol',
    listitem: 'editor-listitem',
    nested: { listitem: 'editor-nested-listitem' },
  },
  link: 'editor-link',
  code: 'editor-code-block',
};

// ── Toolbar ───────────────────────────────────────────────────────────────────

type BlockType = 'paragraph' | 'h1' | 'h2' | 'h3' | 'bullet' | 'number' | 'code' | 'other';

function Toolbar({ disabled }: { disabled?: boolean }) {
  const [editor] = useLexicalComposerContext();
  const [isBold, setIsBold] = useState(false);
  const [isItalic, setIsItalic] = useState(false);
  const [isUnderline, setIsUnderline] = useState(false);
  const [blockType, setBlockType] = useState<BlockType>('paragraph');

  useEffect(() => {
    return editor.registerUpdateListener(({ editorState }) => {
      editorState.read(() => {
        const selection = $getSelection();
        if (!$isRangeSelection(selection)) return;

        setIsBold(selection.hasFormat('bold'));
        setIsItalic(selection.hasFormat('italic'));
        setIsUnderline(selection.hasFormat('underline'));

        const anchorNode = selection.anchor.getNode();
        const element =
          anchorNode.getKey() === 'root'
            ? anchorNode
            : ($findMatchingParent(anchorNode, (n) => {
                const parent = n.getParent();
                return parent !== null && $isRootOrShadowRoot(parent);
              }) ?? anchorNode.getTopLevelElementOrThrow());

        if ($isHeadingNode(element)) {
          setBlockType(element.getTag() as BlockType);
        } else if ($isListNode(element)) {
          setBlockType(element.getListType() === 'bullet' ? 'bullet' : 'number');
        } else if ($isCodeNode(element)) {
          setBlockType('code');
        } else {
          setBlockType('paragraph');
        }
      });
    });
  }, [editor]);

  const formatText = (format: 'bold' | 'italic' | 'underline') => {
    editor.dispatchCommand(FORMAT_TEXT_COMMAND, format);
  };

  const formatBlock = (type: 'paragraph' | 'h1' | 'h2' | 'h3' | 'code') => {
    editor.update(() => {
      const selection = $getSelection();
      if (!$isRangeSelection(selection)) return;
      if (blockType === type) {
        $setBlocksType(selection, () => $createParagraphNode());
      } else if (type === 'h1') {
        $setBlocksType(selection, () => $createHeadingNode('h1'));
      } else if (type === 'h2') {
        $setBlocksType(selection, () => $createHeadingNode('h2'));
      } else if (type === 'h3') {
        $setBlocksType(selection, () => $createHeadingNode('h3'));
      } else if (type === 'code') {
        $setBlocksType(selection, () => $createCodeNode());
      } else {
        $setBlocksType(selection, () => $createParagraphNode());
      }
    });
  };

  const toggleList = (type: 'bullet' | 'number') => {
    if (blockType === type) {
      editor.dispatchCommand(REMOVE_LIST_COMMAND, undefined);
    } else {
      editor.dispatchCommand(
        type === 'bullet' ? INSERT_UNORDERED_LIST_COMMAND : INSERT_ORDERED_LIST_COMMAND,
        undefined,
      );
    }
  };

  const btn = (active: boolean, onClick: () => void, title: string, label: string) => (
    <button
      type="button"
      className={`toolbar-btn${active ? ' active' : ''}`}
      onClick={onClick}
      disabled={disabled}
      title={title}
      aria-label={title}
      aria-pressed={active}
    >
      {label}
    </button>
  );

  return (
    <div className="editor-toolbar">
      {btn(isBold, () => formatText('bold'), 'Bold', 'B')}
      {btn(isItalic, () => formatText('italic'), 'Italic', 'I')}
      {btn(isUnderline, () => formatText('underline'), 'Underline', 'U')}
      <span className="toolbar-divider" />
      {btn(blockType === 'h1', () => formatBlock('h1'), 'Heading 1', 'H1')}
      {btn(blockType === 'h2', () => formatBlock('h2'), 'Heading 2', 'H2')}
      {btn(blockType === 'h3', () => formatBlock('h3'), 'Heading 3', 'H3')}
      <span className="toolbar-divider" />
      {btn(blockType === 'bullet', () => toggleList('bullet'), 'Bullet list', '• List')}
      {btn(blockType === 'number', () => toggleList('number'), 'Numbered list', '1. List')}
      <span className="toolbar-divider" />
      {btn(blockType === 'code', () => formatBlock('code'), 'Code block', '</>')}
    </div>
  );
}

// ── Plugins ───────────────────────────────────────────────────────────────────

function HtmlOutputPlugin({ onChange }: { onChange: (html: string) => void }) {
  const stableOnChange = useCallback(
    (_state: EditorState, ed: LexicalEditor) => {
      ed.getEditorState().read(() => {
        onChange($generateHtmlFromNodes(ed, null));
      });
    },
    [onChange],
  );
  return <OnChangePlugin onChange={stableOnChange} ignoreSelectionChange />;
}

function EditablePlugin({ disabled }: { disabled?: boolean }) {
  const [editor] = useLexicalComposerContext();
  useEffect(() => {
    editor.setEditable(!disabled);
  }, [editor, disabled]);
  return null;
}

// ── Public component ──────────────────────────────────────────────────────────

interface RichTextEditorProps {
  /** Initial HTML (or legacy plain-text) content. Only read on mount. */
  initialContent: string;
  onChange: (html: string) => void;
  disabled?: boolean;
  placeholder?: string;
}

export default function RichTextEditor({
  initialContent,
  onChange,
  disabled,
  placeholder = 'Write your post content…',
}: RichTextEditorProps) {
  const initialConfig = {
    namespace: 'BlogEditor',
    nodes: [HeadingNode, QuoteNode, ListNode, ListItemNode, LinkNode, AutoLinkNode, CodeNode, CodeHighlightNode],
    theme: editorTheme,
    editorState: (editor: LexicalEditor) => {
      const html = normalizeToHtml(initialContent);
      if (!html) return;
      try {
        const dom = new DOMParser().parseFromString(html, 'text/html');
        const nodes = $generateNodesFromDOM(editor, dom);
        const root = $getRoot();
        root.clear();
        nodes.forEach((n: LexicalNode) => root.append(n));
      } catch (e) {
        console.error('RichTextEditor: failed to parse initial content', e);
      }
    },
    onError: (error: Error) => console.error('RichTextEditor error:', error),
  };

  return (
    <LexicalComposer initialConfig={initialConfig}>
      <div className={`editor-wrapper${disabled ? ' editor-disabled' : ''}`}>
        <Toolbar disabled={disabled} />
        <div className="editor-inner">
          <RichTextPlugin
            contentEditable={
              <ContentEditable
                className="editor-content"
                data-testid="rich-text-editor"
                aria-label="Post content"
              />
            }
            placeholder={<div className="editor-placeholder">{placeholder}</div>}
            ErrorBoundary={LexicalErrorBoundary}
          />
          <HistoryPlugin />
          <ListPlugin />
          <LinkPlugin />
          <HtmlOutputPlugin onChange={onChange} />
          <EditablePlugin disabled={disabled} />
        </div>
      </div>
    </LexicalComposer>
  );
}
