'use client';

import type { GardenView } from '../lib/api';
import { tierColorClass, tierLabel } from '../lib/tier-colors';

interface Props {
  garden: GardenView;
}

export default function GardenCard({ garden }: Props) {
  const colorClass = tierColorClass(garden.rolledUpTier);
  const label = tierLabel(garden.rolledUpTier);
  const total = garden.totalCount ?? 0;
  const seeded = garden.seededCount ?? 0;
  const frontier = garden.frontierCount ?? 0;

  return (
    <div
      data-testid={`garden-${garden.topicTag}`}
      className={`${colorClass} rounded-2xl p-5 flex flex-col gap-1`}
    >
      <h3 className="font-semibold text-xl capitalize">{garden.topicTag}</h3>
      <span className="text-xs font-medium uppercase tracking-widest opacity-70">{label}</span>
      <div className="mt-3 flex gap-4 text-sm">
        <span>{total} {total === 1 ? 'concept' : 'concepts'}</span>
        {seeded > 0 && (
          <span>{seeded} {seeded === 1 ? 'seed' : 'seeded'}</span>
        )}
        {frontier > 0 && (
          <span>{frontier} waiting</span>
        )}
      </div>
      {garden.avgRetrievability != null && total > 1 && (
        <span className="text-xs opacity-60 mt-1">
          avg retention {Math.round(garden.avgRetrievability * 100)}%
        </span>
      )}
    </div>
  );
}
