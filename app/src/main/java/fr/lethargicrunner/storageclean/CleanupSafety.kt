package fr.lethargicrunner.storageclean

import java.io.File

/**
 * Garde-fous du nettoyage, purs et testables.
 *
 * Regle principale : un groupe de doublons doit toujours garder au moins un exemplaire
 * existant sur le disque. On ne peut donc pas mettre a la corbeille tous les exemplaires.
 */
object CleanupSafety {

    /** Un fichier deja dans une corbeille StorageClean ne compte pas comme exemplaire conserve. */
    private fun inTrash(path: String): Boolean = path.split('/').contains(TRASH_DIR_NAME)

    /**
     * Groupes dont aucun exemplaire ne resterait apres le deplacement des fichiers choisis.
     * Un exemplaire "reste" s'il n'est pas choisi, n'est pas en corbeille et existe encore.
     */
    fun blockedGroups(
        selectedPaths: Set<String>,
        groups: List<DupGroup>,
        exists: (String) -> Boolean = { File(it).isFile },
    ): List<DupGroup> = groups.filter { g ->
        val touched = g.files.any { it.path in selectedPaths }
        touched && g.files.none { it.path !in selectedPaths && !inTrash(it.path) && exists(it.path) }
    }

    /** Exemplaire a garder par defaut dans un groupe : le plus ancien (a egalite, le premier par chemin). */
    fun keeper(group: DupGroup): FileInfo =
        group.files.sortedWith(compareBy<FileInfo> { it.mtime }.thenBy { it.path }).first()

    /** Copies a cocher par defaut : tous les exemplaires sauf celui a garder. */
    fun copiesToSelect(group: DupGroup): List<FileInfo> {
        val keep = keeper(group).path
        return group.files.filter { it.path != keep }
    }

    /** Pour debloquer : un exemplaire a decocher dans chaque groupe bloque (le gardien par defaut). */
    fun pathsToUncheck(blocked: List<DupGroup>): Set<String> = blocked.map { keeper(it).path }.toSet()

    data class Plan(
        val files: List<FileInfo>,
        val bytes: Long,
        val blocked: List<DupGroup>,
    ) {
        val allowed: Boolean get() = files.isNotEmpty() && blocked.isEmpty()
    }

    /** Plan de mise a la corbeille : fichiers, espace libere, groupes de doublons en danger. */
    fun plan(
        files: List<FileInfo>,
        groups: List<DupGroup>,
        exists: (String) -> Boolean = { File(it).isFile },
    ): Plan {
        val distinct = files.distinctBy { it.path }
        val blocked = blockedGroups(distinct.map { it.path }.toSet(), groups, exists)
        return Plan(distinct, distinct.sumOf { it.size }, blocked)
    }
}
