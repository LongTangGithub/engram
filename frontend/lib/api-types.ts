/**
 * API types for the Engram backend.
 *
 * GENERATED — do not hand-edit.
 * Regenerate: start the backend, then run:
 *   npx openapi-typescript http://localhost:8080/v3/api-docs -o lib/api-types.ts
 *
 * Source of truth: GET /v3/api-docs on the running backend (springdoc).
 */

export interface ClozeCardResponse {
  conceptId: string;
  prompt: string;
  answer: string;
}

export interface SubmitRequest {
  userId: string;
  conceptId: string;
  /** 1=Again 2=Hard 3=Good 4=Easy */
  rating: 1 | 2 | 3 | 4;
  /** Caller-generated UUID for idempotency */
  clientEventId: string;
  /** ISO-8601 UTC timestamp */
  reviewedAt: string;
}

export interface ReviewResultResponse {
  retrievabilityNow: number;
  dueAt: string;
  lifecycleState: string;
}
