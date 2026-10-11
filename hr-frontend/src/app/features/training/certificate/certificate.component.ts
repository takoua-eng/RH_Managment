import { Component, Input, Output, EventEmitter, ElementRef, ViewChild, OnInit, OnDestroy, HostListener } from '@angular/core';
import { CommonModule } from '@angular/common';
import html2canvas from 'html2canvas';
import jsPDF from 'jspdf';

@Component({
  selector: 'app-certificate',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './certificate.component.html',
  styleUrl: './certificate.component.scss'
})
export class CertificateComponent implements OnInit, OnDestroy {
  @Input() employeeName: string = '';
  @Input() trainingTitle: string = '';
  @Input() duration: string = '';
  @Input() trainer: string = '';
  @Input() startDate?: string;
  @Input() endDate?: string;
  @Input() level?: string;
  @Input() completionDate?: string;
  @Input() certificateId?: string;
  @Input() officialCertificatePath?: string;

  @Output() close = new EventEmitter<void>();
  @Output() downloadOfficial = new EventEmitter<void>();

  @ViewChild('certificateContent', { static: false }) certificateContent!: ElementRef<HTMLElement>;

  isGenerating = false;

  /** Numéro affiché si aucun identifiant officiel n'est fourni : généré une seule fois. */
  private generatedCertificateId = '';

  ngOnInit(): void {
    document.body.classList.add('modal-open');
    this.generatedCertificateId = this.generateCertificateId();
  }

  ngOnDestroy(): void {
    document.body.classList.remove('modal-open');
  }

  @HostListener('document:keydown.escape')
  onKeydownHandler() {
    this.onClose();
  }

  get formattedCertificateId(): string {
    return this.certificateId || this.generatedCertificateId;
  }

  get formattedCompletionDate(): string {
    if (this.completionDate) return this.completionDate;
    if (this.endDate) return this.endDate;
    return new Date().toLocaleDateString('fr-FR', {
      day: '2-digit',
      month: 'long',
      year: 'numeric'
    });
  }

  /**
   * Numéro au format CERT-AAAA-NNNNNN, tiré avec le générateur cryptographique du navigateur
   * (imprévisible, contrairement à Math.random).
   */
  private generateCertificateId(): string {
    const year = new Date().getFullYear();
    const randomValue = crypto.getRandomValues(new Uint32Array(1))[0];
    const sixDigits = 100000 + (randomValue % 900000);
    return `CERT-${year}-${sixDigits}`;
  }

  async downloadPdf(): Promise<void> {
    if (!this.certificateContent || this.isGenerating) return;

    this.isGenerating = true;

    try {
      // Attend la fin du chargement des polices avant la capture
      if (document.fonts?.ready) {
        await document.fonts.ready;
      }

      const element = this.certificateContent.nativeElement;

      const canvas = await html2canvas(element, {
        scale: 2,
        useCORS: true,
        allowTaint: true,
        backgroundColor: '#ffffff',
        logging: false
      });

      const imgData = canvas.toDataURL('image/png');
      const pdf = new jsPDF({
        orientation: 'landscape',
        unit: 'mm',
        format: 'a4'
      });

      const pdfWidth = pdf.internal.pageSize.getWidth();
      const pdfHeight = pdf.internal.pageSize.getHeight();

      pdf.addImage(imgData, 'PNG', 0, 0, pdfWidth, pdfHeight);

      const cleanTitle = this.toFileNamePart(this.trainingTitle || 'Formation');
      const cleanName = this.toFileNamePart(this.employeeName || 'Employe');

      pdf.save(`Certificat_${cleanTitle}_${cleanName}.pdf`);
    } catch (err) {
      console.error('Error generating PDF certificate:', err);
    } finally {
      this.isGenerating = false;
    }
  }

  /** Retire les accents et remplace les caractères spéciaux pour un nom de fichier sûr. */
  private toFileNamePart(text: string): string {
    return text
      .normalize('NFD')
      .replace(/[\u0300-\u036f]/g, '')
      .replace(/[^a-zA-Z0-9]/g, '_');
  }

  onClose(): void {
    document.body.classList.remove('modal-open');
    this.close.emit();
  }

  onDownloadOfficial(): void {
    this.downloadOfficial.emit();
  }
}