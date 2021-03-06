package lang.meta
package semanticdb

import lang.meta.inputs._
import lang.meta.internal.inputs._

final case class ResolvedName(pos: Position, sym: Symbol, isBinder: Boolean) {
  def syntax = {
    val text = if (pos.text.nonEmpty) pos.text else "ε"
    val binder = if (isBinder) "<=" else "=>"
    s"[${pos.start}..${pos.end}): $text $binder ${sym.syntax}"
  }
  def structure = s"""ResolvedName(${pos.structure}, ${sym.structure}, $isBinder)"""
  override def toString = syntax
}
