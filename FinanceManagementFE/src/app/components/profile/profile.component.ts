import { Component, OnInit, ViewChild, ElementRef, ChangeDetectorRef } from '@angular/core';
import { User } from '../../model/user.model';
import { KeycloakService } from 'keycloak-angular';
import { KeycloakProfile } from 'keycloak-js';
import { LayoutService } from '../../services/layout.service';
import { LanguageService } from '../../services/language.service';
import { UserService } from '../../services/user.service';
import { FileService } from '../../services/file.service';
import { ToastService } from '../../services/toast.service';
import { SepayOAuth2Service, SepayConnectionStatus } from '../../services/sepay-oauth2.service';

@Component({
  selector: 'app-profile',
  templateUrl: './profile.component.html',
  styleUrls: ['./profile.component.css']
})
export class ProfileComponent implements OnInit {

  @ViewChild('fileInput') fileInput!: ElementRef;

  user = new User();
  public userProfile: KeycloakProfile | null = null;
  public isEditMode = false;
  public selectedFile: File | null = null;
  public previewUrl: string | null = null;
  public avatarFileId: string | null = null;

  // Bank token modal properties
  public dateOfBirthCal: Date | null = null;
  public showBankTokenModal = false;
  public showTutorialModal = false;
  public bankToken = '';
  public confirmBankToken = '';

  // SePay OAuth2 properties
  public sepayConnectionStatus: SepayConnectionStatus | null = null;
  public sepayConnecting = false;
  public sepayDisconnecting = false;

  constructor(
    private readonly keycloak: KeycloakService,
    public layout: LayoutService,
    public language: LanguageService,
    private userService: UserService,
    private fileService: FileService,
    private cdr: ChangeDetectorRef,
    private toastService: ToastService,
    private sepayOAuth2Service: SepayOAuth2Service
  ) { }

  public async ngOnInit() {
    const isLoggedIn = await this.keycloak.isLoggedIn();

    if (isLoggedIn) {
      this.userProfile = await this.keycloak.loadUserProfile();
      console.log('User profile loaded:', this.userProfile);

      // Load user data from backend API
      await this.loadUserData();

      // Check SePay OAuth2 connection status
      if (this.user.id) {
        this.loadSepayConnectionStatus();
      }
    }
  }

  private loadUserData(): Promise<void> {
    return new Promise((resolve, reject) => {
      if (this.userProfile?.username) {
        this.userService.getUserByUsername(this.userProfile.username).subscribe({
          next: (userData: User) => {
            this.user = userData;
            // Sync dateOfBirth string → Date object for p-calendar
            if (userData.dateOfBirth) {
              this.dateOfBirthCal = new Date(userData.dateOfBirth);
            }
            console.log('User data loaded:', userData);

            // Check if user has bankToken
            if ((userData as any).bankToken) {
              console.log('User has existing bank token');
              (this.user as any).bankToken = (userData as any).bankToken;
            }

            // Load avatar if exists
            if (userData.avatarFileId) {
              this.loadAvatarLiveUrl(userData.avatarFileId);
            } else if (userData.avatar && !userData.avatar.startsWith('http')) {
              this.user.avatarFileId = userData.avatar;
              this.loadAvatarLiveUrl(userData.avatar);
            } else if (userData.avatar && userData.avatar.startsWith('http')) {
              this.user.avatar = userData.avatar;
            }

            resolve();
          },
          error: (error) => {
            console.error('Error loading user data:', error);
            reject(error);
          }
        });
      } else {
        resolve();
      }
    });
  }

  private loadAvatarLiveUrl(fileId: string) {
    console.log('Loading live URL for file ID:', fileId);
    this.fileService.getLiveUrl(fileId).subscribe({
      next: (liveUrlResponse) => {
        console.log('Live URL response received:', liveUrlResponse);
        this.user.avatar = liveUrlResponse.liveUrl;
        this.user.avatarFileId = fileId;
        console.log('Avatar live URL loaded and assigned:', liveUrlResponse.liveUrl);
      },
      error: (error) => {
        console.error('Error loading avatar live URL:', error);
      }
    });
  }

  // Get user avatar
  onDateOfBirthChange(): void {
    if (this.dateOfBirthCal) {
      const d = this.dateOfBirthCal;
      const pad = (n: number) => (n < 10 ? '0' + n : '' + n);
      this.user.dateOfBirth = `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;
    } else {
      this.user.dateOfBirth = '';
    }
  }

  getUserAvatar(): string {
    if (this.user.avatar && this.user.avatar.startsWith('http')) {
      return this.user.avatar;
    }

    // If user has avatar file ID but no live URL yet, return a placeholder
    if (this.user.avatarFileId && !this.user.avatar?.startsWith('http')) {
      // Return a data URL placeholder while waiting for live URL
      const name = this.user.fullName || this.user.name || 'User';
      return `https://ui-avatars.com/api/?name=${encodeURIComponent(name)}&background=FF6B6B&color=fff&size=120&bold=true`;
    }

    // Default avatar based on user name initials
    const name = this.user.fullName || this.user.name || 'User';
    return `https://ui-avatars.com/api/?name=${encodeURIComponent(name)}&background=FF6B6B&color=fff&size=120&bold=true`;
  }

