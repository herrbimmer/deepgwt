import type { ReactNode } from 'react'
import { motion } from 'motion/react'
import { springs } from '../lib/motion-presets'
import { Squircle } from './Squircle'

export type ModalProps = {
  onClose?: () => void
  maxWidth?: string
  tone?: 'neutral' | 'danger'
  children?: ReactNode
}

// Nota: a entrada aqui é animada (fade do scrim + escala/opacidade da folha, "materializando"
// em vez de só cortar pra dentro). A SAÍDA precisaria que cada tela que usa <Modal> envolvesse
// seu `{show && <Modal>}` condicional num <AnimatePresence> próprio — não feito aqui pra não
// mexer em todo call site; o modal ainda some instantâneo ao fechar.
export function Modal({ onClose, maxWidth = 'max-w-md', tone = 'neutral', children }: ModalProps) {
  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-6">
      <motion.div
        className="absolute inset-0 bg-black/60 backdrop-blur-sm"
        onClick={onClose}
        initial={{ opacity: 0 }}
        animate={{ opacity: 1 }}
        transition={{ duration: 0.2 }}
      />
      {/* motion.create() não consegue mutar o estilo de um componente custom (Squircle não é
          um elemento DOM nativo) — o motion.div externo anima opacity/scale, o Squircle interno
          só desenha a forma/clip-path uma vez, herdando a transformação do pai. */}
      <motion.div
        className={`relative w-full ${maxWidth}`}
        initial={{ opacity: 0, scale: 0.95 }}
        animate={{ opacity: 1, scale: 1 }}
        transition={springs.sheet}
      >
        <Squircle
          radius={26}
          border={{ color: tone === 'danger' ? 'rgba(255,69,58,0.35)' : 'rgba(255,255,255,0.14)' }}
          className="bg-[#17181b]/85 backdrop-blur-2xl p-6 shadow-2xl"
        >
          {children}
        </Squircle>
      </motion.div>
    </div>
  )
}
