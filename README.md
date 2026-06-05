# Termin-Standortwächter – Android-App (CalWatch)

Native Android-App in Kotlin. Sie lädt deine Kalenderdatei (.ics) von einer URL,
prüft etwa alle 15 Minuten im Hintergrund deinen Standort und schickt eine
**Benachrichtigung**, wenn du dem nächsten anstehenden Termin-Ort zu fern bist –
auch wenn die App geschlossen ist.

## APK bauen – ganz ohne lokale Werkzeuge (empfohlen)

Du brauchst nur einen kostenlosen GitHub-Account und dein Handy.

1. Auf GitHub ein neues, leeres Repository anlegen (z. B. `calwatch`).
2. Den **gesamten Inhalt dieses Ordners** hochladen
   (im Repo auf „Add file → Upload files", den entpackten Ordnerinhalt hineinziehen,
   committen). Wichtig: die Datei `.github/workflows/build-apk.yml` muss mit dabei sein.
3. GitHub baut die App automatisch. Im Reiter **Actions** den laufenden Build
   („Build APK") öffnen und ~3–5 Minuten warten, bis er grün ist.
4. Unten beim Build unter **Artifacts** die Datei `calwatch-debug-apk` herunterladen
   (das ist ein ZIP mit der `app-debug.apk` darin).
5. Die `.apk` aufs Handy übertragen und öffnen. Android fragt nach der Erlaubnis,
   Apps aus dieser Quelle zu installieren → erlauben → installieren.

> Startet der Build nicht von selbst: im Reiter **Actions** „Build APK" → **Run workflow**.

## App einrichten

1. **Kalender-URL** eintragen (z. B. die geheime iCal-Adresse aus Google Kalender,
   den iCloud-/Nextcloud-Freigabelink oder die URL von deinem Raspberry Pi).
2. **Warn-Radius** in Metern (Standard 500) und **Vorlaufzeit** in Minuten
   (Standard 90 – ab so vielen Minuten vor Terminbeginn wird geprüft).
3. **1 · Berechtigungen erteilen** antippen. Wichtig für den Hintergrundbetrieb:
   beim Standort **„Immer zulassen"** wählen (sonst warnt die App nur bei offener App).
   Notfalls über **App-Einstellungen öffnen** nachträglich setzen.
4. **2 · Überwachung starten**.
5. **Jetzt testen** löst sofort eine Prüfung aus.

## Wie die Warnung funktioniert

Geprüft wird der zeitlich nächste Termin, der Koordinaten hat und innerhalb der
Vorlaufzeit liegt. Liegt deine aktuelle Entfernung über dem Radius, kommt eine
Benachrichtigung. Pro Termin wird höchstens alle 30 Minuten erneut gewarnt.

## Gut zu wissen

- **Prüfintervall:** Android erlaubt für Hintergrundaufgaben mindestens 15 Minuten.
  Eine sekundengenaue Live-Warnung ist im Hintergrund vom System nicht vorgesehen.
- **Standortgenauigkeit:** Es wird der zuletzt bekannte Standort verwendet
  (stromsparend, aber evtl. ein paar Minuten alt).
- **Adressen → Koordinaten:** über OpenStreetMap/Nominatim (max. ~1 Anfrage/Sek.,
  Ergebnisse werden zwischengespeichert). Termine mit eigenem `GEO`-Eintrag sind am
  zuverlässigsten.
- **Akku-Optimierung:** Manche Hersteller (Samsung, Xiaomi, Huawei …) beenden
  Hintergrund-Apps aggressiv. Falls Warnungen ausbleiben, die App in den
  Akku-Einstellungen von der Optimierung ausnehmen.
- **„Debug"-APK:** zum Selbstnutzen völlig ausreichend. Für den Play Store bräuchte
  es eine signierte Release-Variante.

## Lokaler Build (Alternative, falls gewünscht)

Mit Android Studio: Ordner öffnen → „Run". Oder per Kommandozeile mit JDK 17 und
Android-SDK: `gradle assembleDebug` (die fertige APK liegt dann unter
`app/build/outputs/apk/debug/`).
