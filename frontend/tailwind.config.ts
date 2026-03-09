import type { Config } from 'tailwindcss';

export default {
  darkMode: 'class',
  content: [
    './app.vue',
    './components/**/*.{vue,js,ts}',
    './layouts/**/*.vue',
    './middleware/**/*.{js,ts}',
    './pages/**/*.vue',
    './plugins/**/*.{js,ts}',
    './stores/**/*.{js,ts}'
  ],
  theme: {
    extend: {
      colors: {
        brand: {
          50: '#eef9f5',
          100: '#d7f0e6',
          200: '#b2e2cf',
          300: '#84d0b3',
          400: '#52b992',
          500: '#2c9e76',
          600: '#1f7f5f',
          700: '#1d654f',
          800: '#1a5140',
          900: '#164336'
        }
      }
    }
  }
} satisfies Config;
