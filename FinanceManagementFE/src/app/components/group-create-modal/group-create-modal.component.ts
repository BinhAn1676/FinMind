import { Component, ElementRef, EventEmitter, OnInit, Output, ViewChild } from '@angular/core';
import { take, switchMap } from 'rxjs/operators';
import { GroupInvite, GroupService, GroupSummary, UserSearchResult } from '../../services/group.service';
import { LanguageService } from '../../services/language.service';
import { ToastService } from '../../services/toast.service';
import { FileService } from '../../services/file.service';
import { UserService } from '../../services/user.service';

interface UserSuggestion {
  id: number;
  display: string;
  email?: string;
  phone?: string;
}

@Component({
  selector: 'app-group-create-modal',
  templateUrl: './group-create-modal.component.html',
  styleUrls: ['./group-create-modal.component.css']
})
export class GroupCreateModalComponent implements OnInit {
  @Output() created = new EventEmitter<GroupSummary>();
  @Output() closed = new EventEmitter<void>();
  @ViewChild('avatarInput') avatarInput?: ElementRef<HTMLInputElement>;

  name = '';
  description = '';
  avatarPreview: string | null = null;
  avatarLiveUrl: string | null = null;
  avatarFileId: string | null = null;

  inviteQuery = '';
  suggestions: UserSuggestion[] = [];
  invitedUsers: UserSuggestion[] = [];
  sentInvites: GroupInvite[] = [];

  loading = false;
  avatarUploading = false;
  currentUserId?: number;
  groupCreated = false;
  createdGroup?: GroupSummary;
  inviteLoading = false;
  inviteSearchExecuted = false;

  constructor(
    private groupService: GroupService,
    private languageService: LanguageService,
    private toastService: ToastService,
    private fileService: FileService,
    private userService: UserService
  ) {}

  ngOnInit(): void {
    this.userService.getUserInfo().pipe(take(1)).subscribe({
      next: user => {
        this.currentUserId = user?.id ?? undefined;
      },
      error: () => {
        this.toastService.showError(this.translate('groups.toast.currentUserError'));
      }
    });
  }

  translate(key: string): string {
    return this.languageService.translate(key);
  }

  triggerAvatarUpload(): void {
    if (this.groupCreated || this.avatarUploading) {
      return;
    }
    this.avatarInput?.nativeElement.click();
  }

  onFileChange(event: Event): void {
    if (this.groupCreated || this.avatarUploading) {
      return;
    }
    const input = event.target as HTMLInputElement;
    if (!input.files || !input.files.length) {
      return;
    }
    const file = input.files[0];
    const reader = new FileReader();
    reader.onload = () => (this.avatarPreview = reader.result as string);
    reader.readAsDataURL(file);

    if (!this.currentUserId) {
      this.toastService.showError(this.translate('groups.toast.currentUserError'));
      return;
    }

    const uploadKey = this.createdGroup
      ? this.createdGroup.id.toString()
      : this.generateTempUploadKey();

    this.avatarUploading = true;
    this.fileService.uploadGroupAvatar(file, uploadKey).pipe(
      switchMap(response => {
        this.avatarFileId = response.id;
        return this.fileService.getLiveUrl(response.id);
      })
    ).subscribe({
      next: liveUrlResponse => {
        this.avatarLiveUrl = liveUrlResponse.liveUrl;
        this.avatarPreview = this.avatarLiveUrl;
        this.avatarUploading = false;
      },
      error: () => {
        this.avatarUploading = false;
        this.toastService.showError(this.translate('groups.toast.avatarUploadError'));
      }
    });
  }

  triggerInviteSearch(): void {
    if (!this.groupCreated) {
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
    if (!this.groupCreated) {
      this.toastService.showError(this.translate('groups.toast.createFirst'));
      return;
    }
    if (!this.createdGroup) {
      this.toastService.showError(this.translate('groups.toast.createFirst'));
      return;
    }
    if (this.invitedUsers.some(u => u.id === suggestion.id) || this.sentInvites.some(i => i.inviteeUserId === suggestion.id)) {
      this.toastService.showError(this.translate('groups.toast.inviteExists'));
      this.suggestions = [];
      this.inviteQuery = '';
      return;
    }
    this.groupService.inviteMembers(this.createdGroup.id, [suggestion.id]).subscribe({
      next: invites => {
        this.suggestions = [];
        this.inviteQuery = '';
        if (invites && invites.length) {
          const invite = invites[0];
          this.sentInvites.push(invite);
          this.invitedUsers.push(suggestion);
          this.toastService.showSuccess(this.translate('groups.toast.inviteSuccess'));
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

  removeInvite(userId: number): void {
    // Removal is not supported after invite is sent
  }

  submit(): void {
    if (this.groupCreated) {
      this.close();
      return;
    }
    if (!this.name.trim()) {
      this.toastService.showError(this.translate('groups.validation.nameRequired'));
      return;
    }
    if (this.avatarUploading) {
      this.toastService.showError(this.translate('groups.toast.avatarUploadInProgress'));
      return;
    }
    this.loading = true;
    this.groupService.create({
      name: this.name.trim(),
      description: this.description?.trim(),
      avatarFileId: this.avatarFileId ?? undefined
    }).subscribe({
      next: group => {
        this.loading = false;
        this.groupCreated = true;
        this.createdGroup = group;
        this.created.emit(group);
        this.toastService.showSuccess(this.translate('groups.toast.createSuccess'));
      },
      error: () => {
        this.loading = false;
        this.toastService.showError(this.translate('groups.toast.createError'));
      }
    });
  }

  close(): void {
    this.resetForm();
    this.closed.emit();
  }

  private resetForm(): void {
    this.name = '';
    this.description = '';
    this.avatarPreview = null;
    this.avatarLiveUrl = null;
    this.avatarFileId = null;
    this.inviteQuery = '';
    this.suggestions = [];
    this.invitedUsers = [];
    this.sentInvites = [];
    this.avatarUploading = false;
    this.groupCreated = false;
    this.createdGroup = undefined;
    this.inviteLoading = false;
    this.inviteSearchExecuted = false;
  }

  private generateTempUploadKey(): string {
    const randomPart = typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function'
      ? crypto.randomUUID()
      : `${Date.now().toString(36)}-${Math.random().toString(36).slice(2)}`;
    return `group-temp-${randomPart}`;
  }

  get primaryButtonLabel(): string {
    return this.groupCreated
      ? this.translate('groups.create.doneButton')
      : this.translate('groups.create.createButton');
  }

  get inviteDisabled(): boolean {
    return !this.groupCreated;
  }
}


