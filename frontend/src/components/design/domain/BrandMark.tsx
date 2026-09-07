import { Link } from 'react-router-dom'

/**
 * Interlock symbol — two brackets in 180° rotation, framing a space neither
 * encloses alone. Monochrome inside the product: the accent budget belongs to
 * the primary action, not to the logo. See brand-spec.md.
 */
export function BrandMark({ className }: { className?: string }) {
  return (
    <svg
      className={className ?? 'brand-mark'}
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth={2.6}
      strokeLinecap="square"
      aria-hidden="true"
    >
      <path d="M4 9V4h6" />
      <path d="M20 15v5h-6" />
    </svg>
  )
}

/** The full lockup: mark, gap, wordmark with the accent middot. */
export function BrandLockup({ to = '/', className }: { to?: string; className?: string }) {
  return (
    <Link className={className ?? 'side-brand'} to={to}>
      <BrandMark />
      match<span className="dot">·</span>skill
    </Link>
  )
}
