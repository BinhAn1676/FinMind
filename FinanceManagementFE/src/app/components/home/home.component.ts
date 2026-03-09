import { Component, OnInit, AfterViewInit, OnDestroy } from '@angular/core';
import { Router } from '@angular/router';
import { LanguageService } from '../../services/language.service';
import { KeycloakService } from 'keycloak-angular';
import { Subscription } from 'rxjs';

declare var gsap: any;

@Component({
  selector: 'app-home',
  templateUrl: './home.component.html',
  styleUrls: ['./home.component.css']
})
export class HomeComponent implements OnInit, AfterViewInit, OnDestroy {
  currentLang: string = 'vi';
  private languageSubscription: Subscription = new Subscription();

  constructor(
    private router: Router,
    private languageService: LanguageService,
    private keycloak: KeycloakService
  ) { }

  ngOnInit(): void {
    // Subscribe to language changes
    this.languageSubscription = this.languageService.currentLanguage$.subscribe(lang => {
      this.currentLang = lang;
      this.updateTranslations();
    });
  }

  ngAfterViewInit(): void {
    // Khởi tạo GSAP animations sau khi view được render
    this.initAnimations();
  }

  toggleLanguage(): void {
    const newLang = this.currentLang === 'vi' ? 'en' : 'vi';
    this.languageService.setLanguage(newLang);
  }

  private updateTranslations(): void {
    // Update all elements with data-key attributes
    document.querySelectorAll('[data-key]').forEach(element => {
      const key = element.getAttribute('data-key');
      if (key) {
        const translation = this.languageService.translate(key);
        (element as HTMLElement).textContent = translation;
      }
    });
  }

  navigateToLogin(event?: Event): void {
    if (event) event.preventDefault();
    this.keycloak.login({ redirectUri: window.location.origin + '/dashboard' });
  }

  scrollToFeatures(event: Event): void {
    event.preventDefault();
    const element = document.getElementById('features-pyramid');
    if (element) {
      element.scrollIntoView({ 
        behavior: 'smooth',
        block: 'start'
      });
    }
  }


  ngOnDestroy(): void {
    // Clean up subscription
    this.languageSubscription.unsubscribe();
  }

  initAnimations(): void {
    // Kiểm tra xem GSAP đã được load chưa
    if (typeof gsap !== 'undefined') {
      // Đăng ký ScrollTrigger plugin
      gsap.registerPlugin(gsap.ScrollTrigger);
      
      // Animation cho các element có class animate-on-scroll
      const animateElements = document.querySelectorAll('.animate-on-scroll');
      animateElements.forEach((element: any) => {
        gsap.fromTo(element, 
          { opacity: 0, y: 50 }, 
          {
            opacity: 1,
            y: 0,
            duration: 0.8,
            ease: "power3.out",
            scrollTrigger: {
              trigger: element,
              start: "top 85%", 
              toggleActions: "play none none none",
            }
          }
        );
      });
    } else {
      // Nếu GSAP chưa load, thử lại sau 100ms
      setTimeout(() => this.initAnimations(), 100);
    }
  }
}
