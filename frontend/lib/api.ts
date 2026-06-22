import type { components } from './api-types';

// Dashboard types — generated; do not redefine here.
export type DashboardView = components['schemas']['DashboardView'];
export type GardenView    = components['schemas']['GardenView'];
export type ConceptView   = components['schemas']['ConceptView'];

// Review types — redefined locally because ENG-8b changed the /next response shape.
// Regenerate api-types.ts (see frontend/CLAUDE.md) after backend changes to keep in sync.
export interface NextCardResponse {
  cardType: 'cloze' | 'mcq';
  conceptId: string;
  // cloze fields (null when cardType=mcq)
  prompt?: string | null;
  answer?: string | null;
  // mcq fields (null when cardType=cloze)
  cardId?: string | null;
  question?: string | null;
  options?: string[] | null;
}

export interface SubmitRequest {
  userId: string;
  conceptId: string;
  rating: number;
  clientEventId: string;
  reviewedAt: string;
  format?: string;   // "cloze" | "mcq"; omit → backend defaults to "cloze"
}

export interface ReviewResultResponse {
  retrievabilityNow: number;
  dueAt: string;
  lifecycleState: string;
}

// ENG-9 MCQ auto-grade. Defined locally (OpenAPI regen needs backend + Postgres running — see
// frontend/CLAUDE.md). Regenerate api-types.ts and re-export from there once the backend is up.
export interface McqSubmitRequest {
  userId: string;
  conceptId: string;
  cardId?: string;        // echoed for provenance; server grades by conceptId
  selectedOption: string; // the option text the user picked
  clientEventId: string;
  reviewedAt: string;
}

export interface McqResultResponse {
  retrievabilityNow: number;
  dueAt: string;
  lifecycleState: string;
  isCorrect: boolean;      // server-decided
  correctAnswer: string;   // revealable now (post-commit only)
}

export interface ActivateResponse {
  cardId: string;
  conceptId: string;
  question: string;
  options: string[];   // 4 shuffled options; correctAnswer not flagged (fetch via /reveal)
}

export interface RevealResponse {
  correctAnswer: string;
}

const BASE = process.env.NEXT_PUBLIC_API_URL ?? 'http://localhost:8081';

export async function fetchNextCard(userId: string): Promise<NextCardResponse | null> {
  const res = await fetch(`${BASE}/api/review/next?userId=${encodeURIComponent(userId)}`);
  if (res.status === 204) return null;
  if (!res.ok) throw new Error(`fetchNextCard failed: ${res.status}`);
  return res.json() as Promise<NextCardResponse>;
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

export async function submitMcqReview(body: McqSubmitRequest): Promise<McqResultResponse> {
  const res = await fetch(`${BASE}/api/review/submit-mcq`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  });
  if (!res.ok) throw new Error(`submitMcqReview failed: ${res.status}`);
  return res.json() as Promise<McqResultResponse>;
}

export async function fetchDashboard(userId: string): Promise<DashboardView> {
  const res = await fetch(`${BASE}/api/dashboard?userId=${encodeURIComponent(userId)}`);
  if (!res.ok) throw new Error(`fetchDashboard failed: ${res.status}`);
  return res.json() as Promise<DashboardView>;
}

export async function activateCard(userId: string, conceptId: string): Promise<ActivateResponse> {
  const res = await fetch(`${BASE}/api/activate`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ userId, conceptId }),
  });
  if (!res.ok) throw new Error(`activateCard failed: ${res.status}`);
  return res.json() as Promise<ActivateResponse>;
}

export async function revealCard(conceptId: string): Promise<RevealResponse> {
  const res = await fetch(`${BASE}/api/activate/${encodeURIComponent(conceptId)}/reveal`);
  if (!res.ok) throw new Error(`revealCard failed: ${res.status}`);
  return res.json() as Promise<RevealResponse>;
}
