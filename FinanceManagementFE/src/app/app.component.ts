import { Component, OnInit, OnDestroy } from '@angular/core';
import { Router, NavigationEnd } from '@angular/router';
import { KeycloakService } from 'keycloak-angular';
import { Subscription, filter } from 'rxjs';
import { LayoutService } from './services/layout.service';

@Component({
  selector: 'app-root',
  templateUrl: './app.component.html',
  styleUrls: ['./app.component.css']
})
export class AppComponent implements OnInit, OnDestroy {
  title = 'bank-app-ui';
  isAuthenticated = false;
  showSidebar = false;
  private routerSubscription?: Subscription;

  constructor(
    private keycloak: KeycloakService,
    private router: Router,
    public layout: LayoutService
  ) {}

  async ngOnInit() {
    // Check authentication status
    this.isAuthenticated = await this.keycloak.isLoggedIn();
    this.updateSidebarVisibility();

    // Subscribe to route changes
    this.routerSubscription = this.router.events
      .pipe(filter(event => event instanceof NavigationEnd))
      .subscribe(async () => {
        this.isAuthenticated = await this.keycloak.isLoggedIn();
        this.updateSidebarVisibility();
      });
  }

  private updateSidebarVisibility(): void {
    // Show sidebar only if user is authenticated and not on landing page
    const currentRoute = this.router.url;
    this.showSidebar = this.isAuthenticated && currentRoute !== '/home';
  }

  ngOnDestroy(): void {
    if (this.routerSubscription) {
      this.routerSubscription.unsubscribe();
    }
  }
}
