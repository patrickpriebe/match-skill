import { BrandLockup } from '@/components/design/domain/BrandMark'
import { LanguageSwitcher } from '@/components/design/ui/LanguageSwitcher'
import { useT } from '@/i18n/I18nContext'

/**
 * Public, unauthenticated page. Required by Google's OAuth consent screen
 * before the app can leave testing mode and accept any Google account.
 */
export function PrivacyPage() {
  const t = useT()
  return (
    <div style={{ maxWidth: '68ch', margin: '0 auto', padding: '48px 24px 96px' }}>
      <div className="row-between" style={{ marginBottom: 32 }}>
        <BrandLockup to="/" />
        <LanguageSwitcher />
      </div>
      <h1 className="h1" style={{ marginBottom: 8 }}>{t('privacy.title')}</h1>
      <p className="small dim" style={{ marginBottom: 32 }}>{t('privacy.lastUpdated')}</p>

      <div className="stack" style={{ gap: 22, lineHeight: 1.65 }}>
        <p>{t('privacy.intro')}</p>

        <section>
          <h2 className="h2" style={{ marginBottom: 8 }}>{t('privacy.collectTitle')}</h2>
          <ul style={{ paddingLeft: '1.2em', display: 'grid', gap: 6 }}>
            <li>{t('privacy.collectAccount')}</li>
            <li>
              {t('privacy.collectGooglePrefix')}
              <code>openid</code>, <code>email</code>{t('privacy.scopesAnd')}<code>profile</code>
              {t('privacy.collectGoogleSuffix')}
            </li>
            <li>{t('privacy.collectSkills')}</li>
            <li>{t('privacy.collectActivity')}</li>
          </ul>
        </section>

        <section>
          <h2 className="h2" style={{ marginBottom: 8 }}>{t('privacy.useTitle')}</h2>
          <p>{t('privacy.useBody')}</p>
        </section>

        <section>
          <h2 className="h2" style={{ marginBottom: 8 }}>{t('privacy.meetingsTitle')}</h2>
          <p>{t('privacy.meetingsBody')}</p>
        </section>

        <section>
          <h2 className="h2" style={{ marginBottom: 8 }}>{t('privacy.retentionTitle')}</h2>
          <p>{t('privacy.retentionBody')}</p>
        </section>

        <section>
          <h2 className="h2" style={{ marginBottom: 8 }}>{t('privacy.contactTitle')}</h2>
          <p>
            {t('privacy.contactBodyPrefix')}
            <a className="link" href="mailto:patrickpriebepp@gmail.com">patrickpriebepp@gmail.com</a>
            {t('privacy.contactBodySuffix')}
          </p>
        </section>
      </div>
    </div>
  )
}
