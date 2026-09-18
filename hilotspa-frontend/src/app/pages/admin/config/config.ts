import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { DashShell } from '../../../shared/dash-shell/dash-shell';
import { ToastService } from '../../../core/toast.service';
import { AdminApi, Coverage, ImportResult, ProtocolRow } from '../../../core/admin.api';
import { MassageDto, OpsApi } from '../../../core/ops.api';
import { FormsApi } from '../../../core/forms.api';
import { Branch, COMPLAINTS } from '../../../core/models';
import { describeHttpError } from '../../../core/http-error';
import { readXlsx, rowsToCsv } from '../../../core/xlsx-lite';
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
  imports: [DashShell, RouterLink],
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
      const [rules, services, branches, coverage] = await Promise.all([
        this.api.protocols(),
        this.ops.massages().catch(() => [] as MassageDto[]),
        this.formsApi.branches().catch(() => [] as Branch[]),
        this.api.protocolCoverage().catch(() => null),
      ]);
      this.rules.set(rules);
      this.services.set(services);
      this.branches.set(branches);
      this.coverage.set(coverage);
    } catch {
      this.error.set('We could not load the configuration.');
    } finally {
      this.loading.set(false);
    }
  }

  // ------------------------------------------------- A6 service menu actions

  /** Blank is "anyone", and it is the default on purpose - a new treatment is
   *  unrestricted until somebody says which skill it needs. */
  protected specialtyOptions: { value: string; label: string }[] = [
    { value: '',             label: 'Anyone' },
    { value: 'MASSAGE',      label: 'Massage' },
    { value: 'BONE_SETTING', label: 'Bone setting' },
    { value: 'HEAD_SPA',     label: 'Head spa' },
  ];

  addService(): void {
    this.menuDrawer.set({ id: null, name: '', minutes: '60', price: '0', imageName: '',
      requiredSpecialty: '' });
  }

  editService(m: MassageDto): void {
    this.menuDrawer.set({ id: m.id, name: m.name,
      minutes: String(m.durationMinute), price: String(m.price ?? 0),
      imageName: m.imageName ?? '', requiredSpecialty: m.requiredSpecialty ?? '' });
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
        // Sent even when blank: an empty string is how a photo is REMOVED, and
        // the server tells blank from null on purpose.
        imageName: d.imageName.trim(),
        // Blank is meaningful here too: it clears the restriction, and the
        // server reads blank as "any therapist may perform this".
        requiredSpecialty: d.requiredSpecialty,
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

  // --------------------------------------------------- edit and remove a rule

  /** The row whose rationale is open for editing, and the text being typed. */
  editing = signal<string | null>(null);
  draft = signal('');

  openEdit(r: ProtocolRow): void {
    this.editing.set(r.id);
    this.draft.set(r.rationale ?? '');
  }

  closeEdit(): void { this.editing.set(null); }

  /**
   * Save a new rationale.
   *
   * Exposed because the assistant reads this sentence out WORD FOR WORD when a
   * client asks why a treatment was suggested. It was write-once until now: the
   * API accepted a new one and no screen could send it, so the only way to
   * correct a sentence being spoken to clients was a database edit.
   */
  async saveRationale(r: ProtocolRow): Promise<void> {
    const who = this.signature().trim();
    if (!who) {
      this.toast.show('Type the name of the person authorising this change first.', 3400);
      return;
    }
    if (this.saving()) { return; }
    this.saving.set(r.id);
    try {
      const saved = await this.api.updateProtocol(r.id, {
        rationale: this.draft().trim(), authoredBy: who,
      });
      this.rules.update(list => list.map(x => x.id === r.id ? saved : x));
      this.editing.set(null);
      this.toast.show('Saved. The assistant will use these words from now on.', 3600);
    } catch (e: unknown) {
      this.toast.show(describeHttpError(e, 'That did not save.'), 4000);
    } finally {
      this.saving.set(null);
    }
  }

  // ------------------------------------------------- recording a signature

  signingNote = signal('');
  signingOpen = signal(false);
  signingBusy = signal(false);

  /**
   * Record that the practitioner signed the sheet.
   *
   * Only rules still carrying the seeded placeholder are touched. A rule
   * somebody has already put their name to is left alone — overwriting it would
   * quietly reassign responsibility for a clinical decision to a person who
   * never saw that particular rule.
   */
  async recordSignature(): Promise<void> {
    const who = this.signature().trim();
    if (!who) {
      this.toast.show('Type the practitioner\'s name in "Authorising this change" first.', 3800);
      return;
    }
    if (this.signingBusy()) { return; }
    this.signingBusy.set(true);
    try {
      const r = await this.api.signProtocols(who, this.signingNote().trim());
      this.signingOpen.set(false);
      this.signingNote.set('');
      await this.load();
      this.toast.show(r.note, 5200);
    } catch (e: unknown) {
      this.toast.show(describeHttpError(e, 'That signature was not recorded.'), 4600);
    } finally {
      this.signingBusy.set(false);
    }
  }

  /** The row whose removal is armed and waiting for a second tap. */
  removing = signal<string | null>(null);

  /**
   * Remove a rule, in two taps.
   *
   * Removing a CONTRAINDICATED rule makes that service offerable to those
   * clients again — it is the single most consequential write in this system,
   * and the warning below says so in those words rather than asking "are you
   * sure", which nobody reads.
   */
  async remove(r: ProtocolRow): Promise<void> {
    const who = this.signature().trim();
    if (!who) {
      this.toast.show('Type the name of the person authorising this first.', 3400);
      return;
    }
    if (this.removing() !== r.id) { this.removing.set(r.id); return; }
    if (this.saving()) { return; }
    this.saving.set(r.id);
    try {
      await this.api.deleteProtocol(r.id, who);
      this.removing.set(null);
      await this.load();
      this.toast.show(
        `Removed ${r.serviceName} × ${r.conditionLabel}. The old rule is in the audit log.`, 4200);
    } catch (e: unknown) {
      this.toast.show(describeHttpError(e, 'That rule was not removed.'), 4000);
    } finally {
      this.saving.set(null);
    }
  }

  // ------------------------------------------------ coverage, add, import

  /**
   * How much of the menu has any rule at all.
   *
   * A service with no rule is still offerable to a client — that is a deliberate
   * choice, because the alternative deletes a service from the menu the moment
   * somebody adds one. The cost of that choice is that the gap is INVISIBLE:
   * nothing on screen distinguishes "vetted and permitted" from "nobody has
   * looked at this yet". This counter is what pays that cost.
   */
  coverage = signal<Coverage | null>(null);

  protected readonly complaints = COMPLAINTS;

  /** The add-a-rule form. */
  newService = signal('');
  newCondition = signal('');
  newRule = signal('INDICATED');
  newRationale = signal('');
  adding = signal(false);

  importing = signal(false);
  importResult = signal<ImportResult | null>(null);

  /** Only the rows that failed. The rest are a number; these need reading. */
  importProblems = computed(() =>
    (this.importResult()?.lines ?? []).filter(l => l.outcome === 'REJECTED'));

  async addRule(): Promise<void> {
    if (this.adding()) { return; }
    const who = this.signature().trim();
    if (!who) {
      this.toast.show('Put a name in "Authorising this change" first.', 3200);
      return;
    }
    if (!this.newService().trim() || !this.newCondition()) {
      this.toast.show('Choose a service and a condition.', 3200);
      return;
    }
    this.adding.set(true);
    try {
      await this.api.createProtocol({
        serviceName: this.newService().trim(),
        condition: this.newCondition(),
        rule: this.newRule(),
        rationale: this.newRationale().trim(),
        authoredBy: who,
      });
      this.newCondition.set('');
      this.newRationale.set('');
      await this.load();
      this.toast.show('Rule added.', 2600);
    } catch (e: unknown) {
      this.toast.show(describeHttpError(e, 'That rule was not added.'), 5000);
    } finally {
      this.adding.set(false);
    }
  }

  /**
   * Load a CSV of rules.
   *
   * Read in the browser and posted as text. The file is the same shape as
   * "Export for signature" writes, so the round trip is: export, fill it in,
   * save as CSV, load it back.
   */
  async importFile(ev: Event): Promise<void> {
    const input = ev.target as HTMLInputElement;
    const file = input.files?.[0];
    // Clearing it matters: choosing the SAME file twice fires no change event
    // otherwise, and a corrected re-import is the normal case here.
    input.value = '';
    if (!file) { return; }

    const who = this.signature().trim();
    if (!who) {
      this.toast.show('Put a name in "Authorising this change" first — '
        + 'it signs every row in the file that does not name somebody.', 4200);
      return;
    }

    this.importing.set(true);
    this.importResult.set(null);
    try {
      // .xlsx is read here and turned into the CSV the endpoint already speaks,
      // so there is one parser on the server and one shape of request. The
      // spreadsheet is what the spa actually works in; asking for a CSV export
      // first is a step that gets skipped, or done with the wrong one of
      // Excel's several CSV options.
      const text = /\.xlsx$/i.test(file.name)
        ? rowsToCsv(await readXlsx(file))
        : await file.text();
      const r = await this.api.importProtocols(text, who);
      this.importResult.set(r);
      await this.load();
      this.toast.show(
        `${r.created} added, ${r.updated} updated, ${r.rejected} not loaded.`, 4600);
    } catch (e: unknown) {
      // An unreadable spreadsheet must never look like an empty one, so the
      // reader's own message is shown and it names the way out.
      const msg = e instanceof Error && !(e as { status?: number }).status
        ? `${e.message} Save it as CSV and load that instead.`
        : describeHttpError(e, 'That file could not be read.');
      this.toast.show(msg, 6000);
    } finally {
      this.importing.set(false);
    }
  }

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
  /** Enum name, or '' for "anyone". */
  requiredSpecialty: string;
  /** Photo filename in the app's public/services/ folder. A FILENAME, never the
   *  service id: ids are regenerated on every reseed and the photographs would
   *  break. Blank means the spa has not supplied one, and the screens show a
   *  tinted block rather than a broken image. */
  imageName: string;
}
