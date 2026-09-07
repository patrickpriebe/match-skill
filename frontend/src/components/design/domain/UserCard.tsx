import type { ReactNode } from 'react'
import { Avatar } from '../ui/Avatar'
import { ReputationDisplay } from './ReputationDisplay'
import type { Reputation } from '@/lib/api/types'

/**
 * Identity block: avatar, name, and one line under it. Used in the sidebar,
 * on cards, in list rows and in dialogs, so the name never renders two
 * different ways in the same product.
 */
export function UserCard({ name, meta, reputation, size = 'md', nameSize, trailing }: {
  name: string
  /** Time zone, role, or anything else the API actually returns. */
  meta?: ReactNode
  reputation?: Reputation
  size?: 'sm' | 'md' | 'lg'
  nameSize?: number
  trailing?: ReactNode
}) {
  return (
    <div className="who">
      <Avatar name={name} size={size} />
      <span style={{ minWidth: 0 }}>
        <span className="who-name" style={nameSize ? { fontSize: nameSize, display: 'block' } : { display: 'block' }}>
          {name}
        </span>
        {reputation
          ? <ReputationDisplay reputation={reputation} showStars={size !== 'sm'} />
          : meta && <span className="who-meta">{meta}</span>}
        {reputation && meta && <span className="who-meta" style={{ display: 'block' }}>{meta}</span>}
      </span>
      {trailing}
    </div>
  )
}
