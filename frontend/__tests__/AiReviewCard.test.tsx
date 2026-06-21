/**
 * Law 1 enforcement for AiReviewCard (MCQ).
 * The correct answer must never appear in the DOM before Reveal is clicked.
 * Grade buttons appear only after Reveal + parent sets correctAnswer.
 */
import React from 'react';
import { render, screen, fireEvent } from '@testing-library/react';
import AiReviewCard from '../components/AiReviewCard';

const QUESTION = 'What does spaced repetition optimize?';
const OPTIONS = ['Short-term memorization', 'Long-term memory retention', 'Random guess', 'Passive re-reading'];
const CORRECT = 'Long-term memory retention';

function setup(correctAnswer: string | null = null, onReveal = jest.fn(), onGrade = jest.fn()) {
  return render(
    <AiReviewCard
      question={QUESTION}
      options={OPTIONS}
      correctAnswer={correctAnswer}
      onReveal={onReveal}
      onGrade={onGrade}
    />
  );
}

describe('AiReviewCard — Law 1 reveal gate', () => {
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
    // Simulate parent fetching /reveal and passing back correctAnswer
    rerender(
      <AiReviewCard
        question={QUESTION}
        options={OPTIONS}
        correctAnswer={CORRECT}
        onReveal={jest.fn()}
        onGrade={jest.fn()}
      />
    );
    expect(screen.getByTestId('correct-answer')).toBeInTheDocument();
    expect(screen.getByTestId('correct-answer').textContent).toBe(CORRECT);
  });

  it('grade buttons appear after reveal + correctAnswer is set', () => {
    // Render with correctAnswer already set — simulates state after reveal fetch completes
    const { rerender } = setup();
    fireEvent.click(screen.getByRole('button', { name: /reveal/i }));
    // Grade buttons still absent (correctAnswer still null)
    expect(screen.queryByTestId('grade-good')).toBeNull();
    // Parent sets correctAnswer
    rerender(
      <AiReviewCard
        question={QUESTION}
        options={OPTIONS}
        correctAnswer={CORRECT}
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
    // Render revealed state directly
    const { rerender } = render(
      <AiReviewCard
        question={QUESTION}
        options={OPTIONS}
        correctAnswer={null}
        onReveal={jest.fn()}
        onGrade={onGrade}
      />
    );
    fireEvent.click(screen.getByRole('button', { name: /reveal/i }));
    rerender(
      <AiReviewCard
        question={QUESTION}
        options={OPTIONS}
        correctAnswer={CORRECT}
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
