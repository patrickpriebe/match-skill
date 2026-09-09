import { useI18n } from '@/i18n/I18nContext'

/**
 * Two-way toggle rather than a dropdown: there are only ever two languages,
 * so a select would add a click for no reason.
 */
export function LanguageSwitcher({ className }: { className?: string }) {
  const { locale, setLocale, t } = useI18n()
  return (
    <div className={['lang-switch', className].filter(Boolean).join(' ')} role="group" aria-label={t('language.label')}>
      <button
        type="button"
        className={locale === 'en' ? 'is-active' : undefined}
        aria-pressed={locale === 'en'}
        onClick={() => setLocale('en')}
      >
        EN
      </button>
      <button
        type="button"
        className={locale === 'pt' ? 'is-active' : undefined}
        aria-pressed={locale === 'pt'}
        onClick={() => setLocale('pt')}
      >
        PT
      </button>
    </div>
  )
}
