// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
import { validationOf } from '../validation/status'
import type { RouteNode } from './routeMeta'

/** Separa o que tem conteúdo do que ainda é só estrutura (`not-implemented`). */
export function splitByImplementation(items: RouteNode[]): { ready: RouteNode[]; pending: RouteNode[] } {
  const ready: RouteNode[] = []
  const pending: RouteNode[] = []
  for (const item of items) {
    ;(validationOf(item.capability).status === 'not-implemented' ? pending : ready).push(item)
  }
  return { ready, pending }
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
