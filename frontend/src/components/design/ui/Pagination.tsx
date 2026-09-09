import { Button } from './Button'
import { useT } from '@/i18n/I18nContext'
export function Pagination({ page, size, total, onChange }: {
  page: number; size: number; total: number; onChange: (page: number) => void
}) {
  const t = useT()
  if (total === 0) return null
  return <nav className="pagination row-between wrap" aria-label={t('pagination.label')}>
    <span className="meta">{t('pagination.range', { start: page * size + 1, end: Math.min((page + 1) * size, total), total })}</span>
    <div className="row" style={{ gap: 8 }}>
      <Button size="sm" disabled={page === 0} onClick={() => onChange(page - 1)}>{t('pagination.previous')}</Button>
      <Button size="sm" disabled={(page + 1) * size >= total} onClick={() => onChange(page + 1)}>{t('pagination.next')}</Button>
    </div>
  </nav>
}
