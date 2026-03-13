export default function PageShell({ title, desc, actions, children }) {
  return (
    <div>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-end', gap: '1rem', marginBottom: '1.25rem' }}>
        <div>
          <h1 style={{ fontSize: '1.75rem', fontWeight: 800, marginBottom: '0.35rem' }}>{title}</h1>
          {desc && <p style={{ color: 'var(--text-dim)', fontSize: '0.95rem' }}>{desc}</p>}
        </div>
        <div style={{ display: 'flex', gap: '0.5rem', flexWrap: 'wrap' }}>{actions}</div>
      </div>
      {children}
    </div>
  );
}
