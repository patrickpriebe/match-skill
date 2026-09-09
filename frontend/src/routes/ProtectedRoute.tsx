import { Button } from '@/components/design/ui/Button'
import { Notice } from '@/components/design/ui/Surface'
import { Loader2 } from 'lucide-react'
import { Navigate, Outlet, useLocation } from 'react-router-dom'
import { useAuth } from '@/context/AuthContext'
import { useT } from '@/i18n/I18nContext'

// Two gates, in order: authenticated at all, then skillsRegistered — a
// returning user with skillsRegistered:false is bounced to registration
// before Home, per docs/documentation.md "First-time skill registration".
export function ProtectedRoute() {
  const t = useT()
  const { status, user, error, retry } = useAuth()
  const location = useLocation()

  if (error) return <main className="reg"><Notice tone="stop">{error}<Button onClick={retry}>{t('protectedRoute.tryAgain')}</Button></Notice></main>

  if (status === 'loading') {
    return (
      <div className="flex min-h-screen items-center justify-center text-muted-foreground">
        <Loader2 className="h-5 w-5 animate-spin" />
      </div>
    )
  }

  if (status === 'unauthenticated' || !user) {
    return <Navigate to="/login" state={{ from: location.pathname }} replace />
  }

  if (!user.skillsRegistered && location.pathname !== '/skills/register') {
    return <Navigate to="/skills/register" replace />
  }

  return <Outlet />
}
