import { Component, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { GroupService, GroupSummary } from '../../services/group.service';
import { LanguageService } from '../../services/language.service';
import { ToastService } from '../../services/toast.service';
import { LayoutService } from '../../services/layout.service';
import { FileService } from '../../services/file.service';

@Component({
  selector: 'app-groups',
  templateUrl: './groups.component.html',
  styleUrls: ['./groups.component.css']
})
export class GroupsComponent implements OnInit {
  groups: GroupSummary[] = [];
  selectedGroupId: number | null = null;
  initialTab: string | null = null;
  query = '';
  loading = false;
  showCreateModal = false;
  avatarUrls: Record<string, string> = {};
  loadingAvatars = false;

  constructor(
    private groupService: GroupService,
    private languageService: LanguageService,
    private toastService: ToastService,
    private fileService: FileService,
    private route: ActivatedRoute,
    public layout: LayoutService
  ) {}

  ngOnInit(): void {
    // Check for query parameters
    this.route.queryParams.subscribe(params => {
      if (params['groupId']) {
        this.selectedGroupId = +params['groupId'];
      }
      if (params['tab']) {
        this.initialTab = params['tab'];
      }
    });
    
    this.fetchGroups();
  }

  translate(key: string): string {
    return this.languageService.translate(key);
  }

  fetchGroups(): void {
    this.loading = true;
    this.groupService.search(this.query, 0, 20).subscribe({
      next: res => {
        this.groups = res.content ?? [];
        if (this.groups.length && !this.selectedGroupId) {
          this.selectedGroupId = this.groups[0].id;
        }
        this.loadAvatarUrls(this.groups);
        this.loading = false;
      },
      error: () => {
        this.toastService.showError(this.translate('groups.toast.loadError'));
        this.avatarUrls = {};
        this.loading = false;
      }
    });
  }

  executeSearch(): void {
    this.fetchGroups();
  }

  selectGroup(group: GroupSummary): void {
    this.selectedGroupId = group.id;
  }

  openCreateModal(): void {
    this.showCreateModal = true;
  }

  handleCreated(group: GroupSummary): void {
    this.selectedGroupId = group.id;
    this.fetchGroups();
  }

  handleModalClosed(): void {
    this.showCreateModal = false;
  }

  handleGroupDeleted(): void {
    this.selectedGroupId = null;
    this.fetchGroups();
  }

  private loadAvatarUrls(groups: GroupSummary[]): void {
    const ids = Array.from(new Set(
      groups
        .map(group => group.avatarFileId)
        .filter((id): id is string => !!id)
    ));
    if (!ids.length) {
      this.avatarUrls = {};
      return;
    }
    this.loadingAvatars = true;
    this.fileService.getLiveUrls(ids).subscribe({
      next: map => {
        this.avatarUrls = map || {};
        this.loadingAvatars = false;
      },
      error: () => {
        this.avatarUrls = {};
        this.loadingAvatars = false;
      }
    });
  }
}


