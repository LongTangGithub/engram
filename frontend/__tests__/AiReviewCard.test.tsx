/**
 * Law 1 enforcement for AiReviewCard (MCQ), both modes.
 *
 * Pick-to-grade (primary, ENG-9): clicking an option commits a pick (calls onPick, not onGrade).
 * The correct answer / wrong-pick markers appear only AFTER the parent sets correctAnswer (which
 * only happens post-commit, from the server). Nothing flags the correct option before the click.
 *
 * Self-grade (fallback): the ENG-8b reveal → self-grade flow. correctAnswer must not be in the DOM
 * before Reveal; grade buttons appear only after Reveal + parent sets correctAnswer.
 */
import React from 'react';
import { render, screen, fireEvent } from '@testing-library/react';
import AiReviewCard from '../components/AiReviewCard';

const QUESTION = 'What does spaced repetition optimize?';
const OPTIONS = ['Short-term memorization', 'Long-term memory retention', 'Random guess', 'Passive re-reading'];
const CORRECT = 'Long-term memory retention';

// ── Pick-to-grade (primary) ──────────────────────────────────────────────────

describe('AiReviewCard — pick-to-grade (primary)', () => {
  function setup(
    picked: string | null = null,
    correctAnswer: string | null = null,
    onPick = jest.fn(),
  ) {
    return render(
      <AiReviewCard
        question={QUESTION}
        options={OPTIONS}
        correctAnswer={correctAnswer}
        picked={picked}
        onPick={onPick}
      />
    );
  }

  it('shows the question and all 4 options before any pick', () => {
    setup();
    expect(screen.getByText(QUESTION)).toBeInTheDocument();
    OPTIONS.forEach(opt => expect(screen.getByText(opt)).toBeInTheDocument());
  });

  it('Law 1: no correct or wrong indicator before the user picks', () => {
    setup();
    expect(screen.queryByTestId('correct-answer')).toBeNull();
    expect(screen.queryByTestId('wrong-answer')).toBeNull();
  });

  it('there is no Reveal button in pick mode (the click IS the commit)', () => {
    setup();
    expect(screen.queryByRole('button', { name: /reveal/i })).toBeNull();
  });

  it('clicking an option calls onPick with that option (not onGrade)', () => {
    const onPick = jest.fn();
    setup(null, null, onPick);
    fireEvent.click(screen.getByText('Random guess'));
    expect(onPick).toHaveBeenCalledTimes(1);
    expect(onPick).toHaveBeenCalledWith('Random guess');
  });

  it('after a WRONG pick + server reveal: correct option marked correct, wrong pick marked wrong', () => {
    const wrong = 'Short-term memorization';
    setup(wrong, CORRECT);
    expect(screen.getByTestId('correct-answer').textContent).toBe(CORRECT);
    expect(screen.getByTestId('wrong-answer').textContent).toBe(wrong);
  });

  it('after a CORRECT pick + server reveal: correct option marked, no wrong marker', () => {
    setup(CORRECT, CORRECT);
    expect(screen.getByTestId('correct-answer').textContent).toBe(CORRECT);
    expect(screen.queryByTestId('wrong-answer')).toBeNull();
  });

  it('committed but server not yet responded (correctAnswer null) shows no markers yet', () => {
    setup(CORRECT, null); // picked, awaiting grade
    expect(screen.queryByTestId('correct-answer')).toBeNull();
    expect(screen.queryByTestId('wrong-answer')).toBeNull();
  });
});

// ── Self-grade (fallback) ────────────────────────────────────────────────────

