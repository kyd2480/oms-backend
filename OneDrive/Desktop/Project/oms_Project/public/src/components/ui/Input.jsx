export default function Input({ value, onChange, placeholder, width = 260, type = 'text' }) {
  return (
    <input
      type={type}
      value={value}
      onChange={(e) => onChange(e.target.value)}
      placeholder={placeholder}
      style={{
        padding: '0.7rem 0.9rem',
        borderRadius: 12,
        background: 'rgba(30,41,59,0.5)',
        border: '1px solid rgba(148,163,184,0.2)',
        color: '#F8FAFC',
        minWidth: width,
      }}
    />
  );
}
