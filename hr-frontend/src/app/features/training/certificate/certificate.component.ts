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

  ngOnInit(): void {
    document.body.classList.add('modal-open');
  }

  ngOnDestroy(): void {
    document.body.classList.remove('modal-open');
  }

  @HostListener('document:keydown.escape')
  onKeydownHandler() {
    this.onClose();
  }

  get formattedCertificateId(): string {
    if (this.certificateId) return this.certificateId;
    const year = new Date().getFullYear();
    const randomNum = Math.floor(100000 + Math.random() * 900000);
    return `CERT-${year}-${randomNum}`;
  }

  get formattedCompletionDate(): string {
    if (this.completionDate) return this.completionDate;
    if (this.endDate) return this.endDate;
    const today = new Date();
    return today.toLocaleDateString('fr-FR', {
      day: '2-digit',
      month: 'long',
      year: 'numeric'
    });
  }

  async downloadPdf(): Promise<void> {
    if (!this.certificateContent || this.isGenerating) return;

    this.isGenerating = true;

    try {
      // Wait for document fonts to finish loading
      if (document.fonts && document.fonts.ready) {
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

      const cleanTitle = (this.trainingTitle || 'Formation')
        .normalize('NFD')
        .replace(/[\u0300-\u036f]/g, '')
        .replace(/[^a-zA-Z0-9]/g, '_');

      const cleanName = (this.employeeName || 'Employe')
        .normalize('NFD')
        .replace(/[\u0300-\u036f]/g, '')
        .replace(/[^a-zA-Z0-9]/g, '_');

      const filename = `Certificat_${cleanTitle}_${cleanName}.pdf`;
      pdf.save(filename);
    } catch (err) {
      console.error('Error generating PDF certificate:', err);
    } finally {
      this.isGenerating = false;
    }
  }

  onClose(): void {
    document.body.classList.remove('modal-open');
    this.close.emit();
  }

  onDownloadOfficial(): void {
    this.downloadOfficial.emit();
  }
}

