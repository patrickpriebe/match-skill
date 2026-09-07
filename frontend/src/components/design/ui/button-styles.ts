export type ButtonVariant = 'primary' | 'secondary' | 'ghost' | 'quiet' | 'stop'
export type ButtonSize = 'md' | 'sm'

export const cx = (...parts: Array<string | false | undefined>) => parts.filter(Boolean).join(' ')

export function buttonClass(
  variant: ButtonVariant = 'secondary',
  size: ButtonSize = 'md',
  extra?: string,
) {
  return cx('btn', 'btn-' + variant, size === 'sm' && 'btn-sm', extra)
}

