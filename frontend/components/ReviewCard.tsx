'use client';

import { useState } from 'react';

export interface ReviewCardProps {
  prompt: string;
  answer: string;
  onGrade: (rating: 1 | 2 | 3 | 4) => void;
}

const GRADES = [
  { rating: 1 as const, label: 'Again',  bg: 'bg-red-100   hover:bg-red-200   text-red-800' },
  { rating: 2 as const, label: 'Hard',   bg: 'bg-orange-100 hover:bg-orange-200 text-orange-800' },
  { rating: 3 as const, label: 'Good',   bg: 'bg-green-100 hover:bg-green-200  text-green-800' },
  { rating: 4 as const, label: 'Easy',   bg: 'bg-blue-100  hover:bg-blue-200   text-blue-800' },
] as const;

/**
 * Reveal-gated cloze card (Law 1).
 *
 * The answer is NEVER rendered in the DOM before the user clicks Reveal.
 * Grade buttons appear only after Reveal is clicked.
 */
export default function ReviewCard({ prompt, answer, onGrade }: ReviewCardProps) {
  const [revealed, setReveal] = useState(false);

  return (
    <div className="flex flex-col gap-6">
      {/* Cloze prompt */}
      <p className="text-2xl leading-relaxed font-medium text-[#222]">{prompt}</p>

      {!revealed ? (
        <button
          onClick={() => setReveal(true)}
          className="self-start px-6 py-3 rounded-xl bg-[#FE9AB7] text-white font-semibold
                     hover:opacity-90 active:scale-95 transition-all"
          aria-label="Reveal answer"
        >
          Reveal
        </button>
      ) : (
        <>
          {/* Answer — only rendered after reveal */}
          <p data-testid="answer" className="text-xl font-semibold text-[#FE9AB7]">
            {answer}
          </p>

          {/* Grade buttons — only after reveal */}
          <div className="flex gap-3 flex-wrap">
            {GRADES.map(({ rating, label, bg }) => (
              <button
                key={rating}
                onClick={() => onGrade(rating)}
                className={`px-5 py-2 rounded-lg font-medium transition-colors ${bg}`}
                data-testid={`grade-${label.toLowerCase()}`}
              >
                {label}
              </button>
            ))}
          </div>
        </>
      )}
    </div>
  );
}
