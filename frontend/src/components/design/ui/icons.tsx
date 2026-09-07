// Monoline icon set. Every glyph is a distinct shape, because colour is never
// the only carrier of meaning in this system — a status badge pairs a colour
// with an icon and a text label.

type IconProps = { className?: string; size?: number }

const stroke = {
  fill: 'none',
  stroke: 'currentColor',
  strokeWidth: 2,
  strokeLinecap: 'round' as const,
  strokeLinejoin: 'round' as const,
}

function Svg({ size = 14, className, children }: IconProps & { children: React.ReactNode }) {
  return (
    <svg viewBox="0 0 24 24" width={size} height={size} className={className} aria-hidden="true">
      {children}
    </svg>
  )
}

export const IconClock = (p: IconProps) => (
  <Svg {...p}><g {...stroke}><circle cx="12" cy="12" r="9" /><path d="M12 7.5V12l3 2" /></g></Svg>
)

export const IconCheck = (p: IconProps) => (
  <Svg {...p}><g {...stroke} strokeWidth={2.4}><path d="M5 13l4 4L19 7" /></g></Svg>
)

export const IconCalendar = (p: IconProps) => (
  <Svg {...p}><g {...stroke}><rect x="3" y="5" width="18" height="16" rx="2" /><path d="M3 10h18M8 3v4M16 3v4" /></g></Svg>
)

export const IconCheckCircle = (p: IconProps) => (
  <Svg {...p}><g {...stroke}><circle cx="12" cy="12" r="9" /><path d="M8 12.4l2.6 2.6L16 9.6" /></g></Svg>
)

export const IconXCircle = (p: IconProps) => (
  <Svg {...p}><g {...stroke}><circle cx="12" cy="12" r="9" /><path d="M9 9l6 6M15 9l-6 6" /></g></Svg>
)

export const IconReturn = (p: IconProps) => (
  <Svg {...p}><g {...stroke}><path d="M9 14L4 9l5-5" /><path d="M4 9h11a5 5 0 0 1 0 10h-4" /></g></Svg>
)

export const IconInfo = (p: IconProps) => (
  <Svg {...p}><g {...stroke}><circle cx="12" cy="12" r="9" /><path d="M12 11v5M12 7.6v.3" /></g></Svg>
)

export const IconWarning = (p: IconProps) => (
  <Svg {...p}><g {...stroke}><path d="M12 4l9 16H3z" /><path d="M12 10v4M12 17.2v.2" /></g></Svg>
)

export const IconExternal = (p: IconProps) => (
  <Svg {...p}><g {...stroke}><path d="M14 4h6v6M20 4l-8.5 8.5" /><path d="M18 14v5a1 1 0 0 1-1 1H5a1 1 0 0 1-1-1V7a1 1 0 0 1 1-1h5" /></g></Svg>
)

export const IconStar = ({ size = 13, className, style }: IconProps & { style?: React.CSSProperties }) => (
  <svg viewBox="0 0 24 24" width={size} height={size} className={className} style={style} aria-hidden="true">
    <path fill="currentColor" d="M12 2.6l2.9 5.9 6.5.9-4.7 4.6 1.1 6.5L12 17.4 6.2 20.5l1.1-6.5L2.6 9.4l6.5-.9z" />
  </svg>
)

export const IconGoogle = ({ size = 17 }: IconProps) => (
  <svg viewBox="0 0 24 24" width={size} height={size} aria-hidden="true">
    <path fill="currentColor" opacity=".95" d="M21.6 12.2c0-.7-.1-1.3-.2-1.9H12v3.7h5.4a4.6 4.6 0 0 1-2 3v2.5h3.2c1.9-1.7 3-4.3 3-7.3z" />
    <path fill="currentColor" opacity=".7" d="M12 22c2.7 0 5-.9 6.6-2.4l-3.2-2.5c-.9.6-2 1-3.4 1-2.6 0-4.8-1.8-5.6-4.1H3.1v2.6A10 10 0 0 0 12 22z" />
    <path fill="currentColor" opacity=".5" d="M6.4 14a6 6 0 0 1 0-3.8V7.6H3.1a10 10 0 0 0 0 8.9L6.4 14z" />
    <path fill="currentColor" opacity=".85" d="M12 5.9c1.5 0 2.8.5 3.8 1.5l2.8-2.8A10 10 0 0 0 3.1 7.6l3.3 2.6C7.2 7.7 9.4 5.9 12 5.9z" />
  </svg>
)
