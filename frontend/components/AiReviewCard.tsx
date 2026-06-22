'use client';

import { useState } from 'react';

export interface AiReviewCardProps {
  question: string;
  options: string[];            // 4 shuffled options; no correct indicator pre-commit
  correctAnswer: string | null; // null until committed; server returns it post-pick (or /reveal)

  // Pick-to-grade (primary, ENG-9): clicking an option commits it; the server grades.
  picked?: string | null;       // the option the user committed to (parent state)
  onPick?: (option: string) => void;

  // Self-grade fallback (e.g. auto-grade submit failed): the ENG-8b reveal → grade flow.
  selfGradeFallback?: boolean;
  onReveal?: () => void;
  onGrade?: (rating: 1 | 2 | 3 | 4) => void;
}

const GRADES = [
  { rating: 1 as const, label: 'Again', bg: 'bg-red-100   hover:bg-red-200   text-red-800' },
  { rating: 2 as const, label: 'Hard',  bg: 'bg-orange-100 hover:bg-orange-200 text-orange-800' },
  { rating: 3 as const, label: 'Good',  bg: 'bg-green-100 hover:bg-green-200  text-green-800' },
  { rating: 4 as const, label: 'Easy',  bg: 'bg-blue-100  hover:bg-blue-200   text-blue-800' },
] as const;

const optionBase =
  'w-full text-left px-5 py-3 rounded-xl border font-medium transition-colors';

/**
 * MCQ card — Law 1 compliant in both modes.
 *
 * Primary (pick-to-grade): options are clickable. Clicking one commits it — that IS the answer.
 * The parent submits the pick server-side; the server returns correctness + the correct answer,
 * which the parent passes back via `correctAnswer`. Only THEN do we mark the correct option (green)
 * and the wrong pick (red). Before the click there is no correct indicator and no answer in the DOM.
 *
 * Fallback (selfGradeFallback): the ENG-8b reveal → self-grade flow, kept available for when
 * auto-grading fails. correctAnswer stays out of the DOM until the parent sets it after /reveal.
 */
export default function AiReviewCard({
  question,
  options,
  correctAnswer,
  picked = null,
  onPick,
  selfGradeFallback = false,
  onReveal,
  onGrade,
}: AiReviewCardProps) {
  if (selfGradeFallback) {
    return (
      <SelfGradeView
        question={question}
        options={options}
        correctAnswer={correctAnswer}
        onReveal={onReveal}
        onGrade={onGrade}
      />
    );
  }

  const committed = picked !== null;
  const revealed = committed && correctAnswer !== null;

  return (
    <div className="flex flex-col gap-6">
      <p className="text-2xl leading-relaxed font-medium text-[#222]">{question}</p>

      <div className="flex flex-col gap-3">
        {options.map((opt) => {
          const isCorrect = revealed && opt === correctAnswer;
          const isWrongPick = revealed && opt === picked && opt !== correctAnswer;
          return (
            <button
              key={opt}
              disabled={committed}
              onClick={committed ? undefined : () => onPick?.(opt)}
              data-testid={isCorrect ? 'correct-answer' : isWrongPick ? 'wrong-answer' : undefined}
              className={[
                optionBase,
                isCorrect
                  ? 'border-green-400 bg-green-50 text-green-800'
                  : isWrongPick
                    ? 'border-red-400 bg-red-50 text-red-800'
                    : committed
                      ? 'border-gray-200 bg-gray-50 text-gray-400'
                      : 'border-gray-200 bg-white hover:border-[#FE9AB7] text-[#222]',
              ].join(' ')}
            >
              {opt}
            </button>
          );
        })}
      </div>

      {/* Committed, awaiting the server's grade */}
      {committed && !revealed && (
        <p className="text-sm text-gray-400">Checking…</p>
      )}
    </div>
  );
}

/** ENG-8b reveal → self-grade flow, preserved as the fallback path. */
function SelfGradeView({
  question,
  options,
  correctAnswer,
  onReveal,
  onGrade,
}: Pick<AiReviewCardProps, 'question' | 'options' | 'correctAnswer' | 'onReveal' | 'onGrade'>) {
  const [revealClicked, setRevealClicked] = useState(false);

  const handleReveal = () => {
    setRevealClicked(true);
    onReveal?.();
  };

  const isRevealed = revealClicked && correctAnswer !== null;

  return (
    <div className="flex flex-col gap-6">
      <p className="text-2xl leading-relaxed font-medium text-[#222]">{question}</p>

      <div className="flex flex-col gap-3">
        {options.map((opt) => {
          const isCorrect = isRevealed && opt === correctAnswer;
          return (
            <button
              key={opt}
              disabled={revealClicked}
              data-testid={isCorrect ? 'correct-answer' : undefined}
              className={[
                optionBase,
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

      {revealClicked && !correctAnswer && (
        <p className="text-sm text-gray-400">Revealing…</p>
      )}

      {isRevealed && (
        <div className="flex gap-3 flex-wrap">
          {GRADES.map(({ rating, label, bg }) => (
            <button
              key={rating}
              onClick={() => onGrade?.(rating)}
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
