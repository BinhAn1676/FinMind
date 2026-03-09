import { Component, OnInit, OnDestroy, HostListener } from '@angular/core';
import { User } from 'src/app/model/user.model';
import { KeycloakService } from 'keycloak-angular';
import { KeycloakProfile } from 'keycloak-js';
import { Router } from '@angular/router';
import { LanguageService } from '../../services/language.service';
import { UserService } from '../../services/user.service';
import { FileService } from '../../services/file.service';
import { ChatService } from '../../services/chat.service';
import { ChatRoom } from '../../model/chat.model';
import { Subscription } from 'rxjs';

@Component({
  selector: 'app-header',
  templateUrl: './header.component.html',
  styleUrls: ['./header.component.css']
})
export class HeaderComponent implements OnInit, OnDestroy {

  user = new User();
  currentLang: string = 'vi';
  showModal: boolean = false;
  showUserDropdown: boolean = false;
  private languageSubscription: Subscription = new Subscription();

  public isLoggedIn = false;
  public userProfile: KeycloakProfile | null = null;
  public userInfo: any = null; // Store user info from API
  public avatarUrl: string | null = null; // Store avatar live URL

  constructor(
    private readonly keycloak: KeycloakService,
    private router: Router,
    private languageService: LanguageService,
    private userService: UserService,
    private fileService: FileService,
    private chatService: ChatService
  ) { }

  public async ngOnInit() {
    this.isLoggedIn = await this.keycloak.isLoggedIn();

    if (this.isLoggedIn) {
      this.userProfile = await this.keycloak.loadUserProfile();
      this.user.authStatus = 'AUTH';
      this.user.name = this.userProfile.firstName || "";
      window.sessionStorage.setItem("userdetails",JSON.stringify(this.user));

      // Call user-info API to get/create user profile in backend
      console.log('User logged in via Keycloak, calling user-info API...');
      this.userService.getUserInfo().subscribe(
        userInfo => {
          console.log('User-info API successful:', userInfo);
          // Store user info from API
          this.userInfo = userInfo;
          // Update user details with the response from user-info API
          this.user = userInfo;
          this.user.authStatus = 'AUTH';
          window.sessionStorage.setItem("userdetails",JSON.stringify(this.user));

          // Load avatar if exists
          this.loadAvatarFromUserInfo(userInfo);
        },
        error => {
          console.error('Error fetching user info:', error);
        }
      );
    }


    // Subscribe to language changes
    this.languageSubscription = this.languageService.currentLanguage$.subscribe(lang => {
      this.currentLang = lang;
      this.updateTranslations();
    });
  }

  public login(event?: Event) {
    try {
      if (event) event.preventDefault();
      this.keycloak.login({ redirectUri: window.location.origin + '/overview' });
    } catch (error) {
      console.error('Keycloak login error:', error);
    }
  }

  public logout() {
    let redirectURI: string = "https://finmind.pro.vn/home";
    this.keycloak.logout(redirectURI);
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
    try {
      this.login(event);
    } catch (error) {
      console.error('Keycloak login error:', error);
    }
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

  showLogoModal(): void {
    this.showModal = true;
    document.body.style.overflow = 'hidden'; // Prevent background scrolling
  }

  closeLogoModal(): void {
    this.showModal = false;
    document.body.style.overflow = 'auto'; // Restore scrolling
  }

  ngOnDestroy(): void {
    // Clean up subscription
    this.languageSubscription.unsubscribe();
  }

  // Toggle user dropdown menu
  toggleUserDropdown(): void {
    this.showUserDropdown = !this.showUserDropdown;
  }

  // Load avatar from user info
  private loadAvatarFromUserInfo(userInfo: any) {
    console.log('Loading avatar from user info:', userInfo);

    // If avatar field contains file ID, get live URL
    if (userInfo.avatar && !userInfo.avatar.startsWith('http')) {
      console.log('Avatar field contains file ID, getting live URL for:', userInfo.avatar);
      this.loadAvatarLiveUrl(userInfo.avatar);
    } else if (userInfo.avatar && userInfo.avatar.startsWith('http')) {
      console.log('Avatar field contains live URL:', userInfo.avatar);
      this.avatarUrl = userInfo.avatar;
    }
  }

  // Load avatar live URL from file service
  private loadAvatarLiveUrl(fileId: string) {
    console.log('Loading live URL for file ID:', fileId);
    this.fileService.getLiveUrl(fileId).subscribe({
      next: (liveUrlResponse) => {
        console.log('Live URL response received:', liveUrlResponse);
        this.avatarUrl = liveUrlResponse.liveUrl;
        console.log('Avatar live URL loaded:', liveUrlResponse.liveUrl);
      },
      error: (error) => {
        console.error('Error loading avatar live URL:', error);
      }
    });
  }

  // Get user avatar - you can customize this based on your needs
  getUserAvatar(): string {
    // If we have a live URL, use it
    if (this.avatarUrl) {
      return this.avatarUrl;
    }

    // If user has a profile picture from Keycloak, use it
    if (this.userProfile && this.userProfile.attributes && this.userProfile.attributes['avatar']) {
      const avatar = this.userProfile.attributes['avatar'];
      if (Array.isArray(avatar) && avatar.length > 0) {
        return avatar[0];
      }
    }

    // Use fullName from user-info API if available, otherwise fallback to user.name
    const name = this.userInfo?.fullName || this.user.name || 'User';

    // Fallback to initials avatar
    return `https://ui-avatars.com/api/?name=${encodeURIComponent(name)}&background=FF6B6B&color=fff&size=40&bold=true`;
  }

  // Navigate to user profile page
  navigateToProfile(): void {
    this.showUserDropdown = false;
    this.router.navigate(['/profile']);
  }

  // Get user display name from API or fallback
  getUserDisplayName(): string {
    return this.userInfo?.fullName || this.user.name || 'User';
  }

  // Get translation for dropdown items
  getTranslation(key: string): string {
    return this.languageService.translate(key);
  }

  // Close dropdown when clicking outside
  @HostListener('document:click', ['$event'])
  onDocumentClick(event: Event): void {
    const target = event.target as HTMLElement;
    const userProfile = target.closest('.user-profile');

    if (!userProfile && this.showUserDropdown) {
      this.showUserDropdown = false;
    }
  }

  // Handle opening chat box from dropdown
  onOpenChatBox(room: ChatRoom): void {
    this.chatService.openChatBox(room);
  }
}
