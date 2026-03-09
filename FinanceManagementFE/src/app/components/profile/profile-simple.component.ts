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
  public avatarFileId: string | null = null;

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
      console.log('User profile loaded:', this.userProfile);
      
      // Load user data from backend API
      await this.loadUserData();
    }
  }

  private loadUserData(): Promise<void> {
    return new Promise((resolve, reject) => {
      if (this.userProfile?.username) {
        this.userService.getUserByUsername(this.userProfile.username).subscribe({
          next: (userData: User) => {
            this.user = userData;
            console.log('User data loaded:', userData);
            
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
  getUserAvatar(): string {
    if (this.user.avatar && this.user.avatar.startsWith('http')) {
      return this.user.avatar;
    }
    return '/assets/images/default-avatar.png';
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
        this.showSuccessMessage(this.language.translate('profileSaveSuccess'));
      },
      error: (error) => {
        console.error('Error updating user:', error);
        this.showSuccessMessage('Error updating profile. Please try again.');
      }
    });
  }

  // Cancel edit
  cancelEdit() {
    this.isEditMode = false;
    // Reload user data to reset changes
    this.loadUserData();
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
        this.showSuccessMessage('Error uploading avatar. Please try again.');
      }
    });
  }

  // Show success message
  private showSuccessMessage(message: string) {
    // You can implement a toast notification here
    console.log('Success:', message);
  }
}
