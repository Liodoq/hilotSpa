import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { RouterLink, ActivatedRoute } from '@angular/router';
import { DashShell } from '../../../shared/dash-shell/dash-shell';
import { BodyMap } from '../../../shared/body-map/body-map';
import { ToastService } from '../../../core/toast.service';
import { FormsApi } from '../../../core/forms.api';
import { OpsApi, CatalogueEntry, ScheduleRow } from '../../../core/ops.api';
import { FormsModel, PatientIntakeModel } from '../../../core/models';
import { PainPoint } from '../../../core/assessment.store';

/**
 * S4 — the practitioner's view. Figure 3.2 calls this out specifically.
 *
 * This is the digital half of the bone setter's own sheet: the marked regions,
 * L/R, and BEFORE / AFTER pain, read from the real assessment.
 *
 * AFTER is deliberately empty until the session ends. It is written by staff,
 * never by the client — PUT /patient-intake/{id}/after sits behind the STAFF
 * role, so there is no client-facing route to it at all. BEFORE minus AFTER is
 * the only quantitative outcome measure the spa already collects (§H3).
 */
@Component({
  selector: 'app-staff-report',
  imports: [DashShell, BodyMap, RouterLink],
  templateUrl: './report.html',
  styleUrl: './report.scss',
})
export class StaffReport implements OnInit {
  private route = inject(ActivatedRoute);
  private api = inject(FormsApi);
  private ops = inject(OpsApi);
  protected toast = inject(ToastService);

  form = signal<FormsModel | null>(null);
  today = signal<ScheduleRow[]>([]);
  catalogue = signal<CatalogueEntry[]>([]);
  loading = signal(true);
  error = signal<string | null>(null);
  saving = signal<string | null>(null);
  notes = signal('');

  /** The marks, in the shape BodyMap draws. */
  points = computed<PainPoint[]>(() =>
    (this.form()?.painPoints ?? []).map((p, i) => ({
      key: p.id ?? `p${i}`,
      hotspotId: '',
      view: p.bodyView,
      x: p.coordinateX,
      y: p.coordinateY,
      region: p.anatomicalRegion,
      side: p.side,
      score: p.painScoreBefore,
      qualities: [],
    })) as unknown as PainPoint[]);

  findings = computed(() => (this.form()?.painPoints ?? []).map(p => ({
    id: p.id ?? '',
    region: label(p.anatomicalRegion),
    side: p.side ?? 'CENTRE',
    complaint: p.complaintType ? label(p.complaintType) : '—',
    before: p.painScoreBefore,
    after: p.painScoreAfter ?? null,
  })));

  /** The session this assessment belongs to, if one is booked today. */
  session = computed(() => this.today().find(s => s.formId === this.form()?.id) ?? null);

  /** Today's sessions that HAVE an assessment - the list to choose from. */
  choices = computed(() => this.today().filter(s => s.hasAssessment));

  excluded = computed(() => this.catalogue().filter(c => !c.suitable));
  indicated = computed(() => this.catalogue().filter(c => c.rule === 'INDICATED'));

  complaint = computed(() => {
    const f = this.form();
    if (!f) return '—';
    if (f.intent === 'LEISURE') return 'Here to relax';
    return f.mainComplaint ? label(f.mainComplaint) : (f.mainComplaintOther || 'Not stated');
  });

  /** Every point scored, so the whole sheet is finished. */
  complete = computed(() =>
    this.findings().length > 0 && this.findings().every(f => f.after !== null));

  async ngOnInit(): Promise<void> {
    const fromQuery = this.route.snapshot.queryParamMap.get('formId');
    await this.load(fromQuery);
  }

  async load(formId: string | null): Promise<void> {
    this.loading.set(true);
    this.error.set(null);
    try {
      const sched = await this.ops.schedule().catch(() => [] as ScheduleRow[]);
      this.today.set(sched);

      // No id in the URL: show today's assessments and let staff CHOOSE.
      //
      // This used to guess - it opened the next booked session that had an
      // assessment - so tapping "Pre-assessments" in the sidebar dropped you
      // into one named client's medical record without asking. On a screen at a
      // front desk that is the wrong default twice over: it is not the record
      // you meant to open, and it puts somebody's pain map in front of whoever
      // is standing there.
      if (!formId) {
        this.form.set(null);
        return;
      }
      const id = formId;
      const f = await this.api.form(id);
      this.form.set(f);
      this.notes.set(f.remarks ?? '');
      this.catalogue.set(await this.ops.catalogue(id).catch(() => [] as CatalogueEntry[]));
    } catch {
      this.error.set('We could not open that assessment.');
    } finally {
      this.loading.set(false);
    }
  }

  val(ev: Event): string { return (ev.target as HTMLTextAreaElement).value; }

  num(ev: Event): number { return Number((ev.target as HTMLSelectElement).value); }

  scores = [0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10];

  abs(n: number): number { return Math.abs(n); }

  submitted = computed(() => {
    const iso = this.form()?.createdAt;
    if (!iso) return 'an earlier visit';
    const d = new Date(iso);
    return Number.isNaN(d.getTime()) ? iso
      : d.toLocaleDateString('en-GB', { day: '2-digit', month: 'short', year: 'numeric' });
  });

  /**
   * One point at a time, saved as it is entered. A single "save all" button is
   * how half a sheet gets lost when the front desk phone rings.
   */
  async setAfter(id: string, after: number): Promise<void> {
    if (!id || this.saving()) return;
    this.saving.set(id);
    try {
      const saved: PatientIntakeModel = await this.api.recordAfter(id, after);
      this.form.update(f => !f ? f : {
        ...f,
        painPoints: f.painPoints.map(p =>
          p.id === id ? { ...p, painScoreAfter: saved.painScoreAfter ?? after } : p),
      });
      this.toast.show(`AFTER recorded — appended to the record, nothing overwritten.`);
    } catch {
      this.toast.show('That did not save. The record is unchanged.', 3200);
    } finally {
      this.saving.set(null);
    }
  }
}

/** LOWER_BACK_PAIN -> Lower Back Pain. Enum constants are not words. */
function label(v: string): string {
  return v.split('_').map(w => w.charAt(0) + w.slice(1).toLowerCase()).join(' ');
}
