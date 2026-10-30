# ViFi Android — 4.1.0 Ayrıntı Eki (iOS `v-4.1.0` olgu listeleri)

> Bu ek [ANDROID-4.1.0-ROADMAP.md](ANDROID-4.1.0-ROADMAP.md)'nin kalemlerine kaynaklık eder. İçerik 30 Ekim 2026'da
> `ViFi-iOS` `v-4.1.0` dalı (`6e44c29`) ve ViFi-Android `v-4.1.0` (4.0.0 kodu) **salt okunarak** çıkarıldı.
>
> - **Esas olan kaynaktır.** Bir maddeyle kod çelişirse iOS kodu ve `firebase/functions/notifications/schema.js` geçerlidir.
>   Kalemi uygulamadan önce ilgili iOS dosyasını aç (her maddede `dosya:satır` var).
> - **Dil:** olgular İngilizce, kullanıcıya görünen metinler **birebir Türkçe** alıntıdır (Android `strings_*.xml`'e
>   aynen taşınır). Erişilebilirlik kimlikleri Android `testTag`'leri olur.
> - **Test listeleri kabul kriteridir:** her iOS test adı, Android'de aynı davranışı doğrulayan bir birim/UI testine eşlenir
>   (`NIT-02`, `NIT-03`).
> - **Android'e uyarlama** kararları ana listede (`NDEC-01`…`04`, §4). Burada iOS'a özgü kalan yerler (provisional izin,
>   App Group rozeti, Apple Haritalar, PrivacyInfo) yalnız bilgi içindir.
>
> | Bölüm | Konu | Kullanan kalemler |
> |---|---|---|
> | **A** | Bildirim sayfası, push dokunuşu, push gösterimi, rozet, istatistik | `PSH-*`, `INB-*` |
> | **B** | İzin + kampanya onayı akışı, Bildirim Ayarları, cihaz kaydı, çıkış temizliği | `PRM-*`, `PSH-04` |
> | **C** | Admin composer, geçmiş, sınav seçici, görsel, konum seçici | `ADM-*`, `MAP-*` |
> | **D** | Android'in bugünkü (4.0.0) bildirim kodu envanteri | tüm hatlar, `NIT-01` (silinecekler) |

---

## Bölüm A — Bildirim sayfası, yönlendirme, push, rozet, istatistik (iOS)


Root: `/Users/emrebuyuker/Desktop/7. Kat/Emre BÜYÜKER/ViFi-iOS`. Paths are under `ViFi/` unless noted. I read every file you listed fully, plus the server files `firebase/functions/notifications/{stats,deliver,schema,admin,automatic}.js`, `firestore.rules`, `NOTIFICATIONS.md` and `CLAUDE.md` for contract confirmation. Nothing was edited.

Terms: `RFC` means whatever the iOS code does. "Fanout" and "broadcast" are the two server delivery modes. The `ViFi-Android` repo already has an old announcement-only inbox (`AnnouncementsStore`, `AnnouncementReadPreferences`, `FirestoreAnnouncementsReader`). That is the same shape as iOS master, so the master → 4.1.0 deltas in section 12 apply to it.

---

### A.1 Data model

#### `NotificationKind` (`Core/Models/AppNotification.swift:8-15`)
Wire values are `general`, `promo`, `system`.
- Unknown or missing `kind` reads as `.general` (`NotificationPayloadParser.swift:165-167`).
- The doc comment says the kind is also "on Android, the notification channel". The server sets `android.notification.channelId = kind`, so Android needs channels with the exact ids `general`, `promo`, `system` (`schema.js:322`).
- Inbox icon per kind (`InboxView.swift:247-256`): general `megaphone.fill`, promo `tag.fill`, system `bell.badge.fill`.

#### `InboxItem` (`AppNotification.swift:67-94`)
| Field | Type | Notes |
|---|---|---|
| `id` | String | The `notificationId`. It is the Firestore doc id, identical in both sources. Used for highlighting and stats. |
| `source` | `.broadcast` (`announcements/{id}`) or `.personal` (`users/{uid}/inbox/{id}`) | |
| `kind` | NotificationKind | |
| `title` | String | |
| `body` | String | `""` if missing. |
| `imageStoragePath` | String? | |
| `action` | NotificationAction? | |
| `createdAt` | Date? | |
| `expiresAt` | Date? | |

`isExpired(at now)` is `expiresAt != nil && expiresAt <= now`, so equality counts as expired (`:90-93`).

#### `NotificationAction` (`:46-64`)
Exactly one action per notification. The wire `type` is one of five values:

| `type` | Payload | Validation |
|---|---|---|
| `location` | `NotificationLocation{latitude, longitude, name, address?, placeId?}` | See below. |
| `exam` | `NotificationExamTarget{examId, path: ArchivePath, kind: ExamKind}` | See below. |
| `paywall` | no fields | |
| `addExam` | no fields | |
| `url` | an `https` URL | See below. |

**Location validity** (`:18-35`, parser `:56-67`):
- `latitude` and `longitude` must be non-bool, finite numbers.
- `name` is required (trimmed, non-empty).
- `address` and `placeId` are optional trimmed strings; blank becomes nil.
- Valid means latitude in [-90, 90], longitude in [-180, 180], trimmed name non-empty and at most 120 characters, address length at most 200.
- `placeId` length is not checked on the client. The server allows up to 300.
- An invalid location makes the action nil. The item is still shown, with no button.

**Exam target** (`NotificationPayloadParser.swift:169-175`). All of these must hold, otherwise the action is nil:
- `examId` is a non-blank string.
- `path` is `[String]` with exactly 5 elements (`ArchiveLevel.allCases.count`), and the last element equals `examId`.
- `examKind` is `"JPG"` (images) or `"PDF"`.
- `names` is optional. It is used only if its count equals the path count; otherwise display names fall back to the ids.
- Server-side shape (`admin.js:149-177`): the admin supplies only `examId`; the server fills `path` (ids `[uni, uni__fac, uni__fac__dep, courseId, examId]`), `names` and `examKind` from the published `exams/{id}`. Ids are composite slugs, and equality uses the ids alone.

**URL action** (`:220-224`): the trimmed string must parse as a URL, the scheme (lowercased) must be `https`, and the host must be non-empty. There is no length check on the client; the server caps it at 500.

**Failure tolerance** (`NotificationPayloadParser.swift:37-54`): a missing `type`, an unknown type, or any malformed action yields nil. The entry is kept without a button, so a newer server never blanks an older app.

#### Parsing an inbox doc (`NotificationPayloadParser.inboxItem`, `:14-27`)
The doc is **skipped** (nil) only if:
- the id is invalid, or
- the trimmed `title` is empty or missing.

Everything else is tolerant.

**Id validity** (`:151-155`): non-empty, at most 128 characters, and each scalar must be ASCII and alphanumeric, `-` or `_`. This matches the server regex `^[A-Za-z0-9_-]{1,128}$`. A doc id with any other character is silently dropped.

**String values** (`:200-205`): every string is trimmed, and blank means nil. So the title is stored trimmed, a missing body is `""`, and a blank address or placeId is nil.

**No client-side length limits.** Title at most 80 and body at most 500 are server limits only (`schema.js:14-15,79-85`). The client does not truncate.

**Dates** (`:159-163`): a `Date` is accepted (the Firestore reader converts every top-level `Timestamp` to a `Date` first), or a number treated as epoch milliseconds. Strings are not accepted.

**Image** (`:31-35`): read from `image.storagePath` only. It must match `notificationImages/<id>.jpg`, where `<id>` is a valid notification id of 1 to 128 characters (`NotificationImage.isValidStoragePath`, `AppNotification.swift:111-117`). The path is trimmed first. A URL-shaped value, `../`, `a/b`, `.png`, an empty name, or a non-string yields nil, so there is no image. Reason in the comment: a stray value must never make the app download something else with the user's credentials.

**Number handling** (`:207-213`): bools are rejected as numbers. NSNumber 0 and 1 are real numbers, not flags.

**Fixed inbox doc shape written by the server** (`schema.js:301-309`): `{kind, title, body, createdAt(serverTimestamp), image?{storagePath}, action?, expiresAt?}`.

**Missing `createdAt`:** the Firestore `orderBy(createdAt)` query excludes docs without that field entirely (`CLAUDE.md`: "createdAt ZORUNLU"). The parser itself tolerates nil. Such an item would still show if it appeared, sorted last, and is never counted unread.

---

### A.2 Data sources and merge

#### Firestore reads (`Core/Services/FirebaseInboxReader.swift`)
All reads use the named `vifi` database.

| Source | Path and query | Limit |
|---|---|---|
| Broadcast | `announcements`, `orderBy createdAt desc` | **50** (`:14-16,24-29`) |
| Personal | `users/{uid}/inbox`, `orderBy createdAt desc` | **100** (`:31-36`) |
| Read marker | `users/{uid}/settings/inbox` (single doc) | n/a |

- Both list listeners use a plain `addSnapshotListener` with **no** `includeMetadataChanges`. Cache-first snapshots are delivered as-is.
- The marker doc listener uses `includeMetadataChanges: true` (`:43`).
- The limits apply server-side, before expiry filtering and merging. Expired entries therefore still consume slots.
- Parsing happens inside the callback. Docs that fail parsing are dropped (`compactMap`).
- A listener error emits `.failure(.unavailable)` once and then finishes the stream (`:79-84`). On master, an error yielded an empty list.
- The streams are `bufferingNewest(1)`, and each listener is removed on termination.
- Rules (`firestore.rules:54,166-171`): `announcements` is readable by +90 phone users or admins. `users/{uid}/inbox` is readable by the owner or admins. The old `read`-flag update rule still exists but the app does not use it.

#### Merge (`Core/Services/InboxStore.swift:89-98`)
- Take `broadcast.items + personal.items`, keyed by `id`. The later one wins, so a doc present in both sources is listed once **as the personal one**.
- Filter out items where `isExpired(at: now())`. `now` is evaluated on each read of `items`. There is no timer; iOS only re-evaluates when something observed changes, and the `hidesExpired` test simply re-reads `items` after advancing the clock.
- Sort newest first (`:240-247`):
  - two dated items sort by `createdAt` descending;
  - dated items come before undated ones;
  - equal dates, or both undated, sort by `id` ascending (stable).

#### Store state (`:100-114`)
| Condition | Result |
|---|---|
| Both sources failed | `.failed(message: InboxError.unavailable.userMessage)` |
| Either source still pending (including the case where the other failed) | `.loading` |
| Otherwise | `.loaded(items)` |

`hasPartialFailure` is `broadcast.isFailed != personal.isFailed`, which means exactly one source failed.

A failed source stays `.failed` until `retry()`, because the real stream ends after the failure.

#### Retry (`:147-149`, `RootView.swift:231-235,264-270`)
- `retry()` increments `retryToken`.
- The `.task(id: InboxObservation(userId, retry))` in `ProfileGate` restarts `observe(uid:)`.
- `observe` resets both sources to `.pending`, so the UI goes back to full-screen loading, then recomputes.
- The inbox is observed as soon as a user is signed in. The task sits on the `ProfileGate` wrapper, so it runs even before the profile is complete.
- `reset()` on sign-out (`RootView.swift:58`) sets everything back to pending and clears `userId`. Stale deliveries from a previous uid are ignored through the `guard userId == uid` checks (`:188,196,203`).

---

### A.3 Unread and read marker

Doc: `users/{uid}/settings/inbox` with a single field `readAt` (Timestamp). Rules (`firestore.rules:217-223`): the owner may read and write; the write must be exactly `{readAt: request.time}`; delete is denied. The client writes with `setData(["readAt": FieldValue.serverTimestamp()])`, a full set with no merge (`FirebaseInboxReader.swift:67-70`).

#### Unread definition (`InboxStore.swift:117-125`)
- An item is unread only if the marker is `.known(readAt)`, the item has a `createdAt`, and `createdAt > readAt` (strictly greater: the same instant counts as read).
- `unreadCount` equals the number of **non-expired merged** items that are unread.
- Marker states:
  - `unknown`: not delivered yet, nothing unread.
  - `unavailable`: read error, nothing unread, "rather than a badge that never clears".
  - `known(Date)`.
- Undated items are never unread.
- The count is the same on every device because `readAt` is server-side.

#### Marker reading (`FirebaseInboxReader.swift:38-65`)
- On a listener error the reader emits `.failure`, the store sets the marker to `unavailable`, and **never writes**.
- A snapshot that is `metadata.isFromCache && !exists` is **skipped**, with no emission. A marker missing only from the local cache (offline) must not be treated as "never opened", because writing one could overwrite the server's. The store waits for the server's answer, and nothing is unread until then. `includeMetadataChanges` exists so that the server's confirmation of the same "missing" (only `isFromCache` flips) is still delivered.
- The value is read with `data(with: .estimate)`. While the marker write is pending, the timestamp shows as the local estimate instead of missing, so the count does not flash after opening.

#### "Missing marker on a new account" rule (`InboxStore.swift:200-237`)
This is the final decision from commit 910524f. It supersedes the older text in `NOTIFICATIONS.md §3.5` ("if empty, all unread").
- On a server-confirmed `.success(nil)`, call `initializeReadMarker`. It runs **once per observation** (`didInitializeReadMarker`).
- It immediately sets the marker to `.known(now())` using the device clock, then calls `markRead` (server timestamp).
- Effect: the announcements already out are listed but not unread. Only later arrivals light the bell.
- If that write fails, it is only logged and the local `.known(now)` stays for this session. The next observation (relaunch or retry) tries again.
- A repeated `.success(nil)` while the first write is pending changes nothing.
- On success it also removes the legacy key `announcements.readIds.v1` from UserDefaults. On failure the legacy ids are kept.
- A read error never writes. A pending marker never writes (`unknown`).

#### Writing on inbox open (`InboxStore.markAllRead`, `:155-172`)
- Guard conditions (all must hold): a user id is set, the marker is `.known`, and at least one unread item with a `createdAt` exists. Otherwise no write.
- Remember `markedThrough = newest unread createdAt`. If a later call has `newest <= markedThrough`, skip it.
- It fires an async `markRead(uid)`. The marker becomes the **server time**, not the newest `createdAt`.
- On failure it logs and resets `markedThrough` to nil if it still equals `newest`, so the next opening or arrival retries.
- **When it is called** (`InboxView.swift:30-40`):
  1. On the inbox view's `onAppear`.
  2. On every `onChange(of: unreadCount)` where `count > 0`. This covers items that arrive while the inbox is open, and items that were still loading or whose marker had not arrived at open time.
- So the write happens once for what is unread, and again only when something newer arrives. It is **not** written on every open: if nothing is unread, there is no write.
- It marks **everything** read at once, regardless of scroll position.
- The row dot (`isUnread`) is computed live, so dots vanish almost immediately after the marker echoes back. There is no frozen "was unread at open" snapshot.

#### Cross-device sync
The marker listener is live (`readMarkerChanges` stays open). A marker written on another device updates the count live (test `markerFollowsOtherDevices`).

---

### A.4 Inbox UI (`Features/Notifications/InboxView.swift`)

#### Presentation and toolbar
- Presented as a sheet from `RootView` (`RootView.swift:144-148`) with `presentationDragIndicator(.visible)`.
- It sits on the archive `NavigationStack`, not on Home, so it can open over a pushed exam or list.
- `NavigationStack` with an inline title **"Bildirimler"** (`:22`).
- Trailing `.confirmationAction` button **"Bitti"** (`:26`), id `inbox.done`, calls `dismiss()`.
- The bell on Home (`HomeView.swift:62-70`): a Button with label **"Bildirimler"**. The icon is `bell.badge.fill` if `unreadCount > 0`, else `bell`. Id `toolbar.inbox`. It calls `notificationRouter.openInbox()`.
- On appear it tracks analytics `screenView(name: "Inbox")`. When the inbox closes, Home's `trackScreen()` runs again.
- `.offersMarketingConsentReask()` is attached to the inbox. After 700 ms it may offer the consent re-ask, unless a push highlight is active (`NotificationPromptPresenter.swift:56-59`). That belongs to the consent-flow area.

#### Content states (`:61-79`)
- `.loading`: `LoadingStateView` with the text **"Yükleniyor…"** (`DesignSystem/Components.swift:21`), id `state.loading`.
- `.failed(message)`: `ErrorStateView` with the title **"Bir sorun oluştu"**, the message **"Bildirimler yüklenemedi. Bağlantını kontrol edip tekrar dene."** (`InboxReading.swift:9`), and a **"Tekrar Dene"** button. Ids: `state.error`, `state.retry`. The button calls `retry()`.
- `.loaded` and the items are empty and there is no partial failure: `EmptyStateView` with the title **"Bildirim yok"**, symbol `bell`, message **"Yeni bildirimler burada görünecek."** (`:70-74`), id `state.empty`. On master the message read "Yeni duyurular burada görünecek."
- `.loaded` with items, or empty with a partial failure: a List.
  - Style `.insetGrouped`.
  - If `hasPartialFailure`, a first Section appears (`:115-128`) with a VStack containing:
    - a Label **"Bazı bildirimler yüklenemedi."** (symbol `exclamationmark.triangle`, subheadline, secondary colour), and
    - a Button **"Tekrar Dene"** (subheadline semibold, id `inbox.retry`).
  - Container id `inbox.partialFailure`.
  - The retry calls `retry()`, which restarts everything and shows full loading again.
- `inbox.list` is the identifier on the content container (`:21`).
- There is no pull-to-refresh (listeners are live), no swipe-delete, and no "mark all read" button.

#### Row layout (`InboxRow`, `:260-369`)
The row is a VStack with 12 pt spacing and 4 pt vertical padding: card, then optional image, then optional button.

**Card** (`cardContent`): an HStack, top-aligned, 12 pt spacing.
- A 36 pt `IconBadge` of the kind symbol: accent colour, 15% accent background, corner radius 28% of the size.
- A VStack with 4 pt spacing:
  - title in `.headline`;
  - body in `.subheadline`, secondary colour, shown only if non-empty;
  - caption row, an HStack with 6 pt spacing: the **"Kampanya"** label (caption semibold, secondary) only when `kind == .promo` (App Store 4.5.4), plus the date in caption/tertiary if `createdAt` exists.
- Date format: `.dateTime.day().month().year()` with the device locale and the default abbreviated month, so Turkish reads like "30 Eki 2026".
- A Spacer, then if unread an 8x8 accent-colour circle with accessibility label **"Okunmadı"**.
- The card has a combined accessibility element, id `inbox.row.<id>`, and the `isSelected` trait when highlighted.

**Highlight** (`:295`, `:83-85`, `:104-110`): the row background is accent colour at 0.18 opacity.
- `highlightedId = router.highlightedId` only if that id is currently among the displayed items. It is nil while the list has not loaded it, or if it is expired or absent.
- `onChange(of: highlightedId, initial: true)` scrolls with `scrollTo(id, anchor: .center)` inside `withAnimation`, and reports `.opened` for that id (see section 8).

**Image** (`InboxImageView`): shown when `imageStoragePath != nil`, under the card.
- Loaded via `environment.fileLoader.image(from:maxPixelSize: 1200)` using `StorageURL.canonical(storagePath:bucket:)` = `https://<firebaseStorageHost>/v0/b/<bucket>/o/<percent-encoded path, / as %2F>?alt=media`. `RemoteFileLoader` adds the ID token and App Check. Images are never public. The path is re-validated with `isValidStoragePath` before loading.
- Layout: full width, height `min(@ScaledMetric(relativeTo: .body) 160, 280)`, scaledToFill and clipped, corner radius 12.
- While loading: a grey (`tertiarySystemFill`) rectangle with a ProgressView.
- On failure: the same rectangle with a `photo` icon (title font, tertiary colour). There is no retry; a cancelled load restarts when the row reappears.
- Accessibility: label **"Bildirim görseli"** with the image trait (`InboxImageView.swift:25`), id `inbox.row.<id>.image`.
- Mock mode shows the bundled `sample-page-1.jpg` instead, with no network.
- Tapping the image runs the action, but only if a button is present.

**Action button** (`:275-292`): `.borderedProminent`, full width, disabled while working.
- Label font subheadline semibold, **black** foreground (white on the brand orange fails contrast).
- If working, a ProgressView tinted black shows to the left.
- Id `inbox.row.<id>.action`.
- The card text area is tappable (`contentShape` + `onTapGesture`, button trait) **only if a button exists**. Cards without an action are inert. Tapping the card, the image or the button all call the same `perform`.

#### Button label and symbol per action (`InboxView.swift:234-245`)
| Action | Label | SF Symbol |
|---|---|---|
| exam | **"Sınava Git"** | `doc.text.magnifyingglass` |
| paywall | **"Premium'a Göz At"** | `star.fill` |
| addExam | **"Sınav Ekle"** | `plus.rectangle.on.rectangle` |
| url | **"Bağlantıyı Aç"** | `safari` |
| location | **"Konuma Git"** | `mappin.and.ellipse` |

**Visibility rule** (`button(for:)`, `:134-138`): no action means no button. A `paywall` action is hidden when `environment.subscription.isSubscribed`, so the entire card, image and button are inert for subscribers (the row still shows). Everything else always shows.

---

### A.5 Action handling (`perform`, `InboxView.swift:141-168`)

First: `guard let action = item.action, button(for: action) != nil`. Then it reports `.actionTapped` (before anything else, "whatever the action then does"). Then:

- **exam**: runs `InboxExamGate.check(...)` in a Task. If it allows, call `open(target)`.
- **paywall**: sets `isShowingPaywall`, presenting `PaywallView()` as a sheet over the inbox.
- **addExam**: sets `isShowingAddExam`, presenting `AddExamView()` as a sheet (drag indicator visible).
- **url**: `openURL(url)`, which opens in the external browser (Safari), not in-app.
- **location**: sets `locationToOpen`, presenting the LocationChoiceSheet.

#### Exam gate (`ExamAccess.swift:18-40`, `InboxExamGate.swift`)
**Resolver order** in `ExamAccessResolver.decision`:
1. The path must be level `.exam` (depth 5) with a parent course path, otherwise `.unavailable` with no request.
2. If `isSubscribed` **or** `unlockedIds.contains(examId)`, return `.open`. **The course is not loaded.** Edge: a subscriber or an already-unlocked user gets `.open` even for a deleted exam, so the viewer is opened without an existence check.
3. Otherwise load `repository.items(at: coursePath)`. This throws the repository error (for example offline). Find the item whose `path == examPath` (id equality) **and** `exam != nil` (has files). If none, return `.unavailable`.
4. `freeIds = ExamLock.freeExamIds(orderedExams: exams)`: the last 2 of the **full, unfiltered, newest-first** course list. File-less exams occupy slots in this ordering. `ExamLock.isLocked(item, freeIds, unlockedIds, isSubscribed)` is false if not an exam or no files, false if subscribed, else `!(free || unlocked)`. Result is `.locked(exam)` or `.open`.

**Constants** (`ExamLock.swift:10-12`): `freeCount = 2`, `unlockCost = 100`.

**The gate's reactions** (`InboxExamGate.check`):
- A second tap while a check runs is ignored: `check` returns false immediately because `checkingExamId != nil`. The row's button shows a spinner (the comparison is on the exam id, so two notifications for the same exam both show it).
- `.open` returns true.
- `.locked` sets `lockedTarget` and `lockedExam`, presents the archive's unlock sheet over the inbox, and returns false.
- `.unavailable` sets `errorMessage` to **"Bu sınav artık arşivde bulunmuyor."** (`InboxExamGate.swift:56`).
- Any thrown error sets `errorMessage = error.userMessage` (for example `ArchiveError.offline.userMessage`). `CancellationError` is silent.
- Errors show as an alert titled **"Sınav açılamadı"** with a **"Tamam"** cancel button; the message is the error text (`InboxView.swift:51-55`).

**Unlock sheet** (the existing `ExamUnlockSheet`, reused as-is; it appears in the archive too):
- On appear it tracks `unlock_sheet_shown` with `can_afford = wallet.balance >= 100` (`trackUnlockSheetShown`).
- Unlock with coins calls the shared `AppEnvironment.unlockExam`: callable unlock, `unlocks.markUnlocked`, analytics `exam_unlock` (`exam_kind`, `coins_spent`=100, `already_unlocked`) or `exam_unlock_failed(reason)`.
- Purchase with money calls `purchaseExamUnlock`. `.pending` returns **"Satın alma onay bekliyor; onaylanınca sınav açılır."** (`ExamAccess.swift:75`).
- "Earn coins" tracks `earn_coins_tapped` and calls `leaveForAddExam` (sheet closes, then the add-exam sheet opens).
- "Premium" calls `leaveForPaywall` (sheet closes, then the paywall sheet opens).
- A follow-up (`.open(target)`, `.addExam`, `.paywall`) is stored, then consumed once in the sheet's `onDismiss` (`runExamFollowUp`) because nothing can be presented or navigated while the sheet is still dismissing. `takeFollowUp` is single-use.
- Closing the sheet without unlocking does nothing more.

**What opens** (`InboxView.open`, `:184-188`): `dismiss()` the inbox sheet, then `router.openExam(at: target.path, kind: target.kind)`. That is `Router.openExam` (`App/Router.swift:34-37`, unchanged): `path = [browse(prefix 1...4)] + [exam(path, kind)]`. It **replaces the whole nav stack** with the ancestor list screens plus the viewer, so Back walks up the archive. Viewer ids in the UI tests are `exam.pdf` and `exam.images`.

