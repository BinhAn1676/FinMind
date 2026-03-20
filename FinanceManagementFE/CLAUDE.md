# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Angular 16 finance management SPA with real-time chat/notifications, AI chatbot, group collaboration, savings goals, bill reminders, financial calendar, and Sepay bank sync. Authentication via Keycloak.

## Common Commands

### Development
- `npm start` or `ng serve` - Start dev server at http://localhost:4200
- `ng build` - Build for production (outputs to `dist/bank-app-ui/`)
- `ng build --watch --configuration development` - Build in watch mode
- `ng test` - Run unit tests via Karma
- `ng generate component components/feature-name` - Generate new component

### Angular CLI
- `ng g component|service|module|directive|pipe|guard` for scaffolding

## Backend Service URLs

| Service | URL | Purpose |
|---------|-----|---------|
| API Gateway | `http://localhost:8072` (env: `rooturl`) | All REST API calls |
| Notification Service | `http://localhost:8083` (env: `notificationServiceUrl`) | WebSocket notifications |
| Chat Service | `http://localhost:8086` (env: `chatServiceUrl`) | WebSocket chat |
| Keycloak | `http://localhost:7080` | Auth (realm: `finance`, client: `finance-auth-server`) |

Configured in:
- `src/environments/environment.ts` (production)
- `src/environments/environment.development.ts` (development)

## Authentication & Authorization

- **Keycloak Integration:** `keycloak-angular` library, PKCE (S256) flow
- **Auth Guard:** `AuthKeyClockGuard` in `src/app/routeguards/auth.route.ts`
- **After login redirect:** `/overview`
- **Session Storage:** `window.sessionStorage.userdetails`
- **Token access:** `await this.keycloak.getToken()`

## WebSocket Architecture (STOMP over SockJS)

### Notification Service
- **URL:** `${notificationServiceUrl}/ws`
- **User queues:** `/user/{userId}/queue/notifications`, `/user/{userId}/queue/notifications-count`, `/user/{userId}/queue/notifications-all`
- **App destinations:** `/app/notifications/count`, `/app/notifications/all`, `/app/notifications/mark-read`, `/app/notifications/mark-all-read`

### Chat Service
- **URL:** `${chatServiceUrl}/ws` (direct connection, NOT through gateway)
- **Topics:** `/topic/chat/{roomId}`, `/topic/chat/{roomId}/typing`, `/topic/chat/{roomId}/read`, `/topic/chat/{roomId}/edit`, `/topic/chat/{roomId}/delete`
- **User notifications:** `/topic/chat/user/{userId}/notifications`
- **Online status:** `/topic/chat/online`
- **App destinations:** `/app/chat/{roomId}/send`, `/app/chat/{roomId}/typing`, `/app/chat/{roomId}/read`, `/app/chat/online`
- **REST API:** goes through gateway with `/chat` prefix: `${rooturl}/chat/api/v1/chat/*`

> **Important:** Chat WebSocket connects directly to ChatService; REST calls go via gateway with `/chat` prefix.

## Routes

| Route | Component | Auth |
|-------|-----------|------|
| `/home` | HomeComponent | Public |
| `/login` | LoginComponent | Public |
| `/contact` | ContactComponent | Public |
| `/notices` | NotificationsPage | Public |
| `/dashboard` | DashboardComponent | Required |
| `/overview` | OverviewComponent | Required |
| `/myAccount` | AccountComponent | Required |
| `/myBalance` | BalanceComponent | Required |
| `/myLoans` | LoansComponent | Required |
| `/myCards` | CardsComponent | Required |
| `/profile` | ProfileComponent | Required |
| `/transactions` | TransactionsComponent | Required |
| `/planning` | PlanningComponent | Required |
| `/savings-goals` | SavingsGoalsComponent | Required |
| `/bills` | BillRemindersComponent | Required |
| `/calendar` | FinancialCalendarComponent | Required |
| `/groups` | GroupsComponent | Required |
| `/statistics` | StatisticsComponent | Required |
| `/analytics` | AnalyticsDashboardComponent | Required |
| `/reports` | ReportsComponent | Required |
| `/investment` | InvestmentComponent | Required |
| `/sepay/callback` | SepayCallbackComponent | Required |

## Components (`src/app/components/`)

### Core Layout
- `header/` - Top navigation bar
- `sidebar/` - Navigation sidebar
- `loading-spinner/` - Global loading indicator
- `toast/` - Toast notifications
- `notification-bell/` - Notification badge
- `market-ticker/` - Market data ticker
- `help-panel/` - Help/FAQ panel

### Finance Features
- `dashboard/` - Main dashboard with charts and overview
- `overview/` - Financial overview
- `transactions/` - Transaction list, create, filter, export (Excel/PDF/CSV)
- `account/` - Financial account management
- `balance/` - Balance view
- `loans/` - Loan tracking
- `cards/` - Credit/debit card management
- `planning/` - Budget planning (personal & group) with repeat cycles
- `savings-goals/` — **NEW:** Savings goal tracking with contribution history (personal & group)
- `bill-reminders/` — **NEW:** Bill reminder management with scheduling
- `financial-calendar/` — **NEW:** Calendar view of bills, reminders, and financial events
- `statistics/` - Financial statistics and trend charts
- `analytics-dashboard/` — **NEW:** AI-powered analytics
- `reports/` - Generate and manage reports

### Group Features
- `groups/` - Group list and management
- `group-detail/` - Group details, members, linked accounts
- `group-chat/` - Group chat interface
- `group-create-modal/` - Create group modal
- `group-update-modal/` - Edit group modal
- `invite-member-modal/` - Invite members modal

