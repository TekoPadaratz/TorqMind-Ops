export function passwordPolicyError(password: string): string | null {
  if (!password || password.length < 8) {
    return 'A senha deve ter ao menos 8 caracteres.';
  }
  const hasLetter = [...password].some((ch) => /\p{L}/u.test(ch));
  const hasDigit = [...password].some((ch) => /\p{N}/u.test(ch));
  if (!hasLetter || !hasDigit) {
    return 'A senha deve conter letras e números.';
  }
  return null;
}

export function passwordConfirmError(password: string, confirm: string): string | null {
  const policy = passwordPolicyError(password);
  if (policy) return policy;
  if (password !== confirm) {
    return 'A confirmação não confere com a nova senha.';
  }
  return null;
}
