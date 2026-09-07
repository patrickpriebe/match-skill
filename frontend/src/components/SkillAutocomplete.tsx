import { useEffect, useRef, useState } from 'react'
import { Loader2, Plus, X } from 'lucide-react'
import { Input } from '@/components/ui/input'
import { Badge } from '@/components/ui/badge'
import { api, type Skill } from '@/lib/api'
import { cn } from '@/lib/utils'

interface SkillAutocompleteProps {
  label: string
  placeholder?: string
  selected: Skill[]
  onChange: (skills: Skill[]) => void
  excludeIds?: string[]
}

// Controlled vocabulary picker: GET /skills?query= drives the dropdown, the
// user always picks a row. A term with no match goes to POST /skills/suggest
// instead of being typed straight into the selected list — see docs/documentation.md
// "Skill vocabulary".
export function SkillAutocomplete({ label, placeholder, selected, onChange, excludeIds = [] }: SkillAutocompleteProps) {
  const [query, setQuery] = useState('')
  const [results, setResults] = useState<Skill[]>([])
  const [loading, setLoading] = useState(false)
  const [open, setOpen] = useState(false)
  const [suggesting, setSuggesting] = useState(false)
  const containerRef = useRef<HTMLDivElement>(null)

  useEffect(() => {
    if (!query.trim()) {
      setResults([])
      return
    }
    let cancelled = false
    setLoading(true)
    const timer = setTimeout(() => {
      api
        .searchSkills(query.trim())
        .then((page) => {
          if (!cancelled) setResults(page)
        })
        .finally(() => {
          if (!cancelled) setLoading(false)
        })
    }, 250)
    return () => {
      cancelled = true
      clearTimeout(timer)
    }
  }, [query])

  useEffect(() => {
    function handleClickOutside(event: MouseEvent) {
      if (containerRef.current && !containerRef.current.contains(event.target as Node)) {
        setOpen(false)
      }
    }
    document.addEventListener('mousedown', handleClickOutside)
    return () => document.removeEventListener('mousedown', handleClickOutside)
  }, [])

  const selectedIds = new Set(selected.map((s) => s.id))
  const excluded = new Set(excludeIds)
  const visibleResults = results.filter((s) => !selectedIds.has(s.id) && !excluded.has(s.id))
  const hasExactMatch = results.some((s) => s.name.toLowerCase() === query.trim().toLowerCase())

  function addSkill(skill: Skill) {
    onChange([...selected, skill])
    setQuery('')
    setResults([])
    setOpen(false)
  }

  function removeSkill(id: string) {
    onChange(selected.filter((s) => s.id !== id))
  }

  async function handleSuggest() {
    const name = query.trim()
    if (!name) return
    setSuggesting(true)
    try {
      const skill = await api.suggestSkill({ name })
      addSkill(skill)
    } finally {
      setSuggesting(false)
    }
  }

  return (
    <div className="space-y-2" ref={containerRef}>
      <label className="text-sm font-medium">{label}</label>

      <div className="flex flex-wrap gap-2">
        {selected.map((skill) => (
          <Badge key={skill.id} variant="secondary" className="pl-2.5 pr-1.5">
            {skill.name}
            <button
              type="button"
              onClick={() => removeSkill(skill.id)}
              className="ml-1 rounded-full p-0.5 hover:bg-black/10 dark:hover:bg-white/10"
              aria-label={`Remove ${skill.name}`}
            >
              <X className="h-3 w-3" />
            </button>
          </Badge>
        ))}
      </div>

      <div className="relative">
        <Input
          value={query}
          placeholder={placeholder}
          onChange={(e) => {
            setQuery(e.target.value)
            setOpen(true)
          }}
          onFocus={() => setOpen(true)}
        />
        {loading && (
          <Loader2 className="absolute right-3 top-1/2 h-4 w-4 -translate-y-1/2 animate-spin text-muted-foreground" />
        )}

        {open && query.trim() && (
          <div className="absolute z-10 mt-1 w-full rounded-md border border-border bg-card shadow-md">
            {visibleResults.length > 0 && (
              <ul className="max-h-56 overflow-auto py-1">
                {visibleResults.map((skill) => (
                  <li key={skill.id}>
                    <button
                      type="button"
                      className={cn('flex w-full items-center px-3 py-2 text-left text-sm hover:bg-accent hover:text-accent-foreground')}
                      onClick={() => addSkill(skill)}
                    >
                      {skill.name}
                    </button>
                  </li>
                ))}
              </ul>
            )}

            {!loading && !hasExactMatch && (
              <button
                type="button"
                disabled={suggesting}
                onClick={handleSuggest}
                className="flex w-full items-center gap-2 border-t border-border px-3 py-2 text-left text-sm text-muted-foreground hover:bg-accent disabled:opacity-50"
              >
                {suggesting ? <Loader2 className="h-3.5 w-3.5 animate-spin" /> : <Plus className="h-3.5 w-3.5" />}
                Suggest "{query.trim()}" for review
              </button>
            )}

            {!loading && visibleResults.length === 0 && hasExactMatch && (
              <div className="px-3 py-2 text-sm text-muted-foreground">Already in the list above.</div>
            )}
          </div>
        )}
      </div>
    </div>
  )
}
