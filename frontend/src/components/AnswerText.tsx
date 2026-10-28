import { Fragment, type ReactNode } from 'react';

const INLINE = /(\*\*[^*]+\*\*|\[\d+(?:\s*,\s*\d+)*\])/g;
const LIST_ITEM = /^\s*(?:[-*•]|\d+\.)\s+/;

interface Props {
  text: string;
  onCite: (n: number) => void;
}

/** Renders the model's answer: paragraphs, bullet lists, **bold** and clickable [n] citations. */
export default function AnswerText({ text, onCite }: Props) {
  const blocks: ReactNode[] = [];
  let list: string[] = [];

  const inline = (line: string) =>
    line.split(INLINE).map((part, i) => {
      if (part.startsWith('**') && part.endsWith('**')) return <strong key={i}>{part.slice(2, -2)}</strong>;
      const cite = part.match(/^\[(.+)]$/);
      if (cite) {
        return (
          <Fragment key={i}>
            {cite[1].split(',').map((n) => (
              <button key={n} className="cite" onClick={() => onCite(Number(n.trim()))} title={`Show source ${n.trim()}`}>
                {n.trim()}
              </button>
            ))}
          </Fragment>
        );
      }
      return <Fragment key={i}>{part}</Fragment>;
    });

  const flushList = () => {
    if (list.length) {
      blocks.push(
        <ul key={`l${blocks.length}`}>
          {list.map((item, i) => (
            <li key={i}>{inline(item)}</li>
          ))}
        </ul>,
      );
      list = [];
    }
  };

  for (const line of text.split('\n')) {
    if (LIST_ITEM.test(line)) {
      list.push(line.replace(LIST_ITEM, ''));
    } else {
      flushList();
      if (line.trim()) blocks.push(<p key={`p${blocks.length}`}>{inline(line)}</p>);
    }
  }
  flushList();

  return <div className="answer-text">{blocks}</div>;
}
