'use client';

import { useState } from 'react';

export interface AiReviewCardProps {
  question: string;
  options: string[];           // 4 shuffled options; no correct indicator
  correctAnswer: string | null; // null until Reveal; parent fetches via /reveal
  onReveal: () => void;        // parent calls /reveal and sets correctAnswer
  onGrade: (rating: 1 | 2 | 3 | 4) => void;
}

const GRADES = [
  { rating: 1 as const, label: 'Again', bg: 'bg-red-100   hover:bg-red-200   text-red-800' },
  { rating: 2 as const, label: 'Hard',  bg: 'bg-orange-100 hover:bg-orange-200 text-orange-800' },
  { rating: 3 as const, label: 'Good',  bg: 'bg-green-100 hover:bg-green-200  text-green-800' },
  { rating: 4 as const, label: 'Easy',  bg: 'bg-blue-100  hover:bg-blue-200   text-blue-800' },
] as const;

/**
 * MCQ card — Law 1 compliant.
 * correctAnswer is NOT in the DOM until parent sets it (after /reveal is called).
 * Grade buttons appear only after reveal.
 */
export default function AiReviewCard({
  question,
  options,
  correctAnswer,
  onReveal,
  onGrade,
}: AiReviewCardProps) {
  const [revealClicked, setRevealClicked] = useState(false);

  const handleReveal = () => {
    setRevealClicked(true);
    onReveal();
  };

  const isRevealed = revealClicked && correctAnswer !== null;

  return (
    <div className="flex flex-col gap-6">
      <p className="text-2xl leading-relaxed font-medium text-[#222]">{question}</p>

      {/* Option buttons */}
      <div className="flex flex-col gap-3">
        {options.map((opt) => {
          const isCorrect = isRevealed && opt === correctAnswer;
          return (
            <button
              key={opt}
              disabled={revealClicked}
              data-testid={isCorrect ? 'correct-answer' : undefined}
              className={[
                'w-full text-left px-5 py-3 rounded-xl border font-medium transition-colors',
                isCorrect
                  ? 'border-green-400 bg-green-50 text-green-800'
                  : revealClicked
                    ? 'border-gray-200 bg-gray-50 text-gray-400'
                    : 'border-gray-200 bg-white hover:border-[#FE9AB7] text-[#222]',
              ].join(' ')}
            >
              {opt}
            </button>
          );
        })}
      </div>

      {/* Reveal button — hidden after click */}
      {!revealClicked && (
        <button
          onClick={handleReveal}
          className="self-start px-6 py-3 rounded-xl bg-[#FE9AB7] text-white font-semibold
                     hover:opacity-90 active:scale-95 transition-all"
          aria-label="Reveal answer"
        >
          Reveal
        </button>
      )}

      {/* Brief "revealing" state while parent fetches /reveal */}
      {revealClicked && !correctAnswer && (
        <p className="text-sm text-gray-400">Revealing…</p>
      )}

      {/* Grade buttons — only after reveal complete */}
      {isRevealed && (
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
      )}
    </div>
  );
}
