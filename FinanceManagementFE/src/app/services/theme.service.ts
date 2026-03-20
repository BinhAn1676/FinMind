import { Injectable } from '@angular/core';
import { BehaviorSubject } from 'rxjs';

@Injectable({ providedIn: 'root' })
export class ThemeService {
  private themeSubject = new BehaviorSubject<string>(
    localStorage.getItem('theme') || 'dark'
  );
  theme$ = this.themeSubject.asObservable();

  constructor() { this.applyTheme(this.themeSubject.value); }

  toggle(): void {
    this.applyTheme(this.themeSubject.value === 'dark' ? 'light' : 'dark');
  }

  isDark(): boolean { return this.themeSubject.value === 'dark'; }

  private applyTheme(theme: string): void {
    document.documentElement.setAttribute('data-theme', theme);
    localStorage.setItem('theme', theme);
    this.swapPrimeNGTheme(theme);
    this.themeSubject.next(theme);
  }

  private swapPrimeNGTheme(theme: string): void {
    const id = 'primeng-theme-link';
    let link = document.getElementById(id) as HTMLLinkElement | null;
    if (!link) {
      link = document.createElement('link');
      link.id = id; link.rel = 'stylesheet';
      document.head.appendChild(link);
    }
    link.href = `assets/primeng-themes/lara-${theme}-blue/theme.css`;
  }
}
