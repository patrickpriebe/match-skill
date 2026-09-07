import type { Match, Skill } from '@/lib/api/types'

export interface MatchDirections {
  /** What the viewer would learn from this person. */
  youLearn: Skill | null
  /** What this person would learn from the viewer. Null on a PARTIAL. */
  theyLearn: Skill | null
}

/**
 * `Match.matchingSkills` is a flat list with no direction, so the client
 * derives which side is which by intersecting it against the viewer's own
 * /me/skills. That derivation lives here and nowhere else. It is open
 * question Q11: the API returning the two sides separately would be cheaper
 * and less fragile.
 */
export function deriveDirections(match: Match, myOffered: Skill[], myWanted: Skill[]): MatchDirections {
  const myWantedIds = new Set(myWanted.filter((s) => s.status === 'APPROVED').map((s) => s.id))
  const myOfferedIds = new Set(myOffered.filter((s) => s.status === 'APPROVED').map((s) => s.id))
  return {
    youLearn: match.user.offeredSkills.find((s) => myWantedIds.has(s.id)) ?? null,
    theyLearn: match.user.wantedSkills.find((s) => myOfferedIds.has(s.id)) ?? null,
  }
}

