import { Routes } from '@angular/router';
import { LoginComponent } from './features/login/login.component';
import { LayoutComponent } from './layout/layout.component';
import { ManagerLayoutComponent } from './layout/manager-layout/manager-layout.component';
import { authGuard } from './auth.guard';
import { roleGuard } from './role.guard';
import { DashboardComponent } from './features/dashboard/dashboard.component';
import { EmployeeDashboardComponent } from './features/dashboard/employee-dashboard/employee-dashboard.component';
import { ManagerDashboardComponent } from './features/manager-dashboard/manager-dashboard.component';
import { ManagerTeamComponent } from './features/manager-team/manager-team.component';
import { MemberDetailComponent } from './features/manager-team/member-detail/member-detail.component';
import { ManagerLeavesComponent } from './features/manager-leaves/manager-leaves.component';
import { TeamManagementComponent } from './features/team-management/team-management.component';
import { EmployeesComponent } from './features/employees/employees.component';
import { DepartmentsComponent } from './features/departments/departments.component';
import { RecruitmentComponent } from './features/recruitment/recruitment.component';
import { AiAnalysisComponent } from './features/ai-analysis/ai-analysis.component';
import { LeaveComponent } from './features/leave/leave.component';
import { EmployeeLeaveComponent } from './features/leave/employee-leave/employee-leave.component';
import { TrainingComponent } from './features/training/training.component';
import { EvaluationsComponent } from './features/evaluations/evaluations.component';
import { SettingsComponent } from './features/settings/settings.component';
import { DepartmentFormComponent } from './features/departments/department-form.component';
import { ManagerTeamTrainingsComponent } from './features/manager-team-trainings/manager-team-trainings';
import { AdminTrainingsComponent } from './features/admin-trainings/admin-trainings.component';
import { AdminJobOffersComponent } from './features/admin-job-offers/admin-job-offers.component';
import { PublicApplyComponent } from './features/public-apply/public-apply.component';
import { PublicCareersComponent } from './features/public-careers/public-careers.component';
import { AdminCandidatesComponent } from './features/admin-candidates/admin-candidates.component';

export const routes: Routes = [
  { path: '', redirectTo: 'login', pathMatch: 'full' },
  { path: 'home', redirectTo: 'dashboard', pathMatch: 'full' },
  { path: 'login', component: LoginComponent },
  { path: 'careers', component: PublicCareersComponent },
  { path: 'apply/:id', component: PublicApplyComponent },

  // =========================================================
  // LAYOUT MANAGER (accessible MANAGER + ADMIN)
  // =========================================================
  {
    path: 'manager',
    component: ManagerLayoutComponent,
    canActivate: [authGuard, roleGuard(['MANAGER', 'ADMIN'])],
    children: [
      { path: '', redirectTo: 'dashboard', pathMatch: 'full' },
      { path: 'dashboard',      component: ManagerDashboardComponent },
      { path: 'profile',        redirectTo: 'settings', pathMatch: 'full' },
      { path: 'team',           component: ManagerTeamComponent },
      { path: 'team/:id',       component: MemberDetailComponent },
      { path: 'recruitment',    loadComponent: () => import('./features/manager-recruitment/manager-recruitment.component').then(m => m.ManagerRecruitmentComponent) },
      { path: 'recruitment/:id', loadComponent: () => import('./features/manager-recruitment/manager-candidate-detail/manager-candidate-detail.component').then(m => m.ManagerCandidateDetailComponent) },
      { path: 'interviews',     loadComponent: () => import('./features/manager-interviews/manager-interviews.component').then(m => m.ManagerInterviewsComponent) },
      { path: 'interviews/:id', loadComponent: () => import('./features/manager-interviews/interview-conduct/interview-conduct.component').then(m => m.InterviewConductComponent) },
      { path: 'leaves',         component: ManagerLeavesComponent },
      { path: 'training',       component: TrainingComponent },
      { path: 'team-trainings', component: ManagerTeamTrainingsComponent },
      { path: 'evaluations',    component: EvaluationsComponent },
      { path: 'settings',       component: SettingsComponent },
    ]
  },

  // =========================================================
  // LAYOUT PRINCIPAL (Admin / RH / Employee)
  // =========================================================
  {
    path: 'dashboard',
    component: LayoutComponent,
    canActivate: [authGuard],
    children: [
      { path: '', component: DashboardComponent },
      { path: 'employee', component: EmployeeDashboardComponent },
      { path: 'profile', redirectTo: 'settings', pathMatch: 'full' },
      { path: 'employees', component: EmployeesComponent, canActivate: [roleGuard(['ADMIN', 'RH', 'MANAGER'])] },
      { path: 'departments', component: DepartmentsComponent, canActivate: [roleGuard(['ADMIN', 'RH', 'MANAGER'])] },
      { path: 'recruitment', component: RecruitmentComponent, canActivate: [roleGuard(['ADMIN', 'RH'])] },
      { path: 'ai-analysis', component: AiAnalysisComponent, canActivate: [roleGuard], data: { roles: ['ADMIN', 'RH', 'MANAGER'] } },
      { path: 'admin-job-offers', component: AdminJobOffersComponent, canActivate: [roleGuard], data: { roles: ['ADMIN', 'RH'] } },
      { path: 'admin-candidates', component: AdminCandidatesComponent, canActivate: [roleGuard], data: { roles: ['ADMIN', 'RH'] } },
      { path: 'recruitment-tracking', loadComponent: () => import('./features/recruitment-tracking/recruitment-tracking.component').then(m => m.RecruitmentTrackingComponent), canActivate: [roleGuard], data: { roles: ['ADMIN', 'RH'] } },
      { path: 'admin-candidates/:id', loadComponent: () => import('./features/admin-candidates/candidate-detail/candidate-detail.component').then(c => c.CandidateDetailComponent), canActivate: [roleGuard], data: { roles: ['ADMIN', 'RH'] } },
      { path: 'leaves', component: LeaveComponent, canActivate: [roleGuard(['ADMIN', 'RH', 'MANAGER'])] },
      { path: 'leave', component: EmployeeLeaveComponent },
      { path: 'training', component: TrainingComponent },
      { path: 'evaluations', component: EvaluationsComponent, canActivate: [roleGuard(['ADMIN', 'RH', 'MANAGER', 'EMPLOYEE'])] },
      { path: 'settings', component: SettingsComponent },
      { path: 'departments/new',      component: DepartmentFormComponent,    canActivate: [roleGuard(['ADMIN', 'RH'])] },
      { path: 'departments/:id/edit', component: DepartmentFormComponent,    canActivate: [roleGuard(['ADMIN', 'RH'])] },
      { path: 'team-management',      component: TeamManagementComponent,    canActivate: [roleGuard(['ADMIN', 'RH'])] },
      { path: 'admin-trainings',      component: AdminTrainingsComponent,    canActivate: [roleGuard(['ADMIN', 'RH'])] },
      { path: 'admin-job-offers',     component: AdminJobOffersComponent,    canActivate: [roleGuard(['ADMIN', 'RH'])] }
    ]
  },
  { path: '**', redirectTo: 'login' }
];