#### Location choice (`Features/Notifications/LocationChoiceSheet.swift`, `MapsLinks.swift`)
A sheet keyed by `"\(latitude),\(longitude),\(placeId ?? name)"`. It is sized to its content (`presentationDetents([.height(contentHeight + 34)])`, drag indicator visible). Contents:
- Headline **"Konumu hangi uygulamada açmak istersin?"**.
- Subline: the name, or `"\(name)\n\(address)"` if the address is non-blank after trimming.
- Prominent button **"Google Haritalar"** (symbol `map`, black label), id `inbox.location.google`.
- Bordered button **"Apple Haritalar"** (symbol `map.fill`), id `inbox.location.apple`.
- Plain button **"Vazgeç"**, id `inbox.location.cancel`.
- Choosing a map closes the sheet. "Vazgeç" closes the sheet and keeps the inbox open.

**Google logic** (`openGoogleMaps`, `:93-100`): probe `canOpenURL(comgooglemaps://)`. The scheme is declared under `LSApplicationQueriesSchemes` in `Info.plist` (`comgooglemaps`). If the app is installed, open the app URL, and if iOS refuses the open (`accepted == false`) fall back to the web URL. If the app is not installed, open the web URL.

**URLs** (`MapsLinks.swift`). Coordinates are formatted `String(format: "%.6f")` with trailing zeros and a trailing dot trimmed, `-0` becomes `0`, no locale grouping:
- Google app: `comgooglemaps://?q=<lat>,<lng>&center=<lat>,<lng>&zoom=16`, for example `comgooglemaps://?q=40.9903,29.029&center=40.9903,29.029&zoom=16`.
- Google web: `https://www.google.com/maps/search/?api=1&query=<lat>%2C<lng>`, plus `&query_place_id=<percent-encoded placeId>` when `placeId` (trimmed) is non-empty. For example `https://www.google.com/maps/search/?api=1&query=41.0082%2C28.9784&query_place_id=ChIJ9z1-abc_DEF`.
- Apple: `https://maps.apple.com/?ll=<lat>,<lng>&q=<percent-encoded trimmed name>`. The `q` part is omitted if the name is empty after trimming. For example `https://maps.apple.com/?ll=40.9903,29.029&q=ViFi%20%C3%96%C4%9Frenci%20Kafesi`.
- Percent encoding keeps only `A-Za-z0-9-._~`, so `&`, `+`, `#` and `%` are escaped. Test: `"Çay & Kahve +%20#1"` becomes `%C3%87ay%20%26%20Kahve%20%2B%2520%231`.

---

### A.6 Push tap routing

#### `NotificationRouter` (`App/NotificationRouter.swift`, `@Observable`)
**State**
- `pending: Request?` (private set), where `Request{notificationId: String?}` is Equatable.
- `isInboxPresented: Bool` (bound to the sheet).
- `highlightedId: String?` (private set).

