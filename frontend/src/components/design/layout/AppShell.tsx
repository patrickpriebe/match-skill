import { useAuth } from '@/context/AuthContext'
import { Button } from '../ui/Button'
import type { ReactNode } from 'react'
import { NavLink } from 'react-router-dom'
import { BrandLockup } from '../domain/BrandMark'
import { UserCard } from '../domain/UserCard'
import { LanguageSwitcher } from '../ui/LanguageSwitcher'
import { useT } from '@/i18n/I18nContext'
import type { User } from '@/lib/api/types'

export interface NavItem {
  to: string
  labelKey: string
  shortKey: string
  count?: number
}

const NAV: NavItem[] = [
  { to: '/home', labelKey: 'nav.matches', shortKey: 'nav.matchesShort' },
  { to: '/search', labelKey: 'nav.search', shortKey: 'nav.searchShort' },
  { to: '/invitations', labelKey: 'nav.invitations', shortKey: 'nav.invitationsShort' },
  { to: '/scheduled', labelKey: 'nav.scheduled', shortKey: 'nav.scheduledShort' },
  { to: '/history', labelKey: 'nav.history', shortKey: 'nav.historyShort' },
  { to: '/availability', labelKey: 'nav.availability', shortKey: 'nav.availabilityShort' },
]

function Sidebar({ items, user }: { items: NavItem[]; user: User | null }) {
  const t = useT()
  const { logout } = useAuth()
  return (
    <aside className="sidebar">
      <div className="row-between">
        <BrandLockup to="/home" />
        <LanguageSwitcher />
      </div>
      <nav className="side-nav" aria-label={t('nav.main')}>
        {items.map((item) => (
          <NavLink key={item.to} to={item.to} className={({ isActive }) => (isActive ? 'is-active' : '')}>
            {t(item.labelKey)}
            {item.count !== undefined && item.count > 0 && <span className="count">{item.count}</span>}
          </NavLink>
        ))}
      </nav>
      {user && (
        <div className="side-foot">
          <NavLink to="/profile/me" style={{ display: 'block' }}>
            <UserCard name={user.displayName} size="sm" nameSize={14} meta={user.timeZone} />
          </NavLink>
          <Button variant="quiet" size="sm" onClick={logout}>{t('common.signOut')}</Button>
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
  const t = useT()
  return (
    <nav className="tabbar" aria-label={t('nav.main')}>
      {items.map((item) => (
        <NavLink key={item.to} to={item.to} className={({ isActive }) => (isActive ? 'is-active' : '')}>
          {t(item.shortKey)}
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
  const t = useT()
  const items = NAV.map((item) =>
    item.to === '/invitations' ? { ...item, count: invitationCount }
      : item.to === '/scheduled' ? { ...item, count: scheduledCount }
        : item)

  const tabs = items.filter((i) => i.to !== '/scheduled' && i.to !== '/availability')
    .concat({ to: '/profile/me', labelKey: 'nav.you', shortKey: 'nav.you' })

  return (
    <div className="app">
      <a className="skip-link" href="#main-content">{t('common.skipToContent')}</a>
      <Sidebar items={items} user={user} />
      <div className="mainpane">
        <MobileBar action={mobileAction} />
        <main className="page" id="main-content" tabIndex={-1}>{children}</main>
        <TabBar items={tabs} />
      </div>
    </div>
  )
}
