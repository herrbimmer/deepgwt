import { BeakerIcon, CheckCircle2Icon, CircleDashedIcon } from 'lucide-react'
import { VALIDATION_LABEL, validationOf, type CapabilityId, type ValidationStatus } from '../validation/status'

const STYLE: Record<ValidationStatus, { cls: string; Icon: typeof BeakerIcon }> = {
  'vehicle-validated': { cls: 'bg-success/15 text-success', Icon: CheckCircle2Icon },
  'not-validated': { cls: 'bg-warning/15 text-warning', Icon: BeakerIcon },
  'not-implemented': { cls: 'bg-white/[0.06] text-text-tertiary', Icon: CircleDashedIcon },
}

export type ValidationBadgeProps = {
  capability?: CapabilityId
  status?: ValidationStatus
  className?: string
}

export function ValidationBadge({ capability, status, className = '' }: ValidationBadgeProps) {
  const resolved = status ?? validationOf(capability).status
  const { cls, Icon } = STYLE[resolved]
  return (
    <span
      className={`inline-flex items-center gap-1 rounded-full px-2 py-0.5 text-[11px] font-medium ${cls} ${className}`}
    >
      <Icon size={11} />
      {VALIDATION_LABEL[resolved]}
    </span>
  )
}
