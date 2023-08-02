package com.broxus.solidity.ide.inspections

import com.broxus.solidity.lang.psi.SolMemberAccessExpression
import com.broxus.solidity.lang.psi.SolVisitor
import com.broxus.solidity.lang.resolve.SolResolver
import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElementVisitor

class ValidateMemberAccessInspection : LocalInspectionTool() {
  override fun getDisplayName(): String = ""

  override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
    return object : SolVisitor() {
      override fun visitMemberAccessExpression(element: SolMemberAccessExpression) {
        val id = element.identifier ?: return
        val refs = SolResolver.resolveMemberAccess(element)
        when {
          refs.isEmpty() -> holder.registerProblem(id, "Member cannot be resolved")
//            refs.size > 1 -> holder.registerProblem(element.parents(true).first { it.textLength > 0 }, "Multiple members resolved")
        }
      }
    }
  }
}