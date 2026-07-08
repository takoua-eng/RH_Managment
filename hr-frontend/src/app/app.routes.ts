import { Routes } from '@angular/router';
import { LoginComponent } from './features/login/login.component';
import { LayoutComponent } from './layout/layout.component';
import { authGuard } from './auth.guard';
import { DashboardComponent } from './features/dashboard/dashboard.component';
import { EmployeesComponent } from './features/employees/employees.component';
import { DepartmentsComponent } from './features/departments/departments.component';
import { RecruitmentComponent } from './features/recruitment/recruitment.component';
import { AiAnalysisComponent } from './features/ai-analysis/ai-analysis.component';
import { LeaveComponent } from './features/leave/leave.component';
import { DocumentsComponent } from './features/documents/documents.component';
import { TrainingComponent } from './features/training/training.component';
import { EvaluationsComponent } from './features/evaluations/evaluations.component';
import { ChatbotComponent } from './features/chatbot/chatbot.component';
import { SettingsComponent } from './features/settings/settings.component';

export const routes: Routes = [
  { path: '', redirectTo: 'login', pathMatch: 'full' },
  { path: 'login', component: LoginComponent },
  {
    path: 'dashboard',
    component: LayoutComponent,
    canActivate: [authGuard],
    children: [
      { path: '', component: DashboardComponent },
      { path: 'employees', component: EmployeesComponent },
      { path: 'departments', component: DepartmentsComponent },
      { path: 'recruitment', component: RecruitmentComponent },
      { path: 'ai-analysis', component: AiAnalysisComponent },
      { path: 'leave', component: LeaveComponent },
      { path: 'documents', component: DocumentsComponent },
      { path: 'training', component: TrainingComponent },
      { path: 'evaluations', component: EvaluationsComponent },
      { path: 'chatbot', component: ChatbotComponent },
      { path: 'settings', component: SettingsComponent }
    ]
  },
  { path: '**', redirectTo: 'login' }
];
