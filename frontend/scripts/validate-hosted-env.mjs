import process from 'node:process'
import { pathToFileURL, URL } from 'node:url'

export function validateHostedApiBase(value) {
  try {
    const url = new URL(value)
    return url.protocol === 'https:' && Boolean(url.hostname) &&
      !url.username && !url.password && !url.search && !url.hash &&
      /^\/api\/?$/.test(url.pathname)
  } catch {
    return false
  }
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  if (!validateHostedApiBase(process.env.VITE_API_BASE_URL)) {
    process.stderr.write('VITE_API_BASE_URL must be an absolute HTTPS backend URL ending in /api, without credentials, query or fragment. Set this public build variable in the Vercel project.\n')
    process.exitCode = 1
  }
}
