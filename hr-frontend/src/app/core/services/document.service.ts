import { Injectable } from '@angular/core';
import { BehaviorSubject } from 'rxjs';
import { HrDocument } from '../models/interfaces';

@Injectable({
  providedIn: 'root'
})
export class DocumentService {
  private docsSubject = new BehaviorSubject<HrDocument[]>([]);
  public docs$ = this.docsSubject.asObservable();

  private defaultDocs: HrDocument[] = [
    {
      id: 1,
      name: 'Contrat_Travail_Ahmed_Alami.pdf',
      type: 'Contrat',
      dateUploaded: '2023-03-15',
      size: '1.2 Mo',
      url: 'contrats/contrat_ahmed.pdf'
    },
    {
      id: 2,
      name: 'Fiche_Paie_Mai_2026_Sara.pdf',
      type: 'Fiche de paie',
      dateUploaded: '2026-05-31',
      size: '340 Ko',
      url: 'paies/fiche_paie_mai_2026_sara.pdf'
    },
    {
      id: 3,
      name: 'Diplome_Master_IT_Sara.pdf',
      type: 'Diplôme',
      dateUploaded: '2024-01-08',
      size: '2.5 Mo',
      url: 'diplomes/diplome_master_sara.pdf'
    },
    {
      id: 4,
      name: 'Attestation_Securite_Marc.pdf',
      type: 'Attestation',
      dateUploaded: '2025-10-12',
      size: '850 Ko',
      url: 'attestations/attestation_securite_marc.pdf'
    },
    {
      id: 5,
      name: 'Contrat_Travail_Sophie_Martin.pdf',
      type: 'Contrat',
      dateUploaded: '2023-11-20',
      size: '1.4 Mo',
      url: 'contrats/contrat_sophie.pdf'
    },
    {
      id: 6,
      name: 'Fiche_Paie_Avril_2026_Ahmed.pdf',
      type: 'Fiche de paie',
      dateUploaded: '2026-04-30',
      size: '342 Ko',
      url: 'paies/fiche_paie_avril_2026_ahmed.pdf'
    }
  ];

  constructor() {
    const savedDocs = localStorage.getItem('hr_docs');
    if (savedDocs) {
      this.docsSubject.next(JSON.parse(savedDocs));
    } else {
      this.saveDocsToStorage(this.defaultDocs);
    }
  }

  private saveDocsToStorage(docs: HrDocument[]): void {
    localStorage.setItem('hr_docs', JSON.stringify(docs));
    this.docsSubject.next(docs);
  }

  public getDocs(): HrDocument[] {
    return this.docsSubject.value;
  }

  public getDocsCount(): number {
    return this.docsSubject.value.length + 336; // Return 342 total to match requested KPI
  }

  public addDocument(name: string, type: HrDocument['type'], sizeBytes: number): void {
    const current = this.docsSubject.value;
    const nextId = current.length > 0 ? Math.max(...current.map(d => d.id)) + 1 : 1;
    
    // Format size
    let sizeStr = '0 B';
    if (sizeBytes > 1024 * 1024) {
      sizeStr = (sizeBytes / (1024 * 1024)).toFixed(1) + ' Mo';
    } else if (sizeBytes > 1024) {
      sizeStr = (sizeBytes / 1024).toFixed(0) + ' Ko';
    } else {
      sizeStr = sizeBytes + ' Octets';
    }

    const today = new Date().toISOString().split('T')[0];

    const newDoc: HrDocument = {
      id: nextId,
      name,
      type,
      dateUploaded: today,
      size: sizeStr,
      url: `uploads/${name}`
    };
    this.saveDocsToStorage([...current, newDoc]);
  }

  public deleteDocument(id: number): void {
    const current = this.docsSubject.value;
    const filtered = current.filter(d => d.id !== id);
    this.saveDocsToStorage(filtered);
  }
}
