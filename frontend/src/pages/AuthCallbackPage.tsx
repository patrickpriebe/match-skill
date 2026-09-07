import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useAuth } from '@/context/AuthContext'
import { LoadingState } from '@/components/design/feedback/LoadingState'
import { Notice } from '@/components/design/ui/Surface'
import { LinkButton } from '@/components/design/ui/Button'

export function AuthCallbackPage() {
  const { acceptToken } = useAuth()
  const navigate = useNavigate()
  const [token] = useState(() => new URLSearchParams(window.location.search).get('token'))
  const [error, setError] = useState(false)
  useEffect(() => {
    window.history.replaceState(null, '', '/auth/callback')
    if (!token) { setError(true); return }
    let cancelled = false
    acceptToken(token).then((user) => {
      if (!cancelled) navigate(user.skillsRegistered ? '/home' : '/skills/register', { replace: true })
    }).catch(() => { if (!cancelled) setError(true) })
    return () => { cancelled = true }
  }, [token, acceptToken, navigate])
  return <main className="reg">
    {error ? <Notice tone="stop">Google sign-in could not be completed. <LinkButton to="/login">Return to sign in</LinkButton></Notice>
      : <LoadingState label="Signing you in" />}
  </main>
}
