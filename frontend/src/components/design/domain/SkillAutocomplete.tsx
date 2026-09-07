import { useEffect, useId, useRef, useState } from 'react'
import { SkillBadge } from './SkillBadge'
import type { Skill } from '@/lib/api/types'
import { ApiError } from '@/lib/api/types'

export function SkillAutocomplete({ value, onChange, onSearch, onSuggest, multiple = true, max, disabled, label, hint }: {
  value: Skill[]; onChange: (next: Skill[]) => void; onSearch: (query: string) => Promise<Skill[]>
  onSuggest?: (name: string) => Promise<Skill>; multiple?: boolean; max?: number
  disabled?: boolean; label: string; hint?: React.ReactNode
}) {
  const id = useId()
  const listId = id + '-list'
  const box = useRef<HTMLDivElement>(null)
  const [query, setQuery] = useState('')
  const [results, setResults] = useState<Skill[]>([])
  const [open, setOpen] = useState(false)
  const [cursor, setCursor] = useState(0)
  const [state, setState] = useState<'idle' | 'loading' | 'ready' | 'error'>('idle')
  const [suggesting, setSuggesting] = useState(false)
  const [suggestError, setSuggestError] = useState<string | null>(null)
  const visible = results.filter((s) => !value.some((v) => v.id === s.id))
  const exact = [...results, ...value].some((s) => s.name.toLowerCase() === query.trim().toLowerCase())
  const canSuggest = Boolean(onSuggest) && query.trim().length > 0 && !exact && state === 'ready'
  const count = visible.length + (canSuggest ? 1 : 0)
  const blocked = disabled || suggesting || (max !== undefined && value.length >= max)
  useEffect(() => {
    if (!query.trim()) { setResults([]); setState('idle'); return }
    let cancelled = false
    setState('loading')
    const timer = window.setTimeout(() => {
      onSearch(query.trim()).then((skills) => { if (!cancelled) { setResults(skills); setState('ready') } })
        .catch(() => { if (!cancelled) { setResults([]); setState('error') } })
    }, 200)
    return () => { cancelled = true; window.clearTimeout(timer) }
  }, [query, onSearch])
  useEffect(() => {
    const close = (e: Event) => { if (!box.current?.contains(e.target as Node)) setOpen(false) }
    document.addEventListener('pointerdown', close)
    document.addEventListener('focusin', close)
    return () => { document.removeEventListener('pointerdown', close); document.removeEventListener('focusin', close) }
  }, [])
  function add(skill: Skill) {
    if (blocked) return
    setSuggestError(null)
    onChange(multiple ? [...value, skill] : [skill]); setQuery(''); setResults([]); setState('idle'); setOpen(false); setCursor(0)
  }
  async function suggest() {
    if (!onSuggest || !canSuggest || suggesting) return
    setSuggesting(true); setSuggestError(null)
    try {
      const skill = await onSuggest(query.trim())
      onChange(multiple ? [...value, skill] : [skill]); setQuery(''); setOpen(false)
    } catch (error) {
      if (error instanceof ApiError && error.code === 'SKILL_IDENTITY_CONFLICT') {
        setSuggestError('This name conflicts with an existing skill. Your suggestion was not saved. Choose an approved entry, or retry after the vocabulary is reviewed.')
      } else if (error instanceof ApiError && error.code === 'INVALID_SKILL_NAME') {
        setSuggestError('Enter a meaningful skill name, such as C or C++. Your suggestion was not saved.')
      } else setSuggestError(error instanceof Error ? error.message : 'Could not submit the suggestion.')
    }
    finally { setSuggesting(false) }
  }
  function keyDown(e: React.KeyboardEvent<HTMLInputElement>) {
    if (e.key === 'Escape') { setOpen(false); return }
    if (e.key === 'ArrowDown' || e.key === 'ArrowUp') {
      e.preventDefault(); setOpen(true)
      if (count) setCursor((n) => Math.max(0, Math.min(count - 1, open ? n + (e.key === 'ArrowDown' ? 1 : -1) : 0)))
    }
    if (e.key === 'Enter' && open) {
      e.preventDefault()
      if (state !== 'ready') return
      if (visible[cursor]) add(visible[cursor])
      else if (canSuggest && cursor === visible.length) void suggest()
    }
  }
  return <div className="field" ref={box}>
    {!!value.length && <div className="chips">{value.map((skill) => <SkillBadge key={skill.id} skill={skill} tone={multiple ? 'wanted' : 'offered'}
      onRemove={disabled || suggesting ? undefined : (s) => onChange(value.filter((v) => v.id !== s.id))} />)}</div>}
    <label htmlFor={id}>{label}</label>
    <div className="combo">
      <input id={id} className="input" role="combobox" aria-expanded={open && Boolean(query.trim())} aria-controls={listId}
        aria-autocomplete="list" aria-invalid={Boolean(suggestError) || undefined}
        aria-describedby={[hint && id + '-hint', suggestError && id + '-error'].filter(Boolean).join(' ') || undefined}
        aria-activedescendant={open && state === 'ready' && count > cursor ? listId + '-' + cursor : undefined}
        disabled={blocked} value={query} placeholder="Start typing - pick from the list"
        onChange={(e) => { setQuery(e.target.value); setResults([]); setState('loading'); setOpen(true); setCursor(0); setSuggestError(null) }}
        onFocus={() => setOpen(true)} onKeyDown={keyDown} />
      {open && query.trim() && <div className="combo-pop" id={listId} role="listbox" aria-label="Skill suggestions">
        {state === 'ready' && visible.map((skill, i) => <button key={skill.id} id={listId + '-' + i} role="option" type="button"
          tabIndex={-1} aria-selected={cursor === i} className="combo-opt" onMouseDown={(e) => e.preventDefault()} onMouseEnter={() => setCursor(i)} onClick={() => add(skill)}>{skill.name}<span className="kind">approved</span></button>)}
        {canSuggest && <button role="option" type="button" tabIndex={-1} id={listId + '-' + visible.length} aria-selected={cursor === visible.length}
          disabled={suggesting} className="combo-opt combo-suggest" onMouseDown={(e) => e.preventDefault()} onClick={() => void suggest()}>Suggest &ldquo;{query.trim()}&rdquo; for review</button>}
      </div>}
    </div>
    {open && state === 'loading' && <span role="status" className="hint">Searching skills...</span>}
    {open && state === 'error' && <span role="alert" className="err-text">Cannot reach the skill list. Try typing again; suggestions are unavailable until the list loads.</span>}
    {open && state === 'ready' && !count && <span role="status" className="hint">{exact ? 'Already selected.' : 'No approved skills found.'}</span>}
    {suggestError && <span id={id + '-error'} role="alert" className="err-text">{suggestError}</span>}
    {hint && <span id={id + '-hint'} className="hint">{hint}</span>}
  </div>
}
