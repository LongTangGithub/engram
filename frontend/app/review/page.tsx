'use client';

import { useCallback, useEffect, useState } from 'react';
import ReviewCard from '@/components/ReviewCard';
import AiReviewCard from '@/components/AiReviewCard';
import {
  fetchNextCard,
  submitReview,
  submitMcqReview,
  activateCard,
  revealCard,
  type NextCardResponse,
} from '@/lib/api';

const DEMO_USER_ID = '00000000-0000-0000-0000-000000000001';

function randomUUID(): string {
  return crypto.randomUUID();
}

export default function ReviewPage() {
  const [card, setCard] = useState<NextCardResponse | null>(null);
  const [loading, setLoading] = useState(true);
  // True while /api/activate is generating a card for a cloze concept
  const [activating, setActivating] = useState(false);
  // True when /api/activate failed — fall back to cloze
  const [activationFailed, setActivationFailed] = useState(false);
  // Set after the user commits (auto-grade response) or clicks Reveal (self-grade fallback)
  const [correctAnswer, setCorrectAnswer] = useState<string | null>(null);
  // The MCQ option the user committed to (pick-to-grade); null until they click one
  const [picked, setPicked] = useState<string | null>(null);
  // Switch the MCQ card to the reveal → self-grade flow when auto-grading fails
  const [selfGradeFallback, setSelfGradeFallback] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [lastResult, setLastResult] =
    useState<{ retrievability: number; dueAt: string; isCorrect?: boolean } | null>(null);

  const loadNext = useCallback(async () => {
    setLoading(true);
    setError(null);
    setLastResult(null);
    setCorrectAnswer(null);
    setPicked(null);
    setSelfGradeFallback(false);
    setActivating(false);
    setActivationFailed(false);
    try {
      const next = await fetchNextCard(DEMO_USER_ID);
      setCard(next);

      // Lazy activation: if next card is cloze, generate MCQ now
      if (next && next.cardType === 'cloze') {
        setActivating(true);
        try {
          const activated = await activateCard(DEMO_USER_ID, next.conceptId);
          setCard({
            cardType: 'mcq',
            conceptId: next.conceptId,
            cardId: activated.cardId,
            question: activated.question,
            options: activated.options,
          });
        } catch {
          // Generation failed — stay on cloze (C4: cloze path always reachable)
          setActivationFailed(true);
        } finally {
          setActivating(false);
        }
      }
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Failed to load card');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    loadNext();
  }, [loadNext]);

  const handleReveal = async () => {
    if (!card?.conceptId) return;
    try {
      const { correctAnswer: ans } = await revealCard(card.conceptId);
      setCorrectAnswer(ans);
    } catch {
      // If reveal fails, show a fallback (non-fatal)
      setCorrectAnswer('(unavailable)');
    }
  };

  // Pick-to-grade (primary MCQ path): the click IS the commit. Server grades and reveals.
  const handlePick = async (option: string) => {
    if (!card) return;
    setPicked(option);
    try {
      const result = await submitMcqReview({
        userId: DEMO_USER_ID,
        conceptId: card.conceptId,
        cardId: card.cardId ?? undefined,
        selectedOption: option,
        clientEventId: randomUUID(),
        reviewedAt: new Date().toISOString(),
      });
      setCorrectAnswer(result.correctAnswer);
      setLastResult({
        retrievability: result.retrievabilityNow ?? 0,
        dueAt: result.dueAt ?? '',
        isCorrect: result.isCorrect,
      });
      await new Promise(r => setTimeout(r, 1500));
      loadNext();
    } catch {
      // Auto-grade failed — fall back to reveal → self-grade so the user isn't stuck.
      setPicked(null);
      setSelfGradeFallback(true);
    }
  };

  const handleGrade = async (rating: 1 | 2 | 3 | 4) => {
    if (!card) return;
    try {
      const result = await submitReview({
        userId: DEMO_USER_ID,
        conceptId: card.conceptId,
        rating,
        clientEventId: randomUUID(),
        reviewedAt: new Date().toISOString(),
        format: card.cardType,
      });
      setLastResult({
        retrievability: result.retrievabilityNow ?? 0,
        dueAt: result.dueAt ?? '',
      });
      await new Promise(r => setTimeout(r, 1200));
      loadNext();
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Failed to submit review');
    }
  };

  if (loading) {
    return (
      <main className="flex items-center justify-center min-h-screen">
        <p className="text-lg text-gray-400">Loading…</p>
      </main>
    );
  }

  if (error) {
    return (
      <main className="flex flex-col items-center justify-center min-h-screen gap-4">
        <p className="text-red-500">{error}</p>
        <button onClick={loadNext} className="underline text-[#FE9AB7]">Try again</button>
      </main>
    );
  }

  if (!card) {
    return (
      <main className="flex items-center justify-center min-h-screen">
        <p className="text-lg text-gray-500">
          No cards to review yet.{' '}
          <span className="text-[#FE9AB7]">Sync your vault first.</span>
        </p>
      </main>
    );
  }

  // Lazy generation in progress
  if (activating) {
    return (
      <main className="flex items-center justify-center min-h-screen">
        <p className="text-lg text-gray-400">Writing your question…</p>
      </main>
    );
  }

  return (
    <main className="flex flex-col items-center justify-center min-h-screen px-4">
      <div className="w-full max-w-xl">
        <div className="bg-white border border-gray-100 rounded-2xl shadow-sm p-8 mb-6">
          {card.cardType === 'mcq' ? (
            <AiReviewCard
              key={card.conceptId}
              question={card.question ?? ''}
              options={card.options ?? []}
              correctAnswer={correctAnswer}
              picked={picked}
              onPick={handlePick}
              selfGradeFallback={selfGradeFallback}
              onReveal={handleReveal}
              onGrade={handleGrade}
            />
          ) : (
            // Cloze fallback: shown when activation failed (C4)
            <ReviewCard
              key={card.conceptId}
              prompt={card.prompt ?? ''}
              answer={card.answer ?? ''}
              onGrade={handleGrade}
            />
          )}
        </div>

        {activationFailed && card.cardType === 'cloze' && (
          <p className="text-center text-xs text-gray-300 mb-4">
            AI card unavailable — showing cloze
          </p>
        )}

        {lastResult && (
          <p className="text-center text-sm text-gray-400">
            {lastResult.isCorrect !== undefined && (
              <span className={lastResult.isCorrect ? 'text-green-600' : 'text-red-500'}>
                {lastResult.isCorrect ? 'Correct' : 'Incorrect'} ·{' '}
              </span>
            )}
            Retrievability {(lastResult.retrievability * 100).toFixed(0)}% · due{' '}
            {new Date(lastResult.dueAt).toLocaleDateString()}
          </p>
        )}
      </div>
    </main>
  );
}
