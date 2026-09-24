import { BrowserQRCodeReader, type IScannerControls } from '@zxing/browser'
import { useEffect, useRef } from 'react'
import type { CameraProblem } from './labels'

function problemOf(error: unknown): CameraProblem {
  const name = error instanceof Error || error instanceof DOMException ? error.name : ''
  return name === 'NotAllowedError' || name === 'SecurityError' ? 'denied' : 'unavailable'
}

/**
 * Leitor de QR com `@zxing/browser` (D-030). Entrega o texto lido como está (o backend normaliza,
 * §12.1) e para a câmera logo após a primeira leitura e ao sair da tela.
 */
export function QrScanner({ onRead, onProblem }: { onRead: (text: string) => void; onProblem: (problem: CameraProblem) => void }) {
  const containerRef = useRef<HTMLDivElement>(null)
  // Os callbacks mudam a cada render; o efeito só deve abrir a câmera uma vez por montagem.
  const handlers = useRef({ onRead, onProblem })
  useEffect(() => {
    handlers.current = { onRead, onProblem }
  })

  useEffect(() => {
    if (!window.isSecureContext) {
      handlers.current.onProblem('insecure')
      return
    }
    if (!navigator.mediaDevices?.getUserMedia) {
      handlers.current.onProblem('unavailable')
      return
    }

    // Cada abertura da câmera tem o seu <video>: ao parar, o zxing limpa o vídeo que recebeu, e um
    // elemento compartilhado apagaria a imagem de uma abertura seguinte (o StrictMode monta duas vezes).
    const video = document.createElement('video')
    video.setAttribute('aria-label', 'Imagem da câmera')
    video.className = 'aspect-square w-full rounded-md bg-black object-cover'
    video.muted = true
    video.playsInline = true
    containerRef.current?.append(video)

    let active = true
    let controls: IScannerControls | undefined
    const stop = () => {
      active = false
      controls?.stop()
      video.remove()
    }

    new BrowserQRCodeReader()
      .decodeFromConstraints({ video: { facingMode: 'environment' } }, video, (result, _error, scanControls) => {
        if (!active || !result) return
        active = false
        scanControls.stop()
        handlers.current.onRead(result.getText())
      })
      .then((started) => {
        controls = started
        // A tela pode ter sido fechada enquanto a câmera abria.
        if (!active) started.stop()
      })
      .catch((error: unknown) => {
        if (active) handlers.current.onProblem(problemOf(error))
      })

    return stop
  }, [])

  return <div ref={containerRef} />
}
