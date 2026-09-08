import { Navigate, Outlet, Route, Routes, useLocation } from 'react-router-dom'
import { AuthProvider, useAuth } from '@/context/AuthContext'
import { ProtectedRoute } from '@/routes/ProtectedRoute'
import { AppShell } from '@/components/design/layout/AppShell'
import { Button } from '@/components/design/ui/Button'
import { LoginPage } from '@/pages/LoginPage'
import { AuthCallbackPage } from '@/pages/AuthCallbackPage'
import { SkillRegistrationPage } from '@/pages/SkillRegistrationPage'
import { HomePage } from '@/pages/HomePage'
import { SearchPage } from '@/pages/SearchPage'
import { ProfilePage } from '@/pages/ProfilePage'
import { InvitationsPage } from '@/pages/InvitationsPage'
import { ExchangeDetailsPage } from '@/pages/ExchangeDetailsPage'
import { SchedulingPage } from '@/pages/SchedulingPage'
import { HistoryPage } from '@/pages/HistoryPage'
import { FeedbackPage } from '@/pages/FeedbackPage'
import { AvailabilityPage } from '@/pages/AvailabilityPage'
import { PrivacyPage } from '@/pages/PrivacyPage'

function Shell() {
  const { user, logout } = useAuth()
  const location = useLocation()
  return <AppShell user={user} mobileAction={<Button variant="quiet" size="sm" onClick={logout}>Sign out</Button>}>
    <Outlet key={location.pathname} />
  </AppShell>
}
export function App() {
  return <AuthProvider><Routes>
    <Route path="/login" element={<LoginPage />} />
    <Route path="/privacy" element={<PrivacyPage />} />
    <Route path="/auth/callback" element={<AuthCallbackPage />} />
    <Route element={<ProtectedRoute />}>
      <Route path="/skills/register" element={<SkillRegistrationPage />} />
      <Route path="/skills" element={<Navigate to="/skills/register" replace />} />
      <Route element={<Shell />}>
        <Route path="/home" element={<HomePage />} />
        <Route path="/matches" element={<Navigate to="/home" replace />} />
        <Route path="/search" element={<SearchPage />} />
        <Route path="/profile/:id" element={<ProfilePage />} />
        <Route path="/invitations" element={<InvitationsPage />} />
        <Route path="/scheduled" element={<InvitationsPage scheduledOnly />} />
        <Route path="/exchanges/:id" element={<ExchangeDetailsPage />} />
        <Route path="/scheduled/:id" element={<SchedulingPage />} />
        <Route path="/history" element={<HistoryPage />} />
        <Route path="/feedback/:id" element={<FeedbackPage />} />
        <Route path="/availability" element={<AvailabilityPage />} />
      </Route>
    </Route>
    <Route path="*" element={<Navigate to="/home" replace />} />
  </Routes></AuthProvider>
}
