import type { Skill, User } from '../types'

// Seed vocabulary for the autocomplete mock. Real data comes from the
// backend's approved Skill table once GET /skills is live.
export const seedSkills: Skill[] = [
  { id: 'skill-js', name: 'JavaScript', slug: 'javascript', status: 'APPROVED' },
  { id: 'skill-ts', name: 'TypeScript', slug: 'typescript', status: 'APPROVED' },
  { id: 'skill-react', name: 'React', slug: 'react', status: 'APPROVED' },
  { id: 'skill-java', name: 'Java', slug: 'java', status: 'APPROVED' },
  { id: 'skill-spring', name: 'Spring Boot', slug: 'spring-boot', status: 'APPROVED' },
  { id: 'skill-python', name: 'Python', slug: 'python', status: 'APPROVED' },
  { id: 'skill-sql', name: 'SQL', slug: 'sql', status: 'APPROVED' },
  { id: 'skill-figma', name: 'Figma', slug: 'figma', status: 'APPROVED' },
  { id: 'skill-guitar', name: 'Guitar', slug: 'guitar', status: 'APPROVED' },
  { id: 'skill-spanish', name: 'Spanish', slug: 'spanish', status: 'APPROVED' },
  { id: 'skill-cooking', name: 'Cooking', slug: 'cooking', status: 'APPROVED' },
  { id: 'skill-photography', name: 'Photography', slug: 'photography', status: 'APPROVED' },
]

export const mockUsers: Array<User & { password: string }> = [
  {
    id: 'user-demo',
    email: 'demo@matchskill.dev',
    password: 'password123',
    displayName: 'Demo User',
    bio: 'Already registered skills — lands straight on Home.',
    timeZone: 'America/Sao_Paulo',
    skillsRegistered: true,
    createdAt: '2026-01-01T00:00:00.000Z',
  },
]
