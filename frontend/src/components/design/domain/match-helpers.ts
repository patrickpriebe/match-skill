import type { Match, Skill } from '@/lib/api/types'

export interface MatchDirections {
  /** What the viewer would learn from this person. */
  youLearn: Skill | null
  /** What this person would learn from the viewer. Null on a PARTIAL. */
  theyLearn: Skill | null
}

/**
 * The API says what the other person offers and wants, not which of those
 * lines up with the viewer. Intersecting the two lists against the viewer's
 * own /me/skills is what turns them into the two directions of a trade, and
 * that derivation lives here and nowhere else.
 */
export function deriveDirections(match: Match, myOffered: Skill[], myWanted: Skill[]): MatchDirections {
  const myWantedIds = new Set(myWanted.filter((s) => s.status === 'APPROVED').map((s) => s.id))
  const myOfferedIds = new Set(myOffered.filter((s) => s.status === 'APPROVED').map((s) => s.id))
  return {
    youLearn: match.user.offeredSkills.find((s) => myWantedIds.has(s.id)) ?? null,
    theyLearn: match.user.wantedSkills.find((s) => myOfferedIds.has(s.id)) ?? null,
  }
}

