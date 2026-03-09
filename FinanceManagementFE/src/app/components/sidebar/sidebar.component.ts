import { Component } from '@angular/core';
import { Router } from '@angular/router';
import { LayoutService } from '../../services/layout.service';
import { LanguageService } from '../../services/language.service';

@Component({
  selector: 'app-sidebar',
  templateUrl: './sidebar.component.html',
  styleUrls: ['./sidebar.component.css']
})
export class SidebarComponent {

  constructor(private router: Router, public layout: LayoutService, public language: LanguageService) {}

  isActive(path: string): boolean {
    return this.router.url.startsWith(path);
  }

  navigate(path: string, event?: Event): void {
    if (event) {
      event.preventDefault();
    }
    this.router.navigate([path]);
  }

  toggleSidebar(event?: Event): void {
    if (event) event.preventDefault();
    this.layout.setSidebarCollapsed(!this.layout.isSidebarCollapsed);
  }
}


