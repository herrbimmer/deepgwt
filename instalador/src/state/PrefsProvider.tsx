import { createContext, useCallback, useContext, useMemo, useState, type ReactNode } from 'react'
import { loadPrefs, savePrefs, type Prefs } from './prefs'

type PrefsApi = {
  prefs: Prefs
  update: (patch: Partial<Prefs>) => void
}

const PrefsContext = createContext<PrefsApi | null>(null)

/** Fonte única das preferências. Tudo que precisa persistir uma escolha do usuário passa por aqui. */
export function PrefsProvider({ children }: { children: ReactNode }) {
  const [prefs, setPrefs] = useState<Prefs>(() => loadPrefs())

  const update = useCallback((patch: Partial<Prefs>) => {
    setPrefs(savePrefs(patch))
  }, [])

  const value = useMemo<PrefsApi>(() => ({ prefs, update }), [prefs, update])

  return <PrefsContext.Provider value={value}>{children}</PrefsContext.Provider>
}

export function usePrefs(): PrefsApi {
  const ctx = useContext(PrefsContext)
  if (!ctx) throw new Error('usePrefs precisa estar dentro de <PrefsProvider>')
  return ctx
}
