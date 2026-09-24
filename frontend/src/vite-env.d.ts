/// <reference types="vite/client" />

interface ImportMetaEnv {
  /** Nome do Resort, definido só no build de produção (D-087). */
  readonly VITE_RESORT_NAME?: string
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}
