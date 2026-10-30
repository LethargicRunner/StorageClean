# 🧹 StorageClean

Etat des lieux du stockage du telephone par type de fichiers (PDF, images,
videos, documents, archives, audio) : selection des dossiers par navigation
(cases a cocher, selection persistante, dossiers caches inclus par defaut),
statistiques par type, et detection des doublons exacts par hash SHA-256.

**Lecture seule stricte** : StorageClean ne modifie, ne deplace et ne
supprime aucun fichier. Il informe, tu decides.

L'audio n'est pas coche par defaut : les doublons MP3 sont deja traites
par DupScan.

## Donnees utilisateur

- Aucune donnee n'est modifiee : les fichiers scannes ne sont jamais ecrits.
- Rapport CSV exportable (groupe, categorie, taille, SHA-256, date, chemin)
  dans Android/data/fr.lethargicrunner.storageclean/files/.
- Selections persistantes (dossiers et types) entre les lancements.

## Telecharger l'APK

Onglet **Actions** → dernier run **Build APK** → artefact **StorageClean-APK**.

## Compiler dans GitHub Codespaces

Dans le terminal du Codespace :

    gradle wrapper --gradle-version 8.7
    ./gradlew assembleDebug

APK obtenu dans app/build/outputs/apk/debug/app-debug.apk.

## Feuille de route

- [x] V0.1 : selection par navigation, filtre par types, stats, doublons exacts
- [ ] V0.2 : nettoyage assiste (deplacement vers corbeille, jamais suppression)
- [ ] V0.3 : exploration des resultats par type et par dossier
- [ ] V1.0 : organisation automatique des documents (arborescence proposee)
