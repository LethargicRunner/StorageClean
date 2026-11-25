# 🧹 StorageClean

Etat des lieux du stockage du telephone par type de fichiers (PDF, images,
videos, documents, archives, audio) : selection des dossiers par navigation
(cases a cocher, selection persistante, dossiers caches inclus par defaut),
statistiques par type, detection des doublons exacts par hash SHA-256 et
nettoyage assiste via une corbeille restaurable.

**Rien n'est jamais supprime directement.** Le scan est en lecture seule ; le
nettoyage deplace les fichiers coches dans une corbeille (`.StorageCleanTrash`,
a la racine de chaque memoire) d'ou ils peuvent etre restaures. Seul « Vider »
ou « Supprimer definitivement » efface vraiment.

L'audio n'est pas coche par defaut : les doublons MP3 sont deja traites
par DupScan.

## Garde-fous du nettoyage (V0.2.1)

- **Un exemplaire minimum** : impossible de mettre a la corbeille tous les
  exemplaires d'un groupe de doublons (verifie a la confirmation, puis une
  seconde fois juste avant le deplacement).
- **Confirmation** avant tout deplacement : nombre de fichiers et espace libere.
- **Cocher les copies** : dans un groupe de doublons, un bouton coche toutes les
  copies et garde le plus ancien.
- **Corbeille sure** : un fichier n'entre dans la corbeille que si l'index est
  ecrit (sinon il reste en place), index ecrit de facon atomique, index illisible
  mis de cote au lieu d'etre ecrase, elements non supprimes conserves dans
  l'index, date de mise a la corbeille enregistree.
- La corbeille n'est jamais scannee (elle ne compte donc pas comme exemplaire
  conserve).

## Donnees utilisateur

- Les fichiers scannes ne sont jamais ecrits ; seuls ceux que tu coches sont deplaces.
- Rapport CSV exportable (groupe, categorie, taille, SHA-256, date, chemin)
  dans Android/data/fr.lethargicrunner.storageclean/files/.
- Selections persistantes (dossiers et types) entre les lancements.

## Telecharger l'APK

Onglet **Actions** → dernier run **Build APK** → artefact **StorageClean-APK**.

## Compiler dans GitHub Codespaces

Dans le terminal du Codespace :

    gradle wrapper --gradle-version 8.7
    ./gradlew testDebugUnitTest assembleDebug

APK obtenu dans app/build/outputs/apk/debug/app-debug.apk.

## Feuille de route

- [x] V0.1 : selection par navigation, filtre par types, stats, doublons exacts
- [x] V0.2 : nettoyage assiste (deplacement vers corbeille, jamais de suppression directe)
- [x] V0.2.1 : garde-fous du nettoyage, tests de la corbeille, README a jour
- [ ] V0.3.0 : reorganisation par type dans un dossier central `Organise` (un par
      memoire) — plan d'abord, validation, journal et annulation du lot
- [ ] V0.4 : exploration des resultats par type et par dossier, plus gros fichiers
- [ ] V1.0 : organisation avancee des documents (arborescence proposee)
