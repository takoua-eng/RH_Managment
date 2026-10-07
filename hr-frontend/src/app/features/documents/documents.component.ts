import { Component, OnInit, OnDestroy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { DomSanitizer, SafeResourceUrl } from '@angular/platform-browser';
import { Subscription } from 'rxjs';
import { DocumentService } from '../../core/services/document.service';
import { AuthService } from '../../core/services/auth.service';
import { EmployeeService } from '../../core/services/employee.service';
import { HrDocument, Employee } from '../../core/models/interfaces';
import { ConfirmDialogService } from '../../core/services/confirm-dialog.service';
import { ToastNotificationService } from '../../core/services/toast-notification.service';

@Component({
  selector: 'app-documents',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './documents.component.html',
  styleUrl: './documents.component.scss'
})
export class DocumentsComponent implements OnInit, OnDestroy {
  documents: HrDocument[] = [];
  filteredDocs: HrDocument[] = [];
  selectedFolder = '';

  folders = [
    { name: 'Contrat', color: 'blue' },
    { name: 'Fiche de paie', color: 'green' },
    { name: 'Diplôme', color: 'yellow' },
    { name: 'Attestation', color: 'red' }
  ];

  // User session state
  currentUser: any = null;
  isAdminOrRHOrManager = false;
  employees: Employee[] = [];
  
  // Filtering & selection state
  selectedEmployeeId: number | null = null;

  // Upload modal state
  showUploadModal = false;
  uploadName = '';
  uploadCategory: HrDocument['type'] = 'Contrat';
  uploadEmployeeId: number | null = null;
  selectedFile: File | null = null;

  // Preview modal state
  showPreviewModal = false;
  selectedDoc: HrDocument | null = null;
  pdfUrl: SafeResourceUrl | null = null;
  private pdfBlobUrl: string | null = null;

  private subscriptions = new Subscription();

  constructor(
    private docService: DocumentService,
    private authService: AuthService,
    private employeeService: EmployeeService,
    private sanitizer: DomSanitizer,
    private confirmService: ConfirmDialogService,
    private toastService: ToastNotificationService
  ) {}

  ngOnInit(): void {
    // 1. Get current user session
    this.subscriptions.add(
      this.authService.currentUser$.subscribe(user => {
        this.currentUser = user;
        if (user) {
          this.isAdminOrRHOrManager = 
            user.role === 'Administrateur' || 
            user.role === 'RH' || 
            user.role === 'Manager';
          
          // Set default selected employee ID for normal employee
          if (!this.isAdminOrRHOrManager) {
            this.selectedEmployeeId = user.id || null;
          }
          
          // Load list of employees for admin/RH dropdowns
          if (this.isAdminOrRHOrManager) {
            this.subscriptions.add(
              this.employeeService.employees$.subscribe(emps => {
                this.employees = emps;
              })
            );
          }

          // Initial load of documents
          this.loadDocuments();
        }
      })
    );

    // 2. Subscribe to document service state updates
    this.subscriptions.add(
      this.docService.docs$.subscribe(data => {
        this.documents = data;
        this.applyFilter();
      })
    );
  }

  ngOnDestroy(): void {
    this.subscriptions.unsubscribe();
    this.revokePdfUrl();
  }

  loading = true;
  errorMessage = '';

  loadDocuments() {
    this.loading = true;
    this.errorMessage = '';
    
    if (this.isAdminOrRHOrManager) {
      if (this.selectedEmployeeId) {
        this.docService.loadDocumentsByEmployee(this.selectedEmployeeId).subscribe({
          next: () => this.loading = false,
          error: err => {
            console.error('Error loading documents by employee:', err);
            this.errorMessage = 'Erreur lors du chargement des documents.';
            this.toastService.error('Erreur lors du chargement des documents.');
            this.loading = false;
          }
        });
      } else {
        this.docService.loadAllDocuments().subscribe({
          next: () => this.loading = false,
          error: err => {
            console.error('Error loading all documents:', err);
            this.errorMessage = 'Erreur lors du chargement des documents.';
            this.toastService.error('Erreur lors du chargement des documents.');
            this.loading = false;
          }
        });
      }
    } else {
      if (this.currentUser && this.currentUser.id) {
        this.docService.loadDocumentsByEmployee(this.currentUser.id).subscribe({
          next: () => this.loading = false,
          error: err => {
            console.error('Error loading personal documents:', err);
            this.errorMessage = 'Erreur lors du chargement de vos documents.';
            this.toastService.error('Erreur lors du chargement de vos documents.');
            this.loading = false;
          }
        });
      } else {
        this.loading = false;
      }
    }
  }

  onEmployeeFilterChange() {
    this.loadDocuments();
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
    this.selectedFile = null;
    // Pre-select employee ID for upload
    if (this.isAdminOrRHOrManager) {
      this.uploadEmployeeId = this.selectedEmployeeId || (this.employees.length > 0 ? this.employees[0].id : null);
    } else {
      this.uploadEmployeeId = this.currentUser?.id || null;
    }
    this.showUploadModal = true;
  }

  onFileSelected(e: Event) {
    const input = e.target as HTMLInputElement;
    if (input.files && input.files.length > 0) {
      this.selectedFile = input.files[0];
      this.uploadName = this.selectedFile.name;
    }
  }

  uploadDocument() {
    if (!this.selectedFile || !this.uploadName) {
      this.toastService.error('Veuillez sélectionner un fichier et renseigner son nom.');
      return;
    }

    const targetEmpId = this.isAdminOrRHOrManager ? this.uploadEmployeeId : this.currentUser?.id;
    if (!targetEmpId) {
      this.toastService.error('Veuillez sélectionner un employé.');
      return;
    }

    this.docService.uploadDocument(
      this.selectedFile,
      this.uploadName,
      this.uploadCategory,
      targetEmpId
    ).subscribe({
      next: () => {
        this.toastService.success('Document téléversé avec succès !');
        this.loadDocuments();
        this.showUploadModal = false;
        this.selectedFile = null;
        this.uploadName = '';
      },
      error: err => {
        this.toastService.error('Erreur lors du dépôt du document: ' + (err.error?.message || err.message));
      }
    });
  }

  previewDoc(doc: HrDocument) {
    this.selectedDoc = doc;
    this.pdfUrl = null;
    this.revokePdfUrl();

    const isRealPdf = doc.name.toLowerCase().endsWith('.pdf');
    if (isRealPdf) {
      this.docService.downloadDocument(doc.id).subscribe({
        next: (blob) => {
          const pdfBlob = new Blob([blob], { type: 'application/pdf' });
          this.pdfBlobUrl = window.URL.createObjectURL(pdfBlob);
          this.pdfUrl = this.sanitizer.bypassSecurityTrustResourceUrl(this.pdfBlobUrl);
          this.showPreviewModal = true;
        },
        error: err => {
          console.error("Failed to load PDF preview from backend:", err);
          this.showPreviewModal = true;
        }
      });
    } else {
      this.showPreviewModal = true;
    }
  }

  downloadDoc(doc: HrDocument) {
    this.docService.downloadDocument(doc.id).subscribe({
      next: (blob) => {
        const url = window.URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = doc.name;
        document.body.appendChild(a);
        a.click();
        document.body.removeChild(a);
        window.URL.revokeObjectURL(url);
        this.toastService.info(`Téléchargement de ${doc.name} démarré.`);
      },
      error: err => {
        this.toastService.error('Erreur lors du téléchargement: ' + err.message);
      }
    });
  }

  async deleteDoc(id: number): Promise<void> {
    const confirmed = await this.confirmService.showConfirm({
      title: 'Suppression de document',
      message: 'Voulez-vous vraiment supprimer ce document ? Cette action est irréversible.',
      confirmText: 'Supprimer',
      cancelText: 'Annuler',
      type: 'danger'
    });

    if (confirmed) {
      this.docService.deleteDocument(id).subscribe({
        next: () => {
          this.toastService.success('Document supprimé avec succès !');
          this.loadDocuments();
        },
        error: err => {
          this.toastService.error('Erreur lors de la suppression: ' + (err.error?.message || err.message));
        }
      });
    }
  }

  closePreviewModal() {
    this.showPreviewModal = false;
    this.selectedDoc = null;
    this.pdfUrl = null;
    this.revokePdfUrl();
  }

  private revokePdfUrl() {
    if (this.pdfBlobUrl) {
      window.URL.revokeObjectURL(this.pdfBlobUrl);
      this.pdfBlobUrl = null;
    }
  }
}
