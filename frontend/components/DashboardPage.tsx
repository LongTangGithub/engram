'use client';

import Link from 'next/link';
import type { DashboardView } from '../lib/api';
import GardenCard from './GardenCard';

interface Props {
  dashboard: DashboardView;
}

export default function DashboardPage({ dashboard }: Props) {
  const isColdStart = dashboard.mode === 'COLD_START';

  return (
    <main className="min-h-screen px-8 py-12 max-w-2xl mx-auto">
      {isColdStart ? <ColdStartHero dashboard={dashboard} /> : <SteadyStateHero dashboard={dashboard} />}

      {/* Gardens grid */}
      {(dashboard.gardens?.length ?? 0) > 0 && (
        <section className="mt-10">
          <h2 className="text-xs font-medium uppercase tracking-widest text-gray-400 mb-4">
            Your Gardens
          </h2>
          <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
            {dashboard.gardens!.map((garden) => (
              <GardenCard key={garden.topicTag} garden={garden} />
            ))}
          </div>
        </section>
      )}
    </main>
  );
}

function ColdStartHero({ dashboard }: { dashboard: DashboardView }) {
  return (
    <section data-testid="cold-start-hero">
      <p className="text-xs font-medium uppercase tracking-widest text-[#FE9AB7] mb-2">
        The Frontier
      </p>
      <h1 className="text-5xl font-bold mb-2">
        {dashboard.frontierCount ?? 0}
      </h1>
      <p className="text-xl text-gray-500 mb-6">seeds waiting</p>
      <p className="text-gray-400 mb-8">
        Import your notes and start turning them into lasting memory.
      </p>
      <Link
        href="/review"
        data-testid="start-review-link"
        className="inline-block bg-[#FE9AB7] text-gray-900 font-semibold px-6 py-3 rounded-full hover:opacity-90 transition-opacity"
      >
        Plant your first seed →
      </Link>
    </section>
  );
}

function SteadyStateHero({ dashboard }: { dashboard: DashboardView }) {
  const pct = dashboard.livingKnowledgePct != null
    ? Math.round(dashboard.livingKnowledgePct)
    : 0;
  const frontier = dashboard.frontierCount ?? 0;

  return (
    <section data-testid="steady-state-hero">
      <p className="text-xs font-medium uppercase tracking-widest text-[#FE9AB7] mb-2">
        Living Knowledge
      </p>
      <h1 data-testid="living-knowledge-pct" className="text-6xl font-bold mb-3">
        {pct}%
      </h1>
      <p className="text-sm text-gray-400 mb-1">
        {dashboard.retrievableCount} of {dashboard.seededCount}{' '}
        {dashboard.seededCount === 1 ? 'concept' : 'concepts'} retained
      </p>
      <p className="text-sm text-gray-400 mb-6">
        {frontier > 0 ? `${frontier} ${frontier === 1 ? 'seed' : 'seeds'} waiting · ` : ''}
        <Link
          href="/review"
          className="font-semibold text-[#222] underline decoration-[#FE9AB7] underline-offset-2 hover:text-[#FE9AB7] transition-colors"
        >
          Review now
        </Link>
      </p>
    </section>
  );
}
