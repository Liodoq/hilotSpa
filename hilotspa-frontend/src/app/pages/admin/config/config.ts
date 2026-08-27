import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { DashShell } from '../../../shared/dash-shell/dash-shell';
import { ToastService } from '../../../core/toast.service';
import { AdminApi, ProtocolRow } from '../../../core/admin.api';
import { MassageDto, OpsApi } from '../../../core/ops.api';
import { FormsApi } from '../../../core/forms.api';
import { Branch } from '../../../core/models';
import { describeHttpError } from '../../../core/http-error';
import { priceLabel } from '../../../core/catalogue.store';

/**
 * A4 — global configuration, and X2: where the signed contraindication table lives.
 *
 * Every row here is a real `service_protocol` row. `signed` is derived from the
 * author's name, not from a checkbox, so the rules the spa has not yet authored
 * are visibly quarantined rather than quietly working. That is a far stronger
 * position at defense than unsigned safety rules operating in the background.
 *
 * Changing a rule requires typing a name. The SERVER enforces that, not this
 * form — an unsigned safety rule is the app making a clinical decision on its own.
 */
@Component({
  selector: 'app-admin-config',
  imports: [DashShell],
  templateUrl: './config.html',
  styleUrl: './config.scss',
})
export class AdminConfig implements OnInit {
  private route = inject(ActivatedRoute);
  private api = inject(AdminApi);
  private ops = inject(OpsApi);
  private formsApi = inject(FormsApi);
  protected toast = inject(ToastService);
  protected priceLabel = priceLabel;

  /** A6 lives here as the Service menu tab. Figure 9.3 asks for "manage
   *  services"; A4 is already Global configuration, and the service menu IS
   *  global configuration — so the paper's 22-screen count holds. */
  protected tabs = [
    { key: 'protocol', label: 'Service protocol' },
    { key: 'menu',     label: 'Service menu' },
    { key: 'branches', label: 'Branches' },
    { key: 'nodes',    label: 'Node settings' },
  ];

  tab = signal('protocol');
  rules = signal<ProtocolRow[]>([]);
  loading = signal(true);
  error = signal<string | null>(null);
  saving = signal<string | null>(null);

  /** Who is authorising the change. Empty means the buttons stay disabled. */
  signature = signal('');

  unsigned = computed(() => this.rules().filter(r => !r.signed));
  contraindicated = computed(() => this.rules().filter(r => r.rule === 'CONTRAINDICATED'));
  signers = computed(() =>
    Array.from(new Set(this.rules().filter(r => r.signed).map(r => r.authoredBy))));

  // ---- A6: the service menu ---------------------------------------------
  services = signal<MassageDto[]>([]);
  branches = signal<Branch[]>([]);
  menuDrawer = signal<MenuForm | null>(null);

  onSale = computed(() => this.services().filter(s => s.active));
  unpriced = computed(() => this.onSale().filter(s => !(s.price > 0)));

  menuValid = computed(() => {
    const d = this.menuDrawer();
    return !!d && !!d.name.trim() && Number(d.minutes) >= 5 && Number(d.minutes) <= 480
        && Number(d.price) >= 0;
  });

  async ngOnInit(): Promise<void> {
    // A1 links straight to the tab that fixes what it is warning about.
    const tab = this.route.snapshot.queryParamMap.get('tab');
    if (tab && this.tabs.some(t => t.key === tab)) this.tab.set(tab);
    await this.load();
  }

  async load(): Promise<void> {
    this.loading.set(true);
    this.error.set(null);
    try {
      const [rules, services, branches] = await Promise.all([
        this.api.protocols(),
        this.ops.massages().catch(() => [] as MassageDto[]),
        this.formsApi.branches().catch(() => [] as Branch[]),
      ]);
      this.rules.set(rules);
      this.services.set(services);
      this.branches.set(branches);
    } catch {
      this.error.set('We could not load the configuration.');
    } finally {
      this.loading.set(false);
    }
  }

  // ------------------------------------------------- A6 service menu actions

