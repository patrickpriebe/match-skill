import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { BrowserRouter } from 'react-router-dom'
import { App } from './App'
import { I18nProvider } from './i18n/I18nContext'
import '@fontsource-variable/source-serif-4'
import '@fontsource-variable/public-sans'
import '@fontsource/jetbrains-mono/latin-400.css'
import '@/styles/globals.css'
import '@/styles/match-skill.css'
import '@/styles/pages.css'
import '@/styles/integration.css'

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <BrowserRouter>
      <I18nProvider>
        <App />
      </I18nProvider>
    </BrowserRouter>
  </StrictMode>,
)
