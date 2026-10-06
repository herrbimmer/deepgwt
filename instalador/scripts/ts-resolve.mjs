// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
// Faz o Node encontrar `./storageKeys` quando o arquivo é `storageKeys.ts`.
//
// O código-fonte importa sem extensão, porque é o Vite que resolve isso no app. O Node exige a
// extensão. Em vez de poluir o código do app com `.ts` em todo import só para agradar os testes,
// o ajuste fica aqui: um resolvedor que tenta `.ts` e `/index.ts` antes de desistir.
//
// Registrado por `--import` na linha de comando dos testes.

import { existsSync } from 'node:fs'
import { fileURLToPath, pathToFileURL } from 'node:url'

export async function resolve(specifier, context, next) {
  // `src/lib/version.ts` importa o package.json sem atributo de import, porque é o Vite que
  // resolve isso no app. O Node exige `with { type: 'json' }` e recusa o módulo sem ele — então o
  // atributo é acrescentado aqui, pelo mesmo motivo da extensão: o teste se adapta ao código do
  // app, não o contrário.
  if (specifier.endsWith('.json')) {
    const resolved = await next(specifier, context)
    return { ...resolved, format: 'json', importAttributes: { type: 'json' } }
  }
  if (specifier.startsWith('.') && !/\.[a-z]+$/i.test(specifier)) {
    const base = new URL(specifier, context.parentURL)
    for (const candidate of [`${base.href}.ts`, `${base.href}.tsx`, `${base.href}/index.ts`]) {
      if (existsSync(fileURLToPath(candidate))) {
        return next(pathToFileURL(fileURLToPath(candidate)).href, context)
      }
    }
  }
  return next(specifier, context)
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
