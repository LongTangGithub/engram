'use client';

import { useCallback, useEffect, useState } from 'react';
import ReviewCard from '@/components/ReviewCard';
import { fetchNextCard, submitReview, type ClozeCardResponse } from '@/lib/api';

// Stable demo userId — in production this comes from auth.
const DEMO_USER_ID = '00000000-0000-0000-0000-000000000001';

function randomUUID(): string {
  return crypto.randomUUID();
}

export default function ReviewPage() {
  const [card, setCard] = useState<ClozeCardResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [lastResult, setLastResult] = useState<{ retrievability: number; dueAt: string } | null>(null);

  const loadNext = useCallback(async () => {
    setLoading(true);
    setError(null);
    setLastResult(null);
    try {
      const next = await fetchNextCard(DEMO_USER_ID);
      setCard(next);
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Failed to load card');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    loadNext();
  }, [loadNext]);

  const handleGrade = async (rating: 1 | 2 | 3 | 4) => {
    if (!card) return;
    try {
      const result = await submitReview({
        userId: DEMO_USER_ID,
        conceptId: card.conceptId,
        rating,
        clientEventId: randomUUID(),
        reviewedAt: new Date().toISOString(),
      });
      setLastResult({
        retrievability: result.retrievabilityNow ?? 0,
        dueAt: result.dueAt ?? '',
      });
      // Brief pause so user sees the result, then load next
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

  return (
    <main className="flex flex-col items-center justify-center min-h-screen px-4">
      <div className="w-full max-w-xl">
        {/* Card */}
        <div className="bg-white border border-gray-100 rounded-2xl shadow-sm p-8 mb-6">
          <ReviewCard
            key={card.conceptId}
            prompt={card.prompt ?? ''}
            answer={card.answer ?? ''}
            onGrade={handleGrade}
          />
        </div>

        {/* Affirmation — shown after grading, before next card loads */}
        {lastResult && (
          <p className="text-center text-sm text-gray-400">
            Retrievability {(lastResult.retrievability * 100).toFixed(0)}% · due{' '}
            {new Date(lastResult.dueAt).toLocaleDateString()}
          </p>
        )}
      </div>
    </main>
  );
}
