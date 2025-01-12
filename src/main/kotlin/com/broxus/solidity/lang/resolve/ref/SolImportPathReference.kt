package com.broxus.solidity.lang.resolve.ref

import com.broxus.solidity.ide.inspections.fixes.ImportFileAction
import com.broxus.solidity.lang.core.SolidityFile
import com.broxus.solidity.lang.psi.impl.SolImportPathElement
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import com.intellij.psi.impl.source.tree.LeafElement
import java.nio.file.Paths

class SolImportPathReference(element: SolImportPathElement) : SolReferenceBase<SolImportPathElement>(element) {
  override fun singleResolve(): PsiElement? {
    val importText = element.text
    if (importText.length < 2) {
      return null
    }
    val path = importText.substring(1, importText.length - 1)
    return findImportFile(element.containingFile.originalFile.virtualFile, path)
      ?.let { PsiManager.getInstance(element.project).findFile(it) }
  }

  companion object {
    fun findImportFile(file: VirtualFile, path: String): VirtualFile? {
      val directFile = if (path.startsWith("/")) LocalFileSystem.getInstance().findFileByPath(path) else file.findFileByRelativePath("../$path")
      if (directFile != null) {
        return directFile
      }
      val npmFile = findNpmImportFile(file, path)
      if (npmFile != null) {
        return npmFile
      }
      val ethPmFile = findEthPMImportFile(file, path)
      if (ethPmFile != null) {
        return ethPmFile
      }
      return findFoundryImportFile(file, path)
    }

    private fun findNpmImportFile(file: VirtualFile, path: String): VirtualFile? {
      val test = file.findFileByRelativePath("node_modules/$path")
      return when {
        test != null -> test
        file.parent != null -> findNpmImportFile(file.parent, path)
        else -> null
      }
    }

    // apply foundry remappings to import path
    private fun applyRemappings(remappings: ArrayList<Pair<String, String>>, path: String): String {
      var output = path;
      remappings.forEach { (prefix, target) ->
        if (path.contains(prefix)) {
          output = path.replace(prefix, target)
          return output
        }
      }
      return output;
    }

    private fun foundryDefaultFallback(file: VirtualFile, path: String): VirtualFile? {
      val count = Paths.get(path).nameCount;
      if (count < 2) {
        return null;
      }
      val libName = Paths.get(path).subpath(0, 1).toString();
      val libFile = Paths.get(path).subpath(1, count).toString();
      val test = file.findFileByRelativePath("lib/$libName/src/$libFile");
      return test;
    }

    // default lib located at: forge-std/Test.sol => lib/forge-std/src/Test.sol
    private fun findFoundryImportFile(file: VirtualFile, path: String): VirtualFile? {
      val testRemappingFile = file.findFileByRelativePath("remappings.txt");
      val remappings = arrayListOf<Pair<String, String>>();
      if (testRemappingFile != null) {
        val mappingsContents = testRemappingFile.contentsToByteArray().toString(Charsets.UTF_8).split("[\r\n]+".toRegex());
        mappingsContents.forEach { mapping ->
          val splitMapping = mapping.split("=")
          if (splitMapping.size == 2) {
            remappings.add(Pair(splitMapping[0].trim(), splitMapping[1].trim()))
          }
        }
      }

      val remappedPath = applyRemappings(remappings, path);
      val testRemappedPath = file.findFileByRelativePath(remappedPath);
      val testFoundryFallback = foundryDefaultFallback(file, path);

      return when {
        testRemappedPath != null -> testRemappedPath
        testFoundryFallback != null -> testFoundryFallback
        file.parent != null -> findFoundryImportFile(file.parent, path)
        else -> null
      }
    }

    private fun findEthPMImportFile(file: VirtualFile, path: String): VirtualFile? {
      val test = file.findFileByRelativePath(
        "installed_contracts/" + path.replaceFirst("/", "/contracts/")
      )
      return when {
        test != null -> test
        file.parent != null -> findEthPMImportFile(file.parent, path)
        else -> null
      }
    }
  }

  override fun doRename(identifier: PsiElement, newName: String) {
    if (identifier !is LeafElement) {
      return
    }
    val renamedElement = resolve()
    if (renamedElement !is SolidityFile) {
      return
    }
    val name = renamedElement.name
    val currentPath: String? = (identifier as PsiElement).text
    if (currentPath == null) {
      return
    }
    val newImportPath = currentPath.replace(name, newName)
    identifier.replaceWithText(newImportPath)
  }

  override fun bindToElement(element: PsiElement): PsiElement {
    val file = element as? SolidityFile ?: return element
    val newPath = ImportFileAction.buildImportPath(this.element.containingFile.virtualFile, file.virtualFile)
    val identifier = this.element.referenceNameElement as? LeafElement ?: return element
    return identifier.replaceWithText("\"$newPath\"").psi ?: element
  }
}
