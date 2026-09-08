import { BrandLockup } from '@/components/design/domain/BrandMark'

/**
 * Public, unauthenticated page. Required by Google's OAuth consent screen
 * before the app can leave testing mode and accept any Google account.
 */
export function PrivacyPage() {
  return (
    <div style={{ maxWidth: '68ch', margin: '0 auto', padding: '48px 24px 96px' }}>
      <div style={{ marginBottom: 32 }}>
        <BrandLockup to="/" />
      </div>
      <h1 className="h1" style={{ marginBottom: 8 }}>Privacy Policy</h1>
      <p className="small dim" style={{ marginBottom: 32 }}>Last updated: September 2026</p>

      <div className="stack" style={{ gap: 22, lineHeight: 1.65 }}>
        <p>
          match&middot;skill (&ldquo;we&rdquo;, &ldquo;us&rdquo;) helps people trade skills: you list what
          you can teach and what you want to learn, and we match you with people whose lists
          complete yours. This page explains what data we collect and how we use it.
        </p>

        <section>
          <h2 className="h2" style={{ marginBottom: 8 }}>What we collect</h2>
          <ul style={{ paddingLeft: '1.2em', display: 'grid', gap: 6 }}>
            <li>Account data: email address, display name, and time zone.</li>
            <li>If you sign in with Google: your Google account&rsquo;s name, email address, and profile picture, as shared by Google under the OAuth scopes <code>openid</code>, <code>email</code>, and <code>profile</code>.</li>
            <li>Skills data: the skills you offer, the skills you want to learn, and any skill suggestions you submit.</li>
            <li>Activity data: scheduled exchanges, availability windows, and feedback you leave for other members.</li>
          </ul>
        </section>

        <section>
          <h2 className="h2" style={{ marginBottom: 8 }}>How we use it</h2>
          <p>
            We use this data to authenticate you, run the matching engine that connects
            complementary skill lists, and let you schedule and manage exchanges. We do not
            sell your data, and we do not share it with third parties for advertising.
          </p>
        </section>

        <section>
          <h2 className="h2" style={{ marginBottom: 8 }}>Meetings</h2>
          <p>
            match&middot;skill arranges the exchange but does not host the meeting itself.
            Calls happen on the video tool you and your match agree on (Zoom, Google Meet,
            Microsoft Teams, or Whereby).
          </p>
        </section>

        <section>
          <h2 className="h2" style={{ marginBottom: 8 }}>Data retention and deletion</h2>
          <p>
            We keep your account data for as long as your account is active. To request
            deletion of your account and associated data, contact us at the address below.
          </p>
        </section>

        <section>
          <h2 className="h2" style={{ marginBottom: 8 }}>Contact</h2>
          <p>
            Questions about this policy or your data can be sent to{' '}
            <a className="link" href="mailto:patrickpriebepp@gmail.com">patrickpriebepp@gmail.com</a>.
          </p>
        </section>
      </div>
    </div>
  )
}
