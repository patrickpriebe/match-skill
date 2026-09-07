import { test } from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { URL } from 'node:url'
import { validateHostedApiBase } from './validate-hosted-env.mjs'

test('hosted build requires HTTPS API prefix and rejects credentials or URL suffixes', () => {
  for (const value of [undefined, '', '/api', 'http://api.example.test/api', 'https://api.example.test', 'https://user:secret@api.example.test/api', 'https://api.example.test/api?token=x', 'https://api.example.test/api#x']) {
    assert.equal(validateHostedApiBase(value), false)
  }
  assert.equal(validateHostedApiBase('https://api.example.test/api'), true)
  assert.equal(validateHostedApiBase('https://api.example.test/api/'), true)
})

test('SPA rewrite accepts callback and deep links while leaving API and assets outside HTML fallback', () => {
  const config = JSON.parse(readFileSync(new URL('../vercel.json', import.meta.url), 'utf8'))
  const rule = config.rewrites[0]
  const match = new RegExp('^' + rule.source + '$')
  for (const route of ['/', '/auth/callback', '/skills/register', '/profile/user-id', '/exchanges/exchange-id', '/scheduled/exchange-id']) assert.equal(match.test(route), true)
  for (const route of ['/api', '/api/auth/me', '/api/exchanges/id', '/assets/missing.js']) assert.equal(match.test(route), false)
  assert.equal(rule.destination, '/index.html')
})
