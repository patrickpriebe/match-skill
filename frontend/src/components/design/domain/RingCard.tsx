import { Link } from 'react-router-dom'
import { Button } from '../ui/Button'
import { useT } from '@/i18n/I18nContext'
import type { Ring, RingMember } from '@/lib/api/types'

/**
 * A ring drawn as a ring.
 *
 * Everything else on this page is a card in a list, and a list is the wrong
 * shape for this: the whole point of a ring is that it closes, and that no
 * two of its members are a match for each other. Three names stacked in a
 * column would read as three suggestions. Three names on a triangle with
 * arrows reads as one thing that only works whole.
 *
 * Node 0 is always the viewer, drawn at the top. The arrows run clockwise
 * because member i teaches member i+1, so the direction of the drawing is the
 * direction of the trade and nothing else has to say so.
 */

const NODES = [
  { x: 150, y: 34, labelY: -26 }, // the viewer
  { x: 262, y: 196, labelY: 30 },
  { x: 38, y: 196, labelY: 30 },
]

/** Pulled back from each node so an arrow points at the circle, not through it. */
const NODE_RADIUS = 21
const ARROW_GAP = 7

function edge(from: { x: number; y: number }, to: { x: number; y: number }) {
  const dx = to.x - from.x
  const dy = to.y - from.y
  const length = Math.hypot(dx, dy)
  const gap = (NODE_RADIUS + ARROW_GAP) / length
  return {
    x1: from.x + dx * gap,
    y1: from.y + dy * gap,
    x2: to.x - dx * gap,
    y2: to.y - dy * gap,
    midX: from.x + dx / 2,
    midY: from.y + dy / 2,
  }
}

function initials(name: string): string {
  return name
    .split(' ')
    .filter(Boolean)
    .slice(0, 2)
    .map((part) => part[0]?.toUpperCase() ?? '')
    .join('')
}

export function RingCard({ ring, onStart, starting }: {
  ring: Ring
  onStart?: (ring: Ring) => void
  starting?: boolean
}) {
  const t = useT()
  const [me, second, third] = ring.members
  // The member who teaches the viewer is the last one, since member i teaches
  // member i+1 and the list wraps.
  const myTeacher = third
  const hours = Math.round((ring.weakestLinkMinutes / 60) * 10) / 10

  return (
    <article className="ring">
      <svg className="ring-svg" viewBox="0 0 300 250" role="img" aria-label={sentence(ring, t)}>
        <defs>
          <marker id="ring-arrow" viewBox="0 0 10 10" refX="9" refY="5"
            markerWidth="6" markerHeight="6" orient="auto-start-reverse">
            <path d="M 0 0 L 10 5 L 0 10 z" fill="var(--accent)" />
          </marker>
        </defs>

        {[[0, 1], [1, 2], [2, 0]].map(([from, to]) => {
          const line = edge(NODES[from], NODES[to])
          return (
            <g key={`${from}-${to}`}>
              <line
                x1={line.x1} y1={line.y1} x2={line.x2} y2={line.y2}
                stroke="var(--accent)" strokeWidth="1.5" markerEnd="url(#ring-arrow)"
              />
              <text className="ring-skill" x={line.midX} y={line.midY} textAnchor="middle" dy="0.35em">
                {ring.members[from].teaches.name}
              </text>
            </g>
          )
        })}

        {ring.members.map((member, index) => (
          <g key={member.userId}>
            <circle
              cx={NODES[index].x} cy={NODES[index].y} r={NODE_RADIUS}
              className={index === 0 ? 'ring-node ring-node-me' : 'ring-node'}
            />
            <text className="ring-initials" x={NODES[index].x} y={NODES[index].y} textAnchor="middle" dy="0.35em">
              {index === 0 ? t('ring.you') : initials(member.displayName)}
            </text>
            <text
              className="ring-name"
              x={NODES[index].x}
              y={NODES[index].y + NODES[index].labelY}
              textAnchor="middle"
            >
              {index === 0 ? '' : member.displayName}
            </text>
          </g>
        ))}
      </svg>

      <div className="ring-body">
        <p className="ring-lead">{t('ring.lead')}</p>
        <ol className="ring-steps">
          <li>{t('ring.youTeach', { skill: me.teaches.name, name: first(second) })}</li>
          <li>{t('ring.theyTeach', {
            name: first(second), skill: second.teaches.name, other: first(third),
          })}</li>
          <li>{t('ring.closes', { name: first(third), skill: third.teaches.name })}</li>
        </ol>

        <p className="small dim">
          {ring.weakestLinkMinutes > 0
            ? t('ring.weakestLink', { hours })
            : t('ring.noSharedWeek')}
        </p>

        <div className="row wrap" style={{ gap: 6 }}>
          <Button variant="primary" size="sm" loading={starting} disabled={!onStart}
            onClick={() => onStart?.(ring)}>
            {t('ring.start', { name: first(myTeacher) })}
          </Button>
          <Link className="btn btn-quiet btn-sm" to={'/profile/' + myTeacher.userId}>
            {t('matchCard.viewProfile')}
          </Link>
        </div>
        <p className="small dim">{t('ring.startNote', { name: first(myTeacher) })}</p>
      </div>
    </article>
  )
}

function first(member: RingMember): string {
  return member.displayName.split(' ')[0]
}

/** The same chain in one sentence, for anyone who gets the diagram read aloud. */
function sentence(ring: Ring, t: (key: string, vars?: Record<string, string | number>) => string): string {
  const [me, second, third] = ring.members
  return [
    t('ring.youTeach', { skill: me.teaches.name, name: first(second) }),
    t('ring.theyTeach', { name: first(second), skill: second.teaches.name, other: first(third) }),
    t('ring.closes', { name: first(third), skill: third.teaches.name }),
  ].join(' ')
}
