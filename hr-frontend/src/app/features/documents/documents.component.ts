import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { DocumentService } from '../../core/services/document.service';
import { HrDocument } from '../../core/models/interfaces';

@Component({
  selector: 'app-documents',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './documents.component.html',
  styleUrl: './documents.component.scss'
})
export class DocumentsComponent implements OnInit {
  documents: HrDocument[] = [];
  filteredDocs: HrDocument[] = [];
  selectedFolder = '';

  folders = [
    { name: 'Contrat', color: 'blue' },
    { name: 'Fiche de paie', color: 'green' },
    { name: 'Diplôme', color: 'yellow' },
    { name: 'Attestation', color: 'red' }
  ];

  // Upload modal state
  showUploadModal = false;
  uploadName = '';
  uploadCategory: HrDocument['type'] = 'Contrat';
  selectedFileBytes = 512000; // 500 Ko default

  // Preview modal state
  showPreviewModal = false;
  selectedDoc: HrDocument | null = null;

  constructor(private docService: DocumentService) {}

  ngOnInit(): void {
    this.docService.docs$.subscribe(data => {
      this.documents = data;
      this.applyFilter();
    });
  }

  selectFolder(folderName: string) {
    this.selectedFolder = folderName;
    this.applyFilter();
  }

  applyFilter() {
    this.filteredDocs = this.documents.filter(doc => {
      return !this.selectedFolder || doc.type === this.selectedFolder;
    });
  }

  getCountByFolder(folderName: string): number {
    return this.documents.filter(d => d.type === folderName).length;
  }

  openUploadModal() {
    this.uploadName = '';
    this.uploadCategory = 'Contrat';
    this.showUploadModal = true;
  }

  onFileSelected(e: Event) {
    const input = e.target as HTMLInputElement;
    if (input.files && input.files.length > 0) {
      const file = input.files[0];
      this.uploadName = file.name;
      this.selectedFileBytes = file.size;
    }
  }

  uploadDocument() {
    if (!this.uploadName) return;
    this.docService.addDocument(this.uploadName, this.uploadCategory, this.selectedFileBytes);
    this.showUploadModal = false;
  }

  previewDoc(doc: HrDocument) {
    this.selectedDoc = doc;
    this.showPreviewModal = true;
  }

  downloadDoc(doc: HrDocument) {
    alert(`Téléchargement de "${doc.name}" démarré...`);
  }

  deleteDoc(id: number) {
    if (confirm('Voulez-vous vraiment supprimer ce document ?')) {
      this.docService.deleteDocument(id);
    }
  }
}
