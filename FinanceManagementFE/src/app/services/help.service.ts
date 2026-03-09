import { Injectable } from '@angular/core';
import { NavigationEnd, Router } from '@angular/router';
import { BehaviorSubject } from 'rxjs';
import { filter } from 'rxjs/operators';
import { LanguageService } from './language.service';

export interface HelpTip {
  icon: string;
  text: string;
}

export interface HelpContent {
  title: string;
  description: string;
  tips: HelpTip[];
}

@Injectable({
  providedIn: 'root'
})
export class HelpService {
  private isOpenSubject = new BehaviorSubject<boolean>(false);
  public isOpen$ = this.isOpenSubject.asObservable();

  private currentHelpSubject = new BehaviorSubject<HelpContent | null>(null);
  public currentHelp$ = this.currentHelpSubject.asObservable();

  private helpData: { [lang: string]: { [route: string]: HelpContent } } = {};
  private currentRoute = '';

  // Routes that should NOT show the help button (public pages)
  private excludedRoutes = ['/home', '/login', '/contact', '/notices'];

  constructor(private router: Router, private languageService: LanguageService) {
    this.loadHelpData();

    this.router.events
      .pipe(filter(event => event instanceof NavigationEnd))
      .subscribe((event: any) => {
        this.currentRoute = event.urlAfterRedirects.split('?')[0];
        this.updateCurrentHelp();
        this.isOpenSubject.next(false);
      });

    this.languageService.currentLanguage$.subscribe(() => {
      this.updateCurrentHelp();
    });
  }

  private async loadHelpData(): Promise<void> {
    try {
      const [viRes, enRes] = await Promise.all([
        fetch('/assets/help/help-content.vi.json'),
        fetch('/assets/help/help-content.en.json')
      ]);
      this.helpData['vi'] = await viRes.json();
      this.helpData['en'] = await enRes.json();
      this.updateCurrentHelp();
    } catch (e) {
      console.warn('Could not load help content', e);
    }
  }

  private updateCurrentHelp(): void {
    const lang = this.languageService.getCurrentLanguage();
    const data = this.helpData[lang] || this.helpData['vi'];
    if (!data) return;

    const routeKey = this.getRouteKey(this.currentRoute);
    this.currentHelpSubject.next(routeKey ? (data[routeKey] || null) : null);
  }

  private getRouteKey(url: string): string {
    // Strip leading slash and map to help content key
    const path = url.replace(/^\//, '');
    const routeMap: { [key: string]: string } = {
      overview: 'overview',
      transactions: 'transactions',
      planning: 'planning',
      groups: 'groups',
      statistics: 'statistics',
      analytics: 'analytics',
      reports: 'reports',
      myAccount: 'myAccount',
      myLoans: 'myLoans',
      myBalance: 'myBalance',
      myCards: 'myCards',
      investment: 'investment',
      profile: 'profile'
    };
    return routeMap[path] || '';
  }

  isExcludedRoute(): boolean {
    return this.excludedRoutes.some(r => this.currentRoute.startsWith(r));
  }

  toggle(): void {
    this.isOpenSubject.next(!this.isOpenSubject.value);
  }

  close(): void {
    this.isOpenSubject.next(false);
  }
}
