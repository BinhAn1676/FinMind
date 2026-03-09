import { Component, OnInit, OnDestroy, HostListener } from '@angular/core';
import { NotificationService, Notification } from '../../services/notification.service';
import { LanguageService } from '../../services/language.service';
import { Subscription } from 'rxjs';
import { GroupService } from '../../services/group.service';
import { ToastService } from '../../services/toast.service';

@Component({
  selector: 'app-notification-bell',
  templateUrl: './notification-bell.component.html',
  styleUrls: ['./notification-bell.component.css']
})
export class NotificationBellComponent implements OnInit, OnDestroy {
  showDropdown = false;
  notifications: Notification[] = [];
  unreadCount = 0;
  loading = false;
  showInviteModal = false;
  inviteActionLoading = false;
  inviteActionError: string | null = null;
  selectedInvitation: Notification | null = null;
  private subscriptions = new Subscription();
  private userId: number | null = null;

  constructor(
    private notificationService: NotificationService,
    private languageService: LanguageService,
    private groupService: GroupService,
    private toastService: ToastService
  ) {}

  async ngOnInit() {
    // Get user ID from session storage
    const userDetails = window.sessionStorage.getItem('userdetails');
    if (userDetails) {
      try {
        const user = JSON.parse(userDetails);
        this.userId = user.id;
        if (this.userId) {
          await this.initializeWebSocket();
          this.notificationService.requestLatestNotifications();
        }
      } catch (error) {
        console.error('Error parsing user details:', error);
      }
    }

    // Subscribe to unread count updates
    this.subscriptions.add(
      this.notificationService.unreadCount$.subscribe(count => {
        this.unreadCount = count;
      })
    );

    // Subscribe to notification list updates
    this.subscriptions.add(
      this.notificationService.notifications$.subscribe(list => {
        this.notifications = list;
        this.loading = false;
      })
    );
  }

  async initializeWebSocket() {
    if (this.userId) {
      try {
        await this.notificationService.initializeWebSocket(this.userId);
      } catch (error) {
        console.error('Failed to initialize WebSocket:', error);
      }
    }
  }

  toggleDropdown() {
    this.showDropdown = !this.showDropdown;
    if (this.showDropdown && this.userId) {
      this.loading = true;
      this.notificationService.requestLatestNotifications(0, 10);
    }
  }

  handleNotificationClick(notification: Notification) {
    if (notification.type === 'GROUP_INVITATION') {
      this.openInviteModal(notification);
      return;
    }
    this.markAsRead(notification);
  }

  private openInviteModal(notification: Notification) {
    this.selectedInvitation = notification;
    this.inviteActionError = null;
    this.showInviteModal = true;
  }

  closeInviteModal() {
    this.showInviteModal = false;
    this.selectedInvitation = null;
    this.inviteActionLoading = false;
    this.inviteActionError = null;
  }

  markAsRead(notification: Notification) {
    if (!this.userId || notification.read) return;
    notification.read = true;
    this.notificationService.markNotificationAsRead(notification.id);
  }

  markAllAsRead() {
    if (!this.userId) return;
    this.notifications.forEach(n => n.read = true);
    this.notificationService.markAllNotificationsAsRead();
  }

  respondToGroupInvitation(accept: boolean) {
    if (!this.selectedInvitation || !this.userId) {
      return;
    }
    const metadata = this.selectedInvitation.metadata || {};
    const groupId = Number(metadata.groupId ?? metadata.groupID);
    const invitationId = Number(metadata.invitationId ?? metadata.invitationID);

    if (!groupId || !invitationId) {
      this.inviteActionError = this.getTranslation('notifications.groupInvite.errorMissing');
      return;
    }

    this.inviteActionLoading = true;
    this.inviteActionError = null;
    this.groupService.respondToInvite(groupId, invitationId, accept).subscribe({
      next: () => {
        this.inviteActionLoading = false;
        this.markAsRead(this.selectedInvitation!);
        this.notificationService.requestLatestNotifications();
        this.closeInviteModal();
        const toastKey = accept ? 'notifications.groupInvite.successAccept' : 'notifications.groupInvite.successReject';
        this.toastService.showSuccess(this.getTranslation(toastKey));
      },
      error: (err) => {
        console.error('Failed to respond to invitation', err);
        this.inviteActionLoading = false;
        this.inviteActionError = this.getTranslation('notifications.groupInvite.errorGeneric');
        this.toastService.showError(this.getTranslation('notifications.groupInvite.errorGeneric'));
      }
    });
  }

  getGroupInviteMessage(): string {
    if (!this.selectedInvitation) {
      return '';
    }
    const metadata = this.selectedInvitation.metadata || {};
    const groupName = metadata.groupName || this.getTranslation('notifications.groupInvite.defaultGroup');
    const inviterName = metadata.inviterName || this.getTranslation('notifications.groupInvite.defaultInviter');
    const template = this.getTranslation('notifications.groupInvite.message');
    return template.replace('{group}', groupName).replace('{inviter}', inviterName);
  }

  @HostListener('document:click', ['$event'])
  onDocumentClick(event: Event) {
    const target = event.target as HTMLElement;
    if (!target.closest('.notification-bell-container')) {
      this.showDropdown = false;
    }
  }

  formatTime(dateString: string): string {
    if (!dateString) return '';
    const date = new Date(dateString);
    const now = new Date();
    const diff = now.getTime() - date.getTime();
    const minutes = Math.floor(diff / 60000);
    const hours = Math.floor(diff / 3600000);
    const days = Math.floor(diff / 86400000);

    if (minutes < 1) return 'Vừa xong';
    if (minutes < 60) return `${minutes} phút trước`;
    if (hours < 24) return `${hours} giờ trước`;
    if (days < 7) return `${days} ngày trước`;

    return date.toLocaleDateString('vi-VN');
  }

  ngOnDestroy() {
    this.subscriptions.unsubscribe();
    this.notificationService.disconnect();
  }

  getTranslation(key: string): string {
    return this.languageService.translate(key);
  }
}

