import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Training } from '../../core/models/interfaces';

@Component({
  selector: 'app-training',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './training.component.html',
  styleUrl: './training.component.scss'
})
export class TrainingComponent implements OnInit {
  courses: Training[] = [
    {
      name: 'Angular 20 & RxJS',
      progression: 75,
      description: 'Maîtriser les composants standalone, le State Management réactif et les architectures complexes avec Angular.',
      status: 'En cours'
    },
    {
      name: 'Java & Spring Boot microservices',
      progression: 100,
      description: 'Concevoir des architectures distribuées de microservices sécurisés avec Spring Boot et Hibernate.',
      status: 'Terminé'
    },
    {
      name: 'Docker Conteneurisation',
      progression: 40,
      description: 'Comprendre et concevoir des conteneurs isolés pour le déploiement reproductible de vos applications.',
      status: 'En cours'
    },
    {
      name: 'Kubernetes Orchestration',
      progression: 0,
      description: 'Pilotez le déploiement, la mise à l\'échelle et la maintenance de grappes de conteneurs en production.',
      status: 'Non commencé'
    },
    {
      name: 'DevOps & AWS Cloud Pipelines',
      progression: 10,
      description: 'Configurez des pipelines de déploiement continu CI/CD robustes sur les infrastructures Cloud AWS.',
      status: 'En cours'
    }
  ];

  constructor() {}

  ngOnInit(): void {
    const saved = localStorage.getItem('hr_courses');
    if (saved) {
      this.courses = JSON.parse(saved);
    }
  }

  saveCourses() {
    localStorage.setItem('hr_courses', JSON.stringify(this.courses));
  }

  getCourseBadgeClass(prog: number): string {
    if (prog === 100) return 'success';
    if (prog === 0) return 'secondary';
    return 'primary';
  }

  getCourseIcon(name: string): string {
    if (name.includes('Angular')) return 'code';
    if (name.includes('Java')) return 'coffee';
    if (name.includes('Docker')) return 'layers';
    if (name.includes('Kubernetes')) return 'sailing';
    return 'settings_suggest';
  }

  getCourseIconBg(name: string): string {
    if (name.includes('Angular')) return 'red';
    if (name.includes('Java')) return 'yellow';
    if (name.includes('Docker')) return 'blue';
    if (name.includes('Kubernetes')) return 'cyan';
    return 'purple';
  }

  getAsciiProgressBar(progression: number): string {
    // Generates representation like ██████████░░░░
    const totalBlocks = 12;
    const filledBlocks = Math.round((progression / 100) * totalBlocks);
    const emptyBlocks = totalBlocks - filledBlocks;
    return '█'.repeat(filledBlocks) + '░'.repeat(emptyBlocks);
  }

  enroll(course: Training) {
    course.status = 'En cours';
    course.progression = 10;
    this.saveCourses();
  }

  study(course: Training) {
    if (course.progression < 100) {
      course.progression += 15;
      if (course.progression >= 100) {
        course.progression = 100;
        course.status = 'Terminé';
      }
      this.saveCourses();
    }
  }

  downloadCertificate(name: string) {
    alert(`Certificat officiel de formation "${name}" généré avec succès pour téléchargement !`);
  }
}
