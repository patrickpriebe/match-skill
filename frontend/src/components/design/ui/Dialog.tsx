import { useEffect, useId, useRef } from 'react'
import type { ReactNode } from 'react'
import { createPortal } from 'react-dom'

export function Dialog({ title, eyebrow, onClose, footer, children }: {
  title: ReactNode; eyebrow?: ReactNode; onClose: () => void; footer?: ReactNode; children: ReactNode
}) {
  const titleId = useId()
  const dialogRef = useRef<HTMLDialogElement>(null)
  const closeRef = useRef(onClose)
  closeRef.current = onClose
  useEffect(() => {
    const previous = document.activeElement as HTMLElement | null
    const dialog = dialogRef.current
    const overflow = document.body.style.overflow
    document.body.style.overflow = 'hidden'
    dialog?.showModal()
    const trap = (event: KeyboardEvent) => {
      if (event.key !== 'Tab' || !dialog) return
      const elements = [...dialog.querySelectorAll<HTMLElement>('button:not(:disabled), a[href], input:not(:disabled), select:not(:disabled), textarea:not(:disabled), [tabindex="0"]')].filter((el) => el.getClientRects().length > 0)
      const first = elements[0], last = elements[elements.length - 1]
      if (!first) { event.preventDefault(); dialog.focus(); return }
      if (event.shiftKey && (document.activeElement === first || document.activeElement === dialog)) { event.preventDefault(); last.focus() }
      else if (!event.shiftKey && document.activeElement === last) { event.preventDefault(); first.focus() }
    }
    dialog?.addEventListener('keydown', trap)
    return () => { dialog?.removeEventListener('keydown', trap); dialog?.close(); document.body.style.overflow = overflow; previous?.focus() }
  }, [])
  return createPortal(<dialog ref={dialogRef} className="dialog-overlay" aria-labelledby={titleId}
    onCancel={(event) => { event.preventDefault(); closeRef.current() }}
    onClick={(event) => { if (event.target === event.currentTarget) closeRef.current() }}>
    <div className="dialog">
      <div className="dialog-head">{eyebrow && <p className="eyebrow">{eyebrow}</p>}<h2 className="h2" id={titleId}>{title}</h2></div>
      <div className="dialog-body">{children}</div>
      {footer && <div className="dialog-foot">{footer}</div>}
    </div>
  </dialog>, document.body)
}
export function PickOption({ selected, onSelect, title, description }: {
  selected: boolean; onSelect: () => void; title: ReactNode; description?: ReactNode
}) {
  return <button type="button" role="radio" aria-checked={selected} onClick={onSelect}
    onKeyDown={(event) => {
      const step = ['ArrowDown', 'ArrowRight'].includes(event.key) ? 1 : ['ArrowUp', 'ArrowLeft'].includes(event.key) ? -1 : 0
      if (!step) return
      event.preventDefault()
      const options = [...(event.currentTarget.closest('[role="radiogroup"]')?.querySelectorAll<HTMLButtonElement>('[role="radio"]') ?? [])]
      const index = options.indexOf(event.currentTarget)
      const next = options[(index + step + options.length) % options.length]
      next?.focus(); next?.click()
    }} className={selected ? 'pick is-picked' : 'pick'}>
    <span className="dot" aria-hidden="true" /><span><b>{title}</b>{description && <><br /><span className="dim small">{description}</span></>}</span>
  </button>
}
