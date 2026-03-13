export default function Toolbar({ left, right }) {
  return (
    <div
      style={{
        display: 'flex',
        justifyContent: 'space-between',
        alignItems: 'center',
        gap: '1rem',
        padding: '1rem',
        border: '1px solid var(--border)',
        borderRadius: 14,
        background: 'rgba(15, 23, 42, 0.6)',
      }}
    >
      <div style={{ display: 'flex', gap: '0.5rem', flexWrap: 'wrap', alignItems: 'center' }}>{left}</div>
      <div style={{ display: 'flex', gap: '0.5rem', flexWrap: 'wrap', alignItems: 'center' }}>{right}</div>
    </div>
  );
}
