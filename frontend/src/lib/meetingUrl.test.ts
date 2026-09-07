import { describe, expect, it } from 'vitest'
import { meetingUrlError } from './meetingUrl'

describe('meeting URL client contract', () => {
  it.each([255, 256, 2048])('accepts a complete HTTPS URL of %i characters', (length) => {
    expect(meetingUrlError('https://meet.google.com/'.padEnd(length, 'a'))).toBeNull()
  })
  it('rejects 2049 characters and measures the trimmed payload', () => {
    const link = 'https://meet.google.com/'.padEnd(2048, 'a')
    expect(meetingUrlError(link + 'a')).toContain('2048')
    expect(meetingUrlError(' ' + link + ' ')).toBeNull()
  })
  it('rejects invalid URLs and leaves provider authorization to Spring', () => {
    for (const value of ['', 'https://', 'http://meet.google.com/room', 'javascript:alert(1)']) expect(meetingUrlError(value)).not.toBeNull()
    expect(meetingUrlError('https://new-provider.example.test/room')).toBeNull()
  })
})