  addService(): void {
    this.menuDrawer.set({ id: null, name: '', minutes: '60', price: '0' });
  }

  editService(m: MassageDto): void {
    this.menuDrawer.set({ id: m.id, name: m.name,
      minutes: String(m.durationMinute), price: String(m.price ?? 0) });
  }

  patchMenu(part: Partial<MenuForm>): void {
    this.menuDrawer.update(d => d ? { ...d, ...part } : d);
  }

  closeMenu(): void { this.menuDrawer.set(null); }

  async saveService(): Promise<void> {
    const d = this.menuDrawer();
    if (!d || !this.menuValid() || this.saving()) return;
    this.saving.set(d.id ?? 'new');
    try {
      const saved = await this.ops.saveMassage(d.id, {
        name: d.name.trim(), durationMinute: Number(d.minutes), price: Number(d.price),
      });
      this.menuDrawer.set(null);
      await this.load();
      this.toast.show(`${saved.name}, ${saved.durationMinute} min — ${priceLabel(saved.price)}.`);
    } catch (e: unknown) {
      this.toast.show(describeHttpError(e, 'That did not save. Nothing was changed.'), 3400);
    } finally {
      this.saving.set(null);
    }
  }

  /**
   * Withdraw, never delete.
   *
   * Appointments already made point at this row and the audit log names it. A
   * withdrawn treatment simply stops being offered.
   */
  async toggleService(m: MassageDto): Promise<void> {
    if (this.saving()) return;
    this.saving.set(m.id);
    try {
      await this.ops.saveMassage(m.id, { active: !m.active });
      await this.load();
      this.toast.show(m.active
        ? `${m.name} ${m.durationMinute} min withdrawn — the assistant will no longer offer it.`
        : `${m.name} ${m.durationMinute} min is on sale again.`, 3400);
    } catch (e: unknown) {
      this.toast.show(describeHttpError(e, 'That did not save.'), 3400);
    } finally {
      this.saving.set(null);
    }
  }

  async toggle(r: ProtocolRow): Promise<void> {
    const who = this.signature().trim();
    if (!who) {
      this.toast.show('Type the name of the person authorising this change first.', 3400);
      return;
    }
    if (this.saving()) return;
    const next = r.rule === 'INDICATED' ? 'CONTRAINDICATED' : 'INDICATED';
    this.saving.set(r.id);
    try {
      const saved = await this.api.updateProtocol(r.id, { rule: next, authoredBy: who });
      this.rules.update(list => list.map(x => x.id === r.id ? saved : x));
      this.toast.show(
        `${r.serviceName} × ${r.conditionLabel} → ${next}, signed by ${who}. Written to the audit log.`,
        3600);
    } catch {
      this.toast.show('That did not save. The rule is unchanged.', 3200);
    } finally {
      this.saving.set(null);
    }
  }

  val(ev: Event): string { return (ev.target as HTMLInputElement).value; }

  /** Prints the table for the practitioner to sign. Nothing here is authored by us. */
  exportForSignature(): void {
    const head = ['service', 'condition', 'rule', 'rationale', 'authoredBy', 'signed'];
    const body = this.rules().map(r => [
      r.serviceName, r.conditionLabel, r.rule, r.rationale ?? '', r.authoredBy,
      r.signed ? 'yes' : 'NO — awaiting practitioner',
    ].map(csv).join(','));
    const blob = new Blob([[head.join(','), ...body].join('\r\n')],
      { type: 'text/csv;charset=utf-8' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = 'hilotspa-service-protocol-for-signature.csv';
    a.click();
    URL.revokeObjectURL(url);
    this.toast.show('Exported. Print it for the practitioner to sign — this becomes Appendix C.', 3600);
  }
}

function csv(v: string): string {
  const s = String(v ?? '');
  return /[",\r\n]/.test(s) ? `"${s.replace(/"/g, '""')}"` : s;
}

/** What the service-menu drawer is editing. Kept as strings so a half-typed
 *  number never becomes NaN mid-keystroke. */
interface MenuForm {
  id: string | null;
  name: string;
  minutes: string;
  price: string;
}