describe('AiReviewCard — self-grade fallback (reveal gate)', () => {
  function setup(correctAnswer: string | null = null, onReveal = jest.fn(), onGrade = jest.fn()) {
    return render(
      <AiReviewCard
        question={QUESTION}
        options={OPTIONS}
        correctAnswer={correctAnswer}
        selfGradeFallback
        onReveal={onReveal}
        onGrade={onGrade}
      />
    );
  }

  function renderRevealed(onGrade = jest.fn()) {
    return render(
      <AiReviewCard
        question={QUESTION}
        options={OPTIONS}
        correctAnswer={CORRECT}
        selfGradeFallback
        onReveal={jest.fn()}
        onGrade={onGrade}
      />
    );
  }

  it('shows the question before reveal', () => {
    setup();
    expect(screen.getByText(QUESTION)).toBeInTheDocument();
  });

  it('all 4 options are present before reveal', () => {
    setup();
    OPTIONS.forEach(opt => expect(screen.getByText(opt)).toBeInTheDocument());
  });

  it('no option is marked as correct before reveal', () => {
    setup();
    expect(screen.queryByTestId('correct-answer')).toBeNull();
  });

  it('grade buttons are NOT in the DOM before reveal', () => {
    setup();
    expect(screen.queryByTestId('grade-again')).toBeNull();
    expect(screen.queryByTestId('grade-hard')).toBeNull();
    expect(screen.queryByTestId('grade-good')).toBeNull();
    expect(screen.queryByTestId('grade-easy')).toBeNull();
  });

  it('Reveal button is present before reveal', () => {
    setup();
    expect(screen.getByRole('button', { name: /reveal/i })).toBeInTheDocument();
  });

  it('clicking Reveal calls onReveal', () => {
    const onReveal = jest.fn();
    setup(null, onReveal);
    fireEvent.click(screen.getByRole('button', { name: /reveal/i }));
    expect(onReveal).toHaveBeenCalledTimes(1);
  });

  it('after Reveal is clicked, Reveal button disappears', () => {
    setup();
    fireEvent.click(screen.getByRole('button', { name: /reveal/i }));
    expect(screen.queryByRole('button', { name: /reveal/i })).toBeNull();
  });

  it('correct option marked after reveal click + parent sets correctAnswer', () => {
    const { rerender } = setup(null);
    fireEvent.click(screen.getByRole('button', { name: /reveal/i }));
    rerender(
      <AiReviewCard
        question={QUESTION}
        options={OPTIONS}
        correctAnswer={CORRECT}
        selfGradeFallback
        onReveal={jest.fn()}
        onGrade={jest.fn()}
      />
    );
    expect(screen.getByTestId('correct-answer')).toBeInTheDocument();
    expect(screen.getByTestId('correct-answer').textContent).toBe(CORRECT);
  });

  it('grade buttons appear after reveal + correctAnswer is set', () => {
    const { rerender } = setup();
    fireEvent.click(screen.getByRole('button', { name: /reveal/i }));
    expect(screen.queryByTestId('grade-good')).toBeNull();
    rerender(
      <AiReviewCard
        question={QUESTION}
        options={OPTIONS}
        correctAnswer={CORRECT}
        selfGradeFallback
        onReveal={jest.fn()}
        onGrade={jest.fn()}
      />
    );
    expect(screen.getByTestId('grade-again')).toBeInTheDocument();
    expect(screen.getByTestId('grade-hard')).toBeInTheDocument();
    expect(screen.getByTestId('grade-good')).toBeInTheDocument();
    expect(screen.getByTestId('grade-easy')).toBeInTheDocument();
  });

  it('clicking a grade button calls onGrade with the correct rating', () => {
    const onGrade = jest.fn();
    const { rerender } = setup(null, jest.fn(), onGrade);
    fireEvent.click(screen.getByRole('button', { name: /reveal/i }));
    rerender(
      <AiReviewCard
        question={QUESTION}
        options={OPTIONS}
        correctAnswer={CORRECT}
        selfGradeFallback
        onReveal={jest.fn()}
        onGrade={onGrade}
      />
    );
    fireEvent.click(screen.getByTestId('grade-good'));
    expect(onGrade).toHaveBeenCalledWith(3);
    fireEvent.click(screen.getByTestId('grade-again'));
    expect(onGrade).toHaveBeenCalledWith(1);
  });
});
