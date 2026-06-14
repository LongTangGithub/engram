/**
 * Law 1 enforcement — the answer must never appear in the DOM before Reveal is clicked,
 * and grade buttons must appear only after Reveal.
 */
import React from 'react';
import { render, screen, fireEvent } from '@testing-library/react';
import ReviewCard from '../components/ReviewCard';

const PROMPT = 'Active [___] strengthens memory traces.';
const ANSWER = 'retrieval';

function setup(onGrade = jest.fn()) {
  return render(<ReviewCard prompt={PROMPT} answer={ANSWER} onGrade={onGrade} />);
}

describe('ReviewCard — Law 1 reveal gate', () => {
  it('shows the prompt before reveal', () => {
    setup();
    expect(screen.getByText(PROMPT)).toBeInTheDocument();
  });

  it('answer is NOT in the DOM before Reveal is clicked', () => {
    setup();
    // The answer string must not appear anywhere in the rendered DOM
    expect(screen.queryByTestId('answer')).toBeNull();
    expect(screen.queryByText(ANSWER)).toBeNull();
  });

  it('grade buttons are NOT in the DOM before Reveal is clicked', () => {
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

  it('after clicking Reveal, answer appears in the DOM', () => {
    setup();
    fireEvent.click(screen.getByRole('button', { name: /reveal/i }));
    expect(screen.getByTestId('answer')).toBeInTheDocument();
    expect(screen.getByTestId('answer').textContent).toBe(ANSWER);
  });

  it('after clicking Reveal, all four grade buttons appear', () => {
    setup();
    fireEvent.click(screen.getByRole('button', { name: /reveal/i }));
    expect(screen.getByTestId('grade-again')).toBeInTheDocument();
    expect(screen.getByTestId('grade-hard')).toBeInTheDocument();
    expect(screen.getByTestId('grade-good')).toBeInTheDocument();
    expect(screen.getByTestId('grade-easy')).toBeInTheDocument();
  });

  it('after Reveal, Reveal button is no longer visible', () => {
    setup();
    fireEvent.click(screen.getByRole('button', { name: /reveal/i }));
    expect(screen.queryByRole('button', { name: /reveal/i })).toBeNull();
  });

  it('clicking a grade button calls onGrade with the correct rating', () => {
    const onGrade = jest.fn();
    setup(onGrade);
    fireEvent.click(screen.getByRole('button', { name: /reveal/i }));
    fireEvent.click(screen.getByTestId('grade-good'));
    expect(onGrade).toHaveBeenCalledWith(3);
    fireEvent.click(screen.getByTestId('grade-again'));
    expect(onGrade).toHaveBeenCalledWith(1);
  });
});
