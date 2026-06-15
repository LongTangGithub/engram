/**
 * Shared tier → CSS-class token map.
 *
 * INVARIANT (Law 2): brand pink (#FE9AB7) is for seed/positive/brand contexts ONLY.
 * A health tier (THRIVING → DORMANT) must NEVER use the tier-seed class.
 * The tier-seed class is reserved for null-tier (unseeded) gardens.
 *
 * Thresholds that define each tier live in the backend's TierThresholds.java:
 *   THRIVING  R >= 0.9
 *   HEALTHY   R >= 0.7
 *   FADING    R >= 0.5
 *   WILTING   R >= 0.3
 *   DORMANT   R <  0.3
 * Keep these names in sync with MoodTier.java and the CSS classes in globals.css.
 */

export type MoodTierLabel = 'THRIVING' | 'HEALTHY' | 'FADING' | 'WILTING' | 'DORMANT';

export const TIER_COLORS: Record<MoodTierLabel | 'SEED', string> = {
  THRIVING: 'tier-thriving',
  HEALTHY:  'tier-healthy',
  FADING:   'tier-fading',
  WILTING:  'tier-wilting',
  DORMANT:  'tier-dormant',
  SEED:     'tier-seed',    // brand pink — ONLY for unseeded (null tier) state
};

/**
 * Returns the CSS class for a given tier.
 * null tier (unseeded concept/garden) → tier-seed (brand pink).
 * Any health tier → its own non-pink color class.
 */
export function tierColorClass(tier: MoodTierLabel | null | undefined): string {
  if (!tier) return TIER_COLORS.SEED;
  return TIER_COLORS[tier] ?? TIER_COLORS.SEED;
}

/**
 * Human-readable label for each tier.
 */
export const TIER_LABELS: Record<MoodTierLabel | 'SEED', string> = {
  THRIVING: 'Thriving',
  HEALTHY:  'Healthy',
  FADING:   'Fading',
  WILTING:  'Wilting',
  DORMANT:  'Dormant',
  SEED:     'Unseeded',
};

export function tierLabel(tier: MoodTierLabel | null | undefined): string {
  if (!tier) return TIER_LABELS.SEED;
  return TIER_LABELS[tier] ?? TIER_LABELS.SEED;
}
