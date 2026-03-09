import { NgModule } from '@angular/core';
import { Routes, RouterModule } from '@angular/router';
import { ContactComponent } from './components/contact/contact.component';
import { LoginComponent } from './components/login/login.component';
import { DashboardComponent } from './components/dashboard/dashboard.component';
import { AccountComponent } from '../app/components/account/account.component';
import { BalanceComponent } from '../app/components/balance/balance.component';
import { NoticesComponent } from './components/notices/notices.component';
import { LoansComponent } from './components/loans/loans.component';
import { CardsComponent } from './components/cards/cards.component';
import { ProfileComponent } from './components/profile/profile.component';
import { AuthKeyClockGuard } from './routeguards/auth.route';
import { HomeComponent } from './components/home/home.component';
import { TransactionsComponent } from './components/transactions/transactions.component';
import { OverviewComponent } from './components/overview/overview.component';
import { PlanningComponent } from './components/planning/planning.component';
import { GroupsComponent } from './components/groups/groups.component';
import { StatisticsComponent } from './components/statistics/statistics.component';
import { AnalyticsDashboardComponent } from './components/analytics-dashboard/analytics-dashboard.component';
import { ReportsComponent } from './components/reports/reports.component';
import { SepayCallbackComponent } from './components/sepay-callback/sepay-callback.component';
import { InvestmentComponent } from './components/investment/investment.component';

const routes: Routes = [
  { path: '', redirectTo: '/home', pathMatch: 'full'},
  { path: 'home', component: HomeComponent},
  { path: 'login', component: LoginComponent},
  { path: 'contact', component: ContactComponent},
  { path: 'notices', component: NoticesComponent},
  { path: 'dashboard', component: DashboardComponent, canActivate: [AuthKeyClockGuard],data: { }},
  { path: 'overview', component: OverviewComponent, canActivate: [AuthKeyClockGuard], data: { roles: ['USER'] }},
  { path: 'myAccount', component: AccountComponent, canActivate: [AuthKeyClockGuard],data: {
    roles: ['USER']
  }},
  { path: 'myBalance', component: BalanceComponent, canActivate: [AuthKeyClockGuard],data: {
    roles: ['USER','ADMIN']
  }},
  { path: 'myLoans', component: LoansComponent, canActivate: [AuthKeyClockGuard],data: {

  }},
  { path: 'myCards', component: CardsComponent, canActivate: [AuthKeyClockGuard],data: {
    roles: ['USER']
  }},
  { path: 'profile', component: ProfileComponent, canActivate: [AuthKeyClockGuard],data: {
    roles: ['USER']
  }},
  { path: 'transactions', component: TransactionsComponent, canActivate: [AuthKeyClockGuard],data: {
    roles: ['USER']
  }},
  { path: 'planning', component: PlanningComponent, canActivate: [AuthKeyClockGuard], data: { roles: ['USER'] }},
  { path: 'groups', component: GroupsComponent, canActivate: [AuthKeyClockGuard], data: { roles: ['USER'] }},
  { path: 'statistics', component: StatisticsComponent, canActivate: [AuthKeyClockGuard], data: { roles: ['USER'] }},
  { path: 'analytics', component: AnalyticsDashboardComponent, canActivate: [AuthKeyClockGuard], data: { roles: ['USER'] }},
  { path: 'reports', component: ReportsComponent, canActivate: [AuthKeyClockGuard], data: { roles: ['USER'] }},
  { path: 'sepay/callback', component: SepayCallbackComponent, canActivate: [AuthKeyClockGuard], data: { roles: ['USER'] }},
  { path: 'investment', component: InvestmentComponent, canActivate: [AuthKeyClockGuard], data: { roles: ['USER'] }}
];

@NgModule({
  imports: [RouterModule.forRoot(routes)],
  exports: [RouterModule]
})
export class AppRoutingModule { }
