# 14. Usability test protocol (field testing with real managers)

How to watch building managers use بناداد and turn what they do into numbers we can compare
between versions. Written after 0.8.x, when testers' feedback still arrived by phone call.
Complements docs/10 (research plan); this file is the short, repeatable session script.

## Who and how many

- **5 managers per round** to find problems (five people surface most issues that affect a third
  of users or more). Mix: at least two over 55, at least two who never used an accounting app.
- **About 20** only when we want a benchmark number to compare against other apps; not needed now.
- One round per release that changes a main flow. Same tasks every round, so numbers compare.

## Session (30–40 minutes, in person or by video call with screen sharing)

1. Their own phone, a fresh install from Cafe Bazaar or the GitHub APK.
2. Say: «ما برنامه را امتحان می‌کنیم، نه شما را. هر جا گیر کردید بلند فکر کنید.»
3. Give one task card at a time. Don't help unless they are stuck for 2 minutes; note where.
4. After each task ask the single ease question (SEQ):
   «انجام این کار چقدر آسان بود؟ از ۱ (خیلی سخت) تا ۷ (خیلی آسان)»
5. At the end, the 10 SUS questions (Persian version below) and «یک چیز که بیشتر از همه اذیتتان
   کرد؟»

## Task cards (same order every round)

| # | Card text (read aloud) | Success means |
|---|---|---|
| 1 | «ساختمان خودتان را با تعداد واحدهای واقعی و شارژ ماهانه‌ای که می‌گیرید در برنامه بسازید.» | Building with correct units and charge |
| 2 | «واحد ۲ از قبل ۳۰۰ هزار تومان بدهکار است و واحد ۴ دویست هزار تومان پیش‌پرداخت داده. این را ثبت کنید.» | Both opening balances correct, signs right |
| 3 | «واحد ۱ امروز ۵۰۰ هزار تومان کارت‌به‌کارت کرد. ثبتش کنید و رسیدش را برایش بفرستید.» | Payment saved, receipt shared |
| 4 | «یک پرداخت را اشتباه ثبت کرده‌اید. درستش کنید.» | Reversed (undo), not a second payment |
| 5 | «قبض این ماه واحد ۳ را برایش بفرستید.» | Bill image shared |
| 6 | «تاریخ آخر مهلت پرداخت این ماه را ۱۵ام کنید.» | Month edited (not deleted and re-made) |
| 7 | «از اطلاعات برنامه نسخه پشتیبان بگیرید و جایی بگذارید که اگر گوشی گم شد از دست نرود.» | File saved and a copy sent off the phone |
| 8 | «یک مشکل یا پیشنهاد را برای سازنده بفرستید.» | Feedback email composed |

## What to record per task

- **Success** (yes / with help / no), **time** (stopwatch from card to done), **errors** (wrong
  screen, wrong amount, wrong sign), and the **SEQ** answer.
- Where they hesitated or tapped something unexpected (a screenshot or a note with the screen).

## KPIs and targets

| KPI | How | Target |
|---|---|---|
| Task success without help | per task, % of testers | ≥ 80% on tasks 1–5 (cross-industry average ≈ 78%) |
| SEQ | mean per task, 1–7 | ≥ 5.5 (average); below 5 = redesign that task |
| SUS | 10 questions, 0–100 | ≥ 68 is average; aim for ≥ 80 |
| Errors in money | wrong amount or wrong debt/credit sign | 0 — any is a bug to fix |
| Self-check reports | «چیزی در حساب‌ها جور درنمی‌آید» cards reported | 0 |

With five people a percentage is rough: report "4 of 5", not "80%".

## SUS (Persian, answer 1 = کاملاً مخالفم … 5 = کاملاً موافقم)

1. فکر می‌کنم دوست دارم مرتب از این برنامه استفاده کنم.
2. برنامه را بی‌جهت پیچیده دیدم.
3. استفاده از برنامه آسان بود.
4. فکر می‌کنم برای استفاده از آن به کمک یک آدم فنی نیاز دارم.
5. بخش‌های مختلف برنامه به‌خوبی با هم جور بودند.
6. در برنامه ناهماهنگی زیادی دیدم.
7. فکر می‌کنم بیشتر مردم خیلی زود یاد می‌گیرند با آن کار کنند.
8. کار با برنامه خیلی دست‌وپاگیر بود.
9. با این برنامه احساس اطمینان می‌کردم.
10. قبل از اینکه بتوانم با برنامه کار کنم، باید چیزهای زیادی یاد می‌گرفتم.

Score: odd items (answer − 1), even items (5 − answer), sum × 2.5.

## Design rules we hold ourselves to (from the same research)

- Body text 16sp, nothing important below 14sp; text size offered in the first setup step.
- Touch targets ≥ 48dp with space between them (Material buttons already are).
- Every important action ends with visible confirmation that stays until dismissed (backup saved,
  payment saved with receipt), not only a snackbar.
- One decision per screen in setup; no option that defaults silently (past debts are an explicit
  yes/no).
- Never show a number that failed to load; say it failed and offer a report.
