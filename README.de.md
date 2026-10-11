# Lowfin · Jellyfin für schwache Android-TVs

## **100 % KI-GENERIERT**

[Vollständiger Changelog ab 0.1.0](CHANGELOG.md) · [GitHub-Builds](https://github.com/303inmyheart/Lowfin/actions/workflows/android.yml)

Native Android-TV-App mit monochromer Oberfläche, lokalem Bibliothekscache und Media3-Player. Für Android 11 ausgelegt; Mindestversion Android 8.0. Aktuelle Version: **0.7.14**.

## Installieren

Die fertige APK liegt unter `dist/Lowfin-0.7.0.apk`. Auf den Fernseher kopieren, dort für den verwendeten Dateimanager die Installation unbekannter Apps erlauben und die APK öffnen. Alternativ bei eingerichteter ADB-Verbindung:

```bash
adb install -r dist/Lowfin-0.7.0.apk
```

Beim ersten Start Serveradresse inklusive `http://` oder `https://`, Benutzername und Passwort eingeben. Server unter einem Unterpfad sind möglich, z. B. `https://example.org/jellyfin`. Das Passwort wird nicht gespeichert; für die automatische Anmeldung wird der Sitzungstoken mit dem Android Keystore verschlüsselt abgelegt.

## Neu in 0.7.0

- Englisch ist die Standardsprache, unabhängig von der TV-Systemsprache. Deutsch ist über **Einstellungen → Sprache** auswählbar; die Sprachwahl steht auch auf der Anmeldeseite bereit. Übersetzt sind Navigation, Player, Trailer, Dialoge, Fehler, Cache-Status und TV-Kanäle. Server-Medientitel und Beschreibungen bleiben unverändert.
- **Genre-Reihen** ist ein eigenes Einstellungsmenü: einzelne Genres pro Konto an-/ausschalten, alle Genre-Reihen ausblenden und die Ansicht mit nur einer fokussierten Reihe wechseln.
- **Startseite und Darstellung** enthält Fortsetzen/Next Up an/aus, 0–5 Empfehlungsreihen, 4/6/8/10 Posterspalten, Dauer der Zufallsanimation, zufälligen oder festen Akzent, Wechselintervall und automatisches Vorladen mit Aktualisierungsintervall.
- **Erweiterte Wiedergabe** bietet separate Intro-/Outro-Automatik, Untertitel beim Start, Audiosprache, Sprungweite, Ausblendzeit des Players und getrennte maximale RAM-Puffer für Filme und Live TV. Die Speicherreserve wird weiter automatisch geschützt. Untertitel bleiben standardmäßig aus.
- Ausgeschaltete Reihen werden bei der Fernbedienungsnavigation übersprungen; die Empfehlungsanzahl gilt auch für Android-TV-Kanäle.

## Neu in 0.6.0

- Android-TV-Kanalnamen tragen keinen „Lowfin ·“-Vorsatz mehr. Bestehende Kanäle werden umbenannt und behalten ihre IDs.
- **Einstellungen → TV-Filmzeile / TV-Serienzeile · Bibliotheken wählen** erlaubt getrennte Mehrfachauswahl. Standardmäßig werden alle Bibliotheken berücksichtigt; Karten werden gleichmäßig aus den gewählten Bibliotheken gemischt.
- Die Film-Slotanimation zeigt echte Titel aus der Bibliothek, mit ruhigerer Gestaltung, hervorgehobenem Treffer und kurzer Abschlussanimation. Danach öffnet automatisch die Detailseite.
- Akzente verwenden einen festen Pool aus zwölf gedämpften Farben. Beim Öffnen der App wird neu gewählt; nach einer Stunde aktiver Nutzung wechselt die Farbe ohne Neuaufbau der Oberfläche. Zeit im Hintergrund zählt nicht mit.
- Bereits geladene externe Bewertungen bleiben dauerhaft im Account-Cache, auch nach Neustarts und nach Änderungen der Anzeigeauswahl. Neue Werte werden nur bei Bedarf geladen, niemals im Bibliotheks-Precache. Bereits vorhandene Sieben-Tage-Einträge bleiben ebenfalls erhalten. Leere Ergebnisse und Fehler werden kurz zwischengespeichert.

## Neu in 0.5.3

- Empfehlungen verwenden ausschließlich **Filme als Ausgangspunkt und als Vorschläge**. Schauspieler-, Regisseur- und Favoriten-Kategorien aus Jellyfin werden auch im alten Cache verworfen. Fehlende Film-Reihen werden aus Jellyfins ähnlichen Filmen zu weiteren tatsächlich gesehenen Filmen ergänzt.
- Die fünf Empfehlungen werden zusätzlich als eigene **Android-TV-Kanäle** veröffentlicht. Ihre Namen enthalten den jeweiligen gesehenen Film; Filmkarten öffnen die Detailseite. Aktivierung unter **Einstellungen → TV-Kanal aktivieren · Film-Empfehlungen 1–5** oder im TV-Hauptmenü unter **Kanäle anpassen**. Ohne passende Filme wird kein neuer leerer Kanal angelegt; veraltete Karten werden entfernt, bestehende Kanal-IDs erhalten.

- Die fünf **„Weil du … gesehen hast“**-Reihen verwenden jetzt dieselbe Fokusansicht wie die Genres: nur die ausgewählte Reihe, Filmhintergrund, Titel, Beschreibung und die eingestellten Bewertungsbadges. Lange Beschreibungen bleiben innerhalb des HD-Bildschirms. Hoch/Runter wechselt weiterhin zwischen allen Empfehlungen und Genres.
- Die Reihen-Navigation verhindert zusätzliches automatisches Scrollen durch Android, verarbeitet schnelle Hoch/Runter-Eingaben anhand der gewählten Reihe und erhält einen gültigen Kartenfokus beim Nachladen kürzerer Reihen. Stressprüfungen nutzen fünf Empfehlungen, sechs Genres und jeweils 40 Filmkarten.
- Android-TV-Kanäle werden fest in der Reihenfolge **Fortsetzen → Als Nächstes → Filme → Serien** angelegt. Der Standardkanal ist immer **Fortsetzen**, auch wenn noch keine angefangenen Titel vorhanden sind.
- Bei älteren Installationen mit zuerst angelegtem Next-Up-Kanal werden die Rollen der beiden bestehenden Lowfin-Zeilen einmalig korrigiert. IDs, Sichtbarkeit und Watch-Next-Karten bleiben erhalten; die Fortsetzen-Karten und Next-Up-Karten erhalten jeweils ihre passenden Links. Eine vom Nutzer im Launcher geänderte Zeilenposition kontrolliert weiterhin Android TV.

## Neu in 0.5.2

- Fünf Film-Empfehlungsreihen **„Weil du … gesehen hast“** stehen direkt unter **Als Nächstes** und vor den angehefteten Genres. Jede Reihe basiert auf einem anderen gesehenen Film. Vorschläge kommen aus [Jellyfins `/Movies/Recommendations`](https://raw.githubusercontent.com/jellyfin/jellyfin/master/Jellyfin.Api/Controllers/MoviesController.cs); fehlen passende Kategorien, werden für weitere gesehene Filme Jellyfins `/Items/{id}/Similar`-Vorschläge genutzt. Favoriten-Kategorien werden nicht als gesehen ausgegeben.
- Empfehlungen werden dauerhaft pro Server/Account gespeichert und beim Start sofort aus dem Cache geladen. Serverfehler erhalten vorhandene Vorschläge. Ohne gesehenen Film beziehungsweise ohne passende Vorschläge bleiben die betreffenden Reihen ausgeblendet; Hoch/Runter überspringt sie.
- Kleine 2:3-Poster öffnen die Film-Detailseite. **Mehr anzeigen** am Ende öffnet alle bis zu 24 Vorschläge der jeweiligen Reihe. Empfehlungen werden im Hintergrund und bei Rückkehr nach zwei Minuten aktualisiert.
- Neues monochromes Logo: ein zurückhaltendes großes **L** hinter dem gut lesbaren **LowFin**-Schriftzug. Enthalten sind normale und adaptive Launcher-Icons, ein breites TV-Banner, hochauflösende PNGs und bearbeitbare SVG-Dateien unter `design/`.

## Neu in 0.5.1

- Genre-Reihen haben kleinere 2:3-Poster. Titel, Bewertungen und Beschreibung nutzen den verbleibenden HD-Bildschirm; lange Beschreibungen werden passend zur verfügbaren Höhe mit „…“ gekürzt.
- Links/Rechts navigiert auch zu noch nicht dargestellten Postern. Hoch/Runter erreicht die letzte Genre-Reihe, behält die Kartenposition und zeigt weiterhin nur die fokussierte Reihe.
- Untertitel starten ausgeschaltet, einschließlich automatisch markierter und erzwungener Spuren. Live-Untertitel werden direkt im Player umgeschaltet, ohne die Jellyfin-Live-Sitzung neu zu öffnen; auch Live-Audiospuren werden lokal gewechselt.
- Der adaptive RAM-Puffer behält ein begrenztes Nachfüllziel, wenn Wiedergabe den Puffer leert. Bei Speicherknappheit bleiben Notreserven erhalten. Wiederverbindungen erzeugen keine mehrfachen Überwachungs-Timer.

## Neu in 0.5.0

- Live-TV verwendet eine eigene Route nach dem [aktuellen Moonfin-Core-Resolver](https://github.com/Moonfin-Client/Moonfin-Core/blob/main/packages/playback_jellyfin/lib/src/jellyfin_media_stream_resolver.dart): geöffneten HTTP-Quellpfad bevorzugen, danach `DirectStreamUrl`, danach aktive Jellyfin-Streamroute. `AutoOpenLiveStream=true` bleibt aktiv; falls der Server trotzdem eine ungeöffnete Quelle liefert, öffnet Lowfin sie ausdrücklich über `/LiveStreams/Open`. Interne `LiveStreamFiles`-Adressen werden auf die konfigurierte Serveradresse inklusive Reverse-Proxy-Unterpfad umgeschrieben. Fehler wechseln zur nächsten verfügbaren Route derselben geöffneten Sitzung. Jellyfin-Authentifizierung wird ausschließlich an den konfigurierten Server gesendet. Filme und Serien behalten die bisherige Wiedergabelogik. Wie Moonfin ist für Live-TV zusätzlich der Jellyfin-FFmpeg-Audiodecoder als lokaler Fallback eingebunden, unter anderem für MP2-Ton; Stereo-Downmix bleibt lokal. Die TS-Auswertung erlaubt wie Moonfin auch Non-IDR-Keyframes, damit Sender ohne regelmäßige IDR-Frames beim Einstieg nicht auf ein nie eintreffendes Startbild warten. Transcoding bleibt standardmäßig ausgeschaltet; eine explizite Codec-copy-Remux-Route ist ohne Neukodierung zulässig.
- **Einstellungen → Bewertungen · Anbieter und API-Keys** bietet OMDb, MDBList und TMDB. Je Anbieter öffnet sich ein Textfeld für den API-Key; TMDB akzeptiert auch den Read Access Token. Schlüssel werden mit Android Keystore verschlüsselt gespeichert. Leeres Feld entfernt einen Key. Ohne Keys werden keine externen Anfragen gestellt.
- Die sichtbaren Bewertungen sind einzeln auswählbar: **IMDb, Rotten Tomatoes Kritiker/Publikum, Metacritic, TMDB, Trakt, Letterboxd und MDBList**. Kompakte Badges mit passenden Icons und ausdrücklichen Skalen erscheinen unter der fokussierten Genre-Reihe und auf Film-/Seriendetailseiten. Fehlende Werte bleiben ausgeblendet. Lange Badge-Reihen brechen innerhalb des verfügbaren HD-Inhaltsbereichs um.
- Bewertungsdaten werden über IMDb-/TMDB-IDs aus Jellyfin zugeordnet. Pro Titel werden Anfragen zusammengefasst, im Hintergrund geladen und dauerhaft lokal gespeichert. Fehler werden drei Minuten zwischengespeichert; HTTP 429 pausiert den Anbieter entsprechend `Retry-After`. Die ganze Bibliothek wird nicht gegen die begrenzten externen APIs vorab abgefragt.
- **Mehr anzeigen** in Weiterschauen und Als Nächstes verwendet denselben 16:9-Bildrahmen und dieselbe Höhe wie Folgenkarten. Genre-Reihen behalten 2:3-Poster.
- Der sichtbare Menübutton entfällt. Links an der ersten Karte öffnet das Menü; auf Seiten ohne Karten öffnet Links ebenfalls das Menü. Die Menü-Taste funktioniert weiterhin. **Live TV** und **Zufälliger Film** stehen über den Bibliotheken.
- Zurück führt weiterhin zuerst zur Startseite. Vor dem endgültigen Schließen erscheint **Lowfin beenden?**; **Weiter ansehen** ist voreingestellt. Zurück im Dialog schließt nur die Abfrage.

API-Dokumentation: [OMDb](https://www.omdbapi.com/), [MDBList](https://docs.mdblist.com/docs/api), [TMDB](https://developer.themoviedb.org/reference/find-by-id). Lowfin und die angepassten Moonfin-Bestandteile werden unter GPL v2 mit vollständigem Quellcode ausgeliefert; siehe `LICENSE` und `THIRD_PARTY.md`.

## Neu in 0.4.2

- Ein kleiner Startpuffer verhindert, dass die Speicherreserve auf schwachen Geräten das Laden schon vor dem ersten Datenpaket blockiert. Live-TV startet ab 750 ms Puffer und verwendet kürzere Nachpufferzeiten. Ein kontinuierlicher Senderstream wird als Stream ohne bekannte Dateilänge behandelt, damit der Player nicht zum vermeintlichen Dateiende springt.
- Nach 20 Sekunden ohne abspielbare Daten wird derselbe Live-Stream einmal neu verbunden. Nach weiteren 20 Sekunden erscheint eine Fehlermeldung. Unter **Player → Optionen → Stream erneut verbinden** ist ein manueller Versuch möglich. Automatisches Transcoding bleibt standardmäßig ausgeschaltet.
- **Mehr anzeigen** steht als 2:3-Karte am Ende jeder Startseitenreihe. **Zuletzt hinzugefügt** entfällt innerhalb der App.
- **Zufälliger Film** und **Genres anheften** stehen ausschließlich im ausklappbaren Menü. Zum Anheften: Genre auswählen, Menü öffnen und **Auf Startseite anheften** wählen. Die Zufallsauswahl berücksichtigt eine gerade geöffnete Filmbibliothek.
- Filmroulette läuft mindestens vier Sekunden und endet mit einer kurzen monochromen Trefferanimation. Nach dem Effekt öffnet die Detailseite automatisch. Abbrechen verhindert das Öffnen; der Treffer-Effekt berücksichtigt die Animationseinstellung.

## Neu in 0.4.1

- Zurück schließt zuerst ein geöffnetes Menü, geht anschließend durch vorhandene Detail-/Unteransichten und führt aus einer Rubrik immer zu Start, bevor die App beendet wird. In einer vergrößerten Genre-Reihe führt Zurück zunächst zur normalen Startseitenansicht.

- Das Menü fokussiert beim Öffnen die aktuelle Rubrik. Hoch/runter gehen ausschließlich durch die Menüeinträge und halten am ersten/letzten Eintrag an. Links an der ersten Filmkarte öffnet ebenfalls das Menü; Rechts oder Zurück führen zur zuvor fokussierten Karte bzw. zum vorherigen Bedienelement zurück. Auch ein Öffnen über die Menü-Taste merkt sich die vorherige Auswahl.
- Beim Wechsel zwischen Startseitenreihen bleibt die gewählte Kartenposition erhalten. Eine erstmals besuchte Reihe übernimmt die horizontale Position der vorherigen Reihe, begrenzt durch ihre Eintragszahl.
- Film- und Serienraster verwenden acht statt fünf Spalten, Staffelraster acht statt sechs. Die Poster sind entsprechend kleiner. Folgenbilder belegen zwei Rasterspalten.
- Alle Kartenrahmen haben **2:3** für Poster und **16:9** für Folgen-/Videothumbnails sowie Senderkarten. Abweichende Bildformate werden mittig auf das feste Seitenverhältnis beschnitten. Senderlogos werden im 16:9-Rahmen vollständig dargestellt. Eine begrenzte verfügbare Höhe verändert das Bildverhältnis nicht mehr.

## Neu in 0.4.0

- Das Menü startet geschlossen. Links an der ersten Karte oder die Menü-Taste öffnet es; **Rechts** oder **Zurück** schließt es und gibt den Fokus an den Inhalt zurück. Die kurze Verschiebe-/Alpha-Animation lässt sich mit „Kleine Fokusanimationen“ deaktivieren. Der Inhalt behält stets die volle Breite, auch während der Menüanimation.
- **Zufälliger Film** steht im Menü. In Filmbibliotheken bleibt die Auswahl auf die betreffende Bibliothek beschränkt. Die Slot-Animation öffnet automatisch die Detailseite.
- **Horror** und **Thriller** sind standardmäßig als zufällige Filmreihen angeheftet. Unter **Menü → Genres anheften → gewünschtes Genre → Menü → Auf Startseite anheften** lassen sich weitere Reihen hinzufügen; **Von Startseite lösen** entfernt sie wieder. Die Reihen werden pro Konto gespeichert. Sie verwenden höchstens 18 zufällige Filme pro Genre und speichern diese Auswahl für den nächsten Start bzw. Offline-Betrieb.
- Sobald eine Genre-Reihe Fokus erhält, bleibt **nur diese eine Reihe sichtbar**. Unter den Postern stehen Titel, Metadaten und Beschreibung. Der Hintergrund wechselt zum Film-Backdrop, leicht entsättigt und abgedunkelt. Hoch/runter wechseln die Reihe; rechts an der letzten Karte bleibt der Fokus in derselben Reihe. Schnelle Filmwechsel laden den Hintergrund erst nach einer kurzen Pause.
- Filme und Serien werden auch auf Start als **2:3-Poster** gezeigt, Folgen als **16:9**. 4:3-Folgenbilder werden mittig beschnitten.
- **▶ Trailer** erscheint bei YouTube-Links in Jellyfins `RemoteTrailers`. Die Wiedergabe läuft vollständig im nativen Media3-Player mit Lowfins Playleiste, ohne YouTube-Oberfläche. Die Stream-Ermittlung orientiert sich an [Moonfins Resolver](https://github.com/Moonfin-Client/Moonfin-Core/blob/main/lib/data/services/youtube_stream_resolver.dart). Die Qualität ist für schwache Geräte auf 720p begrenzt. YouTube muss eine abspielbare Streamadresse liefern; ein Gerätetest steht aus.

## Update 0.3.1

Der Genre-Index wird beim Datenbank-Update nur angelegt und anschließend in Hintergrundschritten von höchstens 50 Filmen/Serien gefüllt. Bereits gespeicherte Metadaten, Bibliothekszuordnungen und Anmeldung bleiben erhalten. Einstellungsseite und Sitzungsprüfung lesen die Datenbank im Hintergrund. Die Bibliotheksnavigation besitzt einen eigenen Worker; lokale Cache-Abfragen teilen keine Worker mehr mit langsamen Serveranfragen. Die Regressionstests prüfen den stückweisen Indexaufbau, das Öffnen der Einstellungen bei gesperrter Datenbank und das Laden der Seitenleiste trotz belegter Inhaltsworker.

## Funktionen

- Kompakte Detailseiten mit Titel, Metadaten, Poster, Wiedergabe- und Statusaktionen sowie Beschreibung. Echte Film-/Serien-Backdrops im Hintergrund behalten die Hälfte ihrer Farbsättigung; dunkle Verläufe halten den Text lesbar. Ohne Backdrop bleibt der Hintergrund dunkel.
- Poster in festen **2:3**-Rahmen mit mittigem Beschnitt bei abweichenden Bildformaten. Folgenbilder erscheinen einheitlich in **16:9**; ältere **4:3**-Cartoonbilder werden mittig auf 16:9 beschnitten. Filme und Serien verwenden ihre Poster auch auf der Startseite; Folgen verwenden verfügbare Thumbs oder Standbilder. Diese Standardverhältnisse entsprechen dem [Jellyfin-Webclient](https://github.com/jellyfin/jellyfin-web/blob/master/src/components/cardbuilder/utils/builder.ts).
- Ausklappbare linke Seitenleiste mit den tatsächlichen Jellyfin-Bibliotheken, **Genres** und Live-TV. Genre-Auswahl filtert Filme und Serien über alle Bibliotheken; Genres werden für den lokalen Cache indexiert. Globale Suche, Favoriten, gesehen/ungesehen, Detailseiten und Wiedergabe fortsetzen.
- Startseite mit horizontalen **Weiterschauen**- und **Als Nächstes**-Karten, Fortschrittsbalken und direktem Wiedergabestart. Weitere Titel sind über die posterförmige „Mehr anzeigen“-Karte am Reihenende erreichbar.
- Virtuelles Staffelraster mit **2:3-Postern**, acht kleinere Staffeln pro Reihe, numerischer Sortierung und direktem Sprung über die Staffelnummer. Der Test umfasst 45 Staffeln und das Öffnen von Staffel 40. Folgen und große Bibliotheken werden in Seiten mit maximal 60 Einträgen angezeigt.
- Native Media3-Wiedergabe mit 720p/4 Mbit/s als Standard, einstellbar. Direkte Wiedergabe mit den vom Fernseher gemeldeten Decodern hat Vorrang; die HD-/Bitratengrenzen gelten für den Server-Fallback. Inkompatible Medien können über Jellyfin umgewandelt werden. Server-Transcoding ist standardmäßig ausgeschaltet. Es kann in den Einstellungen erlaubt oder im Player über „Über Server abspielen“ ausdrücklich angefordert werden. Ohne passenden Decoder können einzelne Medien bei ausgeschaltetem Transcoding nicht abgespielt werden.
- **Audio auf Stereo (2.0) heruntermischen**, standardmäßig aktiv: Der Fernseher decodiert unterstützte 5.1-Tonspuren und mischt das PCM-Signal lokal auf Stereo, einschließlich Center/Dialog und Surround. Audio-Passthrough ist dabei deaktiviert. Erst ein Server-Fallback erhält eine Grenze von zwei Audiokanälen. Abschaltbar in den Wiedergabeeinstellungen; dann sind bis zu sechs Kanäle erlaubt.
- Auswahl von Tonspuren, Untertiteln, Medienversionen und Kapiteln. Tonspurwechsel erfolgen bei direkter Wiedergabe lokal, sofern die Spur unterstützt wird; sonst wird die Wiedergabe über den Server neu ausgehandelt; Bild-/ASS-Untertitel können auf dem Server eingebrannt werden.
- „Zufälliger Film“ im Menü: Auswahl aus der gesamten jeweiligen Bibliothek, Slot-Animation mit Treffer-Effekt mit Filmtiteln und anschließende Detailseite. Ohne Serververbindung Auswahl aus den gespeicherten Filmen. Abbrechen öffnet keinen Film.
- Eigene schlanke Player-Steuerleiste am unteren Bildschirmrand, nach fünf Sekunden automatisch ausgeblendet; Pause, ±10 Sekunden, Zeitleiste, nächste Folge und Senderwahl. Ton/Untertitel/Version/Kapitel und optionale Serverwiedergabe über „Optionen“.
- Fortschrittsmeldungen, automatischer Start der nächsten Folge, optionales Abschalten.
- Live-TV zeigt ausschließlich Sender, keinen Guide. Es startet beim Senderklick unmittelbar; `AutoOpenLiveStream=true`, längere Probe-Zeit für IPTV, Serverstream/Transcoding-Fallback und Freigabe von Live-Streams beim Verlassen. Senderauswahl und Kanal-Plus/Minus; optionale Programmvorschau/Aufnahmeplanung im Player. Vorhandene Aufnahmen sind über die Einstellungen erreichbar.
- Intro Skipper über Jellyfins [MediaSegments-API](https://jellyfin.org/docs/general/server/metadata/media-segments/): **Intro und Outro automatisch**, alle übrigen Segmenttypen ausschließlich über die sichtbare Skip-Taste. Voraussetzung: Jellyfin 10.10 oder neuer und vorhandene Segmentdaten. Lowfin analysiert die Videos selbst nicht.
- Android-TV-Hauptmenü: Preview-Kanäle **Weiterschauen**, **Als Nächstes**, **Filme**, **Serien** und System-**Watch Next**. Direkte Links sind an das angemeldete Konto gebunden. Bilder werden aus dem lokalen Covercache bereitgestellt; Zugangstoken stehen nicht in Launcher-Links. Stündliche Aktualisierung über Android JobScheduler, zusätzlich nach App-/Wiedergabenutzung. Während der Wiedergabe wird die Hintergrundaktualisierung ausgelassen.
- Quadratisches Launcher-Icon, adaptive Android-Icons, Google-TV-Banner und SVG-Designquellen unter `design/`.

## Videocache und RAM-Puffer

Unter **Einstellungen → Wiedergabe → Videocache**: Aus, 128, 256, 500, 1024 oder 2048 MB; „Eigener Wert“ erlaubt 16 bis 16384 MB. Standard sind 500 MB auf dem **Gerätespeicher**. Die neue Größe gilt beim nächsten Wiedergabestart. Ältere Videodaten werden automatisch verdrängt. Der Videocache ist vom Covercache getrennt und verwendet kontogebundene Schlüssel. Bei weniger als 64 MB freiem Speicher wird direkt aus dem Netzwerk gelesen.

Direkt abspielbare Filme und HLS-Segmente werden während der Wiedergabe zwischengespeichert. Live-Playlists werden immer neu geladen; Live-Segmente haben pro Sitzung neue Schlüssel. Kontinuierliche Live-Streams ohne feste Bytebereiche werden nicht auf Platte gecacht. **Die Cachegröße ist eine Obergrenze für bereits geladene Daten, kein vollständiger Vorausdownload.**

Der **RAM-Puffer passt sich automatisch an**: bis 48 MB für Filme/Folgen und bis 32 MB für Live-TV. Ein Check pro Sekunde berücksichtigt den App-Heap und Androids freien RAM. Mindestens 24 MB beziehungsweise ein Viertel des maximalen App-Heaps sowie eine Systemreserve über Androids Speicherschwelle hinaus bleiben als Ziel frei. Bei Android-Speicherdruck sinkt das Pufferziel auf höchstens 8 MB; Nachladen wird begrenzt und der Puffer kann beim Abspielen schrumpfen. Der Checker kann andere Apps und native Decoder nicht kontrollieren und daher keine absolute Auslastungsgrenze garantieren.

Pufferfenster: 30–90 Sekunden bei Filmen, 10–45 Sekunden bei Live-TV, durch das aktuelle RAM-Budget begrenzt. Start ab 2,5 Sekunden, nach erneutem Puffern normalerweise ab 6 Sekunden; bei kleinem Speicherbudget kann der Start früher erfolgen. Netzwerkfehler erhalten mehr Wiederholungsversuche. Videodaten haben kein Gesamtzeitlimit von 200 Sekunden mehr; ein 30-Sekunden-Timeout bei ausbleibenden Daten bleibt bestehen.

## Bibliothekscache

Nach Anmeldung startet ein Hintergrundlauf. Er lädt die Metadaten aller zugänglichen Filme, Serien, Staffeln und Folgen in SQLite und speichert deren verfügbare primäre Cover/Standbilder sowie vorhandene Thumb-/Backdrop-Varianten für die Startseite als kleine 240-Pixel-Vorschaubilder. Vorhandene Informationen erscheinen sofort, während Serverdaten aktualisiert werden. Die Standardreihen und angehefteten Genre-Reihen werden ebenfalls lokal gespeichert.

Der Lauf arbeitet paketweise und pausiert während der Wiedergabe. Beim nächsten App-Start wird nach 24 Stunden ein neuer Lauf gestartet; manuelles Aktualisieren und Stoppen gibt es in den Einstellungen. Unterbrochene Läufe erhalten die vorhandenen Daten. Gelöschte Titel werden erst nach einem erfolgreich beendeten Metadatenlauf entfernt.

Standard: **1024 MB Covercache**, mindestens **64 MB freier Gerätespeicher** bleiben erhalten. Das Budget lässt sich bis „Ohne feste Grenze“ erhöhen. Ist der Platz knapp oder ein Cover nicht erreichbar, zeigt der Cache-Status die noch offenen Cover. Die gesamte Bibliothek ist deshalb erst dann vollständig bebildert, wenn genug Gerätespeicher verfügbar ist. Der Bildcache im RAM ist auf 6 MB begrenzt. Video-/Audiodateien werden vom Jellyfin-Server gestreamt.

## TV-Hauptmenü aktivieren

Unter **Einstellungen → Android-TV-Hauptmenü** die gewünschten TV-Kanäle aktivieren. Die erste Standardreihe kann automatisch erscheinen; weitere Reihen erfordern die Systemfreigabe oder die Auswahl im TV-Launcher unter „Kanäle anpassen“. Das ist eine [Android-TV-Vorgabe](https://developer.android.com/training/tv/discovery/recommendations-channel).

Welche Reihen angezeigt werden, entscheidet der Launcher. Google TV und Hersteller-Launcher können die Android-TV-Kanäle anders behandeln. Abmelden oder Abschalten der Integration entfernt die Lowfin-Inhalte; die Kanal-IDs bleiben erhalten, damit die Reihenfolge des Launchers bestehen bleibt. Vom Nutzer entfernte Watch-Next-Karten werden bis zu einer erneuten expliziten Wiedergabe respektiert.

## Fernbedienung

- Pfeile + OK: navigieren / auswählen; Zurück: vorheriger Bildschirm.
- **OK/Enter auf einer Startseiten- oder Bibliothekskarte halten:** Fortsetzen, Von vorn, Gesehen/Ungesehen, Später ansehen, Favorit sowie Zur Serienseite bei Serien/Folgen. Beim Loslassen nach langem Drücken wird kein normaler Klick ausgelöst.
- **Später ansehen** ist eine dauerhafte lokale Liste pro Konto/Server, unabhängig von Jellyfin-Favoriten; erreichbar über die Seitenleiste.
- **Einstellungen → Startseite und Darstellung:** „Flüssige Navigation und Aktionsanimationen“ steuert Seiten-/Reihenwechsel, Fokus-Vergrößerung und Speicherrückmeldungen. „UI-Animationen“ schaltet alle optionalen UI-Effekte gemeinsam ab.
- Staffelansicht: Staffelnummer eingeben und „Zur Staffel“ wählen.
- Player: OK blendet die Steuerung ein; dort Pause/Abspielen und ±10 Sekunden. Bei ausgeblendeter Steuerung springen Links/Rechts um 10 Sekunden. Oben oder Menü führt zu Optionen beziehungsweise zur angebotenen Skip-Taste.
- Zurück im Player: Steuerung schließen, nochmals Zurück: Wiedergabe verlassen.
- Live-TV: Kanal +/− oder Senderauswahl im Player.

## Lokal bauen

Java, Gradle und Android-SDK liegen projektlokal in `.tools/`. `tools/bootstrap.sh` lädt sie herunter und akzeptiert die SDK-Lizenzen. Ohne diese Werkzeuge alternativ in Android Studio öffnen.

```bash
bash tools/bootstrap.sh
bash tools/build.sh testDebugUnitTest lintDebug lintRelease assembleRelease
```

Die Release-APK liegt anschließend unter `app/build/outputs/apk/release/app-release.apk`. Sie ist mit dem lokalen Entwicklungsschlüssel signiert und für Sideload-Tests vorgesehen. Für eine spätere Store-Veröffentlichung muss eine eigene Release-Signierung eingerichtet werden.

## Prüfung und verbleibender Gerätetest

API-Tests mit einem lokalen Mockserver; Android-11-Tests mit Robolectric für SQLite, Staffelansicht, Startseiten-Karten, Deep Links und TV-Provider-Veröffentlichung. 143 automatisierte Tests bestehen in Version 0.7.0; Android Lint meldet keine Fehler. Zusätzlich ein durch R8 verkleinerter Build. Renderings mit Testdaten stehen unter `design/screenshots/`.

**Version 0.5.2 ist lokal geprüft; die neuen Empfehlungsreihen und das aktualisierte Logo müssen noch auf dem Gerät bestätigt werden. Frühere Versionen wurden vom Nutzer auf dem Fernseher ausprobiert; Wiedergabe funktionierte. Version 0.3.0 zeigte auf dem Gerät Probleme beim Bibliotheksladen und Einfrieren der Einstellungen. Die Korrekturen in 0.3.1 und die neuen Funktionen in 0.4.0 werden lokal auf Android-11-Layouts und mit Regressionstests geprüft; ein erneuter Gerätetest einschließlich YouTube steht aus.** Die Tests bestätigen die lokale Logik, API-Anfragen, Seitenverhältnisse und das Layout; sie ersetzen keine Prüfung aller Decoder- und Live-TV-Varianten auf dem Gerät. Für den ersten Gerätetest:

1. Anmelden, App neu öffnen und automatische Anmeldung prüfen.
2. Einen Film direkt abspielen, pausieren, zurückgehen und fortsetzen.
3. Einen 5.1-Film mit eingeschalteter Stereo-Option abspielen, Dialoge und direkte Wiedergabe im Jellyfin-Dashboard prüfen. Tonspur und Untertitel wechseln; einen Titel testen, der Server-Transcoding braucht.
4. Problematische IPTV-Sender mehrfach öffnen und zwischen Sendern wechseln.
5. Serie mit vielen Staffeln öffnen und direkt zur Staffel 40 springen.
6. Intro/Outro automatisch überspringen lassen, Rückblick/Werbung nur per Taste überspringen.
7. Cache vollständig laden lassen, App neu starten und Startseite/Bibliotheken vergleichen.
8. TV-Kanäle im Hauptmenü aktivieren und eine Karte direkt öffnen.

## Technische Referenzen

- [Moonfin-Core](https://github.com/Moonfin-Client/Moonfin-Core), insbesondere PlaybackInfo mit AutoOpenLiveStream und die nativen Android-TV-Publisher, als Verhaltensreferenz.
- [Android Media3](https://developer.android.com/media/media3/exoplayer/hello-world).
- [Jellyfin MediaSegments](https://jellyfin.org/docs/general/server/metadata/media-segments/) und [Intro Skipper](https://github.com/intro-skipper/intro-skipper).
- [Android-TV-Startseitenkanäle](https://developer.android.com/training/tv/discovery/recommendations-channel).

Eigenständige Implementierung in Java, ohne laufende WebView oder Flutter-Engine. Animationen betreffen kurzzeitig Transparenz und Fokusdarstellung; eine Garantie für ruckelfreie Wiedergabe auf unbekannter Hardware lässt sich erst nach dem Gerätetest beurteilen.

## API-Keys per TXT importieren

Wenn noch keine Bewertungs-API-Keys gespeichert sind, erstellt Lowfin beim Start **Download/Lowfin-API-Keys.txt**. Die Datei erklärt alle Anbieter. Keys hinter `=` eintragen, als UTF-8 speichern und zu Lowfin zurückkehren. Die App prüft im Vordergrund alle fünf Sekunden, speichert ausgefüllte Keys verschlüsselt und löscht die Datei nach erfolgreichem Import. Ungenutzte Anbieter dürfen leer bleiben. Bestehende Keys deaktivieren den automatischen Import. Wenn der Texteditor die ursprüngliche Datei ersetzt, lässt sie sich in den Bewertungseinstellungen über den TXT-Dateiauswahldialog importieren.

```ini
OMDB_API_KEY=
MDBLIST_API_KEY=
TMDB_API_KEY=
```

Ohne Anführungszeichen und ohne `Bearer`-Präfix eintragen.

## Persistenter Bildcache ab 0.7.13

Gecachte Bilder werden unabhängig von Downloads gelesen und decodiert. Oberfläche und Precache verwenden dieselben Bildgrößen; vorhandene 240-Pixel-Cover können auf HD-Geräten weiterverwendet werden. Langsame einzelne Bildanfragen blockieren andere Cover nicht mehr. Bei vollem Speicherbudget ersetzen neue sichtbare Cover die am längsten nicht verwendeten Dateien; der Hintergrund-Precache lädt dann keine weiteren Bilder hinzu.

Automatisches Vorladen ist eine Aktualisierung, kein Löschen des Caches. Standardmäßig prüft Lowfin nach 24 Stunden erneut. Noch nicht vollständig abgeschlossene Metadatenläufe werden beim nächsten Start wieder versucht. Der gespeicherte Status bzw. vorhandene Cover werden nach einem App-Neustart wieder angezeigt.
