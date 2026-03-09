import { Injectable } from '@angular/core';
import { BehaviorSubject, Observable } from 'rxjs';

export interface Translation {
  [key: string]: string;
}

@Injectable({
  providedIn: 'root'
})
export class LanguageService {
  private currentLanguageSubject = new BehaviorSubject<string>('vi');
  public currentLanguage$ = this.currentLanguageSubject.asObservable();

  private translations: { [key: string]: Translation } = {};
  private missingLanguageWarnings = new Set<string>();

  constructor() {
    this.loadTranslations();
  }

  private async loadTranslations(): Promise<void> {
    try {
      // Load Vietnamese translations
      const viResponse = await fetch('/assets/i18n/vi.json');
      this.translations['vi'] = await viResponse.json();

      // Load English translations
      const enResponse = await fetch('/assets/i18n/en.json');
      this.translations['en'] = await enResponse.json();

      // Set initial language from localStorage or default to Vietnamese
      const savedLanguage = localStorage.getItem('language') || 'vi';
      this.setLanguage(savedLanguage);
    } catch (error) {
      console.error('Error loading translations:', error);
      // Fallback to default language
      this.setLanguage('vi');
    }
  }

  setLanguage(language: string): void {
    this.currentLanguageSubject.next(language);
    localStorage.setItem('language', language);
    document.documentElement.lang = language;
  }

  getCurrentLanguage(): string {
    return this.currentLanguageSubject.value;
  }

  translate(key: string): string {
    const currentLang = this.getCurrentLanguage();
    
    // Check if translations are loaded
    if (!this.translations[currentLang]) {
      if (!this.missingLanguageWarnings.has(currentLang)) {
        console.warn(`Translations not loaded for language: ${currentLang}`);
        this.missingLanguageWarnings.add(currentLang);
      }
      return key;
    }
    
    const keys = key.split('.');
    let result: any = this.translations[currentLang];
    
    for (const k of keys) {
      if (result === undefined || result === null) {
        console.warn(`Translation key path broken at: ${k} for key: ${key} in language: ${currentLang}`);
        return key;
      }
      result = result[k];
    }
    
    if (!result || typeof result !== 'string') {
      console.warn(`Translation missing for key: ${key} in language: ${currentLang}`, {
        found: result,
        type: typeof result
      });
      return key; // Return the key itself if translation is missing
    }
    return result;
  }

  getTranslations(): { [key: string]: Translation } {
    return this.translations;
  }

  isLanguageLoaded(language: string): boolean {
    return !!this.translations[language];
  }
}
