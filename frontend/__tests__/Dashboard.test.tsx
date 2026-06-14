/**
 * Dashboard component tests.
 * Tests both modes (cold-start, steady-state) and Law-2 color invariant.
 */
import React from 'react';
import { render, screen, within } from '@testing-library/react';
import DashboardPage from '../components/DashboardPage';
import type { DashboardView } from '../lib/api';

// ── fixtures ──────────────────────────────────────────────────────────────────

const coldStart: DashboardView = {
  mode: 'COLD_START',
  livingKnowledgePct: undefined,
  seededCount: 0,
  retrievableCount: 0,
  frontierCount: 7,
  gardens: [
    {
      topicTag: 'memory',
      totalCount: 4,
      seededCount: 0,
      frontierCount: 4,
      avgRetrievability: undefined,
      rolledUpTier: undefined,
      concepts: [],
    },
    {
      topicTag: 'botany',
      totalCount: 3,
      seededCount: 0,
      frontierCount: 3,
      avgRetrievability: undefined,
      rolledUpTier: undefined,
      concepts: [],
    },
  ],
};

const steadyState: DashboardView = {
  mode: 'STEADY_STATE',
  livingKnowledgePct: 66.67,
  seededCount: 3,
  retrievableCount: 2,
  frontierCount: 2,
  gardens: [
    {
      topicTag: 'memory',
      totalCount: 3,
      seededCount: 3,
      frontierCount: 0,
      avgRetrievability: 0.85,
      rolledUpTier: 'HEALTHY',
      concepts: [],
    },
    {
      topicTag: 'forgotten',
      totalCount: 2,
      seededCount: 1,
      frontierCount: 1,
      avgRetrievability: 0.1,
      rolledUpTier: 'DORMANT',
      concepts: [],
    },
  ],
};

// ── cold-start mode ───────────────────────────────────────────────────────────

describe('DashboardPage — cold-start mode', () => {
  beforeEach(() => render(<DashboardPage dashboard={coldStart} />));

  it('renders the Frontier hero section', () => {
    expect(screen.getByTestId('cold-start-hero')).toBeInTheDocument();
  });

  it('shows frontier count prominently', () => {
    const hero = screen.getByTestId('cold-start-hero');
    expect(within(hero).getByText('7')).toBeInTheDocument();
    expect(within(hero).getByText(/seeds waiting/i)).toBeInTheDocument();
  });

  it('has a primary action link that routes to /review', () => {
    const link = screen.getByTestId('start-review-link');
    expect(link).toBeInTheDocument();
    expect(link).toHaveAttribute('href', '/review');
  });

  it('does NOT render the steady-state hero', () => {
    expect(screen.queryByTestId('steady-state-hero')).toBeNull();
  });

  it('renders gardens in seed state (no rollup tier)', () => {
    const memGarden = screen.getByTestId('garden-memory');
    expect(memGarden).toHaveClass('tier-seed');
    expect(memGarden).not.toHaveClass('tier-dormant');
  });
});

// ── steady-state mode ─────────────────────────────────────────────────────────

describe('DashboardPage — steady-state mode', () => {
  beforeEach(() => render(<DashboardPage dashboard={steadyState} />));

  it('renders the Living Knowledge hero section', () => {
    expect(screen.getByTestId('steady-state-hero')).toBeInTheDocument();
  });

  it('shows Living Knowledge percentage rounded', () => {
    const pctEl = screen.getByTestId('living-knowledge-pct');
    expect(pctEl).toBeInTheDocument();
    expect(pctEl.textContent).toBe('67%');
  });

  it('does NOT render the cold-start hero', () => {
    expect(screen.queryByTestId('cold-start-hero')).toBeNull();
  });

  it('mentions frontier count as secondary stat within hero', () => {
    const hero = screen.getByTestId('steady-state-hero');
    expect(within(hero).getByText(/2 seeds waiting/i)).toBeInTheDocument();
  });

  it('renders a garden card for each garden', () => {
    expect(screen.getByTestId('garden-memory')).toBeInTheDocument();
    expect(screen.getByTestId('garden-forgotten')).toBeInTheDocument();
  });
});

// ── Law 2: health color must never be brand pink ──────────────────────────────

describe('Law 2 — steady-state tier colors', () => {
  beforeEach(() => render(<DashboardPage dashboard={steadyState} />));

  it('healthy garden uses tier-healthy class, not tier-seed (brand pink)', () => {
    const memGarden = screen.getByTestId('garden-memory');
    expect(memGarden).toHaveClass('tier-healthy');
    expect(memGarden).not.toHaveClass('tier-seed');
  });

  it('dormant garden uses tier-dormant class, not tier-seed (brand pink)', () => {
    const dormantGarden = screen.getByTestId('garden-forgotten');
    expect(dormantGarden).toHaveClass('tier-dormant');
    expect(dormantGarden).not.toHaveClass('tier-seed');
  });
});

describe('Law 2 — unseeded garden uses seed class only', () => {
  beforeEach(() => render(<DashboardPage dashboard={coldStart} />));

  it('unseeded garden has tier-seed class (brand pink) and no health class', () => {
    const seedGarden = screen.getByTestId('garden-memory');
    expect(seedGarden).toHaveClass('tier-seed');
    expect(seedGarden).not.toHaveClass('tier-thriving');
    expect(seedGarden).not.toHaveClass('tier-healthy');
    expect(seedGarden).not.toHaveClass('tier-fading');
    expect(seedGarden).not.toHaveClass('tier-wilting');
    expect(seedGarden).not.toHaveClass('tier-dormant');
  });
});
