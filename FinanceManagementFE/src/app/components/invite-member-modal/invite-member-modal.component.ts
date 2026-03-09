import { Component, EventEmitter, Input, OnInit, Output } from '@angular/core';
import { GroupInvite, GroupService, UserSearchResult } from '../../services/group.service';
import { LanguageService } from '../../services/language.service';
import { ToastService } from '../../services/toast.service';

interface UserSuggestion {
  id: number;
  display: string;
  email?: string;
  phone?: string;
}

@Component({
  selector: 'app-invite-member-modal',
  templateUrl: './invite-member-modal.component.html',
  styleUrls: ['./invite-member-modal.component.css']
})
export class InviteMemberModalComponent implements OnInit {
  @Input() groupId!: number;
  @Output() closed = new EventEmitter<void>();
  @Output() invited = new EventEmitter<void>();

  inviteQuery = '';
  suggestions: UserSuggestion[] = [];
  invitedUsers: UserSuggestion[] = [];
  sentInvites: GroupInvite[] = [];
  inviteLoading = false;
  inviteSearchExecuted = false;

  constructor(
    private groupService: GroupService,
    private languageService: LanguageService,
    private toastService: ToastService
  ) {}

  ngOnInit(): void {
    // Component initialized
  }

  translate(key: string): string {
    return this.languageService.translate(key);
  }

  triggerInviteSearch(): void {
    if (!this.groupId) {
      this.toastService.showError(this.translate('groups.toast.createFirst'));
      return;
    }
    this.inviteSearchExecuted = false;
    const keyword = this.inviteQuery.trim();
    if (keyword.length < 2) {
      this.suggestions = [];
      return;
    }
    this.inviteLoading = true;
    this.groupService.searchUsers(keyword, 0, 5).subscribe({
      next: res => {
        this.suggestions = (res.content || []).map(user => this.toSuggestion(user));
        this.inviteLoading = false;
        this.inviteSearchExecuted = true;
      },
      error: () => {
        this.suggestions = [];
        this.inviteLoading = false;
        this.inviteSearchExecuted = true;
        this.toastService.showError(this.translate('groups.toast.inviteSearchError'));
      }
    });
  }

  private toSuggestion(user: UserSearchResult): UserSuggestion {
    return {
      id: user.id,
      display: user.fullName || user.username,
      email: user.email,
      phone: user.phone
    };
  }

  addInvite(suggestion: UserSuggestion): void {
    if (!this.groupId) {
      this.toastService.showError(this.translate('groups.toast.createFirst'));
      return;
    }
    if (this.invitedUsers.some(u => u.id === suggestion.id) || this.sentInvites.some(i => i.inviteeUserId === suggestion.id)) {
      this.toastService.showError(this.translate('groups.toast.inviteExists'));
      this.suggestions = [];
      this.inviteQuery = '';
      return;
    }
    this.groupService.inviteMembers(this.groupId, [suggestion.id]).subscribe({
      next: invites => {
        this.suggestions = [];
        this.inviteQuery = '';
        if (invites && invites.length) {
          const invite = invites[0];
          this.sentInvites.push(invite);
          this.invitedUsers.push(suggestion);
          this.toastService.showSuccess(this.translate('groups.toast.inviteSuccess'));
          this.invited.emit();
        } else {
          this.toastService.showError(this.translate('groups.toast.inviteExists'));
        }
      },
      error: () => {
        this.toastService.showError(this.translate('groups.toast.inviteError'));
        this.suggestions = [];
        this.inviteQuery = '';
      }
    });
  }

  close(): void {
    this.resetForm();
    this.closed.emit();
  }

  private resetForm(): void {
    this.inviteQuery = '';
    this.suggestions = [];
    this.invitedUsers = [];
    this.sentInvites = [];
    this.inviteLoading = false;
    this.inviteSearchExecuted = false;
  }
}


