import { describe, expect, it } from 'vitest';
import { passwordConfirmError, passwordPolicyError } from './password';

describe('passwordPolicyError', () => {
  it('accepts a password with letters and digits', () => {
    expect(passwordPolicyError('TorqMind@123')).toBeNull();
    expect(passwordPolicyError('Manager@123')).toBeNull();
  });

  it('rejects short passwords', () => {
    expect(passwordPolicyError('Ab1')).toBe('A senha deve ter ao menos 8 caracteres.');
  });

  it('rejects passwords without digits or letters', () => {
    expect(passwordPolicyError('SomenteLetras')).toBe('A senha deve conter letras e números.');
    expect(passwordPolicyError('12345678')).toBe('A senha deve conter letras e números.');
  });
});

describe('passwordConfirmError', () => {
  it('requires confirmation to match', () => {
    expect(passwordConfirmError('Senha1234', 'Senha1234')).toBeNull();
    expect(passwordConfirmError('Senha1234', 'Outra1234')).toBe('A confirmação não confere com a nova senha.');
  });
});
