export default function Select({ value, onChange, options, width = 180 }) {
  return (
    <select
      value={value}
      onChange={(e) => onChange(e.target.value)}
      style={{
        padding: '0.7rem 0.9rem',
        borderRadius: 12,
        background: 'rgba(30,41,59,0.5)',
        border: '1px solid rgba(148,163,184,0.2)',
        color: '#F8FAFC',
        minWidth: width,
      }}
    >
      {options.map((o) => (
        <option key={o.value} value={o.value}>
          {o.label}
        </option>
      ))}
    </select>
  );
}
