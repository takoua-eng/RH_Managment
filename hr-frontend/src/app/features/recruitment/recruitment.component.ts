import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { RecruitmentService } from '../../core/services/recruitment.service';
import { MockCandidate as Candidate } from '../../core/models/interfaces';

@Component({
  selector: 'app-recruitment',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './recruitment.component.html',
  styleUrl: './recruitment.component.scss'
})
export class RecruitmentComponent implements OnInit {
  candidates: Candidate[] = [];
  filteredCandidates: Candidate[] = [];
  statusFilter = '';

  // Add Candidate modal state
  showAddModal = false;
  newCandidate: Partial<Candidate> = {
    name: '',
    position: '',
    cvName: '',
    aiScore: 85,
    status: 'Nouveau',
    matchPercent: 85,
    skills: ['Spring', 'Git', 'Agile'],
    strengths: ['Bon relationnel', 'Autonomie'],
    weaknesses: ['DevOps initial']
  };

  // Interview modal state
  showInterviewModal = false;
  selectedCandidate: Candidate | null = null;
  interviewDate = '';
  interviewTime = '';
  interviewInterviewer = '';

  // CV preview modal state
  showCvModal = false;

  constructor(private recruitmentService: RecruitmentService, private router: Router) {}

  ngOnInit(): void {
    this.recruitmentService.candidates$.subscribe(data => {
      this.candidates = data;
      this.applyFilter();
    });
  }

  setStatusFilter(status: string) {
    this.statusFilter = this.statusFilter === status ? '' : status;
    this.applyFilter();
  }

  applyFilter() {
    this.filteredCandidates = this.candidates.filter(c => {
      return !this.statusFilter || c.status === this.statusFilter;
    });
  }

  getCountByStatus(status: string): number {
    return this.candidates.filter(c => c.status === status).length;
  }

  getScoreClass(score: number): string {
    if (score >= 90) return 'success';
    if (score >= 80) return 'primary';
    return 'warning';
  }

  updateStatus(id: number, status: Candidate['status']) {
    this.recruitmentService.updateCandidateStatus(id, status);
  }

  openAddModal() {
    this.newCandidate = {
      name: '',
      position: '',
      cvName: '',
      aiScore: 85,
      status: 'Nouveau',
      matchPercent: 85,
      skills: ['Angular', 'TypeScript', 'CSS'],
      strengths: ['Autonomie', 'Sens du détail'],
      weaknesses: ['Expérience Cloud limitée']
    };
    this.showAddModal = true;
  }

  saveCandidate() {
    this.recruitmentService.addCandidate(this.newCandidate as Candidate);
    this.showAddModal = false;
  }

  openInterviewModal(candidate: Candidate) {
    this.selectedCandidate = candidate;
    this.interviewDate = new Date().toISOString().split('T')[0];
    this.interviewTime = '14:00';
    this.interviewInterviewer = 'Marc Dubois (RH)';
    this.showInterviewModal = true;
  }

  scheduleInterview() {
    if (this.selectedCandidate) {
      this.recruitmentService.updateCandidateStatus(this.selectedCandidate.id, 'Entretien');
      alert(`Entretien planifié pour ${this.selectedCandidate.name} le ${this.interviewDate} à ${this.interviewTime} avec ${this.interviewInterviewer}.`);
    }
    this.showInterviewModal = false;
  }

  viewCv(candidate: Candidate) {
    this.selectedCandidate = candidate;
    this.showCvModal = true;
  }

  analyzeCandidate(candidate: Candidate) {
    // Redirect to AI Analysis tab, saving selected candidate info in local storage for details page sync
    localStorage.setItem('pending_ai_file', candidate.cvName);
    localStorage.setItem('pending_ai_position', candidate.position);
    this.router.navigate(['/dashboard/ai-analysis']);
  }
}