  // Toggle edit mode
  toggleEditMode() {
    this.isEditMode = !this.isEditMode;
  }

  // Save profile changes
  saveProfile() {
    console.log('Saving profile with user data:', this.user);

    // Create a copy of user object for update
    const updateUser = { ...this.user };

    // For avatar, send file ID instead of live URL
    if (this.user.avatarFileId) {
      updateUser.avatar = this.user.avatarFileId;
      updateUser.avatarFileId = this.user.avatarFileId;
    } else {
      updateUser.avatar = undefined;
      updateUser.avatarFileId = undefined;
    }

    console.log('Updating user with data:', updateUser);

    // Call API to update user
    this.userService.updateUser(updateUser).subscribe({
      next: (updatedUser: User) => {
        this.user = updatedUser;
        console.log('User updated successfully:', updatedUser);

        // Load avatar if exists
        if (updatedUser.avatarFileId) {
          this.loadAvatarLiveUrl(updatedUser.avatarFileId);
        } else if (updatedUser.avatar && !updatedUser.avatar.startsWith('http')) {
          this.user.avatarFileId = updatedUser.avatar;
          this.loadAvatarLiveUrl(updatedUser.avatar);
        } else if (updatedUser.avatar && updatedUser.avatar.startsWith('http')) {
          this.user.avatar = updatedUser.avatar;
        }

         this.isEditMode = false;
         const successMessage = this.language.translate('profileSaveSuccess');
         console.log('Showing success toast with message:', successMessage);
         this.toastService.showSuccess(successMessage || 'Profile updated successfully!');

         // Reload user-info API to update header
         this.reloadUserInfo();
       },
       error: (error) => {
         console.error('Error updating user:', error);
         this.toastService.showError(this.language.translate('profileSaveError') || 'Error updating profile. Please try again.');
       }
     });
  }

  // Cancel edit
  cancelEdit() {
    this.isEditMode = false;
    // Reload user data to reset changes
    this.loadUserData();
  }

  // Reload user-info API to update header
  private reloadUserInfo() {
    console.log('Reloading user-info API to update header...');
    this.userService.getUserInfo().subscribe({
      next: (userInfo: any) => {
        console.log('User-info API response:', userInfo);

        // Update user data with new info
        if (userInfo.fullName) {
          this.user.fullName = userInfo.fullName;
        }
        if (userInfo.name) {
          this.user.name = userInfo.name;
        }
        if (userInfo.email) {
          this.user.email = userInfo.email;
        }

        // Trigger change detection to update header
        this.cdr.detectChanges();
        console.log('Header updated with new user data');
      },
      error: (error) => {
        console.error('Error reloading user-info API:', error);
      }
    });
  }

  // File input trigger
  triggerFileInput() {
    // Only allow file selection when in edit mode
    if (!this.isEditMode) {
      console.log('Cannot upload avatar: not in edit mode');
      return;
    }
    this.fileInput.nativeElement.click();
  }

  // Handle file selection
  onFileSelected(event: any) {
    const file = event.target.files[0];
    if (file) {
      this.selectedFile = file;

      // Create preview URL
      const reader = new FileReader();
      reader.onload = (e: any) => {
        this.previewUrl = e.target.result;
      };
      reader.readAsDataURL(file);

      // Upload avatar
      this.uploadAvatar(file);
    }
  }

  // Upload avatar
  uploadAvatar(file: File) {
    this.fileService.uploadAvatar(file, this.user.id.toString()).subscribe({
      next: (response) => {
        console.log('Avatar uploaded successfully:', response);
        this.user.avatarFileId = response.id;

        // Get live URL for the uploaded file
        this.fileService.getLiveUrl(response.id).subscribe({
          next: (liveUrlResponse) => {
            this.user.avatar = liveUrlResponse.liveUrl;
            console.log('Live URL obtained:', liveUrlResponse.liveUrl);
          },
          error: (error) => {
            console.error('Error getting live URL:', error);
          }
        });
      },
      error: (error) => {
        console.error('Error uploading avatar:', error);
        this.toastService.showError('Error uploading avatar. Please try again.');
      }
    });
  }

