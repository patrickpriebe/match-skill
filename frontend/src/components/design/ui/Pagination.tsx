import { Button } from './Button'
export function Pagination({ page, size, total, onChange }: {
  page: number; size: number; total: number; onChange: (page: number) => void
}) {
  if (total === 0) return null
  return <nav className="pagination row-between wrap" aria-label="Pagination">
    <span className="meta">{page * size + 1}–{Math.min((page + 1) * size, total)} of {total}</span>
    <div className="row" style={{ gap: 8 }}>
      <Button size="sm" disabled={page === 0} onClick={() => onChange(page - 1)}>Previous</Button>
      <Button size="sm" disabled={(page + 1) * size >= total} onClick={() => onChange(page + 1)}>Next</Button>
    </div>
  </nav>
}
