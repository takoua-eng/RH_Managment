import { Injectable } from '@angular/core';
import { BehaviorSubject, Observable, of } from 'rxjs';
import { delay } from 'rxjs/operators';
import { Candidate } from '../models/interfaces';

@Injectable({
  providedIn: 'root'
})
export class RecruitmentService {
  private candidatesSubject = new BehaviorSubject<Candidate[]>([]);
  public candidates$ = this.candidatesSubject.asObservable();

  private defaultCandidates: Candidate[] = [
    {
      id: 1,
      name: 'Ahmed Alami',
      cvName: 'ahmed_cv_java.pdf',
      position: 'Développeur Java Senior',
      aiScore: 95,
      status: 'Entretien',
      matchPercent: 95,
      skills: ['Java', 'Spring Boot', 'Hibernate', 'PostgreSQL', 'Docker', 'Kubernetes'],
      strengths: ['Expertise Java backend', 'Conception d\'architectures microservices', 'Expérience CI/CD'],
      weaknesses: ['Compétences Frontend limitées', 'NoSQL databases']
    },
    {
      id: 2,
      name: 'Sara Benjelloun',
      cvName: 'sara_resume_angular.pdf',
      position: 'Développeuse Angular',
      aiScore: 90,
      status: 'Nouveau',
      matchPercent: 90,
      skills: ['Angular', 'TypeScript', 'RxJS', 'NgRx', 'Bootstrap 5', 'HTML5/CSS3'],
      strengths: ['Excellente maîtrise du framework Angular', 'Composants réutilisables', 'Intégration d\'API'],
      weaknesses: ['Expérience backend limitée', 'Configuration DevOps avancée']
    },
    {
      id: 3,
      name: 'Jean Martin',
      cvName: 'cv_jean_devops.pdf',
      position: 'Ingénieur DevOps',
      aiScore: 88,
      status: 'Nouveau',
      matchPercent: 88,
      skills: ['Docker', 'Kubernetes', 'Ansible', 'Terraform', 'AWS', 'GitLab CI'],
      strengths: ['Automatisation complète', 'Gestion d\'infrastructures cloud AWS', 'Scripting Bash/Python'],
      weaknesses: ['Développement applicatif natif', 'Sécurité réseau approfondie']
    },
    {
      id: 4,
      name: 'Leila Bennani',
      cvName: 'leila_data_science.pdf',
      position: 'Data Scientist',
      aiScore: 92,
      status: 'Embauché',
      matchPercent: 92,
      skills: ['Python', 'TensorFlow', 'PyTorch', 'Pandas', 'SQL', 'Scikit-Learn'],
      strengths: ['Modélisation prédictive avancée', 'Analyse statistique', 'Traitement du langage naturel (NLP)'],
      weaknesses: ['Développement web', 'Administration système Linux']
    }
  ];

  constructor() {
    const savedCandidates = localStorage.getItem('hr_candidates');
    if (savedCandidates) {
      this.candidatesSubject.next(JSON.parse(savedCandidates));
    } else {
      this.saveCandidatesToStorage(this.defaultCandidates);
    }
  }

  private saveCandidatesToStorage(cands: Candidate[]): void {
    localStorage.setItem('hr_candidates', JSON.stringify(cands));
    this.candidatesSubject.next(cands);
  }

  public getCandidates(): Candidate[] {
    return this.candidatesSubject.value;
  }

  public updateCandidateStatus(id: number, status: Candidate['status']): void {
    const current = this.candidatesSubject.value;
    const updated = current.map(c => c.id === id ? { ...c, status } : c);
    this.saveCandidatesToStorage(updated);
  }

  public addCandidate(candidate: Omit<Candidate, 'id'>): void {
    const current = this.candidatesSubject.value;
    const nextId = current.length > 0 ? Math.max(...current.map(c => c.id)) + 1 : 1;
    const newCand: Candidate = {
      ...candidate,
      id: nextId
    };
    this.saveCandidatesToStorage([...current, newCand]);
  }

  // Simulates AI Parsing and returns the exact prompt response
  public simulateAiAnalysis(fileName: string, position: string = 'Développeur Fullstack'): Observable<Omit<Candidate, 'id' | 'status'>> {
    const result: Omit<Candidate, 'id' | 'status'> = {
      name: fileName.replace(/\.[^/.]+$/, '').split('_').map(w => w.charAt(0).toUpperCase() + w.slice(1)).join(' '),
      cvName: fileName,
      position: position,
      aiScore: 94,
      matchPercent: 94,
      skills: ['Spring Boot', 'Angular', 'Docker', 'Kubernetes', 'AWS'],
      strengths: ['Java', 'Spring', 'Docker'],
      weaknesses: ['Azure', 'Kafka']
    };

    // Delay by 2 seconds to simulate processing
    return of(result).pipe(delay(2000));
  }
}
