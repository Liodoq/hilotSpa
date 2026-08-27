import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { DashShell } from '../../../shared/dash-shell/dash-shell';
import { ToastService } from '../../../core/toast.service';
import { AdminApi, NodeCard } from '../../../core/admin.api';
import { OpsApi, TherapistDto } from '../../../core/ops.api';
import { BranchContext } from '../../../core/branch-context';
import { FormsApi } from '../../../core/forms.api';
import { Branch } from '../../../core/models';
import { describeHttpError } from '../../../core/http-error';

/**
 * A2 — Manage Branch. Figure 3.3 calls context switching "a key technical feature".
 *
 * Opening a branch does something real: BranchContext is set, and every read on
 * this screen and the staff screens then carries that branchId. The server
 * honours it ONLY for an administrator — a STAFF caller's branch still comes out
 * of the token, so BranchScopingTest stays true and there is no parameter for
 * anyone else to tamper with.
 *
 * The therapist list below is the proof: it is fetched with the branch filter
 * applied, not filtered in the browser after the fact. Filtering here would look
 * identical and mean nothing.
 */
@Component({
  selector: 'app-admin-branches',
  imports: [DashShell],
  templateUrl: './branches.html',
  styleUrl: './branches.scss',
})
export class AdminBranches implements OnInit {
  private api = inject(AdminApi);
  private ops = inject(OpsApi);
  private formsApi = inject(FormsApi);
  private router = inject(Router);
  protected toast = inject(ToastService);
  protected ctx = inject(BranchContext);

  nodes = signal<NodeCard[]>([]);
  branches = signal<Branch[]>([]);
  scoped = signal<TherapistDto[]>([]);
  loading = signal(true);
  error = signal<string | null>(null);

  /** Add / edit a branch. Null when the drawer is closed. */
  drawer = signal<{ id: string | null; name: string; address: string } | null>(null);
  saving = signal(false);

  current = computed<NodeCard | null>(() =>
    this.nodes().find(n => n.branchId === this.ctx.branchId()) ?? null);

  canSave = computed(() => {
    const d = this.drawer();
    return !!d && !!d.name.trim() && !!d.address.trim();
  });

  async ngOnInit(): Promise<void> { await this.load(); }

  async load(): Promise<void> {
    this.loading.set(true);
    this.error.set(null);
    try {
      const [data, branches] = await Promise.all([
        this.api.overview(),
        this.formsApi.branches().catch(() => [] as Branch[]),
      ]);
      this.nodes.set(data.nodes);
      this.branches.set(branches);
      if (this.ctx.active()) await this.loadScoped();
    } catch {
      this.error.set('We could not load the branch list.');
    } finally {
      this.loading.set(false);
    }
  }

  /** Fetched WITH the filter, not filtered afterwards — that is the whole point. */
  private async loadScoped(): Promise<void> {
    try {
      this.scoped.set(await this.ops.therapists(this.ctx.branchId()));
    } catch {
      this.scoped.set([]);
    }
  }

  initials(name: string): string {
    return this.short(name).slice(0, 2).toUpperCase();
  }

  short(name: string): string {
    const dash = name.lastIndexOf('-');
    return dash === -1 ? name : name.slice(dash + 1).trim();
  }

  async select(n: NodeCard): Promise<void> {
    if (this.ctx.branchId() === n.branchId) { this.leave(); return; }
    this.ctx.enter({ id: n.branchId, name: n.branchName });
    this.toast.show(`Now managing ${this.short(n.branchName)}`);
    await this.loadScoped();
  }

  leave(): void {
    this.ctx.leave();
    this.scoped.set([]);
    this.toast.show('Left the branch view');
  }

  open(path: string): void { this.router.navigateByUrl(path); }

  label(v: string): string {
    return v.charAt(0) + v.slice(1).toLowerCase().replace(/_/g, ' ');
  }

  // ------------------------------------------------------------- the drawer

  addBranch(): void { this.drawer.set({ id: null, name: 'Knead Wellness Spa - ', address: '' }); }

  editBranch(n: NodeCard): void {
    const b = this.branches().find(x => x.id === n.branchId);
    this.drawer.set({ id: n.branchId, name: b?.name ?? n.branchName, address: b?.address ?? '' });
  }

  patch(part: Partial<{ name: string; address: string }>): void {
    this.drawer.update(d => d ? { ...d, ...part } : d);
  }

  close(): void { this.drawer.set(null); }

  val(ev: Event): string { return (ev.target as HTMLInputElement).value; }

  async save(): Promise<void> {
    const d = this.drawer();
    if (!d || !this.canSave() || this.saving()) return;
    this.saving.set(true);
    try {
      await this.ops.saveBranch(d.id, { name: d.name.trim(), address: d.address.trim() });
      this.drawer.set(null);
      await this.load();
      this.toast.show(d.id
        ? `${d.name.trim()} saved.`
        : `${d.name.trim()} added. It has no therapists or rooms yet, so it can take no bookings.`,
        d.id ? 3000 : 4200);
    } catch (e: unknown) {
      this.toast.show(describeHttpError(e, 'That did not save. Nothing was changed.'), 3400);
    } finally {
      this.saving.set(false);
    }
  }
}
