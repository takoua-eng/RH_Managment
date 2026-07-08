import { Injectable } from '@angular/core';
import { BehaviorSubject, Observable, of } from 'rxjs';
import { delay } from 'rxjs/operators';

export interface ChatMessage {
  id: number;
  sender: 'user' | 'bot';
  text: string;
  time: string;
}

@Injectable({
  providedIn: 'root'
})
export class ChatbotService {
  private messagesSubject = new BehaviorSubject<ChatMessage[]>([
    {
      id: 1,
      sender: 'bot',
      text: 'Bonjour 👋. Je suis l\'Assistant IA RH de votre portail. Comment puis-je vous aider aujourd\'hui ?',
      time: new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
    }
  ]);
  public messages$ = this.messagesSubject.asObservable();

  constructor() {}

  public getMessages(): ChatMessage[] {
    return this.messagesSubject.value;
  }

  public sendMessage(text: string): Observable<ChatMessage> {
    const current = this.messagesSubject.value;
    const userMsg: ChatMessage = {
      id: current.length + 1,
      sender: 'user',
      text,
      time: new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
    };
    this.messagesSubject.next([...current, userMsg]);

    // Compute bot response
    const botReply = this.generateBotResponse(text);
    
    // Simulate thinking/typing delay
    return new Observable<ChatMessage>(subscriber => {
      setTimeout(() => {
        const updated = this.messagesSubject.value;
        const botMsg: ChatMessage = {
          id: updated.length + 1,
          sender: 'bot',
          text: botReply,
          time: new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
        };
        this.messagesSubject.next([...updated, botMsg]);
        subscriber.next(botMsg);
        subscriber.complete();
      }, 1200);
    });
  }

  private generateBotResponse(input: string): string {
    const query = input.toLowerCase();

    if (query.includes('congé') || query.includes('conges') || query.includes('reste')) {
      return `### Solde de vos congés 📅

D'après nos bases de données RH :
- **Congés payés acquis** : **25 jours**
- **RTT** : **6 jours**
- **Congés en attente de validation** : **18 jours** (cumulés sur l'ensemble de l'entreprise)

*Pour votre compte personnel, il vous reste actuellement **14 jours de congés validés** à prendre avant la fin de l'année.*`;
    }

    if (query.includes('fiche de poste') || query.includes('java')) {
      return `### Fiche de Poste : Développeur(euse) Java Senior ☕

**Département** : IT / R&D  
**Rattachement** : Lead Developer & Responsable IT  

#### 🚀 Missions principales :
1. **Conception et Développement** : Concevoir et développer des architectures microservices robustes sous **Spring Boot / Java 21**.
2. **Qualité du code** : Rédiger du code propre, documenté et testé (JUnit/Mockito), tout en respectant les standards SonarQube.
3. **CI/CD & DevOps** : Collaborer au déploiement des applications conteneurisées via **Docker** et **Kubernetes** sur des environnements Cloud (**AWS**).
4. **Mentorat** : Accompagner et guider les développeurs juniors de l'équipe IT.

#### 🎓 Profil recherché :
- **Expérience** : Minimum 5 ans d'expérience en développement Java.
- **Stack technique** : Java 17+, Spring Boot, Hibernate, PostgreSQL, REST APIs, Docker, CI/CD, Git.
- **Soft Skills** : Esprit d'équipe, rigueur, autonomie et communication transparente.`;
    }

    if (query.includes('résume') || query.includes('resume') || query.includes('cv')) {
      return `### Résumé du profil : Sara Benjelloun 👥

**Poste visé** : Développeuse Angular  
**Score de compatibilité IA** : 90%  

#### 🔑 Compétences clés :
* **Frontend** : Angular (v14-v18), TypeScript, RxJS, NgRx, Bootstrap 5, TailwindCSS.
* **Outils & Pratiques** : Git, méthodologies Agiles (Scrum), Tests unitaires (Jasmine/Karma).

#### 💼 Expériences significatives :
* **Développeuse Frontend** chez Tech Solutions (2 ans) : Refonte d'un tableau de bord de gestion financière sous Angular 17, réduction du temps de chargement de 30%.
* **Développeuse Web Junior** chez WebAgency (1.5 ans) : Intégration de maquettes UX interactives et développement d'API REST de base.

#### 🟢 Points forts :
- Excellente maîtrise de l'écosystème Angular et de la programmation réactive (RxJS).
- Sensibilité prononcée pour l'ergonomie et les micro-animations.`;
    }

    if (query.includes('analyse') || query.includes('candidature') || query.includes('candidat')) {
      return `### Analyse de candidature : Ahmed Alami 🤖

**Candidature** : Développeur Java Senior  
**Score de matching global** : **95%**  

| Dimension | Évaluation IA | Observations |
| :--- | :--- | :--- |
| **Adéquation Technique** | 🌟 98% | Maîtrise parfaite de Spring Boot, Hibernate, microservices et Docker. |
| **Expérience Secteur** | 🌟 90% | Plus de 6 ans dans le développement d'applications financières critiques. |
| **Culture d'Entreprise** | 🌟 85% | Profil orienté collaboration et mentorat technique de développeurs juniors. |

#### 📋 Verdict IA :
* **Recommandation** : **Très Favorable**. Profil hautement qualifié qui s'intégrera immédiatement dans l'équipe IT.
* **Prochaine étape proposée** : Programmer un entretien technique de 45 minutes portant sur les architectures cloud.`;
    }

    return `Je ne suis pas sûr de bien comprendre votre demande. 

Voici quelques exemples de questions auxquelles je peux répondre :
* *Combien de jours de congé me restent ?*
* *Génère une fiche de poste Java.*
* *Résume ce CV.*
* *Analyse cette candidature.*`;
  }
}
