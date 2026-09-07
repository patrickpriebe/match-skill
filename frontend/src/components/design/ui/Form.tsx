import { useId } from 'react'
import type { InputHTMLAttributes, SelectHTMLAttributes, TextareaHTMLAttributes, ReactNode } from 'react'

const cx = (...p: Array<string | false | undefined>) => p.filter(Boolean).join(' ')

interface FieldProps {
  label: ReactNode
  hint?: ReactNode
  error?: ReactNode
  ok?: ReactNode
  className?: string
  children: (id: string, invalid: boolean) => ReactNode
}

/**
 * Label, control, hint and error in one place. The error is rendered inline
 * and persistently — never as a toast that disappears before it is read.
 */
export function Field({ label, hint, error, ok, className, children }: FieldProps) {
  const id = useId()
  return (
    <div className={cx('field', className)}>
      <label htmlFor={id}>{label}</label>
      {children(id, Boolean(error))}
      {error && <span className="err-text">{error}</span>}
      {!error && ok && <span className="ok-text">{ok}</span>}
      {hint && <span className="hint">{hint}</span>}
    </div>
  )
}

type InputProps = InputHTMLAttributes<HTMLInputElement> & { invalid?: boolean; mono?: boolean }

export function Input({ invalid, mono, className, ...rest }: InputProps) {
  return (
    <input
      {...rest}
      aria-invalid={invalid || undefined}
      className={cx('input', mono && 'num', invalid && 'input-err', className)}
    />
  )
}

type SelectProps = SelectHTMLAttributes<HTMLSelectElement> & { invalid?: boolean }

export function Select({ invalid, className, ...rest }: SelectProps) {
  return (
    <select
      {...rest}
      aria-invalid={invalid || undefined}
      className={cx('select', invalid && 'input-err', className)}
    />
  )
}

type TextareaProps = TextareaHTMLAttributes<HTMLTextAreaElement> & { invalid?: boolean }

export function Textarea({ invalid, className, ...rest }: TextareaProps) {
  return (
    <textarea
      {...rest}
      aria-invalid={invalid || undefined}
      className={cx('textarea', invalid && 'input-err', className)}
    />
  )
}
