// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
// Registra o resolvedor de `./modulo` -> `./modulo.ts` no carregador de módulos do Node.
import { register } from 'node:module'
import { pathToFileURL } from 'node:url'

register('./ts-resolve.mjs', pathToFileURL(`${import.meta.dirname}/`))
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
