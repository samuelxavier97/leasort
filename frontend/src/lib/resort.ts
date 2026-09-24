/**
 * Nome do Resort exibido na imagem de compartilhamento do convite (D-087). Vem de
 * `VITE_RESORT_NAME` no build de produção (Fase 11); sem a variável, ou com ela em branco, fica o
 * genérico "Resort". O nome real nunca entra num arquivo do repositório.
 */
export const RESORT_NAME = import.meta.env.VITE_RESORT_NAME?.trim() || 'Resort'
