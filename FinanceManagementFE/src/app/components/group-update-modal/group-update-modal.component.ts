import { Component, ElementRef, EventEmitter, Input, OnInit, Output, ViewChild } from '@angular/core';
import { GroupDetail, GroupService, GroupUpdatePayload } from '../../services/group.service';
import { LanguageService } from '../../services/language.service';
import { ToastService } from '../../services/toast.service';
import { FileService } from '../../services/file.service';
import { UserService } from '../../services/user.service';
import { take, switchMap } from 'rxjs/operators';

@Component({
  selector: 'app-group-update-modal',
  templateUrl: './group-update-modal.component.html',
  styleUrls: ['./group-update-modal.component.css']
})
export class GroupUpdateModalComponent implements OnInit {
  @Input() group!: GroupDetail;
  @Output() updated = new EventEmitter<GroupDetail>();
  @Output() closed = new EventEmitter<void>();
  @ViewChild('avatarInput') avatarInput?: ElementRef<HTMLInputElement>;

  name = '';
  description = '';
  avatarPreview: string | null = null;
  avatarLiveUrl: string | null = null;
  avatarFileId: string | null = null;

  loading = false;
  avatarUploading = false;
  currentUserId?: number;

  constructor(
    private groupService: GroupService,
    private languageService: LanguageService,
    private toastService: ToastService,
    private fileService: FileService,
    private userService: UserService
  ) {}

  ngOnInit(): void {
    if (this.group) {
      this.name = this.group.name || '';
      this.description = this.group.description || '';
      this.avatarFileId = this.group.avatarFileId || null;
      if (this.group.avatarFileId) {
        this.loadAvatar();
      }
    }
    this.userService.getUserInfo().pipe(take(1)).subscribe({
      next: user => {
        this.currentUserId = user?.id ?? undefined;
      },
      error: () => {
        this.toastService.showError(this.translate('groups.toast.currentUserError'));
      }
    });
  }

  loadAvatar(): void {
    if (this.group?.avatarFileId) {
      this.fileService.getLiveUrl(this.group.avatarFileId).subscribe({
        next: response => {
          this.avatarLiveUrl = response.liveUrl;
          this.avatarPreview = this.avatarLiveUrl;
        },
        error: () => {
          console.error('Error loading avatar');
        }
      });
    }
  }

  translate(key: string): string {
    return this.languageService.translate(key);
  }

  triggerAvatarUpload(): void {
    if (this.avatarUploading) {
      return;
    }
    this.avatarInput?.nativeElement.click();
  }

  onFileChange(event: Event): void {
    if (this.avatarUploading) {
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

    if (!this.currentUserId || !this.group) {
      this.toastService.showError(this.translate('groups.toast.currentUserError'));
      return;
    }

    const uploadKey = this.group.id.toString();

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

  submit(): void {
    if (!this.name.trim()) {
      this.toastService.showError(this.translate('groups.validation.nameRequired'));
      return;
    }
    if (this.avatarUploading) {
      this.toastService.showError(this.translate('groups.toast.avatarUploadInProgress'));
      return;
    }
    if (!this.group) {
      return;
    }

    this.loading = true;
    const payload: GroupUpdatePayload = {
      name: this.name.trim(),
      description: this.description?.trim() || undefined,
      avatarFileId: this.avatarFileId || undefined
    };

    this.groupService.update(this.group.id, payload).subscribe({
      next: group => {
        this.loading = false;
        this.updated.emit(group);
        this.toastService.showSuccess(this.translate('groups.toast.updateSuccess'));
        this.close();
      },
      error: () => {
        this.loading = false;
        this.toastService.showError(this.translate('groups.toast.updateError'));
      }
    });
  }

  close(): void {
    this.closed.emit();
  }
}

