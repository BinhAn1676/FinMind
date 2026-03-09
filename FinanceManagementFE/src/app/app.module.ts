import { BrowserModule } from '@angular/platform-browser';
import { BrowserAnimationsModule } from '@angular/platform-browser/animations';
import { APP_INITIALIZER,NgModule } from '@angular/core';
import { FormsModule, ReactiveFormsModule } from '@angular/forms';
import { HttpClientModule, HttpClientXsrfModule, HTTP_INTERCEPTORS } from '@angular/common/http';
import { AppRoutingModule } from './app-routing.module';
import { AppComponent } from './app.component';
import { HeaderComponent } from './components/header/header.component';
import { HomeComponent } from './components/home/home.component';
import { ContactComponent } from './components/contact/contact.component';
import { LoginComponent } from './components/login/login.component';
import { DashboardComponent } from './components/dashboard/dashboard.component';
import { NoticesComponent } from './components/notices/notices.component';
import { AccountComponent } from './components/account/account.component';
import { BalanceComponent } from './components/balance/balance.component';
import { LoansComponent } from './components/loans/loans.component';
import { CardsComponent } from './components/cards/cards.component';
import { ProfileComponent } from './components/profile/profile.component';
import { TransactionsComponent } from './components/transactions/transactions.component';
import { OverviewComponent } from './components/overview/overview.component';
import { PlanningComponent } from './components/planning/planning.component';
import { SidebarComponent } from './components/sidebar/sidebar.component';
import { LoadingSpinnerComponent } from './components/loading-spinner/loading-spinner.component';
import { ToastComponent } from './components/toast/toast.component';
import { GroupsComponent } from './components/groups/groups.component';
import { GroupCreateModalComponent } from './components/group-create-modal/group-create-modal.component';
import { GroupUpdateModalComponent } from './components/group-update-modal/group-update-modal.component';
import { InviteMemberModalComponent } from './components/invite-member-modal/invite-member-modal.component';
import { GroupDetailComponent } from './components/group-detail/group-detail.component';
import { NotificationBellComponent } from './components/notification-bell/notification-bell.component';
import { StatisticsComponent } from './components/statistics/statistics.component';
import { ChatDropdownComponent } from './components/chat-dropdown/chat-dropdown.component';
import { ChatBoxComponent } from './components/chat-box/chat-box.component';
import { ChatContainerComponent } from './components/chat-container/chat-container.component';
import { GroupChatComponent } from './components/group-chat/group-chat.component';
import { AnalyticsDashboardComponent } from './components/analytics-dashboard/analytics-dashboard.component';
import { ReportsComponent } from './components/reports/reports.component';
import { CronBuilderComponent } from './components/reports/cron-builder/cron-builder.component';
import { SepayCallbackComponent } from './components/sepay-callback/sepay-callback.component';
import { InvestmentComponent } from './components/investment/investment.component';
import { MarketTickerComponent } from './components/market-ticker/market-ticker.component';
import { HelpPanelComponent } from './components/help-panel/help-panel.component';
import { KeycloakAngularModule, KeycloakService } from 'keycloak-angular';
import { LanguageService } from './services/language.service';
import { LoadingInterceptor } from './interceptors/loading.interceptor';
import { NgApexchartsModule } from "ng-apexcharts";
import { CalendarModule } from 'primeng/calendar';

// function initializeKeycloak(keycloak: KeycloakService) {
//   return () =>
//     keycloak.init({
//       config: {
//         url: 'https://auth.finmind.pro.vn/',
//         realm: 'finance',
//         clientId: 'finance-auth-server',
//       },
//       initOptions: {
//         pkceMethod: 'S256',
//         redirectUri: 'https://finmind.pro.vn/overview',
//       },loadUserProfileAtStartUp: false
//     });
// }

function initializeKeycloak(keycloak: KeycloakService) {
  return () =>
    keycloak.init({
      config: {
        url: 'http://localhost:7080/',
        realm: 'finance',
        clientId: 'finance-auth-server',
      },
      initOptions: {
        pkceMethod: 'S256',
        redirectUri: 'http://localhost:4200/overview',
      },loadUserProfileAtStartUp: false
    });
}
@NgModule({
  declarations: [
    AppComponent,
    HeaderComponent,
    HomeComponent,
    ContactComponent,
    LoginComponent,
    DashboardComponent,
    SidebarComponent,
    NoticesComponent,
    AccountComponent,
    BalanceComponent,
    LoansComponent,
    CardsComponent,
    ProfileComponent,
    TransactionsComponent,
    OverviewComponent,
    PlanningComponent,
    LoadingSpinnerComponent,
    ToastComponent,
    GroupsComponent,
    GroupCreateModalComponent,
    GroupUpdateModalComponent,
    InviteMemberModalComponent,
    GroupDetailComponent,
    NotificationBellComponent,
    StatisticsComponent,
    ChatDropdownComponent,
    ChatBoxComponent,
    ChatContainerComponent,
    GroupChatComponent,
    AnalyticsDashboardComponent,
    ReportsComponent,
    CronBuilderComponent,
    SepayCallbackComponent,
    InvestmentComponent,
    MarketTickerComponent,
    HelpPanelComponent
  ],
  imports: [
    NgApexchartsModule,
    BrowserModule,
    BrowserAnimationsModule,
    AppRoutingModule,
    FormsModule,
    ReactiveFormsModule,
    KeycloakAngularModule,
    HttpClientModule,
    HttpClientXsrfModule.withOptions({
      cookieName: 'XSRF-TOKEN',
      headerName: 'X-XSRF-TOKEN',
    }),
    CalendarModule,
  ],
  providers: [
    {
      provide: APP_INITIALIZER,
      useFactory: initializeKeycloak,
      multi: true,
      deps: [KeycloakService],
    },
    {
      provide: HTTP_INTERCEPTORS,
      useClass: LoadingInterceptor,
      multi: true
    },
    LanguageService
  ],
  bootstrap: [AppComponent]
})
export class AppModule {

}
