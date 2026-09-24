export type Role = 'ADMIN' | 'PROSPECTOR' | 'GATE' | 'HOST'

export interface Me {
  id: string
  name: string
  email: string
  role: Role
  mustChangePassword: boolean
}
