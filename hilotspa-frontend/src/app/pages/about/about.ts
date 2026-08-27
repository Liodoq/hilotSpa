import { Component, inject } from '@angular/core';
import { Router } from '@angular/router';
import { AppNav } from '../../shared/app-nav/app-nav';

/**
 * The ABOUT link was in the navigation from the first build and pointed at a
 * route that did not exist, so it landed on "We could not find that" — which
 * reads as a broken system rather than a missing page.
 *
 * It also carries the disclosure the assistant needs somewhere permanent:
 * what it is, what it will not do, and who wrote the clinical rules.
 */
@Component({
  selector: 'app-about',
  imports: [AppNav],
  template: `
<app-nav />
<main style="max-width:820px;margin:0 auto;padding:40px 24px 80px">
  <h1 class="t-l">About Knead Wellness Spa</h1>
  <p class="s" style="font-size:20px;margin-top:10px">
    Traditional Filipino hilot and wellness care in Bulan, Sorsogon.
  </p>

  <div class="card" style="padding:24px;margin-top:28px">
    <h2 class="t-s">What this system does</h2>
    <p style="margin-top:10px">
      Before your visit you can mark where it hurts on a body diagram and answer a few short
      questions. Your therapist reads that before you sit down, so you do not have to explain
      everything from the beginning while you are already uncomfortable.
    </p>
  </div>

  <div class="card" style="padding:24px;margin-top:20px">
    <h2 class="t-s">About the booking assistant</h2>
    <p style="margin-top:10px">
      The assistant suggests services and can hold a time for you. It is not a doctor and it
      does not diagnose.
    </p>
    <ul style="margin-top:12px;padding-left:20px;line-height:1.8">
      <li>It can only offer services this spa actually provides.</li>
      <li>It cannot suggest a service the practitioner has ruled out for your condition.</li>
      <li>It will not tell you what is wrong with you, or what will cure it.</li>
      <li>If you describe something urgent, it will ask you to see a physician.</li>
    </ul>
    <p class="help" style="margin-top:14px">
      Which services suit which conditions is decided by this spa's own practitioner, not by
      the software. Every suggestion is checked against that list before you see it.
    </p>
  </div>

  <div class="card" style="padding:24px;margin-top:20px">
    <h2 class="t-s">Your information</h2>
    <p style="margin-top:10px">
      Your assessments stay on your own account so your practitioner can see whether something
      is improving between visits. We do not share your details.
    </p>
  </div>

  <button type="button" class="btn btn-quiet" style="margin-top:28px" (click)="home()">
    Back to home</button>
</main>`,
})
export class About {
  private router = inject(Router);
  home(): void { this.router.navigateByUrl('/home'); }
}
