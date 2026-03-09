import { Component, OnInit } from '@angular/core';
import { LayoutService } from '../../services/layout.service';
import { UserService } from '../../services/user.service';
import { FileService } from '../../services/file.service';
import { User } from 'src/app/model/user.model';

@Component({
  selector: 'app-dashboard',
  templateUrl: './dashboard.component.html',
  styleUrls: ['./dashboard.component.css']
})
export class DashboardComponent implements OnInit {

  user = new User();
  public avatarUrl: string | null = null;

  constructor(
    public layout: LayoutService,
    private userService: UserService,
    private fileService: FileService
  ) {
    
  }

  ngOnInit() {
    if(sessionStorage.getItem('userdetails')){
      this.user = JSON.parse(sessionStorage.getItem('userdetails') || "");
    }
    
    // Load user info and avatar
    this.loadUserInfoAndAvatar();
  }

  private loadUserInfoAndAvatar() {
    console.log('Loading user info and avatar for dashboard...');
    
    // Call user-info API
    this.userService.getUserInfo().subscribe({
      next: (userInfo: any) => {
        console.log('User-info API response:', userInfo);
        
        // Update user data
        this.user = userInfo;
        
        // If avatar field contains file ID, get live URL
        if (userInfo.avatar && !userInfo.avatar.startsWith('http')) {
          console.log('Avatar field contains file ID, getting live URL for:', userInfo.avatar);
          this.loadAvatarLiveUrl(userInfo.avatar);
        } else if (userInfo.avatar && userInfo.avatar.startsWith('http')) {
          console.log('Avatar field contains live URL:', userInfo.avatar);
          this.avatarUrl = userInfo.avatar;
        }
      },
      error: (error) => {
        console.error('Error loading user info:', error);
      }
    });
  }

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

  // Get user avatar for display
  getUserAvatar(): string {
    if (this.avatarUrl) {
      return this.avatarUrl;
    }
    
    // Fallback to initials if no avatar
    const name = this.user.fullName || this.user.name || 'User';
    return `https://ui-avatars.com/api/?name=${encodeURIComponent(name)}&background=FF6B6B&color=fff&size=120&bold=true`;
  }

}
