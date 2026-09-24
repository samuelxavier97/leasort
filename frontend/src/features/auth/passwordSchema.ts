import { z } from 'zod'

/** Regras da D-049: mínimo de 10 caracteres e máximo de 72 bytes (limite do BCrypt). */
export const newPasswordSchema = z
  .string()
  .min(10, 'A senha deve ter no mínimo 10 caracteres.')
  .refine((value) => new TextEncoder().encode(value).length <= 72, 'A senha é longa demais.')
