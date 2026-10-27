# VerbiLume — Google Play listing copy

## App name (max 30)
```
Verbilume: Visual Dictionary
```

## Short description (max 80)
```
Learn English words with illustrations, audio and smart spaced review.
```

## Full description (max 4000)
```
Verbilume is a visual dictionary that finally makes English vocabulary stick. Every one of its 7,298 words comes with its own hand-painted illustration, real pronunciation in British and American English, and example sentences — arranged so you remember them, not just read them.

MEET THE WORD, DON'T MEMORISE IT
Most word lists are a wall of text you abandon on page one. Verbilume shows you the word first: a single illustration that captures the scene, a plain-English definition, and a sentence you can actually use. Then active recall does the rest.

REMEMBER ON SCHEDULE, NOT BY GUESSWORK
Verbilume schedules every word with FSRS, a modern spaced-repetition algorithm. Each word returns exactly when you are about to forget it — no cramming, no wasted evenings. You will see the next interval before you answer, so the system never feels like a black box.

A GALAXY OF YOUR OWN PROGRESS
Every word you learn lights up a star on your personal word galaxy, organised into 19 realms from Education and Technology to Nature and Travel. Watch your sky fill as your vocabulary grows.

PRACTISE THE WAY YOU LIKE
• Guided review — flip, judge, and move on in seconds
• Flashcards — quick, calm deck sweeps
• Word pairing — match words to meanings against the clock
• Typing — build spelling and recall together
• Daily challenge — twelve mixed questions, one new set every day

BUILT FOR QUIET, EVERYDAY USE
• Fully offline — every word, image and recording lives on your device
• No account, no sign-in, no analytics of our own
• Optional one-time Remove ads purchase ($4.99); rewarded ads stay optional
• Dark and light themes, with a warm paper palette designed for long sessions
• Adjustable daily goal, word level (CEFR A1–C2), preferred realms and voice accent
• A gentle daily reminder at the hour you choose
• Export or restore your whole learning record as a plain JSON file

FOR EVERY LEVEL
Words are graded CEFR A1 to C2 and organised into 19 everyday and IELTS-style topics. Start where you are — Verbilume draws new words from your level and gently revisits the one below.

WHO IT IS FOR
Students preparing for IELTS and other English exams, professionals working in English, and anyone who wants a vocabulary that reaches for the right word without hesitation.

A VISUAL ATLAS, BUILT TO LAST
7,298 words · 7,298 original illustrations · 14,596 example sentences · 22,905 audio recordings · 19 realms · CEFR A1–C2.

Verbilume works fully offline for learning. There is no account and no analytics of our own; ads are served by Google AdMob when a connection is available. A one-time Remove ads purchase ($4.99) hides banner and interstitial ads, while rewarded ads remain optional. Your learning record never leaves your device unless you export it yourself.

The illustrated dictionary, dataset and recordings are derived from the open-source Word Atlas project and used under CC BY 4.0. See the in-app Licences screen for full attribution.
```

## Category & tags
- Category: Education
- Tags: English vocabulary, dictionary, IELTS, flashcards, spaced repetition

## Content rating
Everyone. No user-generated content. Contains ads (Google AdMob); optional one-time $4.99 Remove ads purchase.

## Data safety form answers
| Question | Answer |
| --- | --- |
| Does your app collect or share any of the required user data types? | Yes — via the Google Mobile Ads SDK (advertising ID, IP address, ad interactions) |
| Is all of the user data collected by your app encrypted in transit? | Yes — ad requests use HTTPS |
| Do you provide a way for users to request that their data be deleted? | Yes — Settings → Reset all progress erases all local data; the advertising ID can be reset in Android settings |
| Data types collected | Device or other IDs; approximate location; app interactions (advertising only) |
| Data types shared | Device or other IDs; approximate location; app interactions — shared with Google for advertising |
| In-app purchases | One non-consumable remove-ads product ($4.99), processed entirely by Google Play; the developer receives no payment data |

Notes for the reviewer: the INTERNET permission is used by the Google Mobile Ads SDK and by Google Play Billing for the optional remove-ads purchase; the dictionary and all learning features work offline. `POST_NOTIFICATIONS` is used only for the optional daily reminder, and `VIBRATE` for its haptic. All progress is stored in a local SQLite file and a local datastore; the user can export it to a file they choose, or erase it, from Settings. The remove-ads purchase is a non-consumable in-app product (`remove_ads`, $4.99) processed entirely by Google Play; the developer receives no payment data.

## Store assets in this folder
| File | Purpose | Spec |
| --- | --- | --- |
| `icon-512.png` | App icon | 512 × 512 PNG, opaque |
| `feature-graphic-1024x500.png` | Feature graphic | 1024 × 500 PNG |
| `phone-*.png` | Phone screenshots | captured from the emulator at 720 × 1600 |

Screenshots to capture before publishing (each 720 × 1600 or larger, 4–8 images):
1. Home — word of the day and daily progress
2. Word detail — illustration, IPA, UK/US audio
3. Guided review — a card mid-session with interval buttons
4. Galaxy — the word sky filling in
5. Daily challenge — a question screen
6. Atlas — realms and the illustrated word grid
7. Insights — stats and the review heatmap
8. Dark theme — any screen above, for the dark-mode audience

## Upload checklist
- [ ] Host `privacy-policy.html` at a public URL (GitHub Pages or similar) and paste it into the Play Console listing
- [ ] Back up `keystore/verbilume-release.jks` and `keystore.properties` — losing them means you can never update this app
- [ ] Decide the distribution route for the ~396 MB of media (see `PACKAGING.md`)