**API**
- `receive(notificationId: String?)` only sets `pending`. A newer request replaces an older one.
- `deliverPending()`: if there is a pending request, clear it, set `highlightedId = request.notificationId`, and set `isInboxPresented = true`.
- `openInbox()` is the bell: `highlightedId = nil`, then present.
- `inboxDidDismiss()` clears `highlightedId`. It runs from the sheet's `onDismiss`.
- `reset()` clears `pending`, `highlightedId` and `isInboxPresented`.
- `sessionDidChange(from:to:)` (called from `RootView`'s `onChange(of: session.state)`, `RootView.swift:45-48`):
  - `(any, .signedOut)` calls `reset()`.
  - `(.signedOut, .signedIn)` sets `pending = nil`, because a tap made while signed out belongs to no account.
  - Everything else is a no-op. In particular `(.unknown, .signedIn)` **keeps** the pending request. This is the cold-start case: the tap that launched the app waits through `unknown` and survives.
  - `(.unknown, .signedOut)` resets, so a later sign-in opens nothing.
  - A sign-in with nothing pending leaves an already-open inbox and its highlight alone.

#### `AppDelegate` (`App/AppDelegate.swift:114-123`)
On a tap (`didReceive response`):
1. If Firebase is configured, call `Messaging.appDidReceiveMessage(userInfo)` (FCM delivery metrics).
2. Call `notificationRouter.receive(notificationId: NotificationPayloadParser.notificationId(fromPush: userInfo))`.

`notificationId(fromPush:)` (`NotificationPayloadParser.swift:145-148`): `userInfo["notificationId"]` must be a String that passes `isValidNotificationId`, otherwise nil. A push with no id (a hand-made Console message, or anything else) still opens the inbox, with no highlight. `kind` in the payload is **not consumed** by iOS. Navigation never happens in `AppDelegate`.

#### Where the inbox is presented (`HomeView.swift` ~108-121, ~232-262, `RootView.swift:129-148`)
- `HomeView` exists only after every gate: required update, sign-in, profile completion. So a cold-start tap waits.
- `.task(id: router.pending)` calls `showPendingNotification()` when `pending` changes. A newer tap replaces a waiting one because the id changed.
- It is also called when the onboarding tour finishes (`onChange(of: onboarding.isPresenting)` false).
- `.onChange(of: isInboxPresented)`: when it becomes false, Home's `trackScreen()` re-runs.

**`showPendingNotification`**
1. Return if there is no `pending`, or the onboarding tour is presenting. The tour finishes first and then calls this again.
2. If the inbox is already presented: `deliverPending()` and return. That just moves the highlight (the same list scrolls and highlights the new id).
3. Otherwise set `isShowingAbout = false` and call `notificationPrompts.dismiss()`. Only these two "nothing to lose" sheets are closed.
4. `while ModalPresentation.isShowing` (`Core/Utilities/ModalPresentation.swift`): sleep 500 ms and poll. `isShowing` is true when the key window's root view controller has a `presentedViewController`, which covers any sheet, cover or alert. Inside the loop: `guard !Task.isCancelled, pending != nil else { return }`.
5. Then `deliverPending()`.

Any other modal is **never closed**. This includes Add Exam, Account and its forms, a paywall, or an unlock sheet over an exam list, because they may hold user work or a purchase. The inbox opens as soon as that modal goes away.

#### The bug fixed by commit 3ec75d4
A push tapped while another view's sheet was up (an unlock sheet or paywall over an exam list) tried to present the inbox from the root anyway. SwiftUI refused, `isInboxPresented` stayed true but nothing was visible, and the bell stopped working until relaunch. The fix is the wait-for-no-modal poll above, plus the "already open, just move the highlight" shortcut, plus closing only About and the prompt flow. Android equivalent to guard against: never mark the inbox as open without it actually being shown, and never leave a pending request stranded.

#### Launch argument
`-ViFiOpenNotification <id>` (with `-ViFiMockData`) is parsed in `Preview Content/MockServices.swift:63,96-101`. The id is the next argv element, taken raw with no validation. If it is the last argument, nothing happens. It calls `router.receive(notificationId:)` at environment creation, and then goes through the normal gate path.

---

### A.7 Push display, payload keys, badge, extension

#### FCM payload (`schema.js:317-330`, `deliver.js`)
- `notification{title, body, imageUrl?}`.
- `data{notificationId, kind}` (strings).
- iOS: `apns.payload.aps{sound:'default', mutableContent:true}`, always set. `apns.fcmOptions.imageUrl` when there is an image.
- Android: `android.priority='high'`, `android.notification.channelId = kind` (general, promo or system), `imageUrl` when present.
- The signed image URL is a 7-day V4 signed URL minted at delivery. It is only in the payload, never written to Firestore. If signing fails, the push goes without the image and the inbox is unaffected.
- Broadcast (`general` or `system`, audience all): written to `announcements/{id}` and pushed to FCM topic `all`. The iOS app subscribes to `all` whenever it gets a token.
- Fanout (everything else, including every `promo`): copy at `users/{uid}/inbox/{id}` plus pushes to the registered device tokens. The inbox copies are written first, so a tapped push always finds its item.
- Android clients must register their device token in `users/<uid>/devices/<id>` to receive fanout. That is another agent's area; the shape is in `CLAUDE.md`.

#### Foreground presentation (`AppDelegate.swift:101-109`)
- `willPresent` calls `Messaging.appDidReceiveMessage` and returns `[.banner, .list, .sound]`. It always shows banner plus sound, and does **not** include `.badge`. There is no special handling for the inbox being open.
- The live snapshot listeners add the new entry to the open inbox.
- A silent or data push (`didReceiveRemoteNotification`) is handed to Firebase Auth first, then to Messaging. There is no routing.

#### Icon badge
- **`NotificationBadgeCounter`** (`Core/Utilities/NotificationBadgeCounter.swift`) is Foundation-only and compiled into both the app and the extension.
  - App Group `group.com.BuyukerYazilim.ViFi2`, key `notifications.badge.v1`.
  - `current` is `max(0, stored)`. `record(n)` stores `max(0, n)`. `increment()` records and returns `current + 1`, saturating at `Int.max`.
  - A corrupt non-integer stored value reads as 0.
- **App side** (`RootView.swift`):
  - At launch (`.task`) and on `scenePhase == .active`: `setNotificationBadge(0)`. The launch call exists because `scenePhase` does not deliver its initial `.active` through `onChange`, so a cold start opened from a push would otherwise keep the badge.
  - On `scenePhase == .background`: `setNotificationBadge(environment.inbox.unreadCount)`. The unread count is the same count described in section 3.
  - `setNotificationBadge` calls `UNUserNotificationCenter.setBadgeCount(count)` (a no-op until the user allows badges) **and** records the count in the App Group.
- **Extension** (`ViFiNotificationService/NotificationService.swift`) runs for **every** push, because `mutable-content` is always set:
  1. Make a mutable copy of the content.
  2. If the shared counter opens, set `content.badge = counter.increment()` (recorded plus 1, which is then recorded).
  3. Build a fallback copy of the content (text and badge, no image).
  4. Call `Messaging.serviceExtension().populateNotificationContent`, which downloads `fcm_options.image` and attaches it.
  5. Deliver exactly once through a lock-guarded `SingleDelivery` that races the image download against `serviceExtensionTimeWillExpire`. If the download fails or time runs out first, the fallback is shown: text plus badge, no image.
  - Entitlements: App Group on both the app and the extension. `CFBundleDisplayName` of the extension is "ViFi Bildirimleri". The extension is embedded in the app.
- Resulting behaviour: badge = unread count at backgrounding plus one per push since. The app resets it to its own value on the next open, so a missed or extra increment never lingers. Android has no icon-badge equivalent; the notification dot or system behaviour replaces it.

#### Permission context (other agent's area)
`NotificationPermission` gained `.provisional` and `deliversNotifications`. `SystemNotificationService.requestProvisionalAuthorization()` requests `[.alert, .badge, .sound, .provisional]` only from `notDetermined`. Provisional pushes arrive quietly in the Notification Center.

---

### A.8 Stats (`NotificationEventTracker`, `NotificationEventReporting`, `FirebaseNotificationEventReporter`)

**Events** (`NotificationEventReporting.swift`): `opened` and `actionTapped`, with raw values equal to the callable's event names.

**Callable:** `trackNotificationEvent`, region `europe-west1`. Payload `{notificationId: String, event: "opened" | "actionTapped"}`. The response is ignored. Server returns `{counted: Bool}`.

**When each fires** (all from `InboxView.swift`):
- `.opened`: when `highlightedId` (from a tapped push) resolves to an item that is actually in the displayed list (`:104-110`). It fires **only for push taps**, never for opening the inbox via the bell, and never if the id is not among the items (expired, not delivered to this user, or not loaded yet; it fires later if the item appears).
- `.actionTapped`: inside `perform`, after the `button(for: action) != nil` guard. It fires for any action type, on a tap of the button, card or image. It fires before the exam lock check, so a locked exam still counts. It is **not** fired for a paywall action for a subscriber (no button, so no `perform`).
- Both require `session.user?.id`, otherwise nothing is sent.

**Tracker behaviour** (`NotificationEventTracker.swift:15-65`):
- Fire-and-forget: `report(_:notificationId:uid:)` returns a `Task?` at once (callers ignore it; tests await it). Failures are only logged.
- Dedup key is `"\(uid)|\(event.rawValue)|\(notificationId)"`.
- Acknowledged keys persist in UserDefaults key `notifications.reportedEvents.v1` as an array, oldest first, capped at 500. It keeps the newest 500; oldest ones are forgotten and would be sent again.
- Only acknowledged reports are remembered. A failed report is not remembered, so the next tap on the same notification retries it. There is no retry timer.
- An in-flight set prevents a second request for the same key while one is pending, and the in-flight mark is released on failure.
- The memory is keyed by uid, so a second account on the same device still counts.
- `NoopNotificationEventReporter` is the default and the mock-mode reporter only logs (`MockNotificationEventReporter`).

**Server semantics** (`stats.js`):
- The caller must be a +90 phone user, otherwise `permission-denied`.
- The notification id must match `^[A-Za-z0-9_-]{1,128}$`, otherwise `invalid-argument`.
- `notifications/{id}` must exist, **and** the caller must have a delivered copy. For `delivery == 'broadcast'` that is `announcements/{id}`; otherwise it is `users/{uid}/inbox/{id}`. If either is missing the server returns `not-found`. A nonexistent id and a never-received id look identical.
- So **both** broadcast recipients and fanout recipients count, as long as a delivered copy exists. Counting is atomic and once per user: a batch `create()`s `notifications/{id}/events/{uid}_{event}` and increments `stats.<event>`. The `ALREADY_EXISTS` case returns `{counted:false}`.
- Edge: a hand-written Console announcement with no `notifications/{id}` doc gets `not-found` on every tap. The client never acknowledges it, so it retries on every tap and logs each failure silently.

---

### A.9 Analytics (Firebase mapping in `Core/Services/FirebaseAnalyticsTracker.swift`)
Added in this branch (`ServiceProtocols.swift:136-143`): `notification_priming_shown|accepted|declined` and `marketing_consent_shown|accepted|declined`, each with the param `source` (`prompt`, `reask` or `settings`). These belong to the permission-flow area.

Inbox-related events in this branch: none new. The inbox uses existing events:
- `screen_view` with `screen_name` "Inbox".
- `screen_view` "Home" on return.
- `unlock_sheet_shown` (`can_afford`).
- `earn_coins_tapped`.
- `exam_unlock` (`exam_kind`, `coins_spent`, `already_unlocked`).
- `exam_unlock_failed` (`reason`).

---

### A.10 Mock data and launch arguments

**`StubInboxReader`** (`Preview Content/MockServices.swift:764-910`):
- `readAt` starts at `1_757_000_000`, so every sample is unread.
- The marker stream stays open. `markRead` sets `readAt = Date.now` and re-emits.
- The two list streams yield once and finish.
- Broadcast samples:
  - `a1`: general, "Yeni sınavlar eklendi" / "Bilgisayar Mühendisliği'ne 2024 vize ve finalleri eklendi.", `createdAt` 1_763_000_000, no action.
  - `a2`: general, "ViFi'ye hoş geldin" / "Geçmiş sınavlara tek yerden ulaş, sınav ekleyerek coin kazan.", 1_759_000_000.
  - `a-expired`: general, "Süresi dolmuş duyuru" / "Bu bildirim listede görünmemeli.", `createdAt` 1_760_000_000, `expiresAt` 1_760_500_000. It is never shown.
- Personal samples:
  - `p-exam`: system, "Sınavın onaylandı" / "Gönderdiğin sınav arşive eklendi. Göz atmak ister misin?", action exam (`2018 VİZE`, PDF, a free oldest exam), 1_762_000_000.
  - `p-paywall`: promo, "Premium'da bahar fırsatı" / "Tüm arşivin kilidini aç, kampanyalı fiyatı kaçırma.", action paywall, 1_761_000_000.
  - `p-exam-locked`: general, "Yeni vize sorusu eklendi" / "Mühendislik Matematiği 2023 vizesi arşivde.", an image, action exam (`2023 VİZE`, the newest and therefore locked, images kind), 1_760_800_000.
  - `p-location`: promo, "Kampüse yakın kahve fırsatı" / "ViFi'lilere özel %20 indirim, bu hafta sonuna kadar.", action location (`ViFi Öğrenci Kafesi`, lat 40.9903, lng 29.0290, address "Caferağa, Kadıköy, İstanbul"), 1_758_000_000.
- Visible order: `a1`, `p-exam`, `p-paywall`, `p-exam-locked`, `a2`, `p-location`.
- The mock course "MÜHENDİSLİK MATEMATİĞİ" has four exams (2023 VİZE, 2022 FİNAL, 2019 FİNAL, 2018 VİZE), so the newest two are locked.

**Launch arguments** (`App/AppEnvironment.swift`):
- `-ViFiMockData`: required for all the mock behaviour below.
- `-ViFiSubscribed`: premium on. No "Premium'a Göz At" button, locked exams open directly.
- `-ViFiNoCoins`: balance 0, so the unlock sheet leads to "earn".
- `-ViFiOpenNotification <id>`: simulates a push tap. Samples such as `p-paywall` and `a1` are used in UI tests, and `does-not-exist` just opens the inbox.
- `-ViFiConsentReask` makes the consent re-ask appear when the inbox opens (other area).

---

### A.11 Test inventory (acceptance spec)

**`InboxStoreTests`** (29, `ViFiTests/InboxStoreTests.swift`)
1. `loadingUntilBothAnswer`: state is `.loading` until both sources have answered.
2. `mergesAndSorts`: merge, sort newest first, undated last, and an id in both sources is listed once as personal.
3. `hidesExpired`: expired items hidden, and also once the clock passes `expiresAt`.
4. `emptyIsNotFailed`: empty is `.loaded([])` with no partial failure.
5. `bothFail`: both sources failing gives `.failed` with the error message.
6. `oneFails`: the other source's items stay and `hasPartialFailure` is true.
7. `failsAndRecovers`: a later failure sets the flag, a recovery clears it.
8. `retryBumpsToken`: `retry()` increments the token.
9. `reobservingRestarts`: observing again after a retry restarts from loading and opens the broadcast listener a second time.
10. `noUnreadWithoutMarker`: no marker means unread 0.
11. `entriesNewerThanMarkerAreUnread`: unread items are counted, undated items are not unread, and nothing is written.
12. `unreadIsNewerThanReadAt`: the same instant counts as read.
13. `personalCounts`: personal items count as unread.
14. `unavailableMarker`: a marker read error means unread 0, and `markAllRead` writes nothing.
15. `markerFollowsOtherDevices`: a marker emitted elsewhere updates the count live.
16. `marksReadOnce`: two `markAllRead` calls write once and the count drops to 0.
17. `nothingToMark`: nothing unread means no write.
18. `marksNewArrivals`: a newer arrival after marking is written for again.
19. `failedWriteRetries`: a failed write is retried at the next call.
20. `newAccountStartsRead`: with no marker, the items are listed, unread is 0, the marker is written once, and the echo does not write again.
21. `newAccountLaterEntriesAreUnread`: after initialization only later entries count unread.
22. `newAccountWritesOnce`: a repeated empty marker while the write is pending writes nothing more and never flashes unread.
23. `readErrorNeverWrites`: a marker read error never writes.
24. `pendingMarkerNeverWrites`: a marker that has not arrived never writes.
25. `nextNewAccountWritesItsOwn`: after `reset()`, signing in as another new account writes its own marker.
26. `migratesLegacyState`: legacy local read ids lead to one `readAt` write and the old key is dropped, with unread 0.
27. `existingMarkerSkipsMigration`: an existing `readAt` skips migration, with no write.
28. `failedMigrationKeepsLegacyState`: a failed write keeps the legacy ids.
29. `resetForgets`: `reset()` clears everything and ignores stale deliveries for the old user.

**`InboxExamGateTests`** (8): `opens` (free, unlocked and subscriber open straight away); `locked` (offers the unlock sheet for it); `unlockedThenOpens` (follow-up `.open` runs once); `leaving` (earn coins or Premium closes the sheet and sets `.addExam` or `.paywall`); `closedWithoutUnlocking` (no follow-up); `missing` (message "Bu sınav artık arşivde bulunmuyor.", sheet nil); `failure` (message is `ArchiveError.offline.userMessage`); `doubleTap` (a second tap while loading is ignored and only one repository request is made).

**`ExamAccessResolverTests`** (8): `isLockedRule` (free are `old-1` and `old-2`; subscribers and unlocked are never locked; file-less and non-exam items are never locked); `subscriber` (opens without loading the course); `alreadyUnlocked` (opens without loading); `freeExam` (opens, judged from the full course list); `lockedExam` (returns the archive's own item); `unavailable` (deleted or file-less exam); `notAnExamPath` (no request); `loadFailure` (throws `ArchiveError.offline`).

**`NotificationPayloadParserTests`** (12): `fullInboxItem` (full doc with action, image and dates; title trimmed; `expiresAt` from epoch milliseconds); `legacyAnnouncement` (title, body and `createdAt` only still parses as general with no action or image); `imageStoragePath` (valid trimmed path accepted; ten invalid forms rejected; 128-character id accepted); `requiresTitleAndSafeId` (blank title, id `a/b` and empty id are dropped); `toleratesNewerServer` (unknown kind becomes general, unknown action is dropped, the entry stays); `actions` (exam needs a 5-element path ending in the id; http is rejected; https, paywall and addExam are accepted; kind `"PDF"` gives `.pdf`); `invalidLocation` (latitude 95 and a bool coordinate are rejected); `facets`; `nsNumberCounts` (NSNumber 0 and 1 are counts); `sendResult`; `sentNotification` (admin area); `pushNotificationId` (valid id returned; `../x` or a missing id gives nil).

**`NotificationRouterTests`** (14, plus 1 in `InboxActionButtonTests`): `receiveOnlyQueues`; `deliverOpensAndHighlights`; `pushWithoutId` (inbox opens with no highlight); `deliverWithoutPending`; `newerTapReplaces`; `tapWhileOpen` (moves the highlight); `bellAndDismiss` (bell opens with no highlight, dismiss clears it); `resetDropsEverything`; `coldStartTapSurvivesSignIn` (`unknown → signedIn` keeps the request); `signedOutTapDroppedOnSignIn`; `signOutResets`; `coldStartSignedOut` (`unknown → signedOut`, then a later sign-in opens nothing); `signInWithoutPending` (leaves an open inbox and highlight alone); `tapPayload` (id comes from the payload, unsafe or missing is nil, `kind` ignored). `InboxActionButtonTests.buttons` checks the five button titles.

**`NotificationBadgeCounterTests`** (6): `startsAtZero`; `pushesCountUpFromTheAppsBadge`; `appResets`; `neverNegative` (negative or corrupt stored values read as 0); `noOverflow`; `appGroup`.

**`NotificationEventTrackerTests`** (11): `sendsTheReport`; `onceAcknowledged`; `independentKeys` (other notifications and the other event still report); `perUser`; `rememberedOnDisk`; `failureIsRetriedNextTime`; `doesNotBlockAndDropsRepeatsInFlight`; `inFlightMarkIsReleasedOnFailure`; `memoryIsBounded` (500 cap, the oldest forgotten); `noopReporter`; `eventNames` (`"opened"`, `"actionTapped"`, exactly 2 cases).

**`MapsLinksTests`** (9): `googleMapsApp` (exact app URL); `probe` (`comgooglemaps://`); `googleMapsWeb`; `googleMapsWebWithPlaceId`; `placeIdEdgeCases` (blank is left out, odd is escaped); `googleChoice` (app if installed else web, scheme https); `appleMaps` (exact URL with escaped name); `appleMapsEscaping` (round-trips the name); `numbers` (negatives, whole numbers, `-0.0000001` gives `0`, six decimals, `0.00001`).

**`InboxUITests`** (13, `ViFiUITests/InboxUITests.swift`): `testOpeningTheInboxShowsAnnouncements`; `testInboxMergesBothSourcesAndHidesExpiredEntries` (also checks `a1` has no action button); `testExamActionOpensTheExamBehindTheInbox` (opens `exam.pdf`, inbox closes); `testTappingTheCardRunsTheAction`; `testPremiumActionOpensThePaywall` (`paywall.close`); `testSubscribersAreNotOfferedThePremiumButton`; `testLockedExamFromANotificationGoesThroughTheUnlockSheet` (`exam.unlock.sheet`, `exam.unlock.confirm`, then `exam.images`); `testLockedExamWithoutCoinsLeadsToEarningThem` (`exam.unlock.earn` leads to `addExam.university`); `testSubscriberOpensALockedExamFromANotificationDirectly`; `testNotificationImageIsShownOnItsCard` (`p-exam-locked.image` exists, `p-paywall.image` does not); `testTappedPushOpensTheInboxOnItsNotification` (the `p-paywall` row is selected, `a1` is not); `testTappedPushOfAnUnknownNotificationJustOpensTheInbox`; `testClosingTheInboxOpenedByAPushReturnsToHome` (`inbox.done`, then `home.list`, inbox does not reopen).

Related location tests in `ViFiUITests/LocationCampaignUITests.swift`: `testKonumaGitOffersBothMapsApps`, `testTappingTheCardOffersBothMapsApps`, `testDecliningTheChoiceClosesItAndKeepsTheInbox`.

#### Accessibility identifiers in this area
`toolbar.inbox`, `inbox.list`, `inbox.done`, `inbox.row.<id>`, `inbox.row.<id>.action`, `inbox.row.<id>.image`, `inbox.partialFailure`, `inbox.retry`, `inbox.location.google`, `inbox.location.apple`, `inbox.location.cancel`, `state.loading`, `state.error`, `state.retry`, `state.empty`. Reused from other screens: `exam.unlock.sheet`, `exam.unlock.confirm`, `exam.unlock.earn`, `exam.unlock.premium`, `exam.unlock.error`, `exam.pdf`, `exam.images`, `paywall.close`, `addExam.university`, `home.list`.

---

### A.12 Surprises and edge cases

1. **One source can silently shadow the other.** Personal beats broadcast on an id clash. Normally an id is in only one source.
2. **`.loading` hides partial results.** One source failed and the other still pending shows full-screen loading, not a partial list.
3. **Retry is a full restart.** It resets all three listeners and returns to full-screen loading. In the real reader a failed list stream ends permanently, so recovery happens only through `retry()` or a new sign-in; the scripted test double re-emits to simulate recovery.
4. **Cache-first list snapshots are not guarded.** Only the marker doc skips cache-only snapshots. The two list queries deliver whatever Firestore gives, so the offline behaviour of an empty cache is whatever the SDK does. Android should expect the same.
5. **The 50 and 100 limits are applied before expiry.** Expired items eat slots.
6. **Expiry is not live.** It is re-evaluated whenever the store's `items` is read. Android should refilter on each emission or recomposition if live behaviour is wanted.
7. **Opening the inbox marks everything read in one write**, regardless of scroll, and it is a no-op if nothing is unread.
8. **Device clock vs server clock.** On a new account the temporary marker is the device clock (`now()`), then the server timestamp replaces it. A small clock skew can briefly misclassify a very fresh item.
9. **A subscriber or already-unlocked user skips the existence check.** `.open` is returned without loading the course, so a deleted exam opens the viewer, which must handle the missing exam.
10. **Free-two rule counts file-less exams.** The last two items of the unfiltered newest-first course list are free, even if one of them has no files.
11. **Exam path ids are composite slugs.** Equality uses ids only. The Android repository must resolve `[uni, uni__fac, uni__fac__dep, courseId, examId]` to a course listing. The resolver loads the whole course and finds the exam by id.
12. **`.opened` only for resolved highlights.** An expired or non-delivered id never reports, and the bell never reports.
13. **A tap on a card without a button does nothing** (apart from the highlight when it came from a push).
14. **Paywall action for a subscriber is fully inert** and not reported.
15. **Double reporting is harmless**: client dedup plus server dedup.
16. **`url` actions open in the external browser.** There is no in-app browser, and the parser enforces https plus a non-empty host.
17. **Invalid actions leave a button-less card.** The unknown-action and invalid-location cases keep the entry visible.
18. **Notification ids must pass the strict pattern**, or the doc is dropped silently.
19. **Master → 4.1.0 deltas** (for the Android legacy inbox): per-device read ids are replaced by server `readAt`; list errors used to yield an empty list (now a failure); ids were not validated; only `Timestamp` dates were accepted; the empty-state message changed from "Yeni duyurular burada görünecek." to "Yeni bildirimler burada görünecek."; there was no image, action, kind, expiry or personal source.
20. **Legacy migration relevance for Android.** The iOS rule for `announcements.readIds.v1` (write `readAt` once and drop the local ids) maps to Android's existing `AnnouncementReadPreferences`. The cleanest equivalent is: on the first confirmed-missing marker, write `readAt`, then clear the local read ids; on failure keep them.
21. **Stats caveat.** `CLAUDE.md` says "yalnızca teslim kopyası olan alıcı sayılır". That means any recipient with a delivered copy, broadcast included, not only fanout recipients.
22. **Tap while signed out.** The tap is dropped at the next sign-in. The cold-start tap survives only through `unknown → signedIn`.
23. **Sign-out cleans up.** `RootView` clears the inbox, the router and notification state, and the device registration. The tracker memory is keyed by uid, so it is not cleared.
24. **Android payload implications (server contract, not iOS code).** `data.notificationId` and `data.kind` are the only consumed or relevant keys. Channels `general`, `promo` and `system` must exist on Android. Image sizing and attachments on Android are handled by FCM itself when `imageUrl` is present.
---

## Bölüm B — İzin + kampanya onayı, Bildirim Ayarları, cihaz kaydı (iOS)


All paths are under `ViFi-iOS/ViFi/` unless noted. Line numbers refer to branch `v-4.1.0`. Turkish strings are quoted exactly. The ordering and re-ask rules in section 1 and the device and consent rules in sections 3 and 5 are the ones most likely to be reimplemented wrongly.

Source of truth for the rules: `NOTIFICATIONS.md §1.1` (local planning doc), the code and tests below, and commit `97536ab` ("track F").

---

### B.1 State machine

#### 1.1 Vocabulary

- **`NotificationPermission`** (`Core/Services/NotificationServicing.swift:5-30`):
  - Values are `notDetermined`, `denied`, `provisional` (new in 4.1.0; quiet delivery, no banner or sound), `authorized` (ephemeral maps here).
  - `deliversNotifications` is true for `authorized` and `provisional`.
  - On master, provisional was folded into `.authorized`.
- **`NotificationPrompt`** (`App/NotificationPromptCoordinator.swift:6-21`): `{id, kind: .permission | .consent, source: .prompt | .reask, permission snapshot}`.
  - `.permission` starts on step 1/2 and continues to step 2/2.
  - `.consent` shows step 2/2 alone.
- **`NotificationPromptSource`** (`Core/Models/NotificationConsent.swift:4-11`) has three values: `prompt`, `reask`, `settings`.
  - It is the analytics `source` parameter.
  - `settings` is only ever used by the Account screen.
- **`NotificationConsentSource`** (`NotificationConsent.swift:15-18`) has only `prompt` and `settings`.
  - This is the Firestore `source` field.
  - A re-ask is written as `prompt` ("A re-ask is a prompt too").
- **`NotificationConsent.currentVersion = 1`** (`NotificationConsent.swift:28`). The rules accept 1…99.

#### 1.2 Triggers

All three triggers live in `NotificationPromptCoordinator`.

**A. Silent provisional authorization: `prepare()`** (`:62-65`)
- If the OS status is `notDetermined`, it calls `requestProvisionalAuthorization()`. No UI is shown.
- The live service requests `[.alert, .badge, .sound, .provisional]` and calls `registerForRemoteNotifications()` on grant (`SystemNotificationService.swift:12-30`).
- It does nothing for any other status.
- It is called from `HomeScreen`'s `.task` (`Features/Home/HomeView.swift:103`), right before `offerNotificationsIfNeeded()`.
- Home only exists after sign-in and a complete profile, because `ProfileGate` only shows content in the `.present` state.
- So "first launch after sign-in" really means the first time Home appears.
- It runs even while the onboarding tour is on screen.
- It re-runs on every Home appearance (`.task`), but it is a no-op once the status is decided.

**B. First offer: `offerFirstRunIfNeeded()`** (`:70-90`)
- Callers:
  - `HomeView.swift:104`, after `prepare()`.
  - `HomeView.swift:108`, when `onboarding.isPresenting` flips to false.
- The wrapper `offerNotificationsIfNeeded()` (`HomeView.swift:237`) returns early while the onboarding tour is presenting.
- Steps:
  1. Guard `isFree` (`pending == nil && presented == nil`).
  2. Read the OS permission.
  3. **Legacy back-fill** (`:75-77`):
     - If `history.hasOfferedPriming` and `permission != .authorized`, call `history.recordPermissionDeclined()`.
     - "An earlier build offered the permission without recording a 'no'."
     - This covers `denied`, `provisional` and `notDetermined`.
     - It only records the first decline, with the current clock time. So for a 4.0.0 user the 30-day clock starts at their first 4.1.0 Home appearance.
  4. Switch on permission:
     - `notDetermined` or `provisional`: if `!history.hasOfferedPriming` and still free, queue `pending = Prompt(.permission, .prompt, permission)`.
     - `authorized` (consent catch-up): all of these must hold:
       - `!history.hasOfferedConsent`
       - `await settings.waitUntilLoaded()` returns true (default timeout 5 s)
       - `!settings.hasAnswered`
       - still free
       - If so, queue `Prompt(.consent, .prompt, .authorized)`.
       - This does not check `hasOfferedPriming`.
       - If the consent record is loading, times out or is unreadable, nothing is queued and no flag is set, so it is retried on the next Home appearance.
     - `denied`: nothing is offered.

**C. Contextual re-ask of the permission: `examWasSubmitted()`** (`:94-99`)
- Called from `AddExamView.onSubmitted` (`Features/AddExam/AddExamView.swift:22-26`), after `dismiss()` and in a `Task`.
- `onSubmitted` fires only after `submissionService.submit` succeeds (`AddExamViewModel.swift:171-179`).
- Conditions:
  - `isFree`
  - `history.shouldReaskPermission()`
  - the OS permission is not `authorized` (`notDetermined`, `provisional` or `denied` all qualify)
  - still free
- Result: `pending = Prompt(.permission, .reask, permission)`.
- There is no check for the first submission. It fires after the next successful submission once the 30-day gate is open.

**D. Contextual re-ask of the consent: `inboxConsentReask()`** (`:104-108`)
- Called by `ConsentReaskPresenter` (`Features/Notifications/NotificationPromptPresenter.swift:43-61`), attached via `.offersMarketingConsentReask()` in `InboxView.swift:56`.
- It runs as a `.task` each time the inbox appears and does the following:
  - Sleeps 700 ms.
  - Aborts if the task was cancelled, if a prompt is already being shown by the presenter, or if `notificationRouter.highlightedId != nil` (the user tapped a push, so don't disturb reading).
  - Calls `inboxConsentReask()`.
- `inboxConsentReask()` returns a prompt only if all hold:
  - `isFree`
  - `history.shouldReaskConsent()`
  - `settings.waitUntilLoaded(timeout: 2 s)` is true
  - `!settings.marketingEnabled`
  - still free
- It returns `Prompt(.consent, .reask, currentPermission)`.
- It does not check OS permission. The consent re-ask can appear even if OS notifications are off or quiet.
- The sheet is presented by the inbox itself, over the inbox, because SwiftUI cannot present a second sheet from `RootView`.

#### 1.3 Presenting and ordering

- `RootView.archive` applies `.presentsNotificationPrompts()` (`RootView.swift:151`). It is `.sheet(item: $prompts.presented)` plus `.task(id: prompts.pending) { presentWhenFree() }` (`NotificationPromptPresenter.swift:17-41`).
- `presentWhenFree` polls every 400 ms while `ModalPresentation.isShowing`, then calls `present()`.
- `ModalPresentation.isShowing` (`Core/Utilities/ModalPresentation.swift`) is true when the key window's root VC has any `presentedViewController`. It covers:
  - sheets: Account, About, AddExam, Inbox, paywall and unlock sheets
  - the onboarding `fullScreenCover`
  - alerts, including the optional-update alert
- The queue is a single slot. A newer `pending` replaces a waiting one (the task is keyed on it).

Ordering versus other gates:
1. **Required force update.** `ForceUpdateView` replaces the whole UI (`RootView.swift:22-28`), so Home does not exist and nothing is offered.
2. **Sign-in, then `ProfileGate`.**
   - The device-registration and consent-listener tasks start as soon as the user is signed in, even while the profile is loading, absent or failed (`RootView.swift:270-273`).
   - The permission flow cannot start until the profile is `.present` and Home appears.
3. **Home appears.** `prepare()` runs silently. On a fresh install the onboarding `fullScreenCover` (per-device flag `onboarding.tour.v1`) shows first, and the first offer is deferred until the tour is skipped or finished.
4. **Optional update alert / any sheet.** The queued prompt waits.
5. **Tapped push preempts the flow.**
   - `showPendingNotification` (`HomeView.swift:247-262`) sets `isShowingAbout = false` and calls `notificationPrompts.dismiss()`. That clears the queued and presented prompt, and the unanswered page counts as a "no" (see 1.5).
   - Other modals (AddExam, Account, paywall, unlock) are never force-closed. The inbox waits for them (polls every 500 ms).
6. **Sign-out.** `RootView.swift:52-65` calls `notificationSettings.reset()` and `notificationPrompts.dismiss()`.

#### 1.4 Step 1/2: `NotificationPermissionFlowModel` page `.permission`

- `start()` (`NotificationPermissionFlowModel.swift:59-73`) runs when the page appears (`.onAppear`) and only once.
  - For a `.reask` source it calls `history.markPermissionReaskShown()`.
  - Otherwise it calls `markPrimingOffered()`.
  - It tracks `notification_priming_shown(source)`.
  - So a prompt that never reached the screen is not used up (`queuedReaskNotUsedUp` test).
- **"Bildirimleri Aç"** → `enableNotifications()` (`:79-97`):
  - Ignored if not on the permission page or already working.
  - Sets `isWorking`, tracks `notification_priming_accepted(source)` before the system prompt, then awaits `requestAuthorization()` with `[.alert, .badge, .sound]`.
  - Re-reads the permission.
  - If `!permission.deliversNotifications` (refused → `denied`): `history.recordPermissionDeclined()` and finish. No step 2/2.
  - Otherwise, if `settings.hasAnswered` (answered on another device): finish, no consent page.
  - Otherwise `page = .consent` and `consentDidAppear()`.
  - Surprise: the check is `deliversNotifications`, so a result of `provisional` (never fully granted) also leads to step 2/2.
  - Surprise: `hasAnswered` is read without waiting for the record to load.
- **"Ayarlar'ı Aç"** (shown when `needsSettingsApp`, i.e. `permission == .denied`) → `settingsWereOpened()` (`:100-103`):
  - Tracks `notification_priming_accepted(source)` and finishes. It is not recorded as a "no".
  - The view opens `UIApplication.openNotificationSettingsURLString` (`NotificationPermissionFlowView.swift:61-66`).
- **"Şimdi Değil"** → `declineNotifications()` (`:106-111`): tracks `notification_priming_declined(source)`, `history.recordPermissionDeclined()`, finish. No system prompt is used.
- **Swipe-down or any other close of the sheet** → `onDisappear` → `sheetDidClose()` (`:138-145`):
  - If not already resolved, it records `permissionDeclined` for the permission page.
  - It tracks no analytics event and writes no consent.
- Re-ask variant:
  - The title changes.
  - If the OS status is `denied`, the primary button becomes "Ayarlar'ı Aç" and a footnote appears. The first-ever prompt is never offered when `denied`, so this only occurs on a re-ask.

#### 1.5 Step 2/2: page `.consent`

- `consentDidAppear()` (`:149-157`):
  - Always calls `history.markConsentOffered()`.
  - Calls `markConsentReaskShown()` only if `prompt.kind == .consent && source == .reask`. The consent page that follows a (re-asked) permission step is its first offer, not its re-ask. This is tested.
  - Tracks `marketing_consent_shown(source: prompt.source)`.
- **"Evet, fırsatları bildir"** or **"Hayır, teşekkürler"** → `answerConsent(Bool)` (`:117-133`):
  - Ignored if not on the consent page or already working.
  - Calls `settings.setMarketing(marketing, source: .prompt)`. Both answers write the record.
  - On failure: `saveFailed = true`, the page stays open, no analytics, and the user can retry or close.
  - On success: tracks `marketing_consent_accepted` or `marketing_consent_declined` with `source = prompt.source` (`prompt` or `reask`). For "no" it also calls `history.recordConsentDeclined()`. Then it finishes.
- **Closing the consent page unanswered** (push preemption, sign-out dismiss, swipe after a failed save) → `sheetDidClose()` records `consentDeclined`. No consent is written ("not answering never opts anyone in"). It fires no analytics.
- A "yes" arms no re-ask.

#### 1.6 `NotificationPromptHistory` (persistence)

File: `Core/Services/NotificationPromptHistory.swift`. It is a value type over `UserDefaults` with an injected clock.

It is **per device, not per user**: it is not cleared on sign-out, so a second user on the same device inherits the flags.

| Key | Type / value |
|---|---|
| `notifications.primingOffered.v1` | Bool. Keeps its original key from the pre-4.1 priming screen, so those devices don't see step 1 again. |
| `notifications.permissionDeclinedAt.v1` | Double, `timeIntervalSince1970`. Only the first "no" is stored (`recordFirst`: no overwrite if the key exists, `:88-91`). |
| `notifications.permissionReaskShown.v1` | Bool |
| `notifications.consentOffered.v1` | Bool |
| `notifications.consentDeclinedAt.v1` | Double, first "no" only |
| `notifications.consentReaskShown.v1` | Bool |

- `reaskDelay = 30 * 24 * 60 * 60` seconds (`:17`).
- `isReaskDue` (`:93-97`) is true when the "shown" flag is false, a decline timestamp exists, and `now - declinedAt >= 30 days`.
- Once due, it stays due until shown.
- A later "no" cannot postpone it, and "no" to the re-ask arms nothing.
- A re-ask is never due without a recorded "no". Offering alone does not arm it.

Summary of the rules:
- The first offer of each step happens once per device.
- After a "no" the step is offered exactly once more, no sooner than 30 days after the first "no". The permission goes after a successful submission and the consent when the inbox opens.
- After that, only Account › Bildirim Ayarları.

---

### B.2 Screens

#### 2.1 Shared layout: `NotificationPromptLayout` (`Features/Notifications/NotificationPrimingView.swift:69-125`)

- Layout:
  - A `ScrollView` holds the content. The content is a `VStack(spacing: 20)` with:
    - an `IconBadge`, size `min(@ScaledMetric(64, relativeTo: .largeTitle), 88)`
    - the title, `.title2.bold()`, centered, `fixedSize` vertically, with the header accessibility trait
    - the message
  - The content is max width 440, padding horizontal 24, top 32, bottom 16.
  - The buttons are pinned with `.safeAreaInset(edge: .bottom)`, in a `VStack(spacing: 12)`, max width 440, padding horizontal 24 and vertical 16, background `.bar`. They stay in reach at large text sizes.
  - The page background is `systemGroupedBackground`.
- **Sizing fix (commit `5fa1d3c`):**
  - Before, both pages used `[.medium, .large]`, and on a real device the last message lines hid behind the buttons: "Önemli duyurular" and "İstediğin an Hesap'tan kapat". The latter is a term the user must see before opting in.
  - Now the message height (the scroll content, via a GeometryReader `PreferenceKey`) and the actions height are measured.
  - The detent is `.height(messageHeight + actionsHeight + 34)`. 34 is the `homeIndicatorAllowance`.
  - Until both are measured, the detent is `[.medium]`.
  - When that is taller than the screen (largest text sizes), the system caps it at full height and the message scrolls.
  - Android equivalent: every line of the message must be visible without scrolling at default font scale. The sheet height follows content, and the message scrolls only at large font scales.
- Accessibility id on the container: `notifications.priming` or `notifications.consent`, with children contained.

`NotificationBenefitRow`:
- `HStack(spacing: 12)`, icon width 28 with `.tint`, hidden from accessibility.
- Text `.body`, `fixedSize` vertical.
- The row's elements are combined into one accessibility element.

`NotificationPrimaryButton`:
- Full-width `.borderedProminent`, large, `.headline`.
- **Black** text on the brand orange, because white is below 3:1 contrast.
- Shows a black-tinted `ProgressView` and is disabled while `isWorking`.

#### 2.2 Step 1/2: `NotificationPrimingView` (id `notifications.priming`)

- Icon: SF Symbol `bell.badge.fill`.
- Title (`:21`):
  - First offer: **"Hiçbir şeyi kaçırma"**
  - Re-ask (`source == .reask`): **"Onaylanınca haber verelim mi?"**
- Benefit rows (`:25-30`):
  - `books.vertical.fill`: "Bölümüne yeni sınav gelince ilk sen haberdar ol"
  - `bitcoinsign.circle.fill`: "Gönderdiğin sınav onaylanınca coin'in yüklendiğini gör"
  - `megaphone.fill`: "Önemli duyurular"
- If `needsSettingsApp` (`:32`), a footnote in secondary colour: "Bildirimler şu an kapalı. Ayarlar'dan açabilirsin." (id `notifications.priming.deniedNote`)
- Buttons:
  - Primary, normal: "Bildirimleri Aç" (id `notifications.priming.enable`, with spinner while working)
  - Primary, when `denied`: "Ayarlar'ı Aç" (id `notifications.priming.openSettings`)
  - Secondary plain button: "Şimdi Değil" (id `notifications.priming.dismiss`), disabled while working
- **Dismissal:**
  - Swipe-down is allowed and the drag indicator is visible.
  - Swipe-down counts as a "no", with no analytics event (see 1.4).
  - The `interactiveDismissDisabled` modifier applies only to the consent page.
- There is an `.animation(.smooth, value: model.page)` transition between the pages inside the same sheet.

#### 2.3 Step 2/2: `NotificationConsentView` (id `notifications.consent`)

- Icon: `sparkles`.
- Title: **"Öğrenci fırsatları"**
- Body (`.body`, `fixedSize`): "Kampüsüne yakın indirimler, öğrenciye özel kampanyalar ve Premium fırsatları."
- Benefit rows:
  - `moon.zzz.fill`: "Gece 22:00–09:00 arası asla." (en dash)
  - `gearshape.fill`: "İstediğin an Hesap'dan kapat."
- On `saveFailed`, a red footnote Label with `exclamationmark.circle.fill`: "Tercihin kaydedilemedi. Bağlantını kontrol edip tekrar dene." (id `notifications.consent.error`)
- Buttons:
  - "Evet, fırsatları bildir" (id `notifications.consent.accept`), the same orange primary button with spinner.
  - "Hayır, teşekkürler" (id `notifications.consent.decline`), `.bordered` and large, `.headline`, full width.
  - Both are disabled while working.
  - **Neither is preselected, and both are equally reachable.** Both write the consent record.
- **Dismissal** (`NotificationPermissionFlowView.swift:50-51`):
  - `interactiveDismissDisabled(page == .consent && !saveFailed)` and `presentationDragIndicator(.hidden)` in the same condition.
  - The consent page cannot be swiped away. The user answers.
  - After a failed save, the sheet may be closed (drag indicator visible).
  - The `NotificationPermissionUITests` test `testConsentPageCannotBeSwipedAway` covers this.
  - Closing counts as a "no" for the schedule only.

---

### B.3 Consent persistence

- **Doc:** `users/{uid}/settings/notifications` in the named `vifi` Firestore.
- **Write** (`FirebaseNotificationSettingsService.swift:37-59`):
  - Uses `setData(...)`, a full overwrite with no merge, of exactly these fields:
    - `marketing`: Bool
    - `source`: `"prompt"` or `"settings"`
    - `version`: Int `1` (`NotificationConsent.currentVersion`)
    - `updatedAt`: `FieldValue.serverTimestamp()`
  - Rules (`firebase/firestore.rules`):
    - `keys().hasOnly` and `hasAll` for those four keys.
    - `marketing is bool`, `source in ['prompt','settings']`, `version` int in 1…99, `updatedAt == request.time`.
    - Create and update: owner only. **Delete: never**.
  - Client code must use a server timestamp. A client date is rejected.
  - Writes happen on every answer, "no" included. Both answers write, so the record shows when and where consent was given or declined.
- **When it is written:**
  - Step 2/2 "Evet" or "Hayır" (source `prompt`, also on a re-ask).
  - Account toggle changes (source `settings`).
  - There is no write on swipe-away or close, so "not answering never opts anyone in".
- **Read** (`:18-35`):
  - A live snapshot listener on the doc, wrapped as `AsyncStream<NotificationConsentFeed>`, buffering newest 1.
  - It uses `snapshot.data(with: .estimate)`, so a pending local write reads with its local values and the toggle follows the tap.
  - **No `includeMetadataChanges`.** The inbox read marker does use it, but this listener does not.
  - Parsing: `parse(data)` returns a consent only if `data["marketing"] as? Bool` exists. A missing doc, an empty doc, or a non-boolean `marketing` means **no answer** (`nil`).
  - **Default when missing:** `marketingEnabled = consent?.marketing ?? false`, so campaigns are off.
  - On a listener error it logs, emits `.failure(.unavailable)` once, and finishes the stream. There is no automatic restart.
- **Store: `NotificationSettingsStore`** (`Core/Services/NotificationSettingsStore.swift`):
  - States: `availability: loading | ready | unavailable`, plus `consent: NotificationConsent?`.
  - It keeps three states apart and does not guess one from another: not loaded, never answered (`consent == nil`), and the user's answer.
  - `hasAnswered = consent != nil`, `isLoaded = availability == .ready`.
  - **Lifecycle:** `observe(uid:)` is started by a `.task(id: user.id)` in `ProfileGate` (`RootView.swift:273`), so it starts at sign-in, before the profile is complete.
  - **User switch:** observing a different uid clears the previous consent and sets `availability = .loading`.
  - **Stale values:** values for a user who signed out are ignored (`guard userId == uid`). This holds for both `.success` and `.failure` (tests `staleValueAfterResetIsIgnored`, `staleFailureAfterResetIsIgnored`).
  - **Sign-out:** `reset()` clears consent, sets loading and `userId = nil`, so the next user never inherits it. `setMarketing` is a no-op that returns false when there is no user.
  - **`waitUntilLoaded(timeout:)`:** polls every 25 ms until the record is no longer loading or the timeout (default 5 s) expires, and returns `isLoaded`.
  - **`setMarketing(enabled, source)`** (`:88-128`):
    - It is optimistic: `consent` is set to the new value at once.
    - It races the write against a **4 s** timer (`saveTimeout`).
    - If the write throws, the value is reverted to the previous one and it returns false. This covers a rules rejection and a hard failure.
    - If the 4 s pass without acknowledgement (offline), the write counts as saved: it returns true and the value stays shown, because Firestore has queued the write and the local cache already has it (logs "stays queued"). Waiting longer would only hang the UI.
    - On cancellation the write is cancelled and it throws.
- **UI error handling:** the consent page shows "Tercihin kaydedilemedi. Bağlantını kontrol edip tekrar dene." and stays open for retry. The Account toggle reverts and shows the same message (section 4).
- **"No reward" and "default off":**
  - Consent is **off by default** and **cannot be skipped**: no pre-ticked box, and swipe-away is disabled.
  - **No coin or other reward is given for consent** (Apple 5.1.1 would treat that as manipulation; see `NOTIFICATIONS.md §1.1` item 6 and `CLAUDE.md` "Bildirimler").
  - The server filters campaigns (`promo`) on `settings/notifications.marketing == true`, so an unanswered user sees none, in the inbox too.
  - `promo` never goes through the topic or `announcements` path.
  - Quiet hours 22:00–09:00 Europe/Istanbul apply to promo (server side).
- **Server cleanup:** `onAccountDeleted` (`firebase/functions/accountCleanup.js`) recursively deletes `users/{uid}`, including `devices`, `inbox` and `settings`. The client does not delete the consent doc (the rules forbid it), and it persists across sign-outs and is restored on the next sign-in.

---

### B.4 Account › Bildirim Ayarları

**Entry row** (`Features/Account/NotificationSettingsView.swift:20-44`):
- A `Section` of its own, containing a `NavigationLink`.
- Layout: `IconBadge("bell.badge.fill")`, title "Bildirim Ayarları" (`.headline`), subtitle "Bildirim izni ve kampanya tercihin" (`.subheadline`, secondary).
- Disabled while the Account `viewModel.isWorking` (deletion in progress).
- Id `account.notificationSettings`.
- Position in the Account list (`AccountView.swift:67-80`): profile, premium, coins (only if not subscribed), moderation (admin only; it also gained a "Bildirim Gönder" row, a different feature), **Bildirim Ayarları**, phone, sign-out, delete.

**Screen:**
- Inline navigation title "Bildirim Ayarları".
- List id `notificationSettings.list`.
- Screen-view analytics `NotificationSettings` on appear.
- Permission refreshed on `.task` and whenever `scenePhase` becomes `.active` (the user may come back from the Settings app).

**Section 1, permission** (`NotificationSettingsView.swift:89-122`, `NotificationSettingsViewModel.swift`):
- Row: `IconBadge("bell.badge.fill")`, "Bildirim izni" (`.headline`), trailing secondary status text. The row is one combined accessibility element, id `notificationSettings.permission`.
- Status text:
  - `authorized` → "Açık"
  - `provisional` → "Sessiz"
  - `denied` → "Kapalı"
  - `notDetermined` → "Sorulmadı"
  - not yet read (nil) → "Yükleniyor…"
- Footer note under the permission section:
  - `provisional`: "Bildirimler sessizce Bildirim Merkezi'ne düşüyor. Anında haberdar olmak için bildirimleri aç."
  - `denied`: "Bildirimler kapalı. Açmak için Ayarlar'a git."
  - `notDetermined`: "Bildirim izni henüz verilmedi."
  - `authorized` or nil: none
- Buttons:
  - `canRequestPermission` (`notDetermined` or `provisional`): **"Bildirimleri Aç"** (id `notificationSettings.enable`).
    - It calls `requestAuthorization()`, then re-reads the status.
    - It is disabled while the system prompt is up.
    - The call is guarded against double taps.
    - **It fires no analytics events and does not touch `NotificationPromptHistory`.**
  - `needsSettingsApp` (`denied`): **"Ayarlar'ı Aç"** (id `notificationSettings.openSettings`), which opens the app's notification settings.
  - Nothing is offered until the first read.

**Section 2, campaign toggle** (`:124-145`):
- Toggle label: "Kampanya ve fırsat bildirimleri" (id `notificationSettings.marketing`).
- The switch shows `store.marketingEnabled` live. It is disabled when `!isConsentAvailable` (`isLoaded` is false) or while `isSavingConsent`.
- Footer text (exact; the Swift `\` line continuations mean one paragraph): "Kampüsüne yakın indirimler, öğrenciye özel kampanyalar ve Premium fırsatları. Gece 22:00–09:00 arası bildirim göndermeyiz. Kapalıyken kampanyaları ne bildirim olarak ne de Bildirimler sayfasında görürsün."
- If the record is unavailable, a red line is added to the footer: "Tercihin şu an okunamıyor. Bağlantını kontrol edip bu sayfayı tekrar aç." (id `notificationSettings.unavailable`).
  - Quirk: the store's observation is not restarted by reopening this page. It only restarts on sign-in or relaunch.
  - The `NotificationSettingsError` description is "Bildirim tercihin şu an okunamıyor. Bağlantını kontrol edip tekrar dene." but it is never shown in the UI.
- Toggling (`setMarketing`):
  - Ignored unless loaded and not already saving.
  - Clears the error first.
  - Calls `settings.setMarketing(enabled, source: .settings)`.
  - On success it tracks `marketing_consent_accepted` or `marketing_consent_declined` with `source: settings`.
  - On failure the switch snaps back (store revert) and an error section appears: "Tercihin kaydedilemedi. Bağlantını kontrol edip tekrar dene." (id `notificationSettings.error`, red with `exclamationmark.circle.fill`). It is cleared on the next toggle attempt.
  - **It does not call `history.recordConsentDeclined()`**, so turning campaigns off in Account never arms the inbox re-ask.
  - It fires no `marketing_consent_shown`.

**Other Account changes:**
- Sign-out and delete cleanup (see section 5).
- `AccountViewModel.signOut()` is now `async`, with an `isSigningOut` double-tap guard in addition to `isWorking`. It awaits `beforeSignOut()` before `auth.signOut()`, with no spinner during the wait (up to ~3 s).

---

### B.5 Device (FCM token) registration

**Document:** `users/{uid}/devices/{installId}` (`Core/Services/FirebaseDeviceRegistrar.swift`).

- `setData(...)` is a full overwrite of exactly:
  - `token`: String, the FCM token
  - `platform`: `"ios"` (rules also accept `"android"`)
  - `appVersion`: `Bundle shortVersionString` (for example "4.1.0"), clipped to 32 characters (`prefix(32)`)
  - `updatedAt`: `FieldValue.serverTimestamp()`
- Rules: all four keys are required and no others are allowed. `token` is a string of 1…4096 characters. `appVersion` is at most 32 characters. `updatedAt == request.time`. The doc id must match `^[A-Za-z0-9_-]{1,128}$`. The owner can read and delete.
- A server trigger (`onDeviceWritten`, `firebase/functions/notifications/devices.js`) deletes docs holding the same token under any other uid, but only when the token is new or changed on that doc. This covers a different user signing in on the same device and a failed sign-out delete.

**Install id** (`DeviceInstallationID`, `:56-76`):
- `UUID().uuidString.lowercased()`, created on first use, and stored in `UserDefaults` under `notifications.installationId.v1`.
- It is regenerated if the stored value does not match the rule pattern.
- It is stable for the life of the install: a token refresh updates the same document. A fresh install gets a new id, and the old document dies with its token.
- `NOTIFICATIONS.md` says "Firebase Installation ID", but the shipped code uses this per-install UUID. A UUID or a Firebase Installation ID both satisfy the regex.

**Store: `DeviceRegistrationStore`** (`Core/Services/DeviceRegistrationStore.swift`):
- Inputs:
  - `tokenDidChange(token?)`: called by `AppDelegate.messaging(_:didReceiveRegistrationToken:)` (`App/AppDelegate.swift:87-94`).
    - It trims whitespace. A blank or nil token is stored as nil and not registered.
    - It fires at launch and on every refresh.
  - `userDidSignIn(uid:)`: called by the `ProfileGate` task `.task(id: user.id)` (`RootView.swift:271`). That is immediately on sign-in, **not** gated on a complete profile.
  - `userDidSignOut()`: in-memory reset only (`RootView.swift:59`).
  - `unregister(uid:) async`.
- **Rule:** a write needs both a token and a signed-in user. Whichever arrives last triggers it.
- **`registerIfNeeded()`** (`:107-127`):
  - It skips if `(token, userId)` equals the in-memory acknowledged `registered` or the `inFlight` pair.
  - On success it sets `registered`.
  - On failure it logs and clears `inFlight`. There is no surfaced error and **no timer-based retry**. A retry only comes from the next trigger: a token refresh, the next `userDidSignIn` (relaunch or re-sign-in), or the next launch.
- `registered` and `inFlight` are **in-memory only**, so every cold start with a token and user rewrites the doc. That refreshes `updatedAt` and `appVersion`, which is how an app update is picked up.
  - There is no persistent dedupe and no explicit app-version comparison.
- A different uid signing in resets `registered` and `inFlight`.
- **Registration is independent of the OS notification permission and of campaign consent.**
  - `registerForRemoteNotifications()` is called unconditionally at launch (`AppDelegate.swift:41`, silent pushes for phone auth need no permission), so the FCM token usually exists before any permission is granted.
  - Consent is enforced server-side per uid at delivery time.
- **Unregister** (`:67-103`):
  - Calls `userDidSignOut()` first, so a token refresh while the delete is in flight cannot re-register.
  - It races `registrar.unregister(uid:)` (a Firestore `delete()` of the install-id doc) against a **3 s** timer (`unregisterTimeout = .seconds(3)`).
  - It never throws. A failure or timeout is logged and the caller continues. A flaky network never blocks sign-out.
  - A device left behind is cleaned up by the server when its token stops working or the same token is registered under another account.
  - Callers:
    - `AccountView.swift:26-27`: `beforeSignOut`, run before `auth.signOut()`.
    - `AccountView.swift:15-18`: inside `deleteUserData`, **before** `profileService.deleteProfile` and before `auth.deleteAccount()`.
    - `ProfileCompletionView.swift:20-21, 71-77`: the "Çıkış Yap" button on the profile-completion screen also unregisters first.
  - Edge cases:
    - If deletion then hits `requiresRecentLogin` and the user cancels the re-verification, the device doc is already deleted and `userId` is nil. It will not re-register until the next `userDidSignIn` (relaunch).
    - Non-user-initiated sign-outs (expired session, remote revoke) do not delete the doc. They rely on the server cleanup above.
- **Topic `all` (4.1.0 still subscribes):**
  - `messaging.subscribe(toTopic: "all")` runs on every token delivery (`AppDelegate.swift:89-93`; `PushTopic.broadcast = "all"`, `:11-13`).
  - It is idempotent and **not** gated on sign-in or permission.
  - Reason (the comment at `:8-10`): the broadcast "general" delivery sends one push to the `all` topic plus a public `announcements/{id}` copy. This reaches 4.0.0 installs that never register tokens.
  - Per-person fan-out (personal, system and segment notifications) goes through the registered `devices` tokens instead.
  - `promo` never uses the topic.
  - **An Android client must keep subscribing to `all`, or it will miss broadcast pushes.**
- Mock: `MockDeviceRegistrar` keeps tokens in memory (`Preview Content/MockNotificationServices.swift:186-196`).

---

### B.6 Analytics

Event names (`Core/Services/FirebaseAnalyticsTracker.swift`): `notification_priming_shown`, `notification_priming_accepted`, `notification_priming_declined`, `marketing_consent_shown`, `marketing_consent_accepted`, `marketing_consent_declined`.

Each carries exactly one parameter, `source` ∈ {`prompt`, `reask`, `settings`} (the raw value of `NotificationPromptSource`).

| Event | Fired when |
|---|---|
| `notification_priming_shown` | Step 1/2 page appears (`start()`), once per flow. |
| `notification_priming_accepted` | Tapping "Bildirimleri Aç" (before the system prompt; fires regardless of the OS result) or tapping "Ayarlar'ı Aç". |
| `notification_priming_declined` | "Şimdi Değil" only. Not on swipe-away. |
| `marketing_consent_shown` | Step 2/2 first appears. After step 1 it uses the same `prompt.source` as the flow. For a stand-alone consent it carries the prompt's source. Not on retry after a failed save. |
| `marketing_consent_accepted`, `marketing_consent_declined` | After a successful write, from the flow (source `prompt` or `reask`) or from the Account toggle (`settings`). Not fired when the write fails, and not fired on swipe-away. |

- The priming events never use source `settings`. Account's "Bildirimleri Aç" fires nothing.

---

### B.7 Launch arguments (debug, with `-ViFiMockData`)

Defined in `App/AppEnvironment.swift` (`LaunchArgument`) and implemented in `Preview Content/MockServices.swift`.

Mock permission precedence (`mockNotificationPermission`): Prompt > Denied > Reask > authorized (default).

| Argument | Mock permission | Prompt history | Consent record | Effect |
|---|---|---|---|---|
| `-ViFiNotificationsPrompt` | `.notDetermined` | **Blank** (no seeding), as a fresh install | none | `prepare()` makes it provisional, then step 1/2 appears after onboarding. "Bildirimleri Aç" → step 2/2. |
| `-ViFiNotificationsDenied` | `.denied` | Seeded: priming offered, consent offered | none | No first-run sheet. Settings shows "Kapalı" and "Ayarlar'ı Aç". |
| `-ViFiNotificationsReask` | `.provisional` | Seeded as offered, plus `permissionDeclinedAt` 31 days ago | none | The next successful exam submission shows the re-ask (title "Onaylanınca haber verelim mi?"). |
| `-ViFiConsentReask` | `.authorized` | Seeded as offered, plus `consentDeclinedAt` 31 days ago | `marketing: false` | Opening the inbox offers step 2/2 as a re-ask. |
| (none) | `.authorized` | Seeded: priming offered, consent offered | none | No sheets. The Account toggle starts off. |

- `seedNotificationPromptHistory` uses a clock fixed at now minus 31 days so the declines are "due".
- `MockNotificationService` follows the system rules:
  - Provisional only from `notDetermined`.
  - A full request is granted unless the status is `denied`. The system never asks twice.
- `StubNotificationSettingsService` keeps the consent in memory, pushes writes to all open streams, and ignores `source`.
- The mock defaults come from `UserDefaults.mockSuite`, which is wiped at each launch (`MockServices.swift:126`).
- `-ViFiOpenNotification <id>` (push-tap simulation) is covered by other areas. `-ViFiShowOnboarding` is used in the permission UI tests to show the tour first.

---

### B.8 Privacy manifest, Info.plist, entitlements

Commit `c65545b`. Data types are all `Linked: true`, `Tracking: false`.

**`PrivacyInfo.xcprivacy` (app) changes:**
- Developer's Advertising or Marketing was added as a purpose to **User ID**, **Device ID** and **Other Data Types**. Reasons from the comments:
  - Campaign (promo) notifications are delivered to the inbox of each consenting user id. This is marketing sent directly to our own users, never shared with third parties.
  - Profile ids (university, faculty, department, gender) choose who receives a notification. The consent record `{marketing, source, version, updatedAt}` is kept with the account.
  - The FCM token is used to deliver notifications, campaign ones included.
- App Functionality was added to **Product Interaction**. Reason: the notification-page last-read marker (`settings/inbox`) and the notification open and action-tap statistics.
- Full declared set after the change:

| Data type | Purposes |
|---|---|
| Phone Number | AppFunctionality |
| User ID | AppFunctionality, Analytics, DeveloperAdvertising |
| Purchase History | AppFunctionality |
| Name | AppFunctionality |
| Other Data Types (profile segments, consent) | AppFunctionality, Analytics, DeveloperAdvertising |
| Product Interaction | Analytics, AppFunctionality |
| Device ID (Analytics instance id, FCM token) | Analytics, AppFunctionality, DeveloperAdvertising |
| Other Diagnostic Data (sign-in failure diagnostics) | AppFunctionality |

- Required-reason API: `UserDefaults` now declares `CA92.1` and `1C8F.1`. `1C8F.1` is for the badge count shared with the Notification Service Extension through the App Group.
- The notification extension has its own manifest (`ViFiNotificationService/PrivacyInfo.xcprivacy`) with no collected data and `1C8F.1`. App Store Connect rejects uploads (ITMS-91053) where a bundle uses a required-reason API without a manifest.
- Device location is deliberately **not** declared. It only focuses the admin map, and is never stored or sent.
- The Google Maps SDK's Crash and Performance data are declared in the App Store label via the SDK's own manifest.
- The App Store Connect label was published (commit `1c09bef`).

**`Info.plist`:**
- No key was added for the notification permission itself (iOS needs no usage string for it).
- Added keys:
  - `GMSApiKey`
  - `LSApplicationQueriesSchemes` (`comgooglemaps`)
  - `NSLocationWhenInUseUsageDescription`, "Bildirim için konum seçerken haritayı bulunduğun yere odaklamak için konumun kullanılır. Konumun kaydedilmez ve paylaşılmaz."
- These are for the admin map location picker, not the consent flow.
- `UIBackgroundModes: remote-notification` and `aps-environment` pre-exist.

**Entitlements (`ViFi.entitlements`):**
- Added the App Group `group.com.BuyukerYazilim.ViFi2`, sharing the icon-badge counter with the Notification Service Extension (`NotificationBadgeCounter`). This is iOS-only.

**Play Data Safety (my inference, not in the iOS code):**
- If Android declares the same facts, use:
  - Device or other IDs (FCM token, installation id): app functionality, analytics, and advertising or marketing.
  - User IDs: app functionality, analytics, and advertising or marketing.
  - Phone number: app functionality or account management.
  - Name.
  - Profile segments under personal info or other.
  - App interactions (notification opened, last-read marker).
- No tracking or sharing with third parties. Consent is opt-in and revocable in-app.

---

### B.9 Test inventory (acceptance spec)

#### `NotificationPromptCoordinatorTests`
- `provisionalForNewUsers`: `prepare()` from `notDetermined` makes 1 provisional and 0 full requests, leaving `.provisional`.
- `noProvisionalOtherwise` (provisional, authorized, denied): no provisional request, status unchanged.
- `offersThePermissionStep` (notDetermined, provisional): pending is `.permission`, `.prompt`, and carries the permission.
- `notOfferedTwice`: priming already offered gives no pending offer.
- `noDuplicates`: a second offer does not replace pending. `present()` moves it to presented. While presented, no new pending.
- `noOfferWhenDenied`: nothing offered when `denied`.
- `consentCatchUp`: `authorized` plus never answered gives pending `.consent` with `.prompt`. Once `markConsentOffered` is set, nothing more.
- `noCatchUpWhenAnswered` (true, false): no consent catch-up if already answered.
- `noCatchUpWhenUnreadable`: a failing record gives no offer.
- `legacyDeclineArmsTheReask`: priming offered with `notDetermined` gives no pending but records a decline. The re-ask is not due at 0 days and is due at 31 days.
- `reaskAfterSubmission`: after a decline and 31 days, `examWasSubmitted()` queues `.permission` with `.reask`.
- `noReaskTooSoon`: at 29 days, nothing.
- `noReaskWithoutNo`: at 400 days with no decline, nothing.
- `noReaskWhenAllowed`: `authorized` gives nothing.
- `reaskWhenDenied`: the pending prompt carries `.denied`.
- `reaskOnlyOnce`: after the flow starts and declines, 365 days later gives nothing.
- `queuedReaskNotUsedUp`: dismissing a pending (unshown) re-ask leaves it due.
- `consentReaskInInbox`: after a consent decline and 31 days, `inboxConsentReask()` returns `.consent` with `.reask`.
- `noConsentReask`: nothing with no earlier "no", at 10 days, or once campaigns are on.
- `consentReaskOnlyOnce`: after `start()`, nil.
- `presentAndDismiss`: `present()` moves pending to presented, and `dismiss()` clears both.

#### `NotificationPromptHistoryTests`
- `freshDevice`: nothing offered, nothing to re-ask.
- `offeringPersists`: flags survive a new instance.
- `permissionReaskTiming`: not due at 29d23h, due at 30d, and still due after 90 more days.
- `permissionReaskOnce`: due, then used up by `markPermissionReaskShown`. A later "no" does not re-arm it.
- `firstNoWins`: a later "no" cannot postpone the re-ask.
- `noReaskWithoutNo`: offering alone never arms it.
- `consentReask`: the consent follows the same rules, independent of the permission.
- `delayIsThirtyDays`: `reaskDelay == 30 days`.

#### `NotificationPermissionFlowModelTests`
- `startRecordsAndReports`: priming offered and `notification_priming_shown(.prompt)` once, even if `start()` is called twice.
- `consentOnlyStart`: a `.consent` prompt starts on the consent page, records consent offered but not priming, and reports `marketing_consent_shown(.prompt)`.
- `enableLeadsToConsent`: one system request, status `authorized`, page `.consent`, not finished, events `[shown, accepted, consent shown]`.
- `systemRefusal`: after `denied`, finished on the permission page, no consent offered, re-ask due at 31 days.
- `skipsConsentWhenAnswered`: if answered elsewhere, finishes with no consent page and no write.
- `notNow`: finished, `notification_priming_declined` tracked, 0 system requests, re-ask due at 30 days.
- `deniedOffersSettings`: `needsSettingsApp` is true. Opening Settings plus close tracks shown and accepted only, makes no system request, and the re-ask is no longer due.
- `reaskRecorded`: starting a re-ask uses it up.
- `consentYes`: writes `{true, .prompt, uid}`, marketing is enabled, finished, `accepted(.prompt)` tracked, no re-ask.
- `consentNo`: writes false, `hasAnswered` is true, finished, `declined(.prompt)` tracked, re-ask due at 30 days.
- `consentSaveFails`: `saveFailed`, not finished, nothing reported beyond "shown". A retry after clearing the error succeeds.
- `consentAfterPermissionReask`: the consent page after a re-asked permission reports `shown(.reask)`, but the consent's own re-ask stays unused.
- `closingPermissionUnanswered`: counts as a "no" (re-ask due), no write.
- `closingConsentUnanswered`: counts as a consent "no", no write, not opted in.
- `closingAfterAnswer`: changes nothing.

#### `NotificationPermissionTests`
- Maps notDetermined, denied, provisional, authorized, and ephemeral (→ authorized).
- `deliversNotifications` is true only for authorized and provisional.

#### `NotificationSettingsStoreTests`
- `loadingAllowsNothing`
- `neverAnswered`: loaded, unanswered, off.
- `followsTheRecord`: follows live changes.
- `unreadable`: unavailable, never an answer.
- `resetForgets`: reset clears, and `setMarketing` returns false with no user.
- `anotherUser`: clears the previous answer until theirs arrives.
- `staleValueAfterResetIsIgnored`
- `staleFailureAfterResetIsIgnored`
- `waitReturnsWhenLoaded`
- `waitTimesOut`
- `writesTheAnswer`: written with source and uid, shown immediately.
- `writesFromSettings`: a settings write.
- `rejectedWriteReverts`: the previous answer is put back.
- `rejectedFirstAnswer`: leaves the user unanswered.
- `unacknowledgedWriteIsQueued`: an unacknowledged write after the timeout counts as saved and stays shown.

#### `NotificationSettingsViewModelTests`
- `permissionStatus` (parameterized): authorized shows "Açık" with no actions. provisional shows "Sessiz" with can-request. denied shows "Kapalı" with needs-settings. notDetermined shows "Sorulmadı" with can-request. A note exists except for authorized.
- `beforeFirstRead`: nothing offered.
- `enable`: after a request the status is authorized.
- `enableWhenDenied`: no request.
- `switchBeforeLoad`: unavailable and no write.
- `turnOn`: writes `{true, .settings}`, tracks `accepted(.settings)`.
- `turnOff`: the mirror, tracks `declined(.settings)`.
- `saveFails`: the switch reverts, an error appears, no analytics, and a retry succeeds and clears the error.
- `unreadableRecord`: unavailable.

#### `FirebaseNotificationSettingsServiceTests`
- `parsesConsent`: a boolean `marketing` is the consent.
- `parsesNoAnswer`: nil, `[:]`, or a non-boolean `marketing` is no answer.
- `fieldsMatchTheRules`: exactly 4 keys, source string, `version` in 1…99, `updatedAt` is a `FieldValue`.
- `writesNo`: a "no" is `marketing: false`.

#### `DeviceRegistrationStoreTests`
- `tokenThenSignIn`: a token before sign-in registers on sign-in.
- `signInThenToken`: registers on token arrival.
- `registersOnce`: the same pair registers once.
- `refreshedToken`: a refresh registers again.
- `blankToken`: blank or nil is ignored.
- `userSwitch`: another user registers the token under them.
- `nothingWhileSignedOut`: no writes after sign-out.
- `failureIsRetried`: retried at the next trigger.
- `unregisters`: removes from the user and stops further registration (`userId` becomes nil).
- `unregisterFailureIsSwallowed`: never throws.
- `unregisterTimesOut`: gives up at the timeout.

#### `FirebaseDeviceRegistrarTests`
- `documentFields`: exactly `token`, `platform`, `appVersion`, `updatedAt`.
- `clipsAppVersion`: clipped to 32 characters.
- `installationIdIsStable`
- `installationIdIsPerInstall`
- `invalidStoredIdIsReplaced`
- `validity`: regex cases such as "abc-DEF_123" valid, "" and "has space" and "slash/id" and "ünicode" invalid, 128 characters valid, 129 invalid.

#### `AccountViewModelTests` (changed) and `AccountSignOutCleanupTests` (new)
- `signOut` and `signOutFailure` are now `async` and unchanged in meaning.
- `signOutWhileDeleting`: sign-out is ignored while a deletion is in flight.
- `signOutRunsHookFirst`: the hook runs while still signed in, then `auth.signOut()`.
- `signOutIgnoresDoubleTap`: a second sign-out while the hook runs is ignored (1 hook, 1 sign-out).

#### `NotificationPermissionUITests`
- `testEnablingThenAcceptingTheConsentTurnsCampaignsOn`: step 1/2, then step 2/2, accept. Settings shows the switch "1" and "Açık".
- `testEnablingThenDecliningTheConsentKeepsCampaignsOff`: decline gives switch "0", permission "Açık".
- `testNotNowClosesThePromptAndLeavesNotificationsQuiet`: "Şimdi Değil" gives no consent. Settings shows "Sessiz", the `notificationSettings.enable` button, and the switch off.
- `testPromptWaitsForTheOnboardingTourToEnd`: no priming over the tour. After `onboarding.skip` it appears.
- `testConsentPageCannotBeSwipedAway`: swipe-down leaves the page up.
- `testCampaignSwitchTurnsOnAndOffAndKeepsItsValue`: switch persistence across leaving and reopening the screen.
- `testDeniedPermissionSendsTheUserToSettings`: "Kapalı", the open-settings button exists, the enable button does not.
- `testInboxOffersTheConsentOnceMoreAfterANo`: the consent appears in the inbox. "Hayır" keeps the inbox open. Reopening shows no repeat.
- `testAcceptingTheInboxReaskTurnsCampaignsOn`: accepting turns campaigns on (switch "1").

---

### B.10 Edge cases, "why" comments, surprises

1. **Provisional is silent and runs early.** It is granted on first Home appearance even during onboarding. It does not count as the user having been "asked". Step 1/2 is still shown, after the tour. Android has no provisional state.
2. **`.provisional` is treated like "not fully allowed".** First offer, re-ask, and Account's "Bildirimleri Aç" all treat `notDetermined` and `provisional` alike. A re-ask is skipped only for `authorized`.
3. **Step 2/2 leads on after `deliversNotifications`**, so `provisional` after the request also reaches the consent page. The spec text says "only if full permission granted", but the code accepts quiet delivery.
4. **Legacy back-fill starts the 30-day clock at the first 4.1.0 launch** for devices that saw the 4.0.0 priming but never granted full permission. It does not use the original decline date.
5. **Consent catch-up** is once per device. If the user closes it unanswered, it counts as a "no", and the one re-ask comes through the inbox after 30 days.
6. **Per-device history leaks across accounts on one device.** A second user on the same device skips the first-run flow and the consent catch-up because the flags are device-level.
7. **Unanswered always equals a "no" for scheduling** (swipe, push preemption, sign-out dismiss), but never writes the Firestore record and never fires a declined analytics event.
8. **Tapped push beats the permission sheet** (`dismiss()` in `showPendingNotification`). If the sheet was mid-flow, that page is counted as a "no".
9. **The re-ask from AddExam** may sit in the queue while the AddExam sheet closes and, if the exam was submitted from inside the inbox, until the inbox is dismissed.
10. **Consent re-ask does not check OS permission**, and does not check whether the user ever said no on this device beyond `consentDeclinedAt`. It requires `!marketingEnabled`, so if they later turned campaigns on in Account, it is skipped.
11. **Turning campaigns off in Account never arms a re-ask**, because the history only records declines from the prompt flow.
12. **The flow never waits for the consent record before step 2/2** (it only reads `hasAnswered`). The catch-up and re-ask do wait (5 s and 2 s). The consent listener has no `includeMetadataChanges`, so an offline first read with an empty cache reads as "never answered".
13. **The consent write can overwrite an earlier answer** from another device if `hasAnswered` was still false when the flow reached step 2/2.
14. **Optimistic UI:** the consent is shown immediately, reverted on a rejection, and treated as saved after 4 s without acknowledgement. A rules rejection after reconnecting reverts silently through the live listener.
15. **Device registration:** `setData` overwrites, with no merge. The write is independent of permission and consent. It is retried only on the next trigger. The in-memory dedupe means a rewrite on each cold start.
16. **Unregister is bounded at 3 s and silent.** If account deletion fails with `requiresRecentLogin`, the device doc stays deleted until relaunch.
17. **`all` topic subscription stays** and is unconditional. Do not remove it on Android without confirming server delivery changes.
18. **Black-on-orange primary button** (contrast), no preselection on consent, and the title/body strings above are verbatim, in Turkish.
19. **Android mapping hazards:**
    - POST_NOTIFICATIONS on Android 13+ has no provisional mode, and on Android 12 and below notifications are on by default.
    - iOS `denied` after one system refusal maps to "permanently denied" on Android (after two refusals, or when no rationale is shown).
    - The FCM token exists without the permission.
    - Notification channels per kind are `general|promo|system` (`NOTIFICATIONS.md §5`, covered by another area).
    - The Android `platform` value is `"android"`, accepted by the rules.
    - The server removes duplicate tokens across accounts.

**Files read in full:**
- `App/NotificationPromptCoordinator.swift`, `App/AppDelegate.swift`, `App/AppEnvironment.swift` (diff), `App/RootView.swift`
- `Core/Models/NotificationConsent.swift`, `Core/Services/Notification*` and `Device*` files, `FirebaseNotificationSettingsService.swift`, `FirebaseDeviceRegistrar.swift`, `SystemNotificationService.swift`, `FirebaseAnalyticsTracker.swift` (diff), `ServiceProtocols.swift` (diff)
- `Features/Notifications/NotificationPermissionFlow*.swift`, `NotificationPrimingView.swift`, `NotificationConsentView.swift`, `NotificationPromptPresenter.swift`
- `Features/Account/NotificationSettings*.swift`, `AccountView.swift`, `AccountViewModel.swift` (diffs)
- `Features/Profile/ProfileCompletionView.swift`, `Features/AddExam/AddExamView.swift` (diffs)
- `Preview Content/MockNotificationSettingsService.swift`, `MockServices.swift`, `MockNotificationServices.swift`
- `Resources/PrivacyInfo.xcprivacy`, `Info.plist`, `ViFi.entitlements`
- All listed test files and `ViFiUITests/NotificationPermissionUITests.swift`
- `CLAUDE.md` (sections "Bildirimler" and "Bildirim sistemi"), `NOTIFICATIONS.md §1.1` and `§3.5`, `firebase/firestore.rules` diff, `firebase/functions/notifications/devices.js`, `accountCleanup.js`, `deliver.js` (topic and fan-out parts)

The full `SubmissionFileEncoder.swift` diff is irrelevant here (it adds a `maxPixelSize` parameter for notification images only).
---

## Bölüm C — Admin composer, geçmiş, konum seçici (iOS)


Branch `v-4.1.0`. All paths are under `ViFi-iOS/ViFi/` unless noted. I only read files and ran read-only git commands. Turkish strings are quoted exactly.

### C.0 Architecture in one paragraph

Four admin callables plus two direct Firebase paths:

- Callables, region `europe-west1`: `getNotificationAudience`, `previewNotification`, `sendNotification`, `cancelNotification`. Each is guarded server-side by `request.auth.token.admin === true`, otherwise `permission-denied` "Bu işlem için yetkin yok." (`firebase/functions/notifications/admin.js:39-44`).
- Campaign images are uploaded straight to Cloud Storage.
- History is read straight from Firestore, not through a callable.

The iOS client holds no business rules the server does not also enforce. The server is the authority, and it throws Turkish messages that the client shows verbatim.

### C.1 Entry points and gating

- **Navigation path.** Home toolbar button `toolbar.account` opens `AccountView` as a sheet (`Features/Home/HomeView.swift:88`). That sheet has its own `NavigationStack`. Inside it, a `NavigationLink` goes to `NotificationComposerView`, and a trailing toolbar button "Geçmiş" (clock.arrow.circlepath, id `notificationComposer.history`) pushes `NotificationHistoryView`. The history screen has no other entry point.
- **Gate.** The row lives in `moderationSection`, shown only `if isAdmin` (`Features/Account/AccountView.swift:74`, 205-240). `isAdmin = await auth.isCurrentUserAdmin()` runs in `.task` (line 98).
  - It reads `getIDTokenResult(forcingRefresh: false).claims["admin"] as? Bool == true` (`Core/Services/FirebaseAuthService.swift:56-66`). The token is cached, so a newly granted claim needs a token refresh.
  - On any error it returns false.
- **Account row.** Title "Bildirim Gönder", subtitle "Duyuru ve kampanya gönder", icon `megaphone.fill`, id `account.notificationComposer`. It sits beside the "Moderasyon" row (`account.moderation`).
- **Security rules.** Firestore `notifications/{id}`: `allow read: if isAdmin(); allow write: if false`. Storage `notificationImages/{file}`: admin create-only (section 3 image).
- **Mock mode.** Launch args `-ViFiMockData -ViFiAdmin` (`App/AppEnvironment.swift:222`) report the user as admin.

### C.2 Models (`Core/Models/NotificationAdmin.swift`, `AppNotification.swift`)

#### NotificationKind (AppNotification.swift:8-15)

Wire values are `general`, `promo`, `system`. Admin labels are in `Features/NotificationComposer/NotificationAdminLabels.swift:8-14`:

| Kind | Label |
|---|---|
| `general` | "Duyuru" |
| `promo` | "Kampanya" |
| `system` | "Sistem" |

- The composer offers only `[.general, .promo]` (ViewModel:39). `system` is for automatic notifications only.
- History icons: `megaphone.fill`, `tag.fill`, `bell.badge.fill`.

#### NotificationDraft fields (NotificationAdmin.swift:51-64)

- `id`: lowercase UUID string, generated once per draft.
- `kind` (default `.general`), `title`, `body`.
- `action?`, `imagePath?`.
- `audience` (default `.all`), `scheduledAt?`, `expiresAt?`.
- `isTest` (default false).

#### Limits

| Limit | Value | Source |
|---|---|---|
| Title | ≤ 80 | NotificationAdmin.swift:6 |
| Body | ≤ 500 | NotificationAdmin.swift:7 |
| URL | ≤ 500 (https only) | NotificationAdmin.swift:8 |
| Location name | ≤ 120 | AppNotification.swift:19 |
| Location address | ≤ 200 | AppNotification.swift:20 |
| `placeId` | ≤ 300 (server clips) | schema.js:19 |
| `examId` | ≤ 700, no "/" | schema.js:20 |
| Image | ≤ 1 MiB = 1048576 bytes | AppNotification.swift:101 |
| Image longest side | 1600 px | AppNotification.swift:103 |

- Title and body are trimmed with `.whitespacesAndNewlines`, and counted and sent trimmed.
- iOS counts Swift `Character`s (grapheme clusters). The server counts code points (`[...title].length`, `schema.js:79,84,183`). iOS can therefore accept an emoji sequence that the server rejects. On Android, count code points (`codePointCount`) to match the server.

#### Validation order

`validationIssue(now:)` (lines 77-89) returns the first issue, in this order. Messages are at lines 35-45.

| Issue | Message |
|---|---|
| `emptyTitle` | "Başlık boş olamaz." |
| `titleTooLong` | "Başlık en fazla 80 karakter olabilir." |
| `emptyBody` | "Mesaj boş olamaz." |
| `bodyTooLong` | "Mesaj en fazla 500 karakter olabilir." |
| `invalidAudience` | "Geçerli bir hedef kitle seç." |
| `invalidLocation` | "Haritadan geçerli bir konum seç." |
| `invalidURL` | "Bağlantı https:// ile başlamalı." |
| `emptyExam` | "Bağlanacak sınavı seç." |
| `invalidImage` | "Görsel bu bildirime ait değil; görseli yeniden seçip tekrar dene." (iOS: "Görsel bu bildirime ait değil; görseli yeniden seç.") |
| `scheduleInPast` | "Gönderim zamanı geçmişte olamaz." |
| `expiryBeforeDelivery` | "Geçerlilik süresi gönderim zamanından sonra olmalı." |

The exact `invalidImage` string is "Görsel bu bildirime ait değil; görseli yeniden seç." (line 43).

Rules behind each check:

- **Audience.** Sendable only if `.all`, or a `.segment` with a non-empty `universityId` and (a faculty, or no department). A department without a faculty is invalid. `.user` and `.admins` are never sendable from the composer (lines 108-120).
- **Location.** `isValid` requires lat in -90...90, lng in -180...180, trimmed name non-empty and ≤ 120, and address length ≤ 200 (AppNotification.swift:29-34).
- **Exam.** The id must be non-empty after trimming spaces.
- **URL.** Scheme "https" (case-insensitive), non-empty host, and `absoluteString.count ≤ 500`.
- **Image path.** `imagePath`, if set, must equal `notificationImages/<draft.id>.jpg`.
- **Schedule.** `scheduledAt < now - 60 s` is `.scheduleInPast`. A one-minute grace is allowed so a time picked "now" is not rejected.
- **Expiry.** `expiresAt <= (scheduledAt ?? now)` is `.expiryBeforeDelivery`.

#### Derived value `effectiveKind` (lines 68-71)

If the action is `.location`, the kind is always `.promo`. Otherwise it is `kind`.

#### Draft payload for `sendNotification` (lines 92-106, 136-177)

The payload is built from the draft:

```
{ notificationId: <lowercase uuid>,
  kind: "general"|"promo",              // effectiveKind
  title: <trimmed>, body: <trimmed>,
  audience: { scope: "all" } |
            { scope: "segment", universityId, facultyId?, departmentId?, gender?: "female"|"male" },
  isTest: Bool,                          // always sent
  action?: ...,                          // see below
  imagePath?: "notificationImages/<id>.jpg",
  scheduledAt?: Int64 epoch ms (rounded), expiresAt?: Int64 epoch ms }
```

- `facultyId` and `departmentId` are omitted when nil or empty.
- Action shapes:
  - `{type:"location", latitude, longitude, name: <trimmed>, address?: (only if non-empty), placeId?: (only if non-empty)}`
  - `{type:"exam", examId: <trimmed>}`
  - `{type:"paywall"}`
  - `{type:"addExam"}`
  - `{type:"url", url: <absoluteString>}`

#### Result and read models

- `NotificationSendResult { notificationId, status, scheduledAt?: Date (epoch ms on the wire), recipients }`.
- `NotificationStatus` raw values: `scheduled`, `sending`, `sent`, `failed`, `canceled`.
- `NotificationPreview { recipients, pushTargets }`.
- `NotificationStats { recipients, pushTargets, pushSent, pushFailed, opened, actionTapped }`, defaulting to 0.
- `SentNotification { id, kind, title, body, audience, audienceLabel, status, isTest, isAutomatic (source == "automatic"), deferredByQuietHours, createdAt, scheduledAt, sentAt, stats }`.
- The client does not read `trigger`, `error`, `delivery`, `createdBy`, `expiresAt` or `canceledAt` for history rows.

### C.3 Composer, section by section (`Features/NotificationComposer/`)

Form order (`NotificationComposerView.swift:23-32`): Kind, İçerik, Hedef kitle, Eylem, Görsel, Zamanlama, feedback, send. Title "Bildirim Gönder" (inline). Every section is `.disabled(viewModel.isWorking)`. Screen analytics are in section 7.

#### 3.1 Kind picker (NotificationComposerSections.swift:9-31)

- Segmented "Tür" with "Duyuru" and "Kampanya". Id `notificationComposer.kind`.
- Footer when unlocked: "Kampanyalar yalnızca kampanya bildirimlerini açmış kullanıcılara gider."
- When the action is Konum, the control is locked on Kampanya, shown via `effectiveKind`, and disabled (`disabled(isWorking || isKindLocked)`).
  - The lock footer replaces the unlocked footer: "Tür Kampanya'ya kilitli: konumlu bildirimler her zaman kampanya olarak gider." (lock icon, id `notificationComposer.kind.lockNote`).
  - The lock happens as soon as Konum is chosen, even before a place is picked (ViewModel:157-174).
  - The admin's own `kind` is never overwritten. Switching to another action restores it. A write to `kindSelection` while locked is ignored.
- `resetDraft` sets `kind` back to `.general`.

#### 3.2 Title and message

- `TextField("Başlık")` with `.submitLabel(.next)`, and a multi-line `TextField("Mesaj")` with `lineLimit(3...8)`. Section header is "İçerik".
- Counter text is "12/80" and "N/500", right aligned, red when `count > limit`. Accessibility label is "\(count) / \(limit) karakter". The counter uses the trimmed length.
- Ids: `.title`, `.titleCounter`, `.body`, `.bodyCounter`.

#### 3.3 Audience

- Segmented "Kitle": "Hepsi" / "Belirli kitle" (`.audience`).
- **Data load.** Facets are loaded once per screen on `.task` via `getNotificationAudience`, called with `nil` data. Cancellation resets the guard so the load retries.
  - States: loading ("Kitle bilgisi yükleniyor…"), or failed (red label plus button "Tekrar dene", id `.audienceRetry`, calls `reloadFacets`).
- **Response shape** (`schema.js:251-271`; parser `NotificationPayloadParser.swift:72-75,177-186`):
  ```
  { total: Int,
    universities: [ { id, name, count, female, male,
        faculties: [ { id, name, count, female, male,
            departments: [ { id, name, count, female, male } ] } ] } ] }
  ```
  - `id` is the profile's canonical slug (`^[a-z0-9-]{1,140}$`). Only values that at least one registered user's profile has are listed.
  - `total` is the number of profiles with a valid `universityId`.
  - `count` includes users with no gender, so female + male can be less than count.
  - The server sorts with `localeCompare('tr')`; the client re-sorts with Turkish collation (`tr_TR`).
  - The server names a node by the most common spelling among users. The client parser accepts children under either `faculties` or `departments`.
- **Segment pickers** appear only when mode is `.segment` and facets are loaded. Each is a menu picker:
  - "Üniversite": "Seç" = none, then options "<name> · <total> kişi" (e.g. "Boğaziçi Üniversitesi · 40 kişi"). Id `.university`.
  - "Fakülte": "Tüm fakülteler". Disabled until a university is picked. Id `.faculty`.
  - "Bölüm": "Tüm bölümler". Disabled until a faculty is picked. Id `.department`.
  - "Cinsiyet": segmented "Hepsi" / "Kadın" / "Erkek". Id `.gender`.
- **Cascade rules** (ViewModel:123-139):
  - Changing the university resets faculty and department. Re-picking the same university keeps them.
  - Changing the faculty resets the department.
  - `selectDepartment` is ignored when no faculty is chosen.
- **Estimate row.** "Tahmini kitle" shows "≈ N kişi" (id `.estimate`).
  - Mode `.everyone`: `facets.total`.
  - Mode `.segment`: the most specific level picked, with the gender count (`female`, `male`, or `total` for "Hepsi"). Hidden while no university is chosen. Also hidden while facets are loading or have failed.
  - It is an estimate only. Campaigns are narrowed by consent, which only the preview knows.
- **Footers:**
  - "Önce bir üniversite seç. Listelerde yalnızca kayıtlı kullanıcısı olanlar görünür." — segment mode, no university, facets loaded.
  - "Kampanyada yalnızca bildirim onayı verenler alır; kesin sayıyı göndermeden önce göreceksin." — when `effectiveKind == .promo`.
- If mode is `.segment` with no university, the draft is invalid with `invalidAudience`. It never silently falls back to everyone (ViewModel:306-307).
- The universityId, facultyId and gender selections persist when toggling the mode back and forth.

#### 3.4 Action ("Eylem" section, header "Eylem")

- Menu picker "Buton" with options "Yok", "Premium", "Sınav Ekle", "Bağlantı", "Sınav", "Konum" (id `.action`).
- Footer: "Karta dokunulduğunda ya da butona basıldığında açılır. Bağlantı https ile başlamalı."
- Admin inputs per action:
  - **Premium / Sınav Ekle:** nothing. Sends `{type:"paywall"}` / `{type:"addExam"}`.
  - **Bağlantı:** `TextField("https://")` (URL keyboard, no autocap, no autocorrect, id `.url`). `urlText` is trimmed and parsed with `URL(string:)`.
  - **Sınav:** the exam row (section 4).
  - **Konum:** the location rows (section 6).
- **No-silent-drop rule** (ViewModel:327-338). If the chosen action cannot produce a valid action, the draft is invalid and must not go out as "no action":
  - `.url` with empty text or text that does not parse → `.invalidURL`.
  - `.exam` with no exam picked → `.emptyExam`.
  - `.location` with no place picked → `.invalidLocation`.
- Switching actions keeps the previously entered URL text, picked exam and picked place, but only the currently chosen action is sent.

#### 3.5 Image (NotificationComposerAttachmentSections.swift:10-75; NotificationComposerImage.swift)

- Header "Görsel". Footer: "İsteğe bağlı. Bildirimde ve bildirim sayfasında görünür; en fazla 1 MB JPEG'e küçültülür."
- UI:
  - `PhotosPicker` (images only, shared library), label "Görsel Seç" or "Görseli Değiştir" (id `.imagePicker`).
  - Preview (id `.imagePreview`, label "Seçilen görsel"), height min(scaled 160, 280), corner radius 12.
  - A spinner (label "Görsel hazırlanıyor") while encoding.
  - Destructive button "Görseli Kaldır" (id `.imageRemove`).
  - Error label (id `.imageError`).
- Reading the photo as `Data` failing gives "Görsel yüklenemedi. Başka bir görsel seç."
- Encoding is `SubmissionFileEncoder.jpeg(from:maxBytes: 1 MiB, maxPixelSize: 1600)` (`Core/Utilities/SubmissionFileEncoder.swift:11-32`):
  - ImageIO thumbnail, longest side ≤ 1600, never upscaled, EXIF orientation applied. Any decodable input works (HEIC, PNG, JPEG).
  - JPEG quality tries 0.8, 0.6, 0.45, 0.3 in turn, returning the first result ≤ 1,048,576 bytes.
  - If none fits, or the data is not an image, the result is nil. The message is "Bu görsel kullanılamıyor. Başka bir görsel seç." and the previous image is kept.
  - A generation counter guarantees a slow earlier encode never replaces a later pick.
  - `canSend` and `canSendTest` are false while `isPreparing`.
- **Upload timing.** The image is uploaded only after the admin confirms the send, and for a test send right before that send. It is never uploaded on pick.
- **Upload details** (`FirebaseNotificationAdminService.swift:44-67`):
  1. Validate: JPEG non-empty, ≤ 1 MiB, and a valid notification id (`[A-Za-z0-9_-]`, ≤ 128, ASCII). Otherwise `.imageUploadFailed`.
  2. Path is `notificationImages/<notificationId>.jpg`.
  3. Call `getMetadata()` first. If the object already exists, return the path without uploading. Storage rules forbid overwrite, and this is what makes a retried send safe.
  4. Only on `objectNotFound`, `putDataAsync` with `contentType = "image/jpeg"`.
- **Storage rule** (`firebase/storage.rules`): `create` only if `isAdmin() && resource == null`, filename matches `^[A-Za-z0-9_-]{1,128}[.]jpg$`, `size > 0`, `size <= 1 MiB`, and `contentType == 'image/jpeg'`. No update or delete. Read via `canReadArchive()`. The server never stores a signed URL, only `image.storagePath`.
- **Server verification** (`admin.js:184-192`): the object must exist, be `image/jpeg`, and be 0 < size ≤ 1 MiB. Otherwise `failed-precondition` with one of:
  - "Görsel bulunamadı. Görseli yeniden seçip tekrar dene."
  - "Görsel JPEG biçiminde olmalı."
  - "Görsel en fazla 1 MB olabilir."
- **Id-per-image rule** (ViewModel:426-454). `imageUploadIds` records ids under which an upload was attempted, including failed attempts. If the image is changed (`setImage` success) or removed after an attempt under the current id, `draftId` is regenerated. An uploaded image can never be replaced. Before any attempt the id stays the same.
- **Upload failure** means the send is not made and the error is shown. The draft id is kept so a retry re-uploads or reuses and sends. Message: "Görsel yüklenemedi; bildirim gönderilmedi. Tekrar dene."
- **Test sends** upload under their own fresh id. The real draft keeps its id and its image.

#### 3.6 Scheduling and expiry (NotificationComposerSections.swift:243-286; ViewModel:222-233)

- Header "Zamanlama". Footer: "Süresi dolan bildirim, kullanıcıların bildirim sayfasından kalkar."
- Toggle "İleri tarihte gönder" (id `.scheduleToggle`) sets `scheduledAt = now + 1 hour` (off = nil).
  - It then shows `DatePicker("Gönderim zamanı")` (id `.scheduledAt`, date + hour/minute, range `Date.now...`).
- Toggle "Geçerlilik süresi" (id `.expiryToggle`) sets `expiresAt = (scheduledAt ?? now) + 7 days`.
  - It then shows `DatePicker("Bitiş zamanı")` (id `.expiresAt`, range `(scheduledAt ?? .now)...`).
- Turning scheduling on pushes an existing expiry to scheduledAt + 7 days if it would now be ≤ the new schedule. Manually editing the picker does not adjust the expiry, so the issue "Geçerlilik süresi gönderim zamanından sonra olmalı." appears and blocks sending.
- **Quiet hours are not handled or explained anywhere in the composer.** The server enforces them (`admin.js:136-146`, `schema.js:45,222-235`):
  - Applies only to `kind == promo` (including location campaigns) and never to tests. `general` is never deferred.
  - If the target time (the requested future time, or now) falls in 22:00–09:00 Europe/Istanbul (fixed UTC+3), the notification is stored `scheduled` with `scheduledAt` = the next 09:00 and `deferredByQuietHours = true`.
  - The client only reveals this afterwards: the immediate-send result comes back with status `scheduled` and a time, shown as "Bildirim <time> için zamanlandı." The history row adds "(sessiz saat)". The confirmation dialog does not mention a possible delay.
  - A requested `scheduledAt` within the 60 s grace and not in the future is treated as "now" by the server.
  - If `expiresAt <= deferral time`, the server throws `invalid-argument` "Geçerlilik süresi sessiz saatler (22:00–09:00) bitmeden doluyor." (shown as-is, section 3.9).
  - The dispatcher runs every 5 minutes and cancels expired scheduled notifications.
- Dates are shown with `date.formatted(date: .abbreviated, time: .shortened)` (device locale and time zone). Dates go to the server as epoch milliseconds.

#### 3.7 Feedback section (lines 291-315)

Shown if there is a `visibleIssue`, `errorMessage` or `successMessage`. Order: error (red `exclamationmark.circle.fill`, id `.error`), success (green check, id `.success`), issue (secondary `info.circle`, id `.issue`).

`visibleIssue` hides `emptyTitle` while the title field is exactly empty, and hides `emptyBody` while the body is exactly empty (ViewModel:198-205). A blank-spaces title still shows the issue. An untouched form is therefore blocked but not scolded.

#### 3.8 Send and test buttons (lines 318-352)

- "Gönder" (black bold text on brand-orange prominent button, spinner while working, id `.send`), disabled unless `canSend`.
  - `canSend = !isWorking && !image.isPreparing && issue == nil`.
- "Bana test gönder" (iphone icon, id `.testSend`), disabled unless `canSendTest`. Footer: "Test yalnızca senin cihazına gider; kitleye ulaşmaz."

**Send flow** (ViewModel:238-270; `NotificationComposerView.swift:48-83`):

1. `requestSend()`: guard `canSend`; `beginWork()` clears the error and success messages and sets `isWorking = true`. Calls `previewNotification` with `{kind: effectiveKind.rawValue, audience: <audience payload>}`.
   - On success: `confirmation = {draft, preview}`; `isConfirming = true`. Nothing is sent yet.
   - On a thrown cancellation: silent. On any other error: `errorMessage = error.userMessage`.
2. Alert title is "Bildirim gönderilsin mi?" or, when the live draft has a `scheduledAt`, "Bildirim zamanlansın mı?". Message is "\(recipients) kişiye gönderilecek; \(pushTargets) cihaza anlık bildirim (push) iletilecek." If the confirmed draft is scheduled, it appends "\nGönderim zamanı: <formatted date>". Buttons: "Gönder" or "Zamanla" (by `confirmation.draft.scheduledAt`, id `.confirm`) and "Vazgeç" (cancel role, id `.confirm.cancel`, calls `cancelConfirmation`).
3. `confirmSend()`: guard `!isWorking` and a stored confirmation (this guard is why the confirmation is stored separately from the dialog flag). It sets `isWorking`, uploads the image if present (`withUploadedImage`, which sets `imagePath`), then calls `sendNotification` with the confirmed draft, not the live form. Afterwards `confirmation = nil` on every outcome.
4. Result handling (`handle`, lines 360-379):
   - status `sent` or `sending`: success "Bildirim gönderildi. Alıcı sayısı: N." and the form is reset (`resetDraft`: new id, kind to general, text cleared, audience back to everyone, university/faculty/department/gender nil, action none, URL, location and exam cleared, image removed, dates nil; facets kept).
   - status `scheduled`: success "Bildirim <time> için zamanlandı." (with `result.scheduledAt`) or "Bildirim zamanlandı." if absent, and the form is reset. **No recipient count** is shown, because the server reports 0 for scheduled sends (commit 910524f).
   - status `failed` or `canceled`: error "Bildirim gönderilemedi. Ayrıntı için geçmişe bak." and a new `draftId`, because the server keeps that id as a failed record. The form is kept.
   - A thrown error: `errorMessage = error.userMessage`; the form and the **same `draftId`** are kept, so tapping send again is the same idempotent send.
5. Idempotency (`admin.js:85-87,116-122`): `sendNotification` looks up `notifications/<notificationId>`. If it exists, it returns that document's `{notificationId, status, scheduledAt (ms or null), recipients: stats.recipients}` and sends nothing. A concurrent create collision also returns the first result.

**Test send** (ViewModel:278-292, 342-352):

- Guards `canSendTest`. `testDraft` takes the current form with:
  - a fresh id on every tap;
  - `isTest = true`;
  - `scheduledAt` and `expiresAt` nil, so schedule or expiry errors do not block a test;
  - if the audience alone is invalid (e.g. segment without a university), `audience = .all`;
  - everything else kept: effective kind (so location gives promo), action, image (uploaded under the test id), title and body.
- It validates the test draft again. There is no preview and no confirmation dialog.
- Success message is fixed: "Test bildirimi gönderildi. Birazdan cihazına ulaşır." The form is not reset and `draftId` is unchanged.
- It ignores the result status. A returned `failed` would still show the success text.
- Server side (`schema.js:216-225`, `audience.js:50-56`): a test uses `fanout` to the author only, with no consent check and no quiet hours. History still labels it by the audience, e.g. "Tüm kullanıcılar", plus a "Test" badge. The iOS mock labels it "Test (yalnız sen)", which is wrong for real data.

#### 3.9 Error mapping (`FirebaseNotificationAdminService.swift:114-168`, `NotificationAdminServicing.swift:37-54`)

| Source | Maps to | Message |
|---|---|---|
| Functions `permission-denied`, `unauthenticated` | `.permissionDenied` | "Bu işlem için yetkin yok." |
| Functions `failed-precondition` while canceling | `.notCancelable` | "Bu bildirim gönderildiği için artık iptal edilemez." |
| Functions `invalid-argument`, `failed-precondition` | `.invalidDraft(message)` | the server's Turkish message, verbatim |
| Functions `unavailable`, `deadline-exceeded` | `.offline` | "İnternet bağlantısı yok. Bağlantını kontrol edip tekrar dene." |
| Other Functions errors (e.g. `not-found`, `internal`) | `.unknown(localizedDescription)` | logged |
| Firestore `permission-denied`, `unauthenticated` | `.permissionDenied` | |
| Firestore `unavailable`, `deadline-exceeded` | `.offline` | |
| `NSURLErrorDomain` | `.offline` | |
| Storage `unauthorized`, `unauthenticated` | `.permissionDenied` | |
| Storage `retryLimitExceeded`, `NSURLError` | `.offline` | |
| Any other Storage error | `.imageUploadFailed` | "Görsel yüklenemedi; bildirim gönderilmedi. Tekrar dene." |
| Callable result not parseable | `.unknown` | "Sunucudan beklenmeyen bir yanıt alındı." |

- `.unavailable` ("Bildirim gönderme henüz kullanılamıyor.") is defined but unused by the live service.
- Server-side validation messages (all passed through): "Bildirim bilgileri eksik.", "Geçersiz bildirim kimliği.", "Geçersiz bildirim türü.", "Geçersiz bildirim aksiyonu.", "Geçersiz görsel.", "Gönderim zamanı geçersiz.", "Geçerlilik süresi geçersiz.", "Geçersiz test bilgisi.", "Geçersiz cinsiyet seçimi.", and the exam errors in section 4.
- The client sets no custom callable timeout. `sendNotification` has a 300 s server timeout, but the iOS Functions default is about 70 s. A very large fan-out can therefore surface as `deadline-exceeded`, i.e. the offline message, and the retry with the same id returns the existing document.

### C.4 Exam picker (NotificationExamPickerView.swift; Attachment sections:80-109)

- Row inside "Eylem" when the action is Sınav: a button with `LabeledContent("Sınav")` whose value is the picked exam's `name`, or "Seç" (id `.exam`). Below it, once picked, a caption showing `exam.path.displayNames.dropLast().joined(" › ")` (id `.examPath`).
- Tapping opens a sheet containing a `NavigationStack` that walks the real archive using the same `ArchiveRepository` as the browser: university, faculty, department, course, exam. It reuses `BrowseViewModel`.
  - Each level is a list under `ArchivePath`, with Turkish diacritic- and case-insensitive search (including İ/ı) and level-specific prompts: "Üniversite ara", "Fakültelerde ara", "Bölümlerde ara", "Derslerde ara", "Sınavlarda ara".
  - Nav title is the node's display name, or "Sınav Seç" at the root. Cancel button "Vazgeç" (id `.exam.cancel`).
  - Row ids are `notificationComposer.exam.row.<display name>`.
  - States: loading, error with retry, empty ("Henüz üniversite yok", "Bu üniversitede henüz fakülte yok", "Bu fakültede henüz bölüm yok", "Bu bölümde henüz ders yok", "Bu derste henüz sınav yok"), and search-empty.
- Non-exam rows drill down. Exam rows are buttons that pick and dismiss. An exam node with no files (`item.exam == nil`) is disabled.
- There is no lock badge, no pull-to-refresh and no live archive refresh in the picker.
- **What is sent: only the exam id.** `examId = selectedExam.path.components.last`, which is the Firestore `exams/{id}` document id, as `{type:"exam", examId}` (ViewModel:323).
- **Server handling** (`admin.js:153-177`): `exams/{examId}` must exist and have `status == 'published'`, and `kind` must be `JPG` or `PDF`. Otherwise `failed-precondition` "Bağlanacak sınav bulunamadı ya da yayında değil." or "Bağlanacak sınavın dosya türü geçersiz." Path ids, names and `examKind` are filled from the exam document, falling back to the `catalogNodes` parent chain. If that chain is broken: "Bağlanacak sınavın arşivdeki yeri bulunamadı."
- The exam action does not force `promo`.
- `selectedExam` is cleared on a successful send. Switching actions only hides it.

### C.5 History (NotificationHistoryView.swift, ViewModel)

- **Data source: a direct Firestore read** from the named `vifi` database, not a callable. Query: `collection("notifications").order(by: "createdAt", descending: true).limit(to: 50).getDocuments()` (`FirebaseNotificationAdminService.swift:79-91`). There is no pagination.
  - Rows with an invalid id, empty title, unknown status or unparseable audience are silently dropped by the parser. An unknown kind reads as `.general`.
  - `Timestamp` fields are converted to dates. The list contains admin notifications and automatic ones (`source == "automatic"`).
- Title "Gönderim Geçmişi".
- Loads once per screen instance (`loadIfNeeded`); the view model is created each time the screen is pushed, so every open reloads. Pull-to-refresh and the error screen's retry call `refresh()`.
- **States:**
  - Loading: "Geçmiş yükleniyor…".
  - Error: full error view with retry, only when there are no rows yet.
  - A failed refresh keeps the rows and shows the error above them (id `notificationComposer.history.error`).
  - Empty: title "Henüz bildirim yok", message "Gönderdiğin ve zamanladığın bildirimler burada görünür." (bell.slash).
- **Row layout:**
  - Kind icon.
  - Title (headline).
  - `audienceLabel` if non-empty. The server writes it (`schema.js:274-294`): "Tüm kullanıcılar", "Kişisel", "Adminler", or "Üniv › Fakülte › Bölüm · Kadın|Erkek".
  - Badges with 22% tinted capsules. Status: "Zamanlandı" (orange), "Gönderiliyor" (blue), "Gönderildi" (green), "Başarısız" (red), "İptal edildi" (gray). Then the kind label (secondary), "Test" (purple) if `isTest`, and "Otomatik" (teal) if `isAutomatic`.
  - Time line:
    - scheduled: "<time> için zamanlandı", or "<time> için zamanlandı (sessiz saat)" when `deferredByQuietHours`. Nothing if `scheduledAt` is nil.
    - sent, sending, failed: `sentAt ?? createdAt`, formatted.
    - canceled: `createdAt`, formatted.
  - Stats lines, only for statuses `sent`, `sending`, `failed`: "N alıcı · N push gönderildi · N başarısız" and "N kişi açtı · N kişi butona dokundu". `pushTargets` is parsed but not displayed.
  - The opened and tapped counters are counted once per user (`stats.js`).
- **Cancel action:**
  - It appears only for `status == .scheduled`, as a trailing swipe action (`allowsFullSwipe: false`) and in the context menu: "İptal et" (destructive, xmark.circle, id `notificationComposer.history.row.<id>.cancel`).
  - There is **no confirmation dialog**.
  - It is disabled while any cancel is in flight (`cancelingId`). The view model also ignores a second cancel or a non-scheduled row.
  - It calls `cancelNotification {notificationId}`.
  - On success it reloads the full list. On `.notCancelable` it shows the message and also reloads, since the notification may have gone out meanwhile. On any other error it shows the message and leaves the list alone.
  - Server (`schedule.js:58-75`): runs in a transaction. `not-found` gives "Bildirim bulunamadı.", an already-canceled one gives "Bu bildirim zaten iptal edilmiş.", any other non-scheduled gives "Bu bildirim gönderildiği için artık iptal edilemez.". The iOS client maps every `failed-precondition` on cancel to its own `.notCancelable` text and discards the server message.
- Row accessibility: combined element, id `notificationComposer.history.row.<id>`. List id `.history.list`.

### C.6 Location picker (`LocationPicker/`)

#### 6.1 Wiring and key handling

- `LocationPicking` protocol (`LocationPicking.swift:7-16`) so mock mode never touches Google. Live: `GoogleLocationPicker`. Mock: `MockLocationPicker`.
- It is presented with `.fullScreenCover` from the Konum rows. The composer passes `initial: viewModel.location`. On pick it sets `viewModel.location` and dismisses; on cancel it only dismisses.
- **Location rows** (`NotificationComposerLocationSection.swift`):
  - Summary: the name (headline) and the address if any (id `.location.summary`, accessibility label "Seçilen konum: <name, address>").
  - Button "Konum seç", or "Değiştir" when a place exists (mappin.and.ellipse, id `.location.pick`).
  - Footnote (id `.location.note`): "Konumlu bildirimler her zaman Kampanya olarak gider ve yalnızca kampanya bildirimlerini açmış kullanıcılara ulaşır."
- **API key:** `GOOGLE_MAPS_API_KEY` lives in `Config/Secrets.xcconfig` (gitignored). `Config/Base.xcconfig` declares the empty default and `#include?`s the secrets file. `project.yml` exposes it as Info.plist `GMSApiKey: $(GOOGLE_MAPS_API_KEY)`.
  - `GoogleMapsConfiguration` (`GoogleMapsConfiguration.swift:9-37`): trims the value; it is unusable if missing, blank, a non-string, or starting with `$(` or `${` (an unexpanded build setting).
  - `GoogleMapsSDK.startIfPossible()` runs lazily on each picker build, not at launch. It calls `GMSServices.provideAPIKey` and `GMSPlacesClient.provideAPIKey` once, and returns false when there is no usable key.
  - Without a key, the picker shows `LocationPickerUnavailableView` instead of the map, because creating a `GMSMapView` without a key throws. It has nav title "Konum Seç", a `ContentUnavailableView` titled "Harita anahtarı yapılandırılmamış" with the text "Konum seçmek için bu sürümün Google Haritalar anahtarıyla derlenmiş olması gerekir. Geliştirici ekibe haber ver." (id `locationPicker.unavailable`), and a "Kapat" button (id `locationPicker.cancel`).
  - Neither the Google SDKs nor CoreLocation are touched in that case.
- **Google Cloud setup** (README/CLAUDE.md): Maps SDK for iOS and Places API (New) enabled; key restricted to the iOS bundle id and those two APIs; Places daily quotas set to 300; budget alerts. The Android equivalent needs its own key, restricted to package name and SHA-1.

#### 6.2 Map (`GoogleMapView.swift`)

- Map settings: compass off, rotate gestures off, tilt gestures off. The my-location dot and button show only when `showsDeviceLocation` (permission authorized).
- A fixed red pin sits at the screen center. The chosen coordinate is wherever the map comes to rest at the center.
  - Event `settled`: reports lat/lng/zoom and `visibleRadius = distance(nearLeft, farRight) / 2` in meters.
  - Event `gestureBegan`: user drag or pinch only. Programmatic moves and the my-location button do not count.
  - Event `placeTapped`: from `didTapPOIWithPlaceID` (placeId, name, coordinate).
- Camera moves use `CameraRequest(id, camera)`. The id makes two moves to the same spot distinct, and the map animates once per id.
- **Default camera:** Turkey at 39.0, 35.2, zoom 5. **When editing:** the existing location's coordinate at zoom 16. Zoom constants: place 16, approximate device fix 12.
- Accessibility: label "Harita", hint "Konum, haritanın ortasındaki iğnenin gösterdiği yerdir." (id `locationPicker.map`). Dynamic type is capped at accessibility1.

#### 6.3 Search / autocomplete (`LocationPickerViewModel.swift`, `GooglePlacesService.swift`)

- Field "Yer ara" (id `locationPicker.search`, clear button "Aramayı temizle" id `.search.clear`).
- **Search quota controls:**
  - Minimum 3 characters after trimming.
  - 350 ms debounce, so each keystroke restarts the wait.
  - Search text is trimmed before sending.
  - One search session = one token.
- **Request** (Places API (New)):
  - `GMSAutocompleteRequest` with `regionCode "TR"`.
  - A circular location *bias* around the visible map area when `0 < visibleRadius <= 50,000` m. A country-wide view sets no bias. It ranks, it does not filter.
  - The session token is renewed after 150 s, and after every place fetch.
- **Result states:** idle, "Aranıyor…", "Sonuç bulunamadı.", failed (message below), or a results list (max height 280). A suggestion shows `attributedPrimaryText` and an optional secondary subtitle (id `locationPicker.suggestion.<placeId>`).
- **Selecting a suggestion** (`select`, lines 156-177):
  - Guarded by `isResolving`.
  - Fetches the place with fields `[name, coordinate, formattedAddress, placeID]` only. Each extra field would be billed at a higher tier.
  - Coordinate must be valid, otherwise `.notFound`.
  - On success: placeId, name and address are filled, with the name cut to 120 characters and the address to 200 (so a long Google name never blocks confirming). The camera flies there at zoom 16, the query box is emptied and the list closed.
  - A POI tapped on the map while this resolved wins (`selectionCount`).
  - On failure the error shows in the panel and the pin is untouched.
- **PlaceSearchError messages:** "Arama şu an kullanılamıyor. Haritayı kaydırarak konumu yine de seçebilirsin." (any service or network failure) and "Bu yer bulunamadı. Başka bir sonuç seç.".
- The pin can always be placed manually without search.

#### 6.4 Tapping a map POI (commit 63708fd)

`selectMapPlace(placeId, name, lat, lng)`:

- The name is collapsed to one line (whitespace runs become one space), trimmed, and cut to 120.
- The address is cleared immediately, so an earlier place's address never lingers.
- Camera zoom is `max(currentZoom, 16)`: it zooms in only, never out.
- The search is closed and the box emptied.
- The address is then fetched in the background with fields `[formattedAddress]` only, the cheapest field set. A spinner shows in the address field (label "Adres alınıyor", id `.addressLoading`).
  - A failed lookup is silently ignored. The place stays chosen without an address.
  - The result applies only if no newer choice was made (`selectionCount`) and the address field is still empty. A typed address wins. It is cut to 200.

#### 6.5 Place id and the pin

- `placeId` belongs to the place that was picked. If the map settles more than 0.00001° (about 1 m) away from the picked place, `placeId` is set to nil, but the name and address stay as editable text. It never comes back for the same spot.
- Editing an existing location with a `placeId`: `placeCoordinate` is its coordinate, so dragging also drops the id.

#### 6.6 Device location (commit 63708fd)

- `locateDevice()` runs once per picker (`.task`; the `hasStartedLocating` flag also covers re-appearance).
  1. If permission is `notDetermined`, it asks for **When In Use** via `requestWhenInUseAuthorization`. It is asked only once, and a denial is never re-asked.
  2. If authorized, it reads one fix (`requestLocation`, `desiredAccuracy = 100 m`). A fix is accepted only with `horizontalAccuracy >= 0` and a valid coordinate.
  3. The map flies to the fix at zoom 16, or zoom 12 if `accuracy > 1000 m` (Precise Location off, so the district rather than a wrong street).
- The device fix is skipped when the admin has already placed the map. That is when `isPlacedByAdmin` is set (editing an existing location from the start, a drag or pinch gesture, a chosen search result, a tapped POI) or `isResolving`. The check runs both after the permission prompt and again after the fix arrives.
  - While editing, the permission is still asked (for the blue dot), but no fix is read.
- Denied or restricted: the map stays on Turkey and shows the quiet note "Konum izni kapalı; harita bulunduğun yere odaklanmaz." (id `.locationDenied`, location.slash icon).
- The location is never stored, logged or sent. The only log line is the failure reason, without coordinates. CoreLocation is created lazily and only by `GoogleLocationPicker`.
- Info.plist usage string: "Bildirim için konum seçerken haritayı bulunduğun yere odaklamak için konumun kullanılır. Konumun kaydedilmez ve paylaşılmaz." (mirrored in `InfoPlist.xcstrings`, key `NSLocationWhenInUseUsageDescription`.) `LSApplicationQueriesSchemes: comgooglemaps` is for the inbox "Konuma Git" only.
- `project.yml` still has a stale comment "No CoreLocation / 'my location'" next to the Google packages. Commit 63708fd superseded it.

#### 6.7 Details card and the result

- Coordinates are shown as "%.5f, %.5f" (accessibility label "Koordinat: …", id `.coordinates`).
- Name field "Yer adı (zorunlu)" (id `.name`). Error: "Ad en fazla 120 karakter olabilir." Autocorrect off.
- Address field "Adres (isteğe bağlı)" (id `.address`). Error: "Adres en fazla 200 karakter olabilir."
- Confirm button "Bu konumu seç" (id `.confirm`), enabled when `location != nil && !isResolving`.
- `location` is built from the map center, the trimmed name, the trimmed address (nil if empty) and `placeId`. It is `nil` when invalid: empty name, name or address over its limit, or out-of-range coordinate.
- What the picker returns to the composer: `NotificationLocation { latitude, longitude, name, address?, placeId? }`. Lat/lng are the full-precision doubles of the pin, not the 5-decimal display text.
- Cancel is "İptal" (id `locationPicker.cancel`). Canceling leaves the previously chosen place unchanged. Title "Konum Seç".

#### 6.8 Mock picker (`Preview Content/MockLocationPicker.swift`)

- Shows "Örnek harita" and a fixed place: lat 40.9903, lng 29.0290, name "ViFi Öğrenci Kafesi", address "Caferağa, Kadıköy, İstanbul", `placeId` nil. If editing, it shows the initial location.
- Buttons "Bu konumu seç" (id `locationPicker.confirm`) and "İptal" (id `locationPicker.cancel`). Name text id `locationPicker.mock.name`.
- No Google SDK or CoreLocation is touched.

### C.7 Accessibility identifiers and analytics

**Composer:** `notificationComposer.history`, `.confirm`, `.confirm.cancel`, `.kind`, `.kind.lockNote`, `.title`, `.titleCounter`, `.body`, `.bodyCounter`, `.audience`, `.audienceRetry`, `.university`, `.faculty`, `.department`, `.gender`, `.estimate`, `.action`, `.url`, `.exam`, `.examPath`, `.exam.cancel`, `.exam.row.<name>`, `.location.pick`, `.location.summary`, `.location.note`, `.imagePreview`, `.imagePicker`, `.imageRemove`, `.imageError`, `.scheduleToggle`, `.scheduledAt`, `.expiryToggle`, `.expiresAt`, `.error`, `.success`, `.issue`, `.send`, `.testSend`.

**History:** `.history.error`, `.history.list`, `.history.row.<id>`, `.history.row.<id>.cancel`.

**Account:** `account.notificationComposer`.

**Location picker:** `locationPicker.map`, `.search`, `.search.clear`, `.suggestion.<placeId>`, `.coordinates`, `.locationDenied`, `.name`, `.address`, `.addressLoading`, `.confirm`, `.cancel`, `.unavailable`, `.mock.name`.

**Analytics:** only screen views, `screenView(name: "NotificationComposer")` and `screenView(name: "NotificationHistory")`, fired on each appear (`NotificationComposerView.swift:11`, `NotificationHistoryView.swift:10`). They map to Firebase's `screen_view` with the `screen_name` parameter. There are no custom events for send, test, cancel, image or location. Engagement counters (`opened`, `actionTapped`) come from the consumer side.

### C.8 Test inventory

#### NotificationDraftTests (9)

- `readyDraft`: a titled, bodied draft to everyone is valid.
- `textLimits`: blank or 81-char title, empty or 501-char body give the right issues.
- `segmentRules`: a segment needs a university, and a department needs its faculty.
- `serverOnlyAudiences`: `.user` and `.admins` are invalid.
- `locationIsPromo`: a location makes `effectiveKind` promo and the payload `kind` "promo".
- `actionValidation`: lat 120, an http URL, and a blank exam id give the right issues.
- `scheduleRules`: -1 h is in the past, -30 s passes within the grace, expiry == now or expiry before a later schedule gives `expiryBeforeDelivery`.
- `payload`: trimmed text, segment keys, action, epoch-ms dates, `isTest`, no `expiresAt` when unset.
- `estimates`: facet estimates by level and gender, 0 for an unknown university.

#### NotificationComposerViewModelTests (10)

- `untouchedForm`: blocked but no visible issue.
- `textRules`: a blank title is flagged, limits, counts, `canSend`.
- `segmentNeedsUniversity`: segment mode without a university gives `invalidAudience`, then valid once picked.
- `actionValidation`: an empty or http link is invalid, a trimmed https link is valid, and the other actions yield their drafts.
- `timing`: schedule defaults to +1 h, expiry to schedule + 7 days, plus past and before-delivery issues and toggling off.
- `facetsLoading`: failure then retry only through `reloadFacets`; the estimate is nil while failed.
- `pickerOptions`: only facet values, Turkish sorting.
- `cascadingReset`: resets below a changed level, same university keeps them.
- `departmentNeedsFaculty`: a department is ignored without a faculty.
- `estimate`: follows audience, level and gender.

#### NotificationComposerSendFlowTests (15)

- `previewConfirmSend`: preview first, send only after confirm, same id, form reset to a new id, facets kept.
- `declining`: cancel sends nothing and keeps the form.
- `dialogDismissalDoesNotDropTheSend`: `confirmSend` still works after `isConfirming` was cleared by the system.
- `invalidSendIsIgnored`: an invalid form never calls the service.
- `scheduledSend`: the success message contains the time and no "Alıcı"; the form is reset.
- `retryKeepsId`: a failed send keeps form and id, and the retry reuses the id.
- `serverRejection`: the server message is shown.
- `failedResultRenewsId`: a `failed` result gives an error and a new id, with the form kept.
- `previewFailure`: error shown, nothing confirmed.
- `doubleTapProtection`: a second tap during work is ignored.
- `testSend`: own id, no confirmation, no schedule or expiry, form untouched.
- `testSendWithUnfinishedSegment`: the audience falls back to all.
- `testSendNeedsContent`: a test still needs content.
- `testSendFailure`: error shown.
- `kindIsSent`: promo is previewed and sent as promo.

#### NotificationComposerAttachmentTests (13)

- `examAction`: needs a picked exam, sends only the exam id, not promo.
- `otherActionIgnoresExam`: a different action drops the exam from the draft.
- `resetAfterSend`: the exam is cleared after a send.
- `downsamples`: 2400×1200 gives a 1600×800 JPEG ≤ 1 MiB.
- `unusableImage`: non-image data gives an error and keeps the previous image.
- `removed`: removing the image gives no upload and no `imagePath`.
- `uploadsOnConfirm`: upload only after confirm, under the draft id, path in the payload, image cleared afterwards.
- `decliningUploadsNothing`: declining uploads nothing.
- `failedUpload`: no send, the same id is kept, and the retry re-uploads and sends.
- `retryReusesUpload`: a send failing after upload retries on the same id with one stored image.
- `changingTheImage`: a new image after an upload gives a new id, and removal after an attempt too.
- `testSend`: the test upload uses its own id and the real draft is untouched.
- `draftImagePath`: another notification's path gives `invalidImage`.

#### NotificationComposerKindLockTests (7)

- `unlockedByDefault`: not locked for any non-Konum action.
- `locksOnKonum`: locks even without a place and the draft says promo.
- `lockedControlIgnoresChanges`: a write is ignored and `kind` is untouched.
- `restoresTheChoice`: another action restores the previous choice.
- `choiceMadeBetweenLocks`: the choice made while unlocked is remembered.
- `sendsAsCampaignThenResets`: preview and send as promo, then the kind returns to general.
- `testSendIsACampaignToo`: a test is promo as well.

#### NotificationComposerLocationTests (8)

- `needsAPlace`: Konum without a place blocks sending and the draft action is nil.
- `pickedPlace`: a picked place becomes the action.
- `alwaysPromo`.
- `payload`: optional address and `placeId` are omitted when nil.
- `invalidPlace`: lat 95 and a blank name are invalid.
- `keptButNotSent`: the place is kept across actions but only sent when Konum is chosen.
- `sendFlow`: previewed as promo, then the place is cleared.
- `testSend`: the test carries the place.

#### NotificationHistoryViewModelTests (7)

- `loads`: loads once; `refresh` reloads.
- `failures`: a failed load shows an error; a failed refresh keeps the rows plus a message.
- `cancelsScheduled`: calls the service and reloads.
- `ignoresOthers`: only a scheduled row can be canceled.
- `cancelFailure`: error shown, list unchanged.
- `notCancelableReloads`: message plus a reload.
- `cancelInFlight`: a second cancel is ignored.

#### FirebaseNotificationAdminServiceTests (8)

- `permission`: `permission-denied` and `unauthenticated` map to `permissionDenied`.
- `invalidDraft`: the server message is carried for `invalid-argument` and `failed-precondition`.
- `storageNotFound`: `objectNotFound` is told apart from other Storage errors.
- `imageUploadErrors`: unauthorized, retryLimit, URL timeout and unknown give the right mapping.
- `notCancelable`: `failed-precondition` on cancel maps to `notCancelable`, other errors keep their mapping.
- `offline`: `unavailable`, `deadline-exceeded`, Firestore `unavailable` and `NSURLError` map to offline.
- `firestorePermission`.
- `unknown`: internal errors keep their message.

#### GoogleMapsConfigurationTests (6)

- `usableKey`: trimmed.
- `missingKey`: nil, empty, blank.
- `unexpandedKey`: `$(…)` and `${…}`.
- `wrongType`.
- `notStartedWithoutKey`: no key means `startIfPossible` is false.
- `keyName`: the key name is `GMSApiKey`.

#### LocationPickerViewModelTests (17)

- `emptyStart`: starts on Turkey with nothing to confirm.
- `startsOnInitial`: starts on the existing location with its name and address.
- `shortQuery`: under 3 characters is not searched.
- `searchShowsResults`: the query is trimmed and results shown.
- `debounced`: fast typing gives one search.
- `noResults`.
- `searchFailure`: the failure state shows and the pin stays usable.
- `clearing`: clearing drops a pending search.
- `selecting`: moves the map and fills name, address and placeId.
- `requestsAreDistinct`: each selection is a new camera request id.
- `resolveFailure`.
- `longPlaceName`: cut to the limits.
- `mapSettles`: coordinate text is "41.50000, 28.50000".
- `placeIdFollowsThePin`: the id is kept within about 1 m and dropped once the pin moves off, and never returns.
- `nameRequired`: a trimmed name is required.
- `optionalAddress`: an empty address is omitted.
- `limits`: name or address over the limit blocks confirm.

#### LocationPickerDeviceLocationTests (13)

- `asksThenFocuses`: prompts once, then zoom 16 on the fix, nothing picked.
- `alreadyAllowed`: no prompt.
- `runsOnce`.
- `approximateFix`: accuracy 3000 m gives zoom 12.
- `refused(2 args)`: from notDetermined or denied, no fix is read, the map stays on Turkey, the note shows, and the picker still works.
- `noFix`: the map stays, the dot still shows.
- `withoutLocator`: nothing is asked.
- `editingKeepsTheLocation`: asks permission but reads no fix.
- `dragBeforeFix`: the map stays where it was dragged.
- `dragDuringPrompt`: no fix is read.
- `searchBeforeFix`: the search result wins.
- `searchResolvingAtFix`: a resolving search wins.
- `tapBeforeFix`: a tapped POI wins.

#### LocationPickerMapPlaceTests (14)

- `tapPicksThePlace`: name, coordinate, id, loading flag, camera at zoom 16, address filled later.
- `addressArrivesLater`.
- `addressFailure`: the place is kept silently.
- `typedAddressWins`.
- `newerTapWins`: an older address answer is dropped.
- `searchAfterTap`: a search result replaces everything.
- `tapWhileResolving`: the tap wins over a resolving search.
- `tapReplacesEarlierPlace`: the old address is cleared.
- `tapEndsTheSearch`.
- `dragAfterTap`: a gesture drops the id but keeps the name and address.
- `labelCleanup`: two-line labels become one line, cut to 120.
- `tapKeepsCloserZoom`: zoom 18.5 is kept.
- `searchFavorsTheMap`: the bias area is the visible radius.
- `noAreaWhenZoomedOut`: nil before the first settle or on a country-wide view.

#### UI tests

- **NotificationComposerUITests (6):**
  - `testEntryIsHiddenFromNonAdmins`.
  - `testSegmentCascadeNarrowsTheEstimate`: estimates 64, 40, 28, 17, then 24 after switching university with the department reset, then 9 for "Kadın".
  - `testAdminSendsAnAnnouncementAfterConfirming`: the history row contains "Gönderildi".
  - `testDecliningTheConfirmationSendsNothing`.
  - `testScheduledNotificationIsCanceledFromHistory`: the row contains "Zamanlandı", swipe to cancel, then "İptal edildi".
  - `testAdminLinksAnExamPickedFromTheArchive`: walks BOZOK ÜNİVERSİTESİ, MÜHENDİSLİK MİMARLIK FAKÜLTESİ, BİLGİSAYAR MÜHENDİSLİĞİ, MÜHENDİSLİK MATEMATİĞİ, 2019 FİNAL.
- **LocationCampaignUITests, composer part (3):**
  - `testAdminAttachesAPlaceAndSendsACampaignAfterConfirming`: send is disabled until a place is picked, and the row shows "Kampanya".
  - `testKonumLocksTheKindToKampanyaAndAnotherActionRestoresIt`.
  - `testCancelingThePickerLeavesNoPlace`.
- The three inbox tests in that file belong to the consumer side.

### C.9 Edge cases, "why" notes and commit specifics

- **3e0a52e.** A pure Swift 6 isolation fix in the image section: `image.hasImage` is read on the main actor before building `PhotosPicker`, because the label closure is `Sendable`. There is no behavior change. Android: nothing to port.
- **63708fd.** Adds CoreLocation (admin picker only) and POI tapping. Details are in 6.4 to 6.6. The previously stale "no CoreLocation" text in `CLAUDE.md` and the README was updated in that commit.
- **910524f.** The "scheduled" success message no longer shows a recipient count.
- **Location is forced to promo on both client and server.** The server overrides kind to `promo` when `action.type == 'location'` (`schema.js:90`).
- **Preview is for the confirmed kind.** `previewNotification` receives the effective kind, so a location campaign previews the opted-in reach.
  - Server: broadcast mode (`all` and not promo) gives `recipients = all profiles` and `pushTargets = count of all registered devices`. Anything else is fan-out: resolved recipients (consent filter for promo) and distinct device tokens (`admin.js:56-71`).
- **`deliveryMode`.** `all` and not promo is broadcast. Everything else, including every campaign and every test, is fan-out.
- **Server validations of note** (`schema.js:67-120`, `admin.js:80-130`):
  - `notificationId` must match `^[A-Za-z0-9_-]{1,128}$`.
  - `imagePath` must equal `notificationImages/<notificationId>.jpg`, else "Geçersiz görsel."
  - `data.isTest` must be a boolean.
  - Gender must be `female` or `male`.
  - The URL must pass `new URL()` with https and a non-empty host.
  - `address` is clipped to 200 (not rejected), `placeId` clipped to 300.
  - `title`, `body` and `name` are trimmed before the length check.
- **Date or time-zone details.** Quiet hours use the fixed UTC+3 offset (Türkiye has no DST). Dates are epoch milliseconds. Date display uses the device locale and zone.
- **Draft id lifecycle.** The id is stable through errors and failed uploads, and renewed on success, on `failed` or `canceled` results, and when the image changes or is removed after an upload attempt. Tests use fresh ids per tap.
- **Facet counts.** `facets.total` counts profiles with a valid `universityId`, but the broadcast preview `recipients` counts all RTDB profiles. The two can differ by a few users.
- **History flood.** The 50-row history includes automatic notifications (e.g. new-submission notices to admins, coin credits). The client does not show `trigger`, so these appear with the "Otomatik" badge and labels "Kişisel" or "Adminler".
- **Mock differences.** The mock `audienceLabel` for tests ("Test (yalnız sen)") and its `preview` halving for promo are not real server behavior.
- **Android pitfalls to avoid:**
  - Count code points like the server (iOS counts graphemes).
  - Do not add client-side quiet-hours logic. Show the server's `scheduled` result, and the "(sessiz saat)" history note.
  - Keep the draft id stable across retries.
  - Check an uploaded image exists before reuploading.
  - Never log or send the device location.
  - The Android picker needs its own restricted Maps/Places key, and the lazy-SDK and "unavailable" fallback when the key is missing.
  - Handle the ~70 s callable timeout for large fan-outs.
  - The cancel action has no confirm dialog on iOS. Decide deliberately whether to add one.
---

## Bölüm D — Android'in bugünkü bildirim kodu (4.0.0)


Path shorthand: `M` = `app/src/main/java/com/salticusteam/vifi`, `L` = `app/src/live/java/com/salticusteam/vifi`, `K` = `app/src/mock/java/com/salticusteam/vifi`, `T` = `app/src/test/java/com/salticusteam/vifi`. Everything below was read directly; "not present" means grep and listing found nothing.

### D.0 Headline findings

1. **The current device-token write will be rejected by the new shared rules.**
   - Android writes `users/<uid>/devices/<FCM token>` with `{platform:"android", updatedAt: serverTimestamp}`, merged.
   - The shared `ViFi-iOS/firebase/firestore.rules` (read-only peek, commit `abd55c2`) now requires doc id `^[A-Za-z0-9_-]{1,128}$`. FCM tokens contain `:` and are longer than 128 characters.
   - It also requires exactly `token, platform, appVersion, updatedAt`; Android sends no `token` or `appVersion`.
   - `PushTokenRegistration` swallows failures (`LogCategory.APP.log.w`), so it would fail silently once those rules are live.
2. **No sign-out or account-deletion cleanup of the device doc exists.** `SessionTeardown` and `AccountViewModel.didDeleteAccount` never touch `devices`.
3. **Android has only two channels: `announcements` and `submissions`.** `general`, `promo` and `system` do not exist. A push with `android.notification.channelId = kind` would fall back to the manifest default channel `announcements`.
4. **Push handling reads only title/body.** Nothing reads `notificationId` or `kind`, and there is no image handling. A tap carries a Boolean "open inbox" only, so no id travels to the inbox.
5. **The inbox reads only the public `announcements` collection.** There is no `users/<uid>/inbox`, no `settings/inbox` and no `settings/notifications`. Read state is local, in DataStore.
6. **No Maps, Places or location library, no location permission, no FileProvider, no `trackNotificationEvent` callable, no installId, no marketing-consent code.**
7. **The exam viewer has no lock check.** `ExamLock` is applied only in the Browse list. There is no examId→path resolver; routes need the full 5-id path.

---

### D.1 FCM (`M/core/notifications/*`)

**`ViFiMessagingService.kt`** (`@AndroidEntryPoint`, injects `PushTokenRegistration`)
- `onMessageReceived` (31-38) builds `NotificationContent.from(notification.title, notification.body, message.data)` and shows it unless empty.
- `onNewToken` (41-45) calls `FirebaseMessaging.subscribeToTopic(PushTopics.ALL)` (`"all"`, lines 84-86) and `pushTokenRegistration.onNewToken(token)`.
- `show()` (49-76):
  - It calls `NotificationChannels.ensure`, then silently returns if API ≥ 33 and `POST_NOTIFICATIONS` is not granted.
  - The PendingIntent targets `MainActivity` with `CLEAR_TOP|SINGLE_TOP` and the extra `InboxDeepLink.EXTRA_OPEN_INBOX = true`.
  - The PendingIntent uses requestCode 0 and `FLAG_UPDATE_CURRENT|FLAG_IMMUTABLE`.
  - The builder uses channel `NotificationChannels.ANNOUNCEMENTS`, `BigTextStyle`, `PRIORITY_DEFAULT`, small icon `R.drawable.ic_notifications_black_24dp` and colour `R.color.notification_accent` (#FB910D).
  - It posts with the constant `NOTIFICATION_ID = 0` (line 79), so successive pushes replace each other.
- **Data keys read:** only `title`, `body` and `message` (fallbacks, `NotificationContent.kt:12-13`). The `notification` block wins. `notificationId`, `kind` and `image` are never read.
- **Foreground:** FCM calls `onMessageReceived` for notification messages too, so the service posts the notification itself.
- **Background:** a message with a `notification` block never reaches the service. The system tray shows it using the manifest defaults: channel `announcements`, the icon and the colour. The tap launches the default launcher intent.

**`NotificationChannels.kt`**
- Ids: `ANNOUNCEMENTS = "announcements"` (line 15) and `SUBMISSIONS = "submissions"` (line 16).
- Both are `IMPORTANCE_DEFAULT`.
- Names and descriptions:
  - `notification_channel_general_name` "Genel bildirimler" (strings.xml:3)
  - `notification_channel_general_description` "Duyurular ve yeni sınav bildirimleri"
  - `notification_channel_submissions_name` "Sınav gönderimleri" (strings_add_exam.xml:69-70)
- `ensure(context)` is called from `VifiApplication.onCreate` (line 35), from `show()`, and from `SubmissionNotifier`.

**`NotificationContent.kt`:** the data class `(title, body)` with `isEmpty` and `from(...)`. A push with neither title nor text is dropped.

**`NotificationBadge.kt`:** `clear()` is `NotificationManagerCompat.cancelAll()` (22-24).
- It is called on every process foreground (`AppLifecycleHooks.onStart`, `M/app/AppLifecycleHooks.kt:36-37`).
- Its KDoc claims every visible notification is an announcement. That is outdated, because `SubmissionNotifier` also posts, so `cancelAll()` clears those too.

**`InboxDeepLink.kt`:** a singleton with `StateFlow<Boolean> pending`, plus `request()`, `consume()` and `handle(extras)`.
- `opensInbox(keys, flag)` is true when extra `open_inbox` is set or the key `google.message_id` is present (the system-tray FCM launch intent). `EXTRA_OPEN_INBOX = "open_inbox"`.
- It carries **no payload**, only a Boolean.

**`PushTokenRegistration.kt`** (singleton, `@ApplicationScope`)
- `start()` (39-46) collects `sessionStore.state`; on `SignedIn` it registers using `notifications.token()`.
- `onNewToken(token)` (49-52) registers only if `sessionStore.user` is non-null.
- `register` (55-68): a Mutex plus an in-memory `registered: Pair<uid, token>?` dedupe (line 35, check at line 59).
- **The dedupe is never reset on sign-out.** Re-signing in as the same user in the same process skips the write.
- It started from `SessionViewModel.init` (`M/app/SessionViewModel.kt:30-34`).

**`NotificationPermissionRequester.kt`:** bridges the `POST_NOTIFICATIONS` `ActivityResultLauncher` to `suspend request(permission)`. `MainActivity` attaches and detaches it.

**`SubmissionNotifier.kt`:** purely local notifications for the user's own upload (channel `SUBMISSIONS`, `CATEGORY_STATUS`, tap opens the app with no extras).
- Progress: "Sınavın gönderiliyor…", foreground-info id `0x5ab`.
- Success: "Sınavın gönderildi" (`submission_notification_sent_*`).
- Failure: "Sınavın gönderilemedi".
- Outcome ids are `draftId.hashCode()`.
- It is suppressed when the form is watched and the process is STARTED.

**Tap routing end to end**
- `MainActivity.onCreate` (49): `if (savedInstanceState == null) inboxDeepLink.handle(intent?.extras)`.
- `MainActivity.onNewIntent` (70-74): `setIntent(intent)` then `handle(intent.extras)`.
- `AppRoot.ArchiveHost` (79-86): `LaunchedEffect(inboxRequested)` runs `router.push(ViFiRoute.Inbox)` then `inboxDeepLink.consume()`.
  - It works only once the signed-in, profile-complete host exists.
  - It has no `launchSingleTop`, so a tap while already on Inbox stacks a second Inbox.
- The manifest declares no `launchMode`, so MainActivity is standard.
  - Own PendingIntents use `SINGLE_TOP|CLEAR_TOP` to get `onNewIntent`.
  - How the system's tray intent delivers extras to an alive-but-backgrounded task is platform behaviour; verify on a device if per-notification routing relies on it.

### D.2 Device token

- **Interface:** `M/core/data/repository/DeviceTokenStore.kt` has only `suspend fun register(uid, token)`. It has no delete method.
- **Live:** `M/core/data/firebase/FirestoreDeviceTokenStore.kt:20-24` writes `firestore.collection("users").document(uid).collection("devices").document(token).set(mapOf("platform" to "android", "updatedAt" to FieldValue.serverTimestamp()), SetOptions.merge()).await()`.
  - The doc id is the FCM token.
  - There is no `token` field, no `appVersion` and no installId.
- **Fake:** `M/core/data/repository/fake/FakeDeviceTokenStore.kt` has `registrations: MutableList<Pair<uid, token>>` and `failure: Exception?`.
- **Bindings:** live `RepositoryModule` (`L/core/data/repository/RepositoryModule.kt:130-133`); mock `provideDeviceTokenStore()` (`K/.../RepositoryModule.kt:117-119`).
- **When written:** a sign-in or an already-signed-in app start (token read via `NotificationService.token()` → `FirebaseMessaging.token.await()`), and every `onNewToken` while signed in.
- **When deleted:** never (sign-out, account deletion).
- Roadmap N11 says "Çıkışta silme yapılmadı (opsiyonel)".
- **Test:** `T/core/notifications/PushTokenRegistrationTest.kt`: signed-in start writes once; signed-out writes nothing; sign-in writes; rotated token is written but a repeat is not; a failed write is retried on the next token.

### D.3 Announcements and inbox

- **`M/core/model/Announcement.kt`:** `Announcement(id, title, body, createdAt: Instant?)`.
- **`AnnouncementParser`:**
  - Field names are `title`, `body`, `createdAt`.
  - It trims text and drops a doc with neither title nor body.
  - `createdAt` accepts Instant, Date or Long.
  - `newestFirst` sorts descending with undated items last, then by id.
- **`FirestoreAnnouncementsReader`:** a `callbackFlow` snapshot listener on `announcements`, `orderBy(createdAt, DESC).limit(50)`, `addSnapshotListener`, `awaitClose{remove}`, `.conflate()`.
  - It maps `Timestamp` to `Date`.
  - Errors go through `FirestoreArchiveRepository.archiveError(code)`.
  - It does not read `kind`, `image`, `action` or `expiresAt`.
- **`AnnouncementsReader`:** `fun announcementsChanges(): Flow<List<Announcement>>`. **`FakeAnnouncementsReader`** has `SAMPLE` ids `a2` and `a1`, `set()` and an optional failure.
- **`M/core/data/announcements/AnnouncementsStore.kt`** (singleton)
  - `state: StateFlow<AnnouncementsState(announcements, isLoading=true, error)>` follows the live list only while `SessionState.SignedIn`.
  - A `retry` counter restarts the read.
  - `unreadCount = combine(state, readPreferences.readIds)` counts announcements whose id is not read.
  - `markAllRead()` writes all listed ids.
- **`AnnouncementReadPreferences`** (`M/core/data/preferences/AnnouncementReadPreferences.kt`): DataStore `stringSetPreferencesKey("announcements.readIds")`. It is device-local and not per-user.
  - It is not cleared on sign-out or account deletion: `LocalDataCleaner` clears only recents and files.
  - The fake is `FakeAnnouncementReadPreferences` (`preferences/fake/FakePreferences.kt:60-66`).
- **`M/feature/inbox/`**
  - `InboxItem(announcement, isUnread)` and `InboxUiState(items, isLoading=true, error, showPermissionOffBanner)`.
  - `InboxViewModel`:
    - `onScreenShown()` tracks the `Inbox` screen, sets the banner when `notifications.permission() == DENIED`, and freezes unread-on-arrival ids from the first non-loading list.
    - It then calls `store.markAllRead()`.
    - `onRetry()` calls `store.retry()`.
  - `InboxBadgeViewModel` exposes `store.unreadCount`.
  - `InboxDate.toInboxDate()` formats "d MMMM yyyy" in tr-TR.
- **`InboxScreen`:** `InboxScreen(onBack, viewModel)` is stateful and `InboxContent(state, onRetry, onOpenSettings, onBack)` is internal and stateless.
  - It uses `ViFiTopAppBar(title = inbox_title)`.
  - It shows `LoadingView`, `ErrorView(message, onRetry)`, `EmptyView(inbox_empty_title / inbox_empty_message)` or a `LazyColumn`.
  - Rows are `Card` with `surfaceContainer`, a title, an "Yeni" label if unread, a body and a date. There is no image, no action button and no tap handler on a row.
- **testTags:**
  - Inbox: `inbox.list`, `inbox.permissionBanner`, `inbox.openSettings`, `inbox.row.<id>`, `inbox.unread.<id>`.
  - Shared state views (`StateViews.kt`): `state.loading`, `state.error`, `state.retry`, `state.empty`.
  - Top bar: `topBar.backButton`.
  - Bell: `toolbar.inbox`.
- **Bell** (`M/feature/browse/BrowseScreen.kt:303-314`): `InboxBell` uses `BadgedBox` with `Badge(min(unread,99))`.
  - The content description is the plural `cd_inbox_open_unread` or `cd_inbox_open`.
  - It appears only on Home: `HomeScreen` passes `onInboxClick`, while `AppRoot`'s `browse` slot does not (lines 105-116).
- **Tests:**
  - `T/core/data/AnnouncementsStoreTest.kt` (5: load/unread, markAllRead, later-published is unread, signed out, failure message).
  - `T/core/model/AnnouncementParserTest.kt` (4).
  - `T/feature/inbox/InboxViewModelTest.kt` (3: screen/markRead/new marks; denied banner; allowed no banner).
  - There is **no** Compose UI test for Inbox or the permission prompt in `androidTest*`.

### D.4 Permission prompt

- **`M/core/model/NotificationPermission.kt`:** enum `NOT_DETERMINED, DENIED, AUTHORIZED`.
- **`NotificationService`** (`M/core/data/repository/NotificationService.kt`): `permission()`, `requestAuthorization()`, `token()`, `subscribe(topic)`, `unsubscribe(topic)`.
  - Live is `FirebaseNotificationService` (in `M/core/data/firebase`).
  - Below API 33, `permission()` maps "off" to DENIED.
  - On API 33 or later: granted → AUTHORIZED; `notificationsPrompted` true → DENIED; otherwise NOT_DETERMINED.
  - `requestAuthorization()` launches the system dialog via the requester, then sets `notificationsPrompted = true`.
  - Fake is `FakeNotificationService(initial, grantOnRequest)`, with `subscribedTopics` and `requestCount`.
- **`M/feature/notifications/NotificationPromptViewModel.kt`:**
  - On `init` it **subscribes to `all` on every start**, via `notifications.subscribe(PushTopics.ALL)` with the failure logged.
  - The dialog shows if `LaunchArg.NOTIFICATIONS_PROMPT` is set, or (`!notificationsPrompted && permission == NOT_DETERMINED`).
  - `onAllow()` hides it, calls `requestAuthorization()` and sets prompted. `onLater()` only sets prompted.
- **`NotificationPrompt.kt`:** an `AlertDialog` (not a sheet) with title `notification_prompt_title` "Bildirimleri aç" and message `notification_prompt_message` "Yeni sınavlar ve duyurulardan ilk sen haberdar ol. İstediğin zaman kapatabilirsin.". Buttons: `notification_prompt_allow` "İzin Ver", `notification_prompt_later` "Şimdi Değil". testTags: `notificationPrompt.dialog`, `.allow`, `.later`.
- **Persisted key:** `UserPreferencesRepository.notificationsPrompted`, DataStore `booleanPreferencesKey("notifications.prompted")`, device-wide. The fake is `FakeUserPreferencesRepository`.
- **Ordering caveat:** `NotificationPromptViewModel` takes only `NotificationService` and `UserPreferencesRepository`; it never consults onboarding state.
  - `NotificationPrompt()` is composed inside `ArchiveHost` (`AppRoot.kt:87`), which is the content of `OnboardingGate`.
  - The tour is a separate `Dialog` window shown over that content (`OnboardingScreen.kt:84`), so the prompt dialog can appear while the tour is up.
- **Manifest:** `POST_NOTIFICATIONS` is declared. **Inbox banner:** `inbox_permission_off_message` "Bildirimler kapalı. Yeni duyuruları kaçırmamak için aç." and `inbox_permission_open_settings` "Ayarlar", opening `ACTION_APP_NOTIFICATION_SETTINGS`.
- **Test:** `T/feature/notifications/NotificationPromptViewModelTest.kt` (5: shows and joins `all`; already allowed still subscribes; allow; later; already prompted).

### D.5 Wiring and gate order

- **Gates** (`M/app/AppRoot.kt`), outermost first:
  1. `AppUpdateGate` (47-53; a required update replaces everything, an optional update is a dialog).
  2. `SessionRoot` (55-70): `Unknown` shows `session.loading`, `SignedOut` shows `LoginScreen`, and `SignedIn` is wrapped in `key(user.id)`.
  3. `ProfileGate` (`ProfileGate.kt`: `profileGate.loading`, `Absent` → `ProfileCompletionScreen`, `Present` → content, `Failed` → `ErrorView`).
  4. `OnboardingGate` (tour as `Dialog` over content).
  5. `ArchiveHost` (74-162).
- **Inside `ArchiveHost`:** `NotificationPrompt()`, `ArchivePrefetch()`, `PaidUnlockRecovery()`, then `ViFiNavHost` with these slots:
  - `home`, `browse`, `exam(path, kind)`, `account`, `editProfile`, `inbox`, `about`, `addExam`, `paywall`, `moderation`, `moderationDetail`, `submissionPreview`.
- **Dialog and sheet hosts today:** `NotificationPrompt` (AlertDialog), the optional update dialog, `ExamUnlockSheet` (in Browse), and `ModalBottomSheet`-style flows inside screens. There is no app-level sheet host.
- **Routes** (`M/core/navigation/ViFiRoute.kt`, all `@Serializable`):
  - `Home`, `Browse(ids, names)`, `Exam(ids, names, kind: ExamKind)`, `Account`, `Inbox` (44-46), `About`, `EditProfile`, `AddExam(ids, names)`, `Paywall`, `Moderation`, `ModerationDetail(submissionId)`, `SubmissionPreview(submissionId)`.
  - `ViFiNavHost.kt:26` gives `inbox` a default `{}` slot.
- **`ViFiRouter`:** `push(route)` (no `launchSingleTop`), `pop()`, `popToRoot()`, `popTo(path)`, and `openExam(examPath, kind)` (41-45).
  - `openExam` calls `popToRoot`, pushes `Browse` for each ancestor, then `Exam`.
  - There is no "open a route with a payload" dispatcher, and no Account → notification-settings route.
- **`SessionTeardown`** (`M/core/data/session/SessionTeardown.kt:47-57`), on `SignedOut` only: `profileStore.reset()`, `analytics.setUser(null)`, `recents.clear()`.
  - The KDoc says "Wallet, unlocks and announcements resets join here when their stores land". The stores follow the session themselves; the read-id set is not reset.
  - There is no token unregister and no topic unsubscribe.
- **`SessionStore`:** `state: StateFlow<SessionState {Unknown, SignedOut, SignedIn(user)}>` and `user`.
- **Account deletion** (`AccountViewModel`):
  - `deleteAccount()` (122-141) runs `profileService.deleteProfile(uid)` then `authService.deleteAccount()`, with re-verification by SMS code on `RequiresRecentLogin`.
  - `didDeleteAccount()` (232-235) tracks `AccountDeleted` and runs `localDataCleaner.purgeAccountData()` in `@ApplicationScope`.
  - It never touches `users/<uid>/devices`.
  - After `deleteAccount()` the user is gone, so a server-side cleanup or an earlier delete is needed.

### D.6 Account screen (`M/feature/account/*`)

`AccountContent` is a `LazyColumn` (`account.list`) with these items in order (`AccountScreen.kt:159-243`):
- `profile` (`account.editProfile`).
- Either `premiumActive` (`account.premium.active` plus `account.premium.manage`) or `premiumUpgrade` (`account.premium.upgrade`, only if `onUpgradePremium != null`) followed by `coins` (`account.coinBalance`).
- `phone` (`account.phone`).
- `moderation` (`account.moderation`, only if `state.isAdmin`; strings `moderation_account_row_title` "Moderasyon" and `moderation_account_row_subtitle`).
- `about` (`account.about`).
- `signOut` (`account.signOut`; dialog tags `account.signOut.confirm` and `.cancel`).
- `delete` (`account.delete`, `account.delete.progress`; `account.delete.confirm` and `.cancel`).
- Optional `reverification` (`account.reauth.code`, `.verify`, `.cancel`) and `account.error`.

Facts that matter for the port:
- There is no notifications row and no "Bildirimler" setting.
- `AccountUiState(profileName, formattedPhoneNumber, isWorking, error, isReverifying, codeText, resendAvailableAt, isAdmin, coinBalance, isSubscribed)`.
- `AccountContent` has a long parameter list with defaults for optional callbacks (`onAbout`, `onModeration`, `onUpgradePremium`, `onManageSubscription`).
- **Admin detection:** `AuthService.isCurrentUserAdmin()`; live `FirebaseAuthService` (line ~64-74) reads `user.getIdToken(false).claims["admin"] == true`.
  - It uses the cached token, with no force refresh, and returns false on error.
  - It is used by `AccountViewModel.onScreenShown` (line 111), `AnalyticsIdentity.identify` (line 50, once per user) and `ExamDeletionViewModel` (line 60).
  - Mock: `FakeAuthService(isAdmin)` from `LaunchArg.ADMIN`.
- **Tests:** `T/feature/account/AccountViewModelTest.kt` (about 25 cases) and `app/src/androidTest/.../feature/account/AccountContentTest.kt` (Compose, `createComposeRule`).

### D.7 Exam lock and exam routes

- **`M/core/model/ExamLock.kt`:** `FREE_COUNT = 2`, `UNLOCK_COST = 100`.
  - `freeExamIds(orderedExams)` takes the last 2 of the newest-first list, mapped to `path.components.last()`.
  - `isAccessible(exam, freeIds, unlockedIds)` is true when a non-exam or id-less item, or an id in free or unlocked.
  - `isLocked` is true for an exam with files that is not accessible.
- **Stores:**
  - `ExamUnlocksStore.unlockedIds` (`M/core/data/coins/ExamUnlocksStore.kt`), `isUnlocked(id)`, `markUnlocked(id)`. It loads per signed-in uid and clears on user change.
  - `SubscriptionStore.isSubscribed` (`M/core/data/billing/SubscriptionStore.kt`) is tied to the Google account and is not reset on sign-out.
  - `CoinWalletStore.wallet`.
- **Where applied:**
  - `ExamUnlockUiState.lockedPaths(items)` (`M/feature/browse/ExamUnlockUiState.kt:35-39`) returns an empty set for a subscriber, otherwise `ExamLock.freeExamIds(fullList)` then `isLocked`.
  - `BrowseScreen` (about lines 219 and 358-364) routes a locked tap to `onLockedExamTap`, which opens the unlock sheet.
  - `ExamUnlockViewModel` handles coins (`unlockExam`), money (`purchaseExamUnlock`) and paywall.
- **Viewer:** `ExamViewerViewModel(@Assisted path, routeKind)` loads `archiveRepository.document(path)`, which reads `exams/<last component>`.
  - There is **no** lock check in `feature/examviewer`.
  - `Home` recents also bypass it, which is acceptable because the user already viewed them.
- **Route args:** `Exam(ids: List<String>, names: List<String>, kind)`, built from `ArchivePath(components, displayNames)` (`M/core/model/ArchivePath.kt`). The path is `<university>/<faculty>/<department>/<lesson>/<exam>`.
  - `ArchiveRepository` (`M/core/data/repository/ArchiveRepository.kt`) has `items(path)` and `document(path)` only.
  - **There is no `examById` or examId→path/names resolver.** `catalogNodes` carries only `parentId`, so ancestors would be a 4-hop lookup.
  - To reuse the lock rule from a notification you need the lesson's `items(path.parent)` for `freeExamIds`, plus `unlockedIds` and `isSubscribed`.
  - Until `ExamUnlocksStore` has loaded, a locked-by-default result is the safe choice.

### D.8 Analytics

- **Events** (`M/core/model/AnalyticsEvent.kt:4-56`, 23 sealed members, indexes 0-22 in `AnalyticsLimitsTest`):
  - `ScreenView`, `Browse`, `ExamOpened`, `RecentExamOpened`, `ExamUnlockSheetShown`, `EarnCoinsTapped`, `ExamUnlocked`, `ExamUnlockFailed`.
  - `ExamSubmitted`, `ExamSubmitFailed`, `LoginCodeRequested`, `LoginSucceeded`, `LoginFailed`, `OnboardingCompleted`, `ProfileCompleted`, `ProfileUpdated`.
  - `SearchUsed`, `SignedOut`, `AccountDeleted`, `UpdatePromptShown`, `UpdateAction`, `SubmissionApproved`, `SubmissionRejected`.
- **Notification-related events: none** except the `Inbox` screen view (`AnalyticsScreen.INBOX`). Roadmap N13 note: permission-result and tap events were not added.
- **Wire format** (`M/core/model/AnalyticsEventMapping.kt`): the `firebaseName` and `firebaseParameters` exhaustive `when`s (5-30, 33-67). Values are String, Long or Boolean. `AnalyticsScreen` constants (86-104) include `PAYWALL`.
- **Tracker:** `AnalyticsTracker.track(event)` and `setUser(user)`; live `FirebaseAnalyticsTracker` (`L/core/data/firebase/`), fake `FakeAnalyticsTracker` (mock). Helper: `AnalyticsTracker.trackScreen(name)`.
- **`AnalyticsLimitsTest`** (`T/core/model/AnalyticsLimitsTest.kt`):
  - `samples` has one instance per event.
  - `kindIndex` is an exhaustive `when` that must list every event, and `samples cover every event exactly once` asserts `(0..22)`.
  - Limits: event name ≤ 40 characters matching `[a-zA-Z][a-zA-Z0-9_]*`, no `firebase_`/`google_`/`ga_` prefix, unique names; ≤ 25 parameters; parameter name ≤ 40; parameter value ≤ 100; user-property name ≤ 24; user-property value ≤ 36.
  - The screens test asserts exactly 15 distinct screen names.
  - The reasons test requires `[a-z_]{1,100}`.
  - Adding an event, or a screen, means updating this test.
- Related: `AnalyticsEventMappingTest`, `FakeAnalyticsTrackerTest`, `AnalyticsIdentityTest`, `AnalyticsUserTest`.

### D.9 Launch args and DI per flavor

- **`LaunchArg`** (`M/core/util/LaunchArgs.kt:11-23`), intent boolean extras read in debug builds only (`LaunchArgs.update`, called from `MainActivity.onCreate` line 51):
  - `SIGNED_OUT` (`vifi_signed_out`), `PHONE_AUTH_TESTING`, `FORCE_UPDATE`, `OPTIONAL_UPDATE`, `PROFILE_INCOMPLETE`, `ADMIN` (`vifi_admin`).
  - `NO_COINS`, `SHOW_ONBOARDING`, `NOTIFICATIONS_PROMPT` (`vifi_notifications_prompt`), `DELETE_REQUIRES_REAUTH`, `SUBSCRIBED` (`vifi_subscribed`).
  - `vifi_admin` is now wired into `FakeAuthService` (the CLAUDE.md note that it is merely defined is stale).
- **Mock consumption** (`K/core/data/repository/RepositoryModule.kt`):
  - `provideWalletReader` (NO_COINS), `provideOnboardingPreferences` (SHOW_ONBOARDING), `provideNotificationService` (NOTIFICATIONS_PROMPT → `NOT_DETERMINED`), `provideProfileService` (PROFILE_INCOMPLETE).
  - `provideAuthService` (SIGNED_OUT, DELETE_REQUIRES_REAUTH, ADMIN).
  - `BillingModule` (SUBSCRIBED).
  - `NotificationPromptViewModel` also reads `NOTIFICATIONS_PROMPT` directly (`forced`).
- **Mock bindings relevant here:** `FakeAnnouncementsReader`, `FakeAnnouncementReadPreferences`, `FakeNotificationService`, `FakeDeviceTokenStore`, `FakeUserPreferencesRepository`, `FakeAnalyticsTracker`. The mock manifest removes `FirebaseInitProvider`.
  - `MockGraphTest` (`androidTestMock`) asserts fakes only and that `FirebaseApp.getApps(...)` is empty.
  - **Every new live binding needs a mock one, or the mock build breaks.**
  - There is no mock binding for Functions or Storage; interfaces plus fakes are the pattern.
- **Live bindings** (`L/core/data/repository/RepositoryModule.kt`): `FirestoreAnnouncementsReader`, `DataStoreAnnouncementReadPreferences`, `FirebaseNotificationService`, `FirestoreDeviceTokenStore`, `FirebaseExamUnlockService`, and so on.
- **`BackendStartup`** exists per flavor (`L/.../BackendStartup.kt`, `K/.../BackendStartup.kt`).

### D.10 Firebase providers and callable pattern

- **Live `FirebaseModule`** (`L/core/data/firebase/FirebaseModule.kt:31-69`):
  - `FirebaseFirestore.getInstance(app, "vifi")` with a persistent local cache.
  - `FirebaseFunctions.getInstance(app, "europe-west1")`.
  - `FirebaseStorage.getInstance()`, `FirebaseAppCheck`, `FirebaseMessaging`, `FirebaseAnalytics`, `FirebaseAuth`, RTDB.
  - Constants live in `M/core/util/FirebaseConstants.kt`: `FIRESTORE_DATABASE_ID = "vifi"`, `FUNCTIONS_REGION`, `STORAGE_HOST`, `COLLECTION_SUBMISSIONS`.
- **Callable pattern** (`FirebaseExamUnlockService`, `FirebaseExamPurchaseBackend`, `FirebaseLoginDiagnosticsReporter`, all in `main` and compiled into both flavors but bound only in live):
  - A small domain interface in `main`, with a live class taking `FirebaseFunctions`.
  - `functions.getHttpsCallable(NAME).call(mapOf(...)).await()`.
  - Parse `response.getData()` with a pure helper in `internal companion object`.
  - Rethrow `CancellationException`, map `FirebaseFunctionsException.Code` and `IOException` to a sealed Turkish-message domain error (`ExamUnlockError`, `SubscriptionError`), and fall back to `GENERIC_ERROR_MESSAGE`.
  - Log unmapped errors with `LogCategory.APP.log.e`.
  - Fire-and-forget variant: `FirebaseLoginDiagnosticsReporter` (app-scope launch, swallow errors, throttle).
  - Tests: `FirebaseExamUnlockServiceTest` and `FirebaseLoginDiagnosticsReporterTest` in `T/.../firebase`, plus `androidTest` error-mapping tests.
  - `Throwable.userMessage` (`M/core/model/UserMessage.kt`) is the single message fallback.
- **Image download from Storage:**
  - `StorageUrl.canonical(storagePath, bucket)` (`M/core/util/StorageUrl.kt:14-17`) returns `https://firebasestorage.googleapis.com/v0/b/<bucket>/o/<encoded>?alt=media`.
  - The `@ExamFilesClient` OkHttp client has `StorageAuthInterceptor` (adds `Authorization: Firebase <idToken>` and `X-Firebase-AppCheck`, only for the app's own bucket, with a 401/403 retry). The live client is in `L/core/data/files/ExamFilesNetworkModule.kt`.
  - The Coil `ImageLoader` is bound to that client (`M/core/di/ExamFilesModule.kt`, disk cache `cacheDir/ExamFiles/Images`, 512 MB).
  - `RemoteFileLoader` offers `localFile(url, fileName)`, `pruneDiskCache()` and `purge()`.
  - So `notificationImages/<id>.jpg` can be fetched by building the canonical URL from its storagePath and loading it through the Coil `ImageLoader` or `localFile`. The bucket comes from `storage.reference.bucket`.
  - There is no existing "download by storagePath" wrapper, and the FCM `image` field would need a public URL.

### D.11 Moderation

- **Present.** Account row "Moderasyon" (`account.moderation`, admin only), then `Moderation` → `ModerationDetail(submissionId)` → `SubmissionPreview(submissionId)`.
- **Screens:** `ModerationScreen` (`moderation.list`, `moderation.empty`, `moderation.row.<id>`, `moderation.footer`) and `ModerationDetailScreen` (`moderation.detail`, `.approve`, `.reject`, `.preview`, `.error`, `.progress`, `moderation.reject.reason|confirm|cancel`, `moderation.approved.message|ok`).
- **Serverless on the admin's device:** `AdminModerationService` + `ModerationBackend` (`M/core/data/moderation/*`).
  - `approve(record)` is one atomic `commitApproval` batch: catalog nodes, the exam doc, the coin credit (`wallets/<owner>` plus a `ledger/<submissionId>` entry) and `status=approved`.
  - `reject(id, reason)` sets the status to `rejected`.
- **No moderation notification is sent or created anywhere today.** `grep -i notif` over `feature/moderation`, `core/data/moderation` and `core/data/submission` hits only `SubmissionNotifier`.
- `SubmissionNotifier` shows only the user's own upload outcomes: "Sınavın gönderildi", "Sınavın gönderilemedi", and the progress notification. These do not overlap with a server "onaylandı / reddedildi" or an admin "Yeni sınav gönderimi".
- ANDROID-PARITY-ROADMAP.md:494 recorded the parity decision that a rejection writes no inbox entry from the client.

### D.12 Dependencies and manifest

- **`gradle/libs.versions.toml` / `app/build.gradle`:**
  - Firebase BoM 33.5.1 with analytics, auth, firestore, database, storage, functions, messaging, appcheck-playintegrity, plus appcheck-debug in debug.
  - Coil 2.7.0 (`coil-compose`) and telephoto-zoomable-image-coil.
  - DataStore preferences 1.1.1, WorkManager 2.9.1, OkHttp 4.12.0.
  - Billing 9.1.0 (`liveImplementation` only).
  - `kotlinx-coroutines-play-services`.
  - Compose BoM 2024.10.01, Navigation 2.8.3, Hilt 2.52, Kotlin 2.0.21, AGP 8.13.2.
  - **No Maps, Places, play-services-location or Maps Compose**, and no `firebase-installations` artifact declared (it comes in transitively).
- **`app/src/main/AndroidManifest.xml`:**
  - Permissions: `POST_NOTIFICATIONS`, `INTERNET`, `ACCESS_NETWORK_STATE`, `DETECT_SCREEN_CAPTURE`. The ad-id permissions are removed. **No location permission.**
  - Meta-data: `com.google.firebase.messaging.default_notification_channel_id = announcements`, `default_notification_icon = @drawable/ic_notifications_black_24dp`, `default_notification_color = @color/notification_accent`. Analytics auto screen reporting is off.
  - `<service .core.notifications.ViFiMessagingService exported=false>` with `com.google.firebase.MESSAGING_EVENT`.
  - `<activity .app.MainActivity exported=true windowSoftInputMode=adjustResize>`. WorkManager initializer is removed.
  - **No FileProvider** and no Maps API-key meta-data.

### D.13 Tests and strings

- **Notification-area tests:**
  - `AnnouncementsStoreTest`, `AnnouncementParserTest`, `NotificationContentTest` (data fallback, and `opensInbox` for our flag or an FCM launch intent), `PushTokenRegistrationTest`, `InboxViewModelTest`, `NotificationPromptViewModelTest`.
  - `AppLifecycleHooksTest` (badge cleared on every foreground).
  - `SessionTeardownTest` (profile, recents and analytics identity reset; no token or announcement coverage).
  - `PreferencesRepositoriesTest` (`notificationsPrompted`).
  - `LaunchArgsTest` (2 cases).
  - `ExamLockTest` (7 cases).
  - `AccountViewModelTest`, `AccountContentTest`.
- **No tests exist for:** `ViFiMessagingService`, `FirestoreDeviceTokenStore` and `FirestoreAnnouncementsReader`.
- **Strings:**
  - `strings_notifications.xml`:
    - `notification_channel_general_description` "Duyurular ve yeni sınav bildirimleri".
    - `inbox_title` "Bildirimler", `inbox_empty_title` "Henüz bildirim yok", `inbox_empty_message` "Yeni duyurular burada görünür.", `inbox_unread_badge` "Yeni".
    - `cd_inbox_open` "Bildirimler", plural `cd_inbox_open_unread` "Bildirimler, %1$d okunmamış".
    - `inbox_permission_off_message`, `inbox_permission_open_settings` "Ayarlar".
    - `notification_prompt_title|message|allow|later` (values in section 4).
  - `strings.xml`:
    - `notification_channel_general_name` "Genel bildirimler" (line 3).
    - Account: `account_title` "Hesap", `account_profile_label` "Profil", `account_edit_profile` "Profili Düzenle", `account_coin_balance_label` "Coin bakiyen", `account_phone_label` "Telefon numarası", `account_sign_out_button` "Çıkış Yap", `account_delete_button` "Hesabı Sil", `account_about_row` "Hakkında".
    - Account premium: `account_premium_title` "ViFi Premium", `account_premium_active`, `account_premium_manage`, `account_premium_upgrade_title`.
    - Account sign-out and delete: `account_sign_out_dialog_*`, `account_delete_*`, `account_reauth_*`.
  - `strings_add_exam.xml:69-74`: `notification_channel_submissions_name|description`, `submission_notification_progress_title|sent_title|sent_message|failed_title`.
  - `strings_moderation.xml:39-40`: `moderation_account_row_title|subtitle`.
  - **No `announcement_` strings exist.** There is no marketing-consent or notification-settings copy.

### D.14 Docs

- **ANDROID-PARITY-ROADMAP.md:**
  - BE-11 (line 77): "native FCM: onNewToken + `all` topic, POST_NOTIFICATIONS istemi, kanal. Canlı push teslimi cihazda denenmedi."
  - N1-N13 (lines 264-276), all done. Examples:
    - N9: `cancelAll()` because background FCM notifications cannot be addressed one by one.
    - N11: `{platform:'android', updatedAt}` merge under `devices/{token}`, "Çıkışta silme yapılmadı (opsiyonel). Canlıda doğrulanmadı."
    - N13: "izin-sonucu/dokunma olayları eklenmedi (ortak AnalyticsEvent çakışması riski, opsiyonel)".
  - Line 413: tap routing with `FLAG_IMMUTABLE`, done.
  - Line 494: reject writes no inbox entry on iOS (parity).
- **ANDROID-PARITY-DETAIL.md:** lines 2664+ hold the Bildirimler section. Backend contract was "announcements/<id> {title, body, createdAt}, write:false"; `users/<uid>/devices/<token>` was optional. The topic `all` name is immutable.
- **PLAY-DATA-SAFETY.md:21:** declares the FCM token under "Cihaz veya diğer kimlikler" (collected, linked, required). **Nothing about location or marketing consent**, so an admin Maps/location feature or a marketing-consent record would need a Data Safety update (the form submission is the owner's).
- **CLAUDE.md:**
  - Rules and Cloud Functions live in `ViFi-iOS/firebase/` and Android only calls them; live-Firebase deploys need explicit approval.
  - The mock flavor must never bind live Firebase.
  - Any new `AnalyticsEvent` needs mapping and test updates.
  - `testTag`s are contracts.
  - New code follows the `.claude/rules` MVVM, `strings.xml` and versioned-comment rules.
- **Shared contract outside this repo (read-only peek):** `ViFi-iOS/NOTIFICATIONS.md` §3.5 specifies `devices/{installationId}` as `{token, platform, appVersion, updatedAt}`, deleted by the client on sign-out, plus `settings/notifications` and `settings/inbox`. `firestore.rules` already encodes these shapes. I did not verify whether those rules are deployed to live.
