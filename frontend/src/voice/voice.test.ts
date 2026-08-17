import { qualityAnalysisPath } from '../fuel';
import { describe, expect, it } from 'vitest';
import { browserSpeechRecognitionConstructor, browserSpeechRecognitionSupported, taskContextFromPath } from './voice';

describe('taskContextFromPath', () => {
  it('detects routine and occurrence ids', () => {
    expect(taskContextFromPath('/routines/12', 'Aferição')).toEqual({
      currentTaskType: 'ROUTINE_RUN',
      currentTaskId: 12,
      currentTaskTitle: 'Aferição'
    });
    expect(taskContextFromPath('/occurrences/3')).toMatchObject({
      currentTaskType: 'OCCURRENCE',
      currentTaskId: 3
    });
    expect(taskContextFromPath('/routines')).toEqual({});
    expect(taskContextFromPath('/occurrences/new/fuel-quality')).toEqual({});
  });
});

describe('quality analysis route', () => {
  it('keeps fuel as a query param', () => {
    expect(qualityAnalysisPath('DIESEL_S10')).toBe('/occurrences/new/fuel-quality?fuel=DIESEL_S10');
    expect(qualityAnalysisPath()).toBe('/occurrences/new/fuel-quality');
  });
});

describe('browser speech recognition', () => {
  it('accepts the standard and prefixed browser implementations', () => {
    class RecognitionMock {}
    expect(browserSpeechRecognitionSupported({ SpeechRecognition: RecognitionMock })).toBe(true);
    expect(browserSpeechRecognitionConstructor({ webkitSpeechRecognition: RecognitionMock })).toBe(RecognitionMock);
    expect(browserSpeechRecognitionSupported({})).toBe(false);
  });
});
