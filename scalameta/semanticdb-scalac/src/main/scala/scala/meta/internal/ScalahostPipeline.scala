package scala.meta.internal

import java.io._
import java.net.URI
import scala.compat.Platform.EOL
import scala.tools.nsc.Phase
import scala.tools.nsc.plugins.PluginComponent
import scala.util.control.NonFatal
import scala.{meta => m}
import scala.meta.io._
import scala.meta.internal.semanticdb.DatabaseOps
import scala.meta.internal.semanticdb.{vfs => v}
import scala.tools.nsc.doc.ScaladocGlobal

trait SemanticdbPipeline extends DatabaseOps { self: SemanticdbPlugin =>
  lazy val scalametaTargetroot = AbsolutePath(
    new File(
      global.settings.outputDirs.getSingleOutput
        .map(_.file.getAbsolutePath)
        .getOrElse(global.settings.d.value)))
  implicit class XtensionURI(uri: URI) { def toFile: File = new File(uri) }

  object SemanticdbComponent extends PluginComponent {
    val global: SemanticdbPipeline.this.global.type = SemanticdbPipeline.this.global
    val runsAfter = List("typer")
    override val runsRightAfter = Some("typer")
    val phaseName = "semanticdb"
    override val description = "compute semanticdb"
    def newPhase(_prev: Phase) = new SemanticdbPhase(_prev)

    class SemanticdbPhase(prev: Phase) extends StdPhase(prev) {
      override def apply(unit: g.CompilationUnit): Unit = {
        if (g.isInstanceOf[ScaladocGlobal]) return
        if (config.mode.isDisabled) return

        try {
          if (config.mode.isDisabled || !unit.source.file.name.endsWith(".scala")) return
          val mminidb = m.Database(List(unit.toAttributes))
          mminidb.save(scalametaTargetroot, config.sourceroot)
        } catch {
          case NonFatal(ex) =>
            val writer = new StringWriter()
            val path = unit.source.file.path
            writer.write(s"failed to generate semanticdb for $path:$EOL")
            ex.printStackTrace(new PrintWriter(writer))
            val msg = writer.toString
            import scala.meta.internal.semanticdb.FailureMode._
            config.failures match {
              case Error => global.reporter.error(g.NoPosition, msg)
              case Warning => global.reporter.warning(g.NoPosition, msg)
              case Info => global.reporter.info(g.NoPosition, msg, force = true)
              case Ignore => // do nothing.
            }
        }
      }

      override def run(): Unit = {
        val vdb = v.Database.load(Classpath(scalametaTargetroot))
        val orphanedVentries = vdb.entries.filter(ventry => {
          val scalaName = v.SemanticdbPaths.toScala(ventry.fragment.name)
          !config.sourceroot.resolve(scalaName).isFile
        })
        orphanedVentries.map(ve => {
          def cleanupUpwards(file: File): Unit = {
            if (file != null) {
              if (file.isFile) {
                file.delete()
              } else {
                if (file.getAbsolutePath == ve.base.toString) return
                if (file.listFiles.isEmpty) file.delete()
              }
              cleanupUpwards(file.getParentFile)
            }
          }
          cleanupUpwards(ve.uri.toFile)
        })
        super.run()
      }
    }
  }
}
