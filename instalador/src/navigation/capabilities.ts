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
