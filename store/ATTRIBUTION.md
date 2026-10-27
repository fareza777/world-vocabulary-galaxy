# VerbiLume — licences and attribution

VerbiLume is a native Android application built on top of the open-source **Word Atlas**
(词光) project. Two different licences apply, and both must be honoured in any distribution.

## Code — MIT License

The Word Atlas source code and interface design are released under the MIT License.
VerbiLume is an independent Kotlin/Jetpack Compose implementation, not a copy of the original
web code, but it is derived from that project and the MIT notice must be preserved.

Dependencies keep their own licences. Notably:

| Component | Licence |
| --- | --- |
| Fraunces typeface | SIL Open Font License 1.1 |
| Manrope typeface | SIL Open Font License 1.1 |
| Jetpack Compose, AndroidX, WorkManager, DataStore, Navigation | Apache License 2.0 |
| Coil | Apache License 2.0 |
| Kotlin standard library, kotlinx.coroutines | Apache License 2.0 |

## Dataset, illustrations and recordings — CC BY 4.0

The dictionary content bundled in VerbiLume is offered under the
[Creative Commons Attribution 4.0 International](https://creativecommons.org/licenses/by/4.0/)
licence, to the extent of the rights the original maintainers can grant:

- the word list, English definitions, example sentences, translations, collocations, word
  families, synonyms and antonyms, CEFR levels and topic classification;
- the 7,298 illustrations;
- the 22,905 pronunciation and example-sentence recordings.

**Required attribution string**

> Word Atlas contributors — CC BY 4.0 — https://creativecommons.org/licenses/by/4.0/

**Changes made by VerbiLume**

- Content was converted into a self-contained SQLite database and re-encoded for offline
  playback inside an Android app (images as WebP, audio as m4a, with original files retained
  where the app needs them).
- The Mandarin-language interface, labels and navigation of the original project were removed;
  VerbiLume presents all interface text and explanations in English.
- The learning scheduler was re-implemented in Kotlin as a faithful port of the FSRS-6
  algorithm configuration used by the original project.
- New features were built on top of the content: onboarding, streaks and XP, achievements,
  a daily challenge, typing and word-pairing practice modes, statistics, bookmarks and offline
  atlas management.

## Further notes from the original licence

- Individual English words and the bare headword list are not claimed as exclusive property.
- Content was generated with AI models under the project's specifications (text: large language
  models; illustrations: image models; recordings: ElevenLabs synthetic voices), checked
  automatically and not proofread entry by entry. It is provided **as-is, without warranty of
  accuracy**.
- The recording licence covers the finished audio files only. It does not cover the underlying
  voice models or voice identities, and implies no endorsement by the voice provider. Voices:
  ElevenLabs “Alice” (UK) and “Sarah” (US).
- This is not an official IELTS vocabulary list and is not affiliated with the IELTS partners.

## Where this appears in the app

The in-app **Licences** screen (About → Licences) repeats this attribution so that every
installed copy carries it. The store listing also links to it.