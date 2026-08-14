import { describe, expect, it } from 'vitest';
import { taskContextFromPath } from './voice';

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
  });
});
