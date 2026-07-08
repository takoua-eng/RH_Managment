import { Component, OnInit, ViewChild, ElementRef, AfterViewChecked } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ChatbotService, ChatMessage } from '../../core/services/chatbot.service';

@Component({
  selector: 'app-chatbot',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <div class="chatbot-page d-flex flex-column">
      <!-- Section Header -->
      <div class="row mb-3 align-items-center flex-shrink-0">
        <div class="col-12">
          <h2 class="fw-bold mb-1">Chatbot IA</h2>
          <p class="text-muted mb-0">Discutez avec l'IA pour analyser des candidatures, résumer des CV ou générer des contenus RH.</p>
        </div>
      </div>

      <div class="chat-container fluent-card p-0 flex-grow-1 d-flex">
        <!-- Sidebar - Suggested Actions / Help (Left Panel) -->
        <div class="chat-sidebar border-end d-none d-md-flex flex-column p-4">
          <h5 class="fw-bold fs-7 mb-3 text-uppercase text-secondary">Prompts Suggérés</h5>
          
          <div class="suggested-buttons d-flex flex-column gap-2">
            <button *ngFor="let prompt of examples" 
                    (click)="submitExample(prompt)" 
                    [disabled]="typing"
                    class="suggested-btn text-start p-2 fs-7 rounded border bg-transparent text-dark">
              <span class="d-block fw-bold mb-1 fs-8 text-primary">{{ prompt.title }}</span>
              <span class="d-block text-muted fs-8 text-truncate">{{ prompt.text }}</span>
            </button>
          </div>

          <div class="mt-auto p-3 bg-light rounded text-center">
            <span class="material-symbols-outlined text-primary fs-3 mb-1">psychology</span>
            <h6 class="fw-bold mb-1 fs-8">Modèle IA Actif</h6>
            <span class="fluent-badge success fs-8">Gemini 3.5 Flash</span>
          </div>
        </div>

        <!-- Main Chat Box (Right Panel) -->
        <div class="chat-main d-flex flex-column flex-grow-1">
          <!-- Messages Scroll area -->
          <div class="messages-area flex-grow-1 p-4" #scrollContainer>
            <div class="message-bubble" 
                 *ngFor="let msg of messages" 
                 [ngClass]="msg.sender === 'user' ? 'user' : 'bot'">
              <div class="avatar-wrapper">
                <span class="material-symbols-outlined avatar-icon">
                  {{ msg.sender === 'user' ? 'person' : 'smart_toy' }}
                </span>
              </div>
              <div class="message-content">
                <div class="message-text" [innerHTML]="formatMessageText(msg.text)"></div>
                <span class="message-time">{{ msg.time }}</span>
              </div>
            </div>

            <!-- Typing indicator -->
            <div class="message-bubble bot" *ngIf="typing">
              <div class="avatar-wrapper">
                <span class="material-symbols-outlined avatar-icon text-primary">smart_toy</span>
              </div>
              <div class="message-content">
                <div class="typing-indicator">
                  <span></span>
                  <span></span>
                  <span></span>
                </div>
              </div>
            </div>
          </div>

          <!-- Suggested grid when chat is fresh (Helper for Mobile/No sidebar) -->
          <div class="examples-grid p-4 border-top bg-light-subtle d-md-none" *ngIf="messages.length <= 1">
            <div class="row g-2">
              <div class="col-6" *ngFor="let prompt of examples">
                <button (click)="submitExample(prompt)" class="suggested-btn-mobile text-start p-2 w-100 rounded border bg-white fs-8">
                  {{ prompt.text }}
                </button>
              </div>
            </div>
          </div>

          <!-- Chat Input -->
          <div class="chat-input-area p-3 border-top bg-light-subtle flex-shrink-0">
            <form (ngSubmit)="sendUserMessage()" class="d-flex gap-2">
              <input type="text" 
                     class="fluent-input" 
                     placeholder="Posez une question ou demandez une tâche..."
                     [(ngModel)]="userInput" 
                     name="message"
                     [disabled]="typing"
                     autocomplete="off">
              <button type="submit" class="fluent-btn-primary" [disabled]="!userInput || typing">
                <span class="material-symbols-outlined">send</span>
              </button>
            </form>
          </div>
        </div>
      </div>
    </div>
  `,
  styles: [`
    .chatbot-page {
      height: calc(100vh - 120px);
    }
    .chat-container {
      height: 100%;
      overflow: hidden;
      background-color: var(--fluent-surface);
    }
    .chat-sidebar {
      width: 250px;
      flex-shrink: 0;
      background-color: var(--fluent-bg);
    }
    .suggested-btn {
      transition: all 0.2s ease;
      
      &:hover {
        background-color: var(--fluent-surface-hover) !important;
        border-color: var(--fluent-primary) !important;
        transform: translateX(2px);
      }
    }
    .chat-main {
      min-width: 0;
    }
    .messages-area {
      overflow-y: auto;
      display: flex;
      flex-direction: column;
      gap: 1.25rem;
    }
    
    /* Message Bubble styles */
    .message-bubble {
      display: flex;
      gap: 0.75rem;
      max-width: 85%;
      align-self: flex-start;
      
      .avatar-wrapper {
        width: 34px;
        height: 34px;
        border-radius: 50%;
        background-color: var(--fluent-primary-light);
        color: var(--fluent-primary);
        display: flex;
        align-items: center;
        justify-content: center;
        flex-shrink: 0;
        
        .avatar-icon { font-size: 1.2rem; }
      }
      .message-content {
        background-color: var(--fluent-surface-hover);
        border: var(--fluent-card-border);
        padding: 0.75rem 1rem;
        border-radius: 12px;
        color: var(--fluent-text);
      }
      .message-time {
        display: block;
        font-size: 0.7rem;
        color: var(--fluent-text-secondary);
        margin-top: 0.25rem;
        text-align: right;
      }
      
      &.user {
        align-self: flex-end;
        flex-direction: row-reverse;
        
        .avatar-wrapper {
          background-color: var(--fluent-success-light);
          color: var(--fluent-success);
        }
        .message-content {
          background-color: var(--fluent-primary);
          color: #ffffff;
          border: none;
          
          .message-time {
            color: rgba(255, 255, 255, 0.7);
          }
        }
      }
    }

    /* Suggested buttons for Mobile view */
    .suggested-btn-mobile {
      height: 100%;
      border: 1px solid var(--fluent-border);
      color: var(--fluent-text);
      transition: background-color 0.2s;
      
      &:hover {
        background-color: var(--fluent-surface-hover);
      }
    }

    /* Typing indicator animation */
    .typing-indicator {
      display: flex;
      align-items: center;
      gap: 4px;
      height: 20px;
      
      span {
        width: 6px;
        height: 6px;
        background-color: var(--fluent-primary);
        border-radius: 50%;
        animation: typingDot 1.4s infinite ease-in-out;
        
        &:nth-child(1) { animation-delay: 0s; }
        &:nth-child(2) { animation-delay: 0.2s; }
        &:nth-child(3) { animation-delay: 0.4s; }
      }
    }
    
    @keyframes typingDot {
      0%, 100% { transform: translateY(0); }
      50% { transform: translateY(-4px); }
    }
    .fs-7 { font-size: 0.85rem; }
    .fs-8 { font-size: 0.725rem; }
  `]
})
export class ChatbotComponent implements OnInit, AfterViewChecked {
  @ViewChild('scrollContainer') private scrollContainer!: ElementRef;

  messages: ChatMessage[] = [];
  userInput = '';
  typing = false;

  examples = [
    { title: '📅 Congés', text: 'Combien de jours de congé me restent ?' },
    { title: '📝 Fiche de poste', text: 'Génère une fiche de poste Java.' },
    { title: '👥 Résumé CV', text: 'Résume ce CV.' },
    { title: '🤖 Analyse Candidat', text: 'Analyse cette candidature.' }
  ];

  constructor(private chatbotService: ChatbotService) {}

  ngOnInit(): void {
    this.messages = this.chatbotService.getMessages();
  }

  ngAfterViewChecked() {
    this.scrollToBottom();
  }

  sendUserMessage() {
    if (!this.userInput.trim() || this.typing) return;
    
    const text = this.userInput;
    this.userInput = '';
    this.typing = true;

    this.chatbotService.sendMessage(text).subscribe({
      next: () => {
        this.messages = this.chatbotService.getMessages();
        this.typing = false;
      }
    });
  }

  submitExample(prompt: { text: string }) {
    this.userInput = prompt.text;
    this.sendUserMessage();
  }

  scrollToBottom(): void {
    try {
      this.scrollContainer.nativeElement.scrollTop = this.scrollContainer.nativeElement.scrollHeight;
    } catch (err) {}
  }

  formatMessageText(text: string): string {
    // Escape standard HTML first to prevent XSS (very basic)
    let html = text
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;');

    // Replace headers ###
    html = html.replace(/^### (.*$)/gim, '<h6 class="fw-bold text-primary mt-2 mb-1">$1</h6>');
    html = html.replace(/^#### (.*$)/gim, '<h6 class="fw-bold mt-2">$1</h6>');

    // Bold text **word**
    html = html.replace(/\*\*(.*?)\*\*/g, '<strong>$1</strong>');

    // List bullets * or -
    html = html.replace(/^\* (.*$)/gim, '<li class="ms-3">$1</li>');
    html = html.replace(/^- (.*$)/gim, '<li class="ms-3">$1</li>');

    // Table formatter (pipe | columns)
    if (html.includes('|')) {
      const lines = html.split('\n');
      let inTable = false;
      let tableHtml = '<div class="table-responsive"><table class="table table-bordered table-sm fs-8 mt-2 text-dark"><thead>';
      
      for (let i = 0; i < lines.length; i++) {
        const line = lines[i].trim();
        if (line.startsWith('|') && line.endsWith('|')) {
          const cells = line.split('|').map(c => c.trim()).filter((c, idx) => idx > 0 && idx < line.split('|').length - 1);
          
          if (line.includes('---')) {
            // separator, ignore
            continue;
          }
          if (!inTable) {
            inTable = true;
            tableHtml += '<tr>' + cells.map(c => `<th>${c}</th>`).join('') + '</tr></thead><tbody>';
          } else {
            tableHtml += '<tr>' + cells.map(c => `<td>${c}</td>`).join('') + '</tr>';
          }
          lines[i] = ''; // clear out parsed line
        } else {
          if (inTable) {
            inTable = false;
            tableHtml += '</tbody></table></div>';
            lines[i] = tableHtml + '\n' + lines[i];
          }
        }
      }
      if (inTable) {
        tableHtml += '</tbody></table></div>';
        html = lines.join('\n') + tableHtml;
      } else {
        html = lines.join('\n');
      }
    }

    return html.replace(/\n/g, '<br>');
  }
}
