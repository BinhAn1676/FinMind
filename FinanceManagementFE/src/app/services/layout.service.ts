import { Injectable } from '@angular/core';
import { BehaviorSubject } from 'rxjs';

@Injectable({ providedIn: 'root' })
export class LayoutService {
  private sidebarCollapsedSubject = new BehaviorSubject<boolean>(true);
  public sidebarCollapsed$ = this.sidebarCollapsedSubject.asObservable();

  setSidebarCollapsed(collapsed: boolean): void {
    this.sidebarCollapsedSubject.next(collapsed);
  }

  get isSidebarCollapsed(): boolean {
    return this.sidebarCollapsedSubject.value;
  }
}


