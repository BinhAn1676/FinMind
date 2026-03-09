import { Component, OnInit, ViewChild, ElementRef } from '@angular/core';
import { User } from '../../model/user.model';
import { KeycloakService } from 'keycloak-angular';
import { KeycloakProfile } from 'keycloak-js';
import { LayoutService } from '../../services/layout.service';
import { LanguageService } from '../../services/language.service';
import { UserService } from '../../services/user.service';
import { FileService } from '../../services/file.service';

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

  constructor(
    private readonly keycloak: KeycloakService,
    public layout: LayoutService,
    public language: LanguageService,
    private userService: UserService,
    private fileService: FileService
  ) { }

  public async ngOnInit() {
    const isLoggedIn = await this.keycloak.isLoggedIn();

    if (isLoggedIn) {
      this.userProfile = await this.keycloak.loadUserProfile();
      this.user.name = this.userProfile.firstName || "";

      // Load user data from backend API first, then initialize form
      await this.loadUserData();
    }
  }

  private loadUserData(): Promise<void> {
    return new Promise((resolve, reject) => {
      if (this.userProfile?.username) {
        this.userService.getUserByUsername(this.userProfile.username).subscribe({
          next: (userData: User) => {
            this.user = userData;









            // Initialize form after data is loaded
            this.initializeForm();

            // Force update form values after a short delay to ensure data binding
            setTimeout(() => {
              this.updateFormValues();
            }, 200);

            // If user has avatar file ID, get live URL
            if (userData.avatarFileId) {

              this.loadAvatarLiveUrl(userData.avatarFileId);
            } else if (userData.avatar && !userData.avatar.startsWith('http')) {
              // If avatar field contains file ID instead of URL, treat it as file ID

              this.user.avatarFileId = userData.avatar; // Store the file ID
              this.loadAvatarLiveUrl(userData.avatar);
            } else if (userData.avatar && userData.avatar.startsWith('http')) {
              // If avatar field already contains live URL, use it directly

              this.user.avatar = userData.avatar;
            }

            resolve();
          },
          error: (error) => {
            console.error('Error loading user data:', error);
            // Still initialize form even if API fails
            this.initializeForm();
            reject(error);
          }
        });
      } else {
        // No username available, initialize form with empty data
        this.initializeForm();
        resolve();
      }
    });
  }

  private loadAvatarLiveUrl(fileId: string) {

    this.fileService.getLiveUrl(fileId).subscribe({
      next: (liveUrlResponse) => {

        this.user.avatar = liveUrlResponse.liveUrl;
        this.user.avatarFileId = fileId; // Store the file ID separately



        // Force change detection to update the UI immediately
        // This ensures the avatar updates without needing to refresh the page
        setTimeout(() => {
          // Trigger change detection by updating a dummy property
          this.user = { ...this.user };

        }, 100);
      },
      error: (error) => {
        console.error('Error loading avatar live URL:', error);
        console.error('File ID that failed:', fileId);
      }
    });
  }

  private initializeForm() {
    // Use fullName if available, otherwise fallback to name
    const displayName = this.user.fullName || this.user.name || '';

    this.profileForm = this.formBuilder.group({
      fullName: [displayName, [Validators.required, Validators.minLength(2)]],
      email: [this.user.email || this.userProfile?.email || '', [Validators.required, Validators.email]],
      username: [{value: this.user.username || this.userProfile?.username || '', disabled: true}],
      phone: [this.user.phone || ''],
      dateOfBirth: [this.user.dateOfBirth || ''],
      address: [this.user.address || ''],
      bio: [this.user.bio || '']
    });

    // Force change detection to ensure UI updates
    setTimeout(() => {
      // Debug DOM elements
      this.debugDOMElements();
    }, 200);
  }

  private debugDOMElements() {
    const fullNameInput = document.getElementById('fullName') as HTMLInputElement;
    const nameInput = document.getElementById('name') as HTMLInputElement;
    const emailInput = document.getElementById('email') as HTMLInputElement;

    // Force set value to test
    if (fullNameInput) {
      fullNameInput.value = this.user.fullName || this.user.name || '';

    }

    // Force set value for name input too
    if (nameInput) {
      nameInput.value = this.user.name || '';

    }

    // Force set value for email input
    if (emailInput) {
      emailInput.value = this.user.email || '';

    }
  }

  private updateFormValues() {
    if (this.profileForm) {





      // Use fullName if available, otherwise fallback to name
      const displayName = this.user.fullName || this.user.name || '';


      this.profileForm.patchValue({
        fullName: displayName,
        email: this.user.email || '',
        phone: this.user.phone || '',
        dateOfBirth: this.user.dateOfBirth || '',
        address: this.user.address || '',
        bio: this.user.bio || ''
      });






    }
  }

  // Get user avatar - use live URL from file service
  getUserAvatar(): string {
    // If user has avatar and it's a full URL (not just file ID), use it
    if (this.user.avatar && this.user.avatar.startsWith('http')) {
      return this.user.avatar;
    }

    // If user has avatar file ID but no live URL yet, return default
    if (this.user.avatarFileId && !this.user.avatar?.startsWith('http')) {
      // Return default avatar while waiting for live URL
      const name = this.user.fullName || this.user.name || 'User';
      return `https://ui-avatars.com/api/?name=${encodeURIComponent(name)}&background=FF6B6B&color=fff&size=120&bold=true`;
    }

    // If user has a profile picture in Keycloak attributes, use it
    if (this.userProfile && this.userProfile.attributes && this.userProfile.attributes['avatar']) {
      const avatar = this.userProfile.attributes['avatar'];
      if (Array.isArray(avatar) && avatar.length > 0) {
        return avatar[0];
      }
    }

    // Default avatar based on user name initials
    const name = this.user.fullName || this.user.name || 'User';

    // You can use a service like Gravatar or create a default avatar
    // For now, we'll use a placeholder
    return `https://ui-avatars.com/api/?name=${encodeURIComponent(name)}&background=FF6B6B&color=fff&size=120&bold=true`;
  }

  // Toggle edit mode
  toggleEditMode() {
    this.isEditMode = !this.isEditMode;
    if (this.isEditMode) {
      this.profileForm.enable();
    } else {
      this.profileForm.disable();
    }
  }

  // Save profile changes
  saveProfile() {
    if (this.profileForm.valid) {
      console.log('Saving profile:', this.profileForm.value);
      console.log('Form fullName value:', this.profileForm.get('fullName')?.value);
      console.log('Form fullName control:', this.profileForm.get('fullName'));
      console.log('Form fullName dirty:', this.profileForm.get('fullName')?.dirty);
      console.log('Form fullName touched:', this.profileForm.get('fullName')?.touched);

      // Create a copy of user object for update
      console.log('Original this.user.fullName:', this.user.fullName);
      const updateUser = { ...this.user };
      console.log('After spread - updateUser.fullName:', updateUser.fullName);

      // Update user object with form data
      console.log('Before assignment - form fullName:', this.profileForm.value.fullName);
      console.log('Before assignment - updateUser fullName:', updateUser.fullName);
      
      updateUser.fullName = this.profileForm.value.fullName;
      updateUser.email = this.profileForm.value.email;
      updateUser.phone = this.profileForm.value.phone;
      updateUser.dateOfBirth = this.profileForm.value.dateOfBirth;
      updateUser.address = this.profileForm.value.address;
      updateUser.bio = this.profileForm.value.bio;
      
      console.log('After assignment - updateUser fullName:', updateUser.fullName);
      console.log('After assignment - form fullName:', this.profileForm.value.fullName);

      // For avatar, send file ID instead of live URL
      console.log('Before avatar logic - updateUser.fullName:', updateUser.fullName);
      if (this.user.avatarFileId) {
        updateUser.avatar = this.user.avatarFileId; // Send file ID, not live URL
        updateUser.avatarFileId = this.user.avatarFileId;
      } else {
        // If no avatar file ID, clear the avatar field
        updateUser.avatar = undefined;
        updateUser.avatarFileId = undefined;
      }
      console.log('After avatar logic - updateUser.fullName:', updateUser.fullName);



      // Store current avatar as backup before update
      const currentAvatar = this.user.avatar;
      const currentAvatarFileId = this.user.avatarFileId;

      console.log('Updating user with data:', updateUser);
      console.log('updateUser.fullName:', updateUser.fullName);
      console.log('updateUser.email:', updateUser.email);
      console.log('updateUser.phone:', updateUser.phone);

      // Call API to update user
      this.userService.updateUser(updateUser).subscribe({
        next: (updatedUser: User) => {
          this.user = updatedUser;




          // If user has avatar file ID, get live URL to display
          if (updatedUser.avatarFileId) {

            this.loadAvatarLiveUrl(updatedUser.avatarFileId);
          } else if (updatedUser.avatar && !updatedUser.avatar.startsWith('http')) {
            // If avatar field contains file ID instead of URL, treat it as file ID

            this.user.avatarFileId = updatedUser.avatar; // Store the file ID
            this.loadAvatarLiveUrl(updatedUser.avatar);
          } else if (updatedUser.avatar && updatedUser.avatar.startsWith('http')) {
            // If avatar field already contains live URL, use it directly

            this.user.avatar = updatedUser.avatar;
          } else {
            // If no avatar in response, keep current avatar as backup

            if (currentAvatar && currentAvatar.startsWith('http')) {
              this.user.avatar = currentAvatar;
            }
            if (currentAvatarFileId) {
              this.user.avatarFileId = currentAvatarFileId;
            }
          }

          this.isEditMode = false;
          this.profileForm.disable();
          this.showSuccessMessage(this.language.translate('profileSaveSuccess'));
        },
        error: (error) => {
          console.error('Error updating user:', error);
          this.showSuccessMessage('Error updating profile. Please try again.');
        }
      });
    } else {
      this.markFormGroupTouched();
    }
  }

  // Cancel edit
  cancelEdit() {
    this.isEditMode = false;
    this.profileForm.disable();
    // Reset form to current user data
    this.profileForm.patchValue({
      fullName: this.user.fullName || '',
      email: this.user.email || '',
      phone: this.user.phone || '',
      dateOfBirth: this.user.dateOfBirth || '',
      address: this.user.address || '',
      bio: this.user.bio || ''
    });
  }

  // Form submission
  onSubmit() {
    if (this.profileForm.valid) {
      this.saveProfile();
    }
  }

  // Mark all form fields as touched to show validation errors
  private markFormGroupTouched() {
    Object.keys(this.profileForm.controls).forEach(key => {
      const control = this.profileForm.get(key);
      control?.markAsTouched();
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

      // Upload file to server
      this.uploadAvatar(file);
    }
  }

  // Upload avatar to file service
  private uploadAvatar(file: File) {
    if (this.user.id) {
      this.fileService.uploadAvatar(file, this.user.id.toString()).subscribe({
        next: (response) => {

          this.user.avatarFileId = response.id;

          // Get live URL for the uploaded file
          this.fileService.getLiveUrl(response.id).subscribe({
            next: (liveUrlResponse) => {
              this.user.avatar = liveUrlResponse.liveUrl;


              // Force change detection to update the UI immediately
              setTimeout(() => {
                this.user = { ...this.user };
              }, 100);
            },
            error: (error) => {
              console.error('Error getting live URL:', error);
            }
          });
        },
        error: (error) => {
          console.error('Error uploading avatar:', error);
          this.showSuccessMessage('Error uploading avatar. Please try again.');
        }
      });
    } else {
      console.error('User ID not available for avatar upload');
    }
  }

  // Account settings methods
  changePassword() {

    // TODO: Implement change password functionality
  }

  setupTwoFactor() {

    // TODO: Implement 2FA setup
  }

  manageNotifications() {

    // TODO: Implement notification settings
  }

  // Show success message
  private showSuccessMessage(message: string) {
    // TODO: Implement toast notification

  }
}
