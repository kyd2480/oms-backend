export default function Button({ variant = 'primary', onClick, children, type = 'button' }) {
  const base = {
    padding: '0.7rem 0.95rem',
    borderRadius: 12,
    fontWeight: 800,
    cursor: 'pointer',
    border: '1px solid transparent',
    display: 'inline-flex',
    alignItems: 'center',
    gap: '0.5rem',
  };

  const styles = {
    primary: { ...base, background: 'linear-gradient(135deg, #3B82F6, #2563EB)', color: '#fff' },
    ghost: { ...base, background: 'rgba(30,41,59,0.35)', color: '#CBD5E1', border: '1px solid rgba(148,163,184,0.18)' },
    danger: { ...base, background: 'rgba(239,68,68,0.12)', color: '#FCA5A5', border: '1px solid rgba(239,68,68,0.25)' },
  };

  return (
    <button type={type} style={styles[variant] || styles.primary} onClick={onClick}>
      {children}
    </button>
  );
}