  // Bank token modal methods
  openBankTokenModal() {
    this.showBankTokenModal = true;

    // Check if user already has a token
    const existingToken = (this.user as any).bankToken;
    if (existingToken) {
      // Show masked token
      this.bankToken = '****';
      this.confirmBankToken = '****';
    } else {
      // No existing token
      this.bankToken = '';
      this.confirmBankToken = '';
    }
  }

  closeBankTokenModal() {
    this.showBankTokenModal = false;
    this.bankToken = '';
    this.confirmBankToken = '';
  }

  saveBankToken() {
    if (!this.bankToken || !this.confirmBankToken) {
      this.toastService.showError(this.language.translate('bankTokenRequiredError'));
      return;
    }

    // If both fields are masked (****), user wants to keep existing token
    if (this.bankToken === '****' && this.confirmBankToken === '****') {
      // Token is already saved, just close the modal
      this.closeBankTokenModal();
      return;
    }

    // Check if tokens match
    if (this.bankToken !== this.confirmBankToken) {
      this.toastService.showError(this.language.translate('bankTokenMismatchError'));
      return;
    }

    // Call API to save bank token
    this.userService.updateBankToken(this.user.id!, this.bankToken).subscribe({
      next: (response: any) => {
        console.log('Bank token saved successfully:', response);
        this.toastService.showSuccess(this.language.translate('bankTokenSaveSuccess'));
        this.closeBankTokenModal();
        // Update user object with new token or keep existing
        (this.user as any).bankToken = this.bankToken;
        // Reload user data to get updated bankToken from backend
        this.loadUserData();
      },
      error: (error: any) => {
        console.error('Error saving bank token:', error);
        this.toastService.showError(this.language.translate('bankTokenSaveError'));
      }
    });
  }

  // Tutorial modal methods
  openTutorialModal() {
    this.showTutorialModal = true;
  }

  closeTutorialModal() {
    this.showTutorialModal = false;
    this.closeLightbox();
  }

  // Lightbox
  public lightboxSrc: string | null = null;
  public lightboxZoomed = false;

  openLightbox(src: string) {
    this.lightboxSrc = src;
    this.lightboxZoomed = false;
  }

  closeLightbox() {
    this.lightboxSrc = null;
    this.lightboxZoomed = false;
  }

  toggleLightboxZoom() {
    this.lightboxZoomed = !this.lightboxZoomed;
  }

  // Account settings methods
  changePassword() {
    // TODO: Implement change password functionality
    console.log('Change password clicked');
  }

  setupTwoFactor() {
    // TODO: Implement 2FA setup
    console.log('Setup two-factor authentication clicked');
  }

  manageNotifications() {
    // TODO: Implement notification settings
    console.log('Manage notifications clicked');
  }

  // ========================================
  // SePay OAuth2 Methods
  // ========================================

  loadSepayConnectionStatus() {
    if (!this.user.id) return;
    this.sepayOAuth2Service.getConnectionStatus(this.user.id.toString()).subscribe({
      next: (status) => {
        this.sepayConnectionStatus = status;
        console.log('SePay connection status:', status);
      },
      error: (error) => {
        console.error('Error loading SePay connection status:', error);
        // If error, assume OAuth2 is not enabled
        this.sepayConnectionStatus = { oauth2Enabled: false, connected: false };
      }
    });
  }

  connectSepayOAuth2() {
    if (!this.user.id) return;
    this.sepayConnecting = true;

    // Store userId in session for callback recovery
    window.sessionStorage.setItem('sepay_oauth2_userId', this.user.id.toString());

    this.sepayOAuth2Service.getAuthorizeUrl(this.user.id.toString()).subscribe({
      next: (response) => {
        // Redirect to SePay authorization page
        window.location.href = response.authorizeUrl;
      },
      error: (error) => {
        this.sepayConnecting = false;
        console.error('Error getting SePay authorize URL:', error);
        this.toastService.showError(this.language.translate('sepayConnectError'));
      }
    });
  }

  disconnectSepayOAuth2() {
    if (!this.user.id) return;
    this.sepayDisconnecting = true;

    this.sepayOAuth2Service.disconnect(this.user.id.toString()).subscribe({
      next: () => {
        this.sepayDisconnecting = false;
        this.sepayConnectionStatus = {
          oauth2Enabled: true,
          connected: false,
          authorizeUrl: this.sepayConnectionStatus?.authorizeUrl
        };
        this.toastService.showSuccess(this.language.translate('sepayDisconnectSuccess'));
        // Reload status
        this.loadSepayConnectionStatus();
      },
      error: (error) => {
        this.sepayDisconnecting = false;
        console.error('Error disconnecting SePay:', error);
        this.toastService.showError(this.language.translate('sepayDisconnectError'));
      }
    });
  }

}
