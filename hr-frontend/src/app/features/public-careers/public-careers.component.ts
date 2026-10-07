import { 
  Component, 
  OnInit, 
  Inject, 
  ChangeDetectionStrategy, 
  signal, 
  computed, 
  DestroyRef 
} from '@angular/core';
import { CommonModule, DOCUMENT } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router, ActivatedRoute } from '@angular/router';
import { Title, Meta } from '@angular/platform-browser';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Subject } from 'rxjs';
import { debounceTime, distinctUntilChanged } from 'rxjs/operators';
import { CandidateService } from '../../core/services/candidate.service';
import { JobOffer } from '../../core/models/interfaces';

@Component({
  selector: 'app-public-careers',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './public-careers.component.html',
  styleUrl: './public-careers.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class PublicCareersComponent implements OnInit {
  // --- SIGNAL STATES ---
  readonly offers = signal<JobOffer[]>([]);
  readonly loading = signal<boolean>(true);
  readonly errorMessage = signal<string>('');

  readonly searchQuery = signal<string>('');
  readonly departmentFilter = signal<string>('');
  readonly contractFilter = signal<string>('');
  readonly sortBy = signal<'recent' | 'title'>('recent');

  readonly pageSize = 6;
  readonly displayedCount = signal<number>(6);

  readonly animatedCount = signal<number>(0);
  readonly copiedOfferId = signal<number | null>(null);
  readonly openFaqIndex = signal<number | null>(0);

  // --- DYNAMICALLY COMPUTED LISTS & FILTERS FROM OFFERS ---
  readonly availableDepartments = computed(() => {
    const set = new Set<string>();
    this.offers().forEach(o => {
      if (o.department?.trim()) set.add(o.department.trim());
    });
    return Array.from(set).sort();
  });

  readonly availableContractTypes = computed(() => {
    const set = new Set<string>();
    this.offers().forEach(o => {
      if (o.contractType?.trim()) set.add(o.contractType.trim());
    });
    return Array.from(set).sort();
  });

  readonly filteredOffers = computed(() => {
    const q = this.searchQuery().toLowerCase().trim();
    const deptFilter = this.departmentFilter().toLowerCase().trim();
    const contractF = this.contractFilter().toLowerCase().trim();
    const sort = this.sortBy();

    let result = this.offers().filter(offer => {
      const matchQuery = !q || (
        (offer.title && offer.title.toLowerCase().includes(q)) ||
        (offer.department && offer.department.toLowerCase().includes(q)) ||
        (offer.location && offer.location.toLowerCase().includes(q)) ||
        (offer.description && offer.description.toLowerCase().includes(q)) ||
        (offer.experienceLevel && offer.experienceLevel.toLowerCase().includes(q))
      );

      const matchDept = !deptFilter || (offer.department && offer.department.toLowerCase() === deptFilter);
      const matchContract = !contractF || (offer.contractType && offer.contractType.toLowerCase() === contractF);

      return matchQuery && matchDept && matchContract;
    });

    if (sort === 'title') {
      result.sort((a, b) => (a.title || '').localeCompare(b.title || ''));
    } else {
      result.sort((a, b) => {
        const dateA = a.publicationDate ? new Date(a.publicationDate).getTime() : (a.id || 0);
        const dateB = b.publicationDate ? new Date(b.publicationDate).getTime() : (b.id || 0);
        return dateB - dateA;
      });
    }

    return result;
  });

  readonly visibleOffers = computed(() => {
    return this.filteredOffers().slice(0, this.displayedCount());
  });

  readonly hasActiveFilters = computed(() => {
    return !!(this.searchQuery() || this.departmentFilter() || this.contractFilter());
  });

  // --- STATIC SECTION DATA ---
  readonly perks = [
    {
      icon: 'trending_up',
      title: 'Évolution de carrière',
      description: 'Des opportunités de mobilité interne et des parcours sur-mesure pour développer tout votre potentiel.'
    },
    {
      icon: 'home_work',
      title: 'Flexibilité & Télétravail',
      description: 'Un mode de travail hybride adapté pour concilier au mieux vie professionnelle et vie personnelle.'
    },
    {
      icon: 'school',
      title: 'Formation continue',
      description: 'Accès illimité à des programmes de formation certifiants et ateliers pour faire évoluer vos compétences.'
    },
    {
      icon: 'sentiment_very_satisfied',
      title: 'Superbe ambiance',
      description: 'Une culture d\'entreprise transparente, basée sur la bienveillance, la confiance et des événements d\'équipe.'
    }
  ];

  keyStats = [
    { label: 'Collaborateurs passionnés', target: 250, suffix: '+', current: 0 },
    { label: 'Années d\'expertise', target: 12, suffix: ' ans', current: 0 },
    { label: 'Offres ouvertes', target: 0, suffix: '', current: 0, isDynamic: true },
    { label: 'Satisfaction équipiers', target: 98, suffix: '%', current: 0 }
  ];

  readonly recruitmentSteps = [
    { step: 1, icon: 'edit_note', title: 'Candidature', description: 'Envoi de votre CV et examen rapide de votre profil par notre équipe RH.' },
    { step: 2, icon: 'forum', title: 'Entretien RH', description: 'Premier échange de 30 min pour faire connaissance et aligner vos attentes.' },
    { step: 3, icon: 'psychology', title: 'Entretien Technique', description: 'Échange approfondi avec l\'équipe métier et étude de cas pratique.' },
    { step: 4, icon: 'handshake', title: 'Offre & Onboarding', description: 'Proposition d\'embauche et parcours d\'intégration personnalisé dès le J1.' }
  ];

  readonly faqItems = [
    {
      question: 'Quel est le délai moyen de réponse après l\'envoi de ma candidature ?',
      answer: 'Notre équipe RH étudie chaque candidature avec attention et s\'engage à vous donner un retour personnalisé sous 48h à 72h ouvrées.'
    },
    {
      question: 'Comment envoyer une candidature spontanée si aucune offre ne correspond ?',
      answer: 'Vous pouvez utiliser notre bouton "Candidature spontanée" au bas de la page pour nous transmettre votre CV. Votre profil sera conservé avec soin dans notre vivier de talents.'
    },
    {
      question: 'Comment se déroule l\'intégration des nouveaux collaborateurs (Onboarding) ?',
      answer: 'Dès votre arrivée, vous bénéficiez d\'un mentor dédié, d\'un équipement complet et de sessions de découverte avec les responsables de pôles.'
    },
    {
      question: 'Le télétravail est-il accessible dès la période d\'essai ?',
      answer: 'Oui, nous proposons une charte hybride (jusqu\'à 2 jours par semaine) disponible après l\'intégration initiale.'
    }
  ];

  private searchSubject = new Subject<string>();

  constructor(
    private candidateService: CandidateService,
    private router: Router,
    private route: ActivatedRoute,
    private titleService: Title,
    private metaService: Meta,
    @Inject(DOCUMENT) private document: Document,
    private destroyRef: DestroyRef
  ) {
    // Search input debouncer with takeUntilDestroyed
    this.searchSubject.pipe(
      debounceTime(300),
      distinctUntilChanged(),
      takeUntilDestroyed(this.destroyRef)
    ).subscribe(query => {
      this.searchQuery.set(query);
      this.resetPagination();
      this.updateUrl();
    });
  }

  ngOnInit(): void {
    // SEO setup
    this.setSeoMetaData();

    // Query parameters subscription with takeUntilDestroyed
    this.route.queryParams.pipe(
      takeUntilDestroyed(this.destroyRef)
    ).subscribe(params => {
      this.searchQuery.set(params['q'] || '');
      this.departmentFilter.set(params['dept'] || '');
      this.contractFilter.set(params['contract'] || '');
      this.sortBy.set(params['sort'] === 'title' ? 'title' : 'recent');
      this.resetPagination();
    });

    // Fetch offers with takeUntilDestroyed
    this.candidateService.getAllPublicOffers().pipe(
      takeUntilDestroyed(this.destroyRef)
    ).subscribe({
      next: (data) => {
        this.offers.set(data);
        this.loading.set(false);
        this.startCounterAnimation(data.length);
        this.animateKeyStats();
        this.injectJsonLdSchema(data);
      },
      error: (err) => {
        this.errorMessage.set("Impossible de charger les offres d'emploi pour le moment.");
        this.loading.set(false);
        console.error(err);
        this.animateKeyStats();
      }
    });
  }

  // --- ACTIONS & STATE UPDATES ---
  onSearchInput(value: string): void {
    this.searchSubject.next(value);
  }

  setDepartmentFilter(dept: string): void {
    this.departmentFilter.set(dept);
    this.resetPagination();
    this.updateUrl();
  }

  toggleContractFilter(contract: string): void {
    if (this.contractFilter().toLowerCase() === contract.toLowerCase()) {
      this.contractFilter.set('');
    } else {
      this.contractFilter.set(contract);
    }
    this.resetPagination();
    this.updateUrl();
  }

  setSortBy(sort: 'recent' | 'title'): void {
    this.sortBy.set(sort);
    this.resetPagination();
    this.updateUrl();
  }

  clearSearch(): void {
    this.searchQuery.set('');
    this.resetPagination();
    this.updateUrl();
  }

  clearDepartment(): void {
    this.departmentFilter.set('');
    this.resetPagination();
    this.updateUrl();
  }

  clearContract(): void {
    this.contractFilter.set('');
    this.resetPagination();
    this.updateUrl();
  }

  clearAllFilters(): void {
    this.searchQuery.set('');
    this.departmentFilter.set('');
    this.contractFilter.set('');
    this.sortBy.set('recent');
    this.resetPagination();
    this.updateUrl();
  }

  loadMoreOffers(): void {
    this.displayedCount.update(count => count + this.pageSize);
  }

  resetPagination(): void {
    this.displayedCount.set(this.pageSize);
  }

  toggleFaq(index: number): void {
    this.openFaqIndex.update(current => current === index ? null : index);
  }

  // --- COUNTER & STATS HELPERS ---
  getDepartmentCount(dept: string): number {
    if (!dept) return this.offers().length;
    return this.offers().filter(o => o.department?.toLowerCase() === dept.toLowerCase()).length;
  }

  getContractCount(contract: string): number {
    if (!contract) return this.offers().length;
    return this.offers().filter(o => o.contractType?.toLowerCase() === contract.toLowerCase()).length;
  }

  private updateUrl(): void {
    const queryParams: Record<string, string | null> = {
      q: this.searchQuery() || null,
      dept: this.departmentFilter() || null,
      contract: this.contractFilter() || null,
      sort: this.sortBy() !== 'recent' ? this.sortBy() : null
    };

    this.router.navigate([], {
      relativeTo: this.route,
      queryParams,
      queryParamsHandling: 'merge'
    });
  }

  // --- ANIMATION HELPERS ---
  private animateKeyStats(): void {
    this.keyStats.forEach(stat => {
      const target = stat.isDynamic ? this.offers().length : stat.target;
      if (target <= 0) {
        stat.current = 0;
        return;
      }
      const duration = 1400;
      const startTime = performance.now();

      const animate = (currentTime: number) => {
        const elapsedTime = currentTime - startTime;
        const progress = Math.min(elapsedTime / duration, 1);
        const easeOutProgress = 1 - (1 - progress) * (1 - progress);
        stat.current = Math.floor(easeOutProgress * target);

        if (progress < 1) {
          requestAnimationFrame(animate);
        } else {
          stat.current = target;
        }
      };

      requestAnimationFrame(animate);
    });
  }

  private startCounterAnimation(target: number): void {
    if (target <= 0) {
      this.animatedCount.set(0);
      return;
    }
    const duration = 1200;
    const startTime = performance.now();

    const animate = (currentTime: number) => {
      const elapsedTime = currentTime - startTime;
      const progress = Math.min(elapsedTime / duration, 1);
      const easeOutProgress = 1 - (1 - progress) * (1 - progress);
      this.animatedCount.set(Math.floor(easeOutProgress * target));

      if (progress < 1) {
        requestAnimationFrame(animate);
      } else {
        this.animatedCount.set(target);
      }
    };

    requestAnimationFrame(animate);
  }

  // --- SEO & META TAGS ---
  private setSeoMetaData(): void {
    const title = 'Rejoignez-nous - Offres d\'emploi & Carrières';
    const description = 'Découvrez nos opportunités professionnelles, postulez en ligne et rejoignez une équipe dynamique et innovante.';
    const currentUrl = typeof window !== 'undefined' ? window.location.href : '';

    this.titleService.setTitle(title);
    this.metaService.updateTag({ name: 'description', content: description });
    this.metaService.updateTag({ property: 'og:title', content: title });
    this.metaService.updateTag({ property: 'og:description', content: description });
    this.metaService.updateTag({ property: 'og:type', content: 'website' });
    this.metaService.updateTag({ property: 'og:url', content: currentUrl });
    this.metaService.updateTag({ name: 'twitter:card', content: 'summary_large_image' });
    this.metaService.updateTag({ name: 'twitter:title', content: title });
    this.metaService.updateTag({ name: 'twitter:description', content: description });
  }

  private injectJsonLdSchema(offers: JobOffer[]): void {
    if (typeof document === 'undefined') return;
    const existingScript = this.document.getElementById('job-posting-jsonld');
    if (existingScript) {
      existingScript.remove();
    }

    if (!offers || offers.length === 0) return;

    const schemaList = offers.map(offer => ({
      '@context': 'https://schema.org/',
      '@type': 'JobPosting',
      'title': offer.title,
      'description': offer.description || offer.title,
      'datePosted': offer.publicationDate ? new Date(offer.publicationDate).toISOString().split('T')[0] : new Date().toISOString().split('T')[0],
      'employmentType': offer.contractType || 'FULL_TIME',
      'hiringOrganization': {
        '@type': 'Organization',
        'name': 'Notre Entreprise',
        'sameAs': typeof window !== 'undefined' ? window.location.origin : ''
      },
      'jobLocation': {
        '@type': 'Place',
        'address': {
          '@type': 'PostalAddress',
          'addressLocality': offer.location || 'Paris',
          'addressCountry': 'FR'
        }
      }
    }));

    const script = this.document.createElement('script');
    script.id = 'job-posting-jsonld';
    script.type = 'application/ld+json';
    script.text = JSON.stringify(schemaList);
    this.document.head.appendChild(script);
  }

  // --- SLUG & NAVIGATION HELPERS ---
  slugify(text: string): string {
    if (!text) return '';
    return text
      .toString()
      .normalize('NFD')
      .replace(/[\u0300-\u036f]/g, '')
      .toLowerCase()
      .trim()
      .replace(/\s+/g, '-')
      .replace(/[^\w\-]+/g, '')
      .replace(/\-\-+/g, '-');
  }

  getOfferSlugUrl(offer: JobOffer): string {
    const slug = this.slugify(offer.title || 'offre');
    return `/apply/${offer.id}-${slug}`;
  }

  getOfferFullUrl(offer: JobOffer): string {
    const origin = typeof window !== 'undefined' ? window.location.origin : '';
    return `${origin}${this.getOfferSlugUrl(offer)}`;
  }

  shareOnLinkedIn(event: Event, offer: JobOffer): void {
    event.stopPropagation();
    const url = this.getOfferFullUrl(offer);
    const shareUrl = `https://www.linkedin.com/sharing/share-offsite/?url=${encodeURIComponent(url)}`;
    if (typeof window !== 'undefined') {
      window.open(shareUrl, '_blank', 'width=600,height=600');
    }
  }

  shareOnWhatsApp(event: Event, offer: JobOffer): void {
    event.stopPropagation();
    const url = this.getOfferFullUrl(offer);
    const text = `Découvrez cette offre d'emploi : ${offer.title} - ${url}`;
    const shareUrl = `https://api.whatsapp.com/send?text=${encodeURIComponent(text)}`;
    if (typeof window !== 'undefined') {
      window.open(shareUrl, '_blank');
    }
  }

  copyOfferLink(event: Event, offer: JobOffer): void {
    event.stopPropagation();
    const url = this.getOfferFullUrl(offer);
    if (typeof navigator !== 'undefined' && navigator.clipboard) {
      navigator.clipboard.writeText(url).then(() => {
        if (offer.id) {
          this.copiedOfferId.set(offer.id);
          setTimeout(() => {
            if (this.copiedOfferId() === offer.id) {
              this.copiedOfferId.set(null);
            }
          }, 2000);
        }
      });
    }
  }

  getRelativeDate(dateInput: string | Date | undefined): string {
    if (!dateInput) return '';
    const date = new Date(dateInput);
    const now = new Date();
    const diffInSeconds = Math.floor((now.getTime() - date.getTime()) / 1000);

    if (isNaN(diffInSeconds) || diffInSeconds < 0) return "Aujourd'hui";

    const days = Math.floor(diffInSeconds / (3600 * 24));
    if (days === 0) return "Aujourd'hui";
    if (days === 1) return 'Hier';
    if (days < 7) return `Il y a ${days} jours`;

    const weeks = Math.floor(days / 7);
    if (weeks === 1) return 'Il y a 1 semaine';
    if (weeks < 4) return `Il y a ${weeks} semaines`;

    const months = Math.floor(days / 30);
    if (months === 1) return 'Il y a 1 mois';
    if (months < 12) return `Il y a ${months} mois`;

    return `Publiée le ${date.toLocaleDateString('fr-FR')}`;
  }

  isNewOffer(dateInput: string | Date | undefined): boolean {
    if (!dateInput) return false;
    const date = new Date(dateInput);
    const now = new Date();
    const diffInDays = (now.getTime() - date.getTime()) / (1000 * 3600 * 24);
    return diffInDays >= 0 && diffInDays <= 7;
  }

  getDepartmentIcon(department: string | undefined): string {
    if (!department) return 'work';
    const dept = department.toLowerCase();
    if (dept.includes('it') || dept.includes('tech') || dept.includes('dev') || dept.includes('informatique')) return 'code';
    if (dept.includes('rh') || dept.includes('humain') || dept.includes('personnel')) return 'groups';
    if (dept.includes('finan') || dept.includes('compta') || dept.includes('gestion')) return 'payments';
    if (dept.includes('market') || dept.includes('com') || dept.includes('media')) return 'campaign';
    if (dept.includes('vent') || dept.includes('commercial') || dept.includes('sale')) return 'sell';
    return 'work';
  }

  getContractBadgeClass(contractType: string | undefined): string {
    if (!contractType) return 'badge-contract-default';
    const type = contractType.toLowerCase().trim();
    if (type === 'cdi') return 'badge-contract-cdi';
    if (type === 'cdd') return 'badge-contract-cdd';
    if (type === 'stage') return 'badge-contract-stage';
    if (type === 'alternance') return 'badge-contract-alternance';
    if (type === 'freelance') return 'badge-contract-freelance';
    return 'badge-contract-default';
  }

  scrollToOffers(): void {
    const element = document.getElementById('offers-section');
    if (element) {
      element.scrollIntoView({ behavior: 'smooth', block: 'start' });
    }
  }

  goToOffer(id: number | undefined, title?: string): void {
    if (id) {
      const slug = title ? this.slugify(title) : 'offre';
      this.router.navigate(['/apply', `${id}-${slug}`]);
    }
  }
}
