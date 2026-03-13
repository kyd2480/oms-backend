export default function Chip({ text, tone = 'blue' }) {
  const tones = {
    blue: { bg: 'rgba(59,130,246,0.14)', bd: 'rgba(59,130,246,0.30)', fg: '#93C5FD' },
    green: { bg: 'rgba(16,185,129,0.14)', bd: 'rgba(16,185,129,0.30)', fg: '#86EFAC' },
    amber: { bg: 'rgba(245,158,11,0.14)', bd: 'rgba(245,158,11,0.30)', fg: '#FCD34D' },
    red: { bg: 'rgba(239,68,68,0.14)', bd: 'rgba(239,68,68,0.30)', fg: '#FCA5A5' },
    purple: { bg: 'rgba(139,92,246,0.14)', bd: 'rgba(139,92,246,0.30)', fg: '#C4B5FD' },
  };
  const t = tones[tone] || tones.blue;
  return (
    <span
      style={{
        display: 'inline-flex',
        alignItems: 'center',
        padding: '0.25rem 0.6rem',
        borderRadius: 10,
        background: t.bg,
        border: `1px solid ${t.bd}`,
        color: t.fg,
        fontWeight: 800,
        fontSize: '0.78rem',
      }}
    >
      {text}
    </span>
  );
}
