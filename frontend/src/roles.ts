export const ROLE_LABEL: Record<string, string> = {
  MASTER: 'Administrador',
  OWNER: 'Dono da empresa',
  MANAGER: 'Gerente',
  OPERATOR: 'Funcionário'
};

export function roleLabel(role: string | undefined | null): string {
  if (!role) return 'Usuário';
  return ROLE_LABEL[role] ?? role;
}

export const ROLE_OPTIONS_ADMIN = [
  { value: 'MASTER', label: 'Administrador' },
  { value: 'OWNER', label: 'Dono da empresa' },
  { value: 'MANAGER', label: 'Gerente' },
  { value: 'OPERATOR', label: 'Funcionário' }
];
