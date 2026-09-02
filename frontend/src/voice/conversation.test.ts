import { describe, expect, it } from 'vitest';
import { confirmationIntent, fieldsFromSpeech, matchAmbiguityOption } from './conversation';

describe('confirmationIntent', () => {
  it('detects yes and no', () => {
    expect(confirmationIntent('sim, pode fazer')).toBe('confirm');
    expect(confirmationIntent('confirmo')).toBe('confirm');
    expect(confirmationIntent('não, cancela')).toBe('deny');
  });
});

describe('matchAmbiguityOption', () => {
  it('matches by label or ordinal', () => {
    const amb = [{
      field: 'branchReference',
      query: 'posto',
      options: [
        { key: 'b:1', label: 'Posto Centro' },
        { key: 'b:2', label: 'Posto Norte' }
      ]
    }];
    expect(matchAmbiguityOption('posto centro', amb)).toEqual({ field: 'branchReference', key: 'b:1' });
    expect(matchAmbiguityOption('a segunda', amb)).toEqual({ field: 'branchReference', key: 'b:2' });
  });
});

describe('fieldsFromSpeech', () => {
  it('fills title and time', () => {
    const fields = fieldsFromSpeech('Conferir extintores às 8', ['title', 'startTime']);
    expect(fields.title).toBe('Conferir extintores às 8');
    expect(fields.startTime).toBe('08:00');
  });
});
