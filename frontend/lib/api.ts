import type { components } from './api-types';

export type ClozeCardResponse  = components['schemas']['ClozeCardResponse'];
export type SubmitRequest      = components['schemas']['SubmitRequest'];
export type ReviewResultResponse = components['schemas']['ReviewResultResponse'];
export type DashboardView      = components['schemas']['DashboardView'];
export type GardenView         = components['schemas']['GardenView'];
export type ConceptView        = components['schemas']['ConceptView'];

const BASE = process.env.NEXT_PUBLIC_API_URL ?? 'http://localhost:8081';

export async function fetchNextCard(userId: string): Promise<ClozeCardResponse | null> {
  const res = await fetch(`${BASE}/api/review/next?userId=${encodeURIComponent(userId)}`);
  if (res.status === 204) return null;
  if (!res.ok) throw new Error(`fetchNextCard failed: ${res.status}`);
  return res.json() as Promise<ClozeCardResponse>;
}

export async function submitReview(body: SubmitRequest): Promise<ReviewResultResponse> {
  const res = await fetch(`${BASE}/api/review/submit`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  });
  if (!res.ok) throw new Error(`submitReview failed: ${res.status}`);
  return res.json() as Promise<ReviewResultResponse>;
}

export async function fetchDashboard(userId: string): Promise<DashboardView> {
  const res = await fetch(`${BASE}/api/dashboard?userId=${encodeURIComponent(userId)}`);
  if (!res.ok) throw new Error(`fetchDashboard failed: ${res.status}`);
  return res.json() as Promise<DashboardView>;
}
