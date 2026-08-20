import { Component, inject } from '@angular/core';
import { ToastService } from '../../core/toast.service';

@Component({
  selector: 'app-toast',
  template: `
    <div class="toast" [class.show]="toast.visible()" role="status" aria-live="polite">
      <span class="tick"></span><span>{{ toast.message() }}</span>
    </div>
  `,
  styles: [`:host { display: contents; }`],
})
export class Toast {
  protected toast = inject(ToastService);
}
