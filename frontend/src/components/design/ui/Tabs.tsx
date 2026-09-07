import type { ReactNode } from 'react'

export interface TabItem {
  id: string
  label: ReactNode
  count?: number
}

export function Tabs({ items, active, onChange, label }: {
  items: TabItem[]
  active: string
  onChange: (id: string) => void
  label: string
}) {
  return (
    <div className="tabs" role="tablist" aria-label={label}>
      {items.map((item) => {
        const selected = item.id === active
        return (
          <button
            key={item.id}
            role="tab"
            type="button"
            aria-selected={selected}
            tabIndex={selected ? 0 : -1}
            onKeyDown={(e) => {
              const current = items.findIndex((i) => i.id === item.id)
              const next = e.key === 'Home' ? 0 : e.key === 'End' ? items.length - 1 : e.key === 'ArrowRight' ? (current + 1) % items.length : e.key === 'ArrowLeft' ? (current - 1 + items.length) % items.length : -1
              if (next < 0) return
              e.preventDefault()
              onChange(items[next].id)
              e.currentTarget.parentElement?.querySelectorAll<HTMLButtonElement>('[role="tab"]')[next]?.focus()
            }}
            className={selected ? 'is-active' : undefined}
            onClick={() => onChange(item.id)}
            style={{ background: 'none' }}
          >
            {item.label}
            {item.count !== undefined && <span className="count">{item.count}</span>}
          </button>
        )
      })}
    </div>
  )
}
