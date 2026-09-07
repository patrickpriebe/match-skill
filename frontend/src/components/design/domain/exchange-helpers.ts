import type { ExchangeView, Participant } from '@/lib/api/types'

export function counterpart(exchange: ExchangeView, meId: string): Participant {
  return exchange.requesterId === meId ? exchange.receiver : exchange.requester
}

export function isIncoming(exchange: ExchangeView, meId: string) {
  return exchange.receiverId === meId
}

