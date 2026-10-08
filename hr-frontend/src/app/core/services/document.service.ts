import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { BehaviorSubject, Observable } from 'rxjs';
import { tap } from 'rxjs/operators';
import { HrDocument } from '../models/interfaces';
import { environment } from '../../../environments/environment';

@Injectable({
  providedIn: 'root'
})
export class DocumentService {
  private apiUrl = `${environment.apiUrl}/documents`;
  private docsSubject = new BehaviorSubject<HrDocument[]>([]);
  public docs$ = this.docsSubject.asObservable();

  constructor(private http: HttpClient) {}

  public loadDocumentsByEmployee(employeeId: number): Observable<HrDocument[]> {
    return this.http.get<HrDocument[]>(`${this.apiUrl}/employee/${employeeId}`).pipe(
      tap(docs => {
        const mapped = docs.map(d => this.formatDoc(d));
        this.docsSubject.next(mapped);
      })
    );
  }

  public loadAllDocuments(): Observable<HrDocument[]> {
    return this.http.get<HrDocument[]>(`${this.apiUrl}/all`).pipe(
      tap(docs => {
        const mapped = docs.map(d => this.formatDoc(d));
        this.docsSubject.next(mapped);
      })
    );
  }

  private formatDoc(doc: any): HrDocument {
    let sizeStr = '0 B';
    const sizeBytes = doc.size;
    if (sizeBytes > 1024 * 1024) {
      sizeStr = (sizeBytes / (1024 * 1024)).toFixed(1) + ' Mo';
    } else if (sizeBytes > 1024) {
      sizeStr = (sizeBytes / 1024).toFixed(0) + ' Ko';
    } else {
      sizeStr = sizeBytes + ' Octets';
    }

    return {
      ...doc,
      dateUploaded: doc.uploadDate || doc.dateUploaded,
      size: sizeStr,
      url: `${this.apiUrl}/${doc.id}/download`
    };
  }

  public getDocsCount(): number {
    return this.docsSubject.value.length;
  }

  public uploadDocument(file: File, name: string, type: string, employeeId?: number): Observable<HrDocument> {
    const formData = new FormData();
    formData.append('file', file);
    formData.append('name', name);
    formData.append('type', type);
    if (employeeId !== undefined && employeeId !== null) {
      formData.append('employeeId', employeeId.toString());
    }

    return this.http.post<any>(`${this.apiUrl}/upload`, formData);
  }

  public downloadDocument(id: number): Observable<Blob> {
    return this.http.get(`${this.apiUrl}/${id}/download`, { responseType: 'blob' });
  }

  public deleteDocument(id: number): Observable<void> {
    return this.http.delete<void>(`${this.apiUrl}/${id}`);
  }
}
