/** Máscaras de exibição. Não rejeitam RG nem documento estrangeiro. */

export function digitsOnly(value: string): string {
  return (value ?? '').replace(/\D/g, '');
}

export function formatCnpj(value: string): string {
  const d = digitsOnly(value).slice(0, 14);
  if (d.length <= 2) return d;
  if (d.length <= 5) return `${d.slice(0, 2)}.${d.slice(2)}`;
  if (d.length <= 8) return `${d.slice(0, 2)}.${d.slice(2, 5)}.${d.slice(5)}`;
  if (d.length <= 12) return `${d.slice(0, 2)}.${d.slice(2, 5)}.${d.slice(5, 8)}/${d.slice(8)}`;
  return `${d.slice(0, 2)}.${d.slice(2, 5)}.${d.slice(5, 8)}/${d.slice(8, 12)}-${d.slice(12)}`;
}

export function formatPersonDocument(value: string): string {
  const raw = value ?? '';
  if (/[A-Za-z]/.test(raw)) return raw;
  const d = digitsOnly(raw);
  if (d.length === 11) {
    return `${d.slice(0, 3)}.${d.slice(3, 6)}.${d.slice(6, 9)}-${d.slice(9)}`;
  }
  if (d.length === 14) return formatCnpj(d);
  return raw;
}

export function formatCep(value: string): string {
  const d = digitsOnly(value).slice(0, 8);
  if (d.length <= 5) return d;
  return `${d.slice(0, 5)}-${d.slice(5)}`;
}

export function formatPlate(value: string): string {
  return (value ?? '').toUpperCase().replace(/[^A-Z0-9-]/g, '').slice(0, 8);
}

export function formatUf(value: string): string {
  return (value ?? '').toUpperCase().replace(/[^A-Z]/g, '').slice(0, 2);
}

export function todayIso(timeZone = 'America/Sao_Paulo'): string {
  return new Intl.DateTimeFormat('en-CA', { timeZone, year: 'numeric', month: '2-digit', day: '2-digit' }).format(new Date());
}
