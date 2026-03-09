import { defineStore } from 'pinia';

type ThemeMode = 'light' | 'dark';
type NavMode = 'expanded' | 'hidden';

const THEME_KEY = 'sm_theme_mode';
const NAV_KEY = 'sm_nav_mode';

let mediaQuery: MediaQueryList | null = null;
let mediaQueryListener: ((event: MediaQueryListEvent) => void) | null = null;

export const useUiStore = defineStore('ui', {
  state: () => ({
    theme: 'light' as ThemeMode,
    navMode: 'expanded' as NavMode,
    mobileDrawerOpen: false,
    isMobile: false,
    initialized: false
  }),

  actions: {
    initialize() {
      if (!import.meta.client || this.initialized) {
        return;
      }

      const savedTheme = localStorage.getItem(THEME_KEY);
      const savedNavMode = localStorage.getItem(NAV_KEY);

      if (savedTheme === 'light' || savedTheme === 'dark') {
        this.theme = savedTheme;
      }

      if (savedNavMode === 'expanded' || savedNavMode === 'hidden') {
        this.navMode = savedNavMode;
      } else if (savedNavMode === 'compact') {
        // Backward compatibility for old nav mode values.
        this.navMode = 'hidden';
      }

      this.applyTheme();

      mediaQuery = window.matchMedia('(max-width: 1023px)');
      this.isMobile = mediaQuery.matches;

      if (!mediaQueryListener) {
        mediaQueryListener = (event) => {
          this.isMobile = event.matches;
          if (!event.matches) {
            this.mobileDrawerOpen = false;
          }
        };
      }

      mediaQuery.addEventListener('change', mediaQueryListener);
      this.initialized = true;
    },

    applyTheme() {
      if (!import.meta.client) {
        return;
      }

      const isDark = this.theme === 'dark';
      const root = document.documentElement;
      root.classList.toggle('dark', isDark);
      root.classList.toggle('app-dark', isDark);
      localStorage.setItem(THEME_KEY, this.theme);
    },

    toggleTheme() {
      this.theme = this.theme === 'dark' ? 'light' : 'dark';
      this.applyTheme();
    },

    toggleNavigation() {
      if (this.isMobile) {
        this.mobileDrawerOpen = !this.mobileDrawerOpen;
        return;
      }

      this.navMode = this.navMode === 'expanded' ? 'hidden' : 'expanded';
      if (import.meta.client) {
        localStorage.setItem(NAV_KEY, this.navMode);
      }
    },

    closeMobileDrawer() {
      this.mobileDrawerOpen = false;
    }
  }
});
