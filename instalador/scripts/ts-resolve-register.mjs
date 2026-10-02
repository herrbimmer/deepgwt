// Registra o resolvedor de `./modulo` -> `./modulo.ts` no carregador de módulos do Node.
import { register } from 'node:module'
import { pathToFileURL } from 'node:url'

register('./ts-resolve.mjs', pathToFileURL(`${import.meta.dirname}/`))