### Chat System (Facebook-style)
- `chat-dropdown/` - Chat list dropdown in header
- `chat-box/` - Individual minimized chat box (max 3 at once, bottom-right)
- `chat-container/` - Full chat interface
- `ChatBoxState` - Manages individual box state (minimized, expanded, position)
- `GroupChatComponent` - Group-specific chat view
- AI chat with AI_BOT_001 is already supported — no separate UI needed

### Other
- `profile/` - User profile management
- `contact/` - Contact information
- `home/` - Landing page
- `login/` / `logout/` - Auth pages
- `sepay-callback/` - Sepay OAuth2 callback

## Services (`src/app/services/`)

- `user.service.ts` - User management, profile
- `group.service.ts` - Group CRUD, invitations, members, accounts
- `account.service.ts` - Financial account management
- `transaction.service.ts` - Transaction CRUD, filtering, analytics, export
- `category.service.ts` - Transaction categories
- `chat.service.ts` - Real-time chat, WebSocket, typing indicators, read receipts
- `notification.service.ts` - Real-time notifications, unread counts (`unreadCount$`, `notifications$` observables)
- `file.service.ts` - File upload/download via FileService
- `loan.service.ts` - Loan management
- `planning-budget.service.ts` - Personal budget planning (DAILY/WEEKLY/MONTHLY/YEARLY/NO_REPEAT)
- `group-planning.service.ts` - Group budget planning
- `savings-goal.service.ts` — **NEW:** Savings goal API calls
- `bill-reminder.service.ts` — **NEW:** Bill reminder API calls
- `cron.service.ts` - Cron expression validation
- `ai-analytics.service.ts` — **NEW:** AI analytics API
- `sepay-oauth2.service.ts` - Sepay OAuth flow
- `dashboard/dashboard.service.ts` - Dashboard data aggregation
- `language.service.ts` - i18n (English/Vietnamese), `language.translate('key.path')`
- `layout.service.ts` - Responsive layout state (mobile/desktop)
- `loading.service.ts` - Global loading state
- `toast.service.ts` - Toast notification management
- `help.service.ts` - Help content
- `market-data.service.ts` - Market ticker data

## Models (`src/app/model/`)

- `user.model.ts`
- `account.model.ts`
- `account.transactions.model.ts`
- `chat.model.ts` - ChatRoom, ChatMessage, SendMessageRequest, etc.
- `loans.model.ts`
- `cards.model.ts`
- `contact.model.ts`

**Key model notes:**
- `GroupSummary.name` (not `groupName`)
- `GroupMember.fullName` (not `username`)
- `GroupPlanning.category` + `budgetAmount` (not `planName`/`totalBudget`)
- `NotificationService.unreadCount$` and `notifications$` are observables (not methods)

## State Management

Services use RxJS BehaviorSubjects:
```typescript
private dataSubject = new BehaviorSubject<Data[]>([]);
public data$ = this.dataSubject.asObservable();
```
Components subscribe and use `async` pipe in templates when possible.

## Common Auth Header Pattern

```typescript
private getAuthHeaders(): Observable<HttpHeaders> {
  return from(this.keycloak.getToken()).pipe(
    switchMap(token => of(new HttpHeaders({
      'Authorization': `Bearer ${token}`,
      'Content-Type': 'application/json'
    })))
  );
}
```

## Internationalization

- Files: `src/assets/i18n/en.json`, `src/assets/i18n/vi.json`
- Usage: `language.translate('key.path')`
- Always update both language files when adding new UI text

## Styling & Libraries

- **Bootstrap 5.3** - Primary CSS framework
- **PrimeNG 15** - UI component library
- **FontAwesome** - Icons
- **ApexCharts** (`ng-apexcharts`) - Charts (transactions, statistics)
- **Highcharts** - Advanced charts (analytics dashboard)
- **@stomp/stompjs** + **sockjs-client** - WebSocket

> **Bundle size note:** Highcharts + ApexCharts = large bundle. Budget limits in angular.json are set to 4MB warning / 8MB error. Be mindful of adding more heavy dependencies.

## CSRF Protection

```typescript
HttpClientXsrfModule.withOptions({
  cookieName: 'XSRF-TOKEN',
  headerName: 'X-XSRF-TOKEN',
})
```

## HTTP Interceptors

- **LoadingInterceptor** - Auto show/hide loading spinner during HTTP requests

## File Structure Conventions

- **Components:** `src/app/components/{feature}/{feature}.component.ts|html|css`
- **Services:** `src/app/services/{feature}.service.ts`
- **Models:** `src/app/model/{feature}.model.ts`
- **Guards:** `src/app/routeguards/{feature}.route.ts`
- **Interceptors:** `src/app/interceptors/{feature}.interceptor.ts`
- **Constants:** `src/app/constants/app.constants.ts`
- **i18n:** `src/assets/i18n/en.json`, `src/assets/i18n/vi.json`

## Adding a New Feature

1. `ng generate component components/feature-name`
2. Create/extend service in `src/app/services/`
3. Add route in `app-routing.module.ts` with `AuthKeyClockGuard`
4. Update models in `src/app/model/` if needed
5. Add translations to both `en.json` and `vi.json`

## WebSocket Connection Pattern

```typescript
ngOnInit(): void {
  this.chatService.connect(userId);  // Initialize WebSocket
  this.chatService.messages$.subscribe(msg => { ... });
}

ngOnDestroy(): void {
  this.chatService.disconnect();  // Clean up
}
```
- STOMP client auto-reconnects with 5-second delay
- Use BehaviorSubject for state management across components
