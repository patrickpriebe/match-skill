import type { ButtonHTMLAttributes, AnchorHTMLAttributes, ReactNode } from 'react'
import { Link } from 'react-router-dom'

import { buttonClass, cx, type ButtonVariant, type ButtonSize } from './button-styles'

interface Common {
  variant?: ButtonVariant
  size?: ButtonSize
  wide?: boolean
  className?: string
  children: ReactNode
}

type ButtonProps = Common &
  Omit<ButtonHTMLAttributes<HTMLButtonElement>, 'className' | 'children'> & {
    /** Keeps the label and adds a spinner. Never swap the label for dots. */
    loading?: boolean
  }

export function Button({
  variant = 'secondary', size = 'md', wide, className, loading, children, disabled, ...rest
}: ButtonProps) {
  return (
    <button
      {...rest}
      disabled={disabled || loading}
      aria-busy={loading || undefined}
      className={buttonClass(variant, size, cx(wide && 'btn-wide', className))}
    >
      {loading && <span className={cx('spinner', variant !== 'primary' && 'spinner-fg')} />}
      {children}
    </button>
  )
}

type LinkButtonProps = Common & { to: string } &
  Omit<AnchorHTMLAttributes<HTMLAnchorElement>, 'className' | 'children' | 'href'>

/** Same visual contract as Button, but it navigates. */
export function LinkButton({
  to, variant = 'secondary', size = 'md', wide, className, children, ...rest
}: LinkButtonProps) {
  return (
    <Link {...rest} to={to} className={buttonClass(variant, size, cx(wide && 'btn-wide', className))}>
      {children}
    </Link>
  )
}
