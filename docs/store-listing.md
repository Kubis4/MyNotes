# Google Play Store listing - MyNotes

## Short description (max 80 characters)

**EN** (70 chars):
```
Free, ad-free notes & to-do lists with colors, backup and live sharing
```

**SK** (75 chars):
```
Zadarmo, bez reklám - poznámky a úlohy s farbami, zálohou a zdieľaním
```

## Full description (max 4000 characters)

**EN:**
```
MyNotes is a fast, free, and completely ad-free note-taking app for
everything from quick thoughts to shared to-do lists.

KEY FEATURES

Colors & themes - Give every note its own color and pattern so your list
stays easy to scan at a glance. Light and dark themes included.

Checklists & to-do lists - Turn any note into a checklist, reorder items
with a long press, and track progress as you go.

Rich text - Bold, italic, underline, headings, bullet points, and more,
right in the note editor.

Real-time collaboration - Share a note with someone else and edit it
together, live, with Google Sign-In.

Automatic backup - Back up your notes to your own Google Drive, on a
schedule or on demand. Your backups live in a private folder only this
app can see - MyNotes never touches the rest of your Drive.

Archive & bin - Archive notes you're not using right now without losing
them, and recover deleted notes from the bin before they're gone for
good.

Search & organize - Find any note instantly, group notes into
categories, and pin the ones you use most.

Optional app lock - Lock the app behind your fingerprint or face unlock,
handled entirely by your device - MyNotes never sees your biometric
data.

Import your notes - Bring notes over from plain text files, Google Keep
exports, or Evernote (.enex) exports.

PRIVACY

MyNotes only uploads what you explicitly choose to share or back up.
Everything else stays on your device. No ads, no tracking for profit,
no data sold - ever. Full privacy policy available in the app and at
our website.
```

**SK:**
```
MyNotes je rýchla, zadarmo dostupná appka na poznámky a úlohy, úplne bez
reklám - od rýchlych myšlienok až po zdieľané to-do zoznamy.

HLAVNÉ FUNKCIE

Farby a témy - Každej poznámke priraďte vlastnú farbu a vzor, aby bol
zoznam prehľadný na prvý pohľad. Svetlá aj tmavá téma.

Checklisty a úlohy - Z ktorejkoľvek poznámky spravte checklist, položky
presúvajte podržaním a sledujte priebeh.

Formátovaný text - Tučné, kurzíva, podčiarknutie, nadpisy, odrážky a
ďalšie priamo v editore poznámok.

Kolaborácia naživo - Zdieľajte poznámku s niekým iným a upravujte ju
spoločne v reálnom čase cez Google prihlásenie.

Automatická záloha - Zálohujte poznámky do vlastného Google Drive,
podľa plánu alebo na požiadanie. Zálohy sú v súkromnom priečinku, ktorý
vidí len táto appka - MyNotes sa nedotkne zvyšku vášho Drivu.

Archív a kôš - Archivujte poznámky, ktoré momentálne nepoužívate, bez
straty dát, a obnovte zmazané poznámky z koša skôr, než nadobro zmiznú.

Vyhľadávanie a organizácia - Nájdite ktorúkoľvek poznámku okamžite,
zoskupujte poznámky do kategórií a pripnite si tie najdôležitejšie.

Voliteľný zámok appky - Uzamknite appku odtlačkom prsta alebo tvárou,
spracované priamo vaším zariadením - MyNotes nikdy nevidí vaše
biometrické údaje.

Import poznámok - Preneste si poznámky z textových súborov, exportu z
Google Keep alebo z Evernote (.enex).

SÚKROMIE

MyNotes nahráva len to, čo si sami vyberiete zdieľať alebo zálohovať.
Všetko ostatné zostáva vo vašom zariadení. Žiadne reklamy, žiadne
sledovanie na predaj dát, žiadny predaj dát - nikdy. Kompletné zásady
ochrany súkromia nájdete v appke aj na našej stránke.
```

## Data safety form (Play Console -> App content -> Data safety)

Based on what the app's code actually does (Firebase Auth, Firestore
for shared notes, Google Drive `drive.file` backup, Crashlytics,
Analytics - see `docs/privacy-policy.html` for the full reasoning).

**Does your app collect or share any of the required user data types?**
Yes.

| Data type | Collected? | Shared? | Purpose |
|---|---|---|---|
| Name | Yes | No | Account management (Google Sign-In) |
| Email address | Yes | No | Account management, collaboration invites |
| User IDs | Yes | No | App functionality, account management |
| App activity (notes content) | Yes, only for notes you explicitly share/back up | No | App functionality (sync, backup) |
| Crash logs | Yes | No | Analytics (Crashlytics) |
| Diagnostics (app performance) | Yes | No | Analytics (Firebase Analytics) |

**Is all user data encrypted in transit?** Yes (Firebase/Google APIs use HTTPS/TLS).

**Do you provide a way for users to request data deletion?** Yes - via
the contact email in the privacy policy, plus in-app deletion of notes/
backups.

**Security practices:**
- Data is encrypted in transit
- Users can request data deletion

## Assets still needed (not something I can generate reliably here)

- **App icon (hi-res)**: 512x512 PNG, no transparency. Easiest source:
  Android Studio -> right-click `res` -> New -> Image Asset -> export at
  512x512, or use the existing adaptive icon layers
  (`res/drawable/ic_launcher_foreground.xml` /
  `ic_launcher_background.xml`) in a vector editor.
- **Feature graphic**: 1024x500 PNG/JPG, shown at the top of the store
  listing. A simple version: app icon centered on a background using
  the app's gradient/brand color, plus the app name.
- **Screenshots**: at least 2, ideally 4-8, phone size. I can capture
  these directly from the connected device/emulator on request - just
  say which screens to capture (note list, note editor, checklist,
  collaboration, etc.).
