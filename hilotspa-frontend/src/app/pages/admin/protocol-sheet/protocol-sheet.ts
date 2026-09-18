import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { Location } from '@angular/common';
import { AdminApi, ProtocolRow } from '../../../core/admin.api';
import { NodeStore } from '../../../core/node.store';

interface Group { service: string; rows: ProtocolRow[]; }

/**
 * Appendix C — the sheet the practitioner signs.
 *
 * "Export for signature" produced a CSV, and nobody signs a CSV. The protocol
 * table has been UNSIGNED since it was seeded, and part of the reason is that
 * there was never an artefact to put a pen on.
 *
 * This is a document, not a screen: printed from the live table so it cannot
 * drift from what the system enforces, with a declaration that states plainly
 * what signing it means. The signature block is deliberately unfilled — a name
 * typed into a web form is not a signature, and the whole value of this table
 * at a defence is that the judgement came from a practitioner rather than from
 * two students and a model.
 */
@Component({
  selector: 'app-protocol-sheet',
  imports: [DatePipe],
  templateUrl: './protocol-sheet.html',
  styleUrl: './protocol-sheet.scss',
})
export class ProtocolSheet implements OnInit {
  private api = inject(AdminApi);
  /**
   * 3.5 - which branch this sheet belongs to.
   *
   * This one matters more than the others on the page. A practitioner signs
   * this document, and a signed protocol table that names the wrong branch is
   * not a clerical slip - it is a record asserting that somebody approved
   * rules for a place they never approved them for.
   */
  protected site = inject(NodeStore);
  private location = inject(Location);

  constructor() { this.site.ensure(); }

  rules = signal<ProtocolRow[]>([]);
  loading = signal(true);
  generatedAt = new Date();

  /** Grouped by service, because that is how a practitioner reads it: one
   *  treatment at a time, deciding what it should and should not be used for. */
  groups = computed<Group[]>(() => {
    const by = new Map<string, ProtocolRow[]>();
    for (const r of [...this.rules()].sort((a, b) =>
      a.serviceName.localeCompare(b.serviceName) || a.conditionLabel.localeCompare(b.conditionLabel))) {
      const list = by.get(r.serviceName) ?? [];
      list.push(r);
      by.set(r.serviceName, list);
    }
    return [...by].map(([service, rows]) => ({ service, rows }));
  });

  contraindicated = computed(() => this.rules().filter(r => r.rule === 'CONTRAINDICATED').length);
  unsigned = computed(() => this.rules().filter(r => !r.signed).length);

  async ngOnInit(): Promise<void> {
    try {
      this.rules.set(await this.api.protocols());
    } finally {
      this.loading.set(false);
    }
  }

  print(): void { window.print(); }
  back(): void { this.location.back(); }
}
