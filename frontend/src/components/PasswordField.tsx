import React, { useState } from 'react';

type Props = {
  value: string;
  onChange: (value: string) => void;
  placeholder?: string;
  autoComplete?: string;
  required?: boolean;
  name?: string;
  id?: string;
};

export default function PasswordField({
  value,
  onChange,
  placeholder,
  autoComplete,
  required,
  name,
  id
}: Props) {
  const [show, setShow] = useState(false);
  const label = show ? 'Ocultar senha' : 'Mostrar senha';
  return (
    <div className="password-field">
      <input
        id={id}
        name={name}
        type={show ? 'text' : 'password'}
        value={value}
        onChange={(e) => onChange(e.target.value)}
        autoComplete={autoComplete}
        placeholder={placeholder}
        required={required}
      />
      <button
        type="button"
        className="password-toggle"
        onClick={() => setShow((current) => !current)}
        aria-label={label}
        aria-pressed={show}
        title={label}
      >
        {show ? (
          <svg viewBox="0 0 24 24" width="20" height="20" aria-hidden="true">
            <path
              fill="currentColor"
              d="M12 6c-5 0-9.3 3.1-11 7.5C2.7 17.9 7 21 12 21s9.3-3.1 11-7.5C21.3 9.1 17 6 12 6zm0 12.5c-3.6 0-6.8-2.2-8.3-5.5C5.2 9.7 8.4 7.5 12 7.5s6.8 2.2 8.3 5.5c-1.5 3.3-4.7 5.5-8.3 5.5zM12 9a4 4 0 1 0 .001 8.001A4 4 0 0 0 12 9zm0 6.5a2.5 2.5 0 1 1 0-5 2.5 2.5 0 0 1 0 5z"
            />
            <path fill="currentColor" d="M3.3 4.7 4.7 3.3l16 16-1.4 1.4z" />
          </svg>
        ) : (
          <svg viewBox="0 0 24 24" width="20" height="20" aria-hidden="true">
            <path
              fill="currentColor"
              d="M12 6c-5 0-9.3 3.1-11 7.5C2.7 17.9 7 21 12 21s9.3-3.1 11-7.5C21.3 9.1 17 6 12 6zm0 12.5c-3.6 0-6.8-2.2-8.3-5.5C5.2 9.7 8.4 7.5 12 7.5s6.8 2.2 8.3 5.5c-1.5 3.3-4.7 5.5-8.3 5.5zM12 9a4 4 0 1 0 .001 8.001A4 4 0 0 0 12 9zm0 6.5a2.5 2.5 0 1 1 0-5 2.5 2.5 0 0 1 0 5z"
            />
          </svg>
        )}
      </button>
    </div>
  );
}
