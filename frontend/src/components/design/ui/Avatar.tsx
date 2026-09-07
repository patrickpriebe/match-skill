const cx = (...p: Array<string | false | undefined>) => p.filter(Boolean).join(' ')

/**
 * Initials only. The User model carries no avatar field, so there is no image
 * to render and no placeholder pretending otherwise. No colour hashed from
 * the name either — a random colour communicates nothing.
 */
export function Avatar({ name, size = 'md', className }: {
  name: string
  size?: 'sm' | 'md' | 'lg'
  className?: string
}) {
  const initials = name
    .split(/\s+/)
    .filter(Boolean)
    .slice(0, 2)
    .map((part) => part[0]?.toUpperCase() ?? '')
    .join('')

  return (
    <span
      className={cx('avatar', size === 'sm' && 'sm', size === 'lg' && 'lg', className)}
      aria-hidden="true"
    >
      {initials}
    </span>
  )
}
