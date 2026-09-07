import { useAuth } from '@/context/AuthContext'
import { Button } from '../ui/Button'
import type { ReactNode } from 'react'
import { NavLink } from 'react-router-dom'
import { BrandLockup } from '../domain/BrandMark'
import { UserCard } from '../domain/UserCard'
import type { User } from '@/lib/api/types'

export interface NavItem {
  to: string
  label: string
  short: string
  count?: number
}

const NAV: NavItem[] = [
  { to: '/home', label: 'Matches', short: 'Matches' },
  { to: '/search', label: 'Search', short: 'Search' },
  { to: '/invitations', label: 'Invitations', short: 'Invites' },
  { to: '/scheduled', label: 'Scheduled', short: 'Times' },
  { to: '/history', label: 'History', short: 'History' },
  { to: '/availability', label: 'Availability', short: 'Free' },
]

function Sidebar({ items, user }: { items: NavItem[]; user: User | null }) {
  const { logout } = useAuth()
  return (
    <aside className="sidebar">
      <BrandLockup to="/home" />
      <nav className="side-nav" aria-label="Main">
        {items.map((item) => (
          <NavLink key={item.to} to={item.to} className={({ isActive }) => (isActive ? 'is-active' : '')}>
            {item.label}
            {item.count !== undefined && item.count > 0 && <span className="count">{item.count}</span>}
          </NavLink>
        ))}
      </nav>
      {user && (
        <div className="side-foot">
          <NavLink to="/profile/me" style={{ display: 'block' }}>
            <UserCard name={user.displayName} size="sm" nameSize={14} meta={user.timeZone} />
          </NavLink>
          <Button variant="quiet" size="sm" onClick={logout}>Sign out</Button>
        </div>
      )}
    </aside>
  )
}

function MobileBar({ action }: { action?: ReactNode }) {
  return (
    <div className="mobilebar">
      <div className="mobilebar-inner">
        <BrandLockup to="/matches" />
        {action}
      </div>
    </div>
  )
}

/** Five destinations at most on a phone — the sidebar's six collapse by
 *  folding Scheduled into Invitations, which is where its work starts. */
function TabBar({ items }: { items: NavItem[] }) {
  return (
    <nav className="tabbar" aria-label="Main">
      {items.map((item) => (
        <NavLink key={item.to} to={item.to} className={({ isActive }) => (isActive ? 'is-active' : '')}>
          {item.short}
        </NavLink>
      ))}
    </nav>
  )
}

/**
 * Sidebar at 921px and up, bottom tab bar below it. Mobile-first: invitations
 * and scheduling confirmations get read on a phone, often minutes before the
 * call, so the small layout is a redesign rather than a squeeze.
 */
export function AppShell({ user, invitationCount, scheduledCount, mobileAction, children }: {
  user: User | null
  invitationCount?: number
  scheduledCount?: number
  mobileAction?: ReactNode
  children: ReactNode
}) {
  const items = NAV.map((item) =>
    item.to === '/invitations' ? { ...item, count: invitationCount }
      : item.to === '/scheduled' ? { ...item, count: scheduledCount }
        : item)

  const tabs = items.filter((i) => i.to !== '/scheduled' && i.to !== '/availability')
    .concat({ to: '/profile/me', label: 'You', short: 'You' })

  return (
    <div className="app">
      <a className="skip-link" href="#main-content">Skip to content</a>
      <Sidebar items={items} user={user} />
      <div className="mainpane">
        <MobileBar action={mobileAction} />
        <main className="page" id="main-content" tabIndex={-1}>{children}</main>
        <TabBar items={tabs} />
      </div>
    </div>
  )
}
