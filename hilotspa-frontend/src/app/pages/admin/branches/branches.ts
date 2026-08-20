import { Component, computed, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { DashShell } from '../../../shared/dash-shell/dash-shell';
import { ToastService } from '../../../core/toast.service';
import { NODES } from '../../../core/demo';

/** A2 — Figure 3.3 calls this "Context Switching". Opening a branch reuses S1. */
@Component({
  selector: 'app-admin-branches',
  imports: [DashShell, RouterLink],
  templateUrl: './branches.html',
  styleUrl: './branches.scss',
})
export class AdminBranches {
  protected toast = inject(ToastService);
  protected nodes = NODES;

  openBranch = signal('Bulan');
  current = computed(() => this.nodes.find(n => n.branch === this.openBranch()) ?? this.nodes[0]);

  initials(b: string): string {
    return b.split(' ').slice(0, 2).map(p => p[0]).join('').toUpperCase();
  }

  select(branch: string): void {
    this.openBranch.set(branch);
    const n = this.nodes.find(x => x.branch === branch);
    this.toast.show(n?.online
      ? `Now managing ${branch}`
      : `${branch} is offline — showing its last synced state`);
  }
}
