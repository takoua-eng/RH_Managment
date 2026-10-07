import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { JobOffer } from '../models/interfaces';

@Injectable({
  providedIn: 'root'
})
export class JobOfferService {
  private apiUrl = 'http://localhost:8087/api/recruitment/offers';

  constructor(private http: HttpClient) {}

  getAllOffers(): Observable<JobOffer[]> {
    return this.http.get<JobOffer[]>(this.apiUrl);
  }

  getPublishedOffers(): Observable<JobOffer[]> {
    return this.http.get<JobOffer[]>(`${this.apiUrl}/published`);
  }

  getOfferById(id: number): Observable<JobOffer> {
    return this.http.get<JobOffer>(`${this.apiUrl}/${id}`);
  }

  createOffer(offer: JobOffer): Observable<JobOffer> {
    return this.http.post<JobOffer>(this.apiUrl, offer);
  }

  updateOffer(id: number, offer: JobOffer): Observable<JobOffer> {
    return this.http.put<JobOffer>(`${this.apiUrl}/${id}`, offer);
  }

  deleteOffer(id: number): Observable<void> {
    return this.http.delete<void>(`${this.apiUrl}/${id}`);
  }
}
