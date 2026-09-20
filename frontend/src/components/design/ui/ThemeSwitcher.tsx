import { Monitor, Moon, Sun } from 'lucide-react'
import { useTheme, type ThemePreference } from '@/context/ThemeContext'
import { useT } from '@/i18n/I18nContext'

const OPTIONS: Array<{ value: ThemePreference; Icon: typeof Sun }> = [
  { value: 'light', Icon: Sun },
  { value: 'dark', Icon: Moon },
  { value: 'system', Icon: Monitor },
]

/**
 * Three buttons rather than a two-way toggle, because the third state is a
 * real answer and not a default: "follow my device" is what most people
 * actually want, and a toggle cannot express it.
 */
export function ThemeSwitcher({ className }: { className?: string }) {
  const t = useT()
  const { preference, setPreference } = useTheme()
  return (
    <div
      className={['lang-switch', className].filter(Boolean).join(' ')}
      role="group"
      aria-label={t('theme.label')}
    >
      {OPTIONS.map(({ value, Icon }) => (
        <button
          key={value}
          type="button"
          className={preference === value ? 'is-active' : undefined}
          aria-pressed={preference === value}
          title={t(`theme.${value}`)}
          onClick={() => setPreference(value)}
        >
          <Icon size={13} aria-hidden />
          <span className="sr-only">{t(`theme.${value}`)}</span>
        </button>
      ))}
    </div>
  )
}
