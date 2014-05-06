package suiryc.scala.sys.linux

import grizzled.slf4j.Logging
import java.io.File
import java.nio.file.{Path, Paths}
import scala.io.Source
import suiryc.scala.io.{NameFilter, PathFinder, SourceEx}
import suiryc.scala.misc.EitherEx
import suiryc.scala.sys.{Command, CommandResult}


class Device(val block: Path) {
  import Device._
  import PathFinder._
  import NameFilter._

  val dev = Paths.get("/dev").resolve(block.getFileName())

  val vendor =
    propertyContent(block, "device", "vendor") getOrElse "<unknown>"

  val model =
    propertyContent(block, "device", "model") getOrElse "<unknown>"

  val ueventProps: Map[String, String] = {
    val uevent = Paths.get(block.toString(), "device", "uevent").toFile()
    val props = Map.empty[String, String]

    if (uevent.exists()) {
      SourceEx.autoCloseFile(uevent) { source =>
        source.getLines().toList.foldLeft(props) { (props, line) =>
          line match {
            case KeyValueRegexp(key, value) =>
              props + (key.trim() -> value.trim())

            case _ =>
              props
          }
        }
      }
    }
    else props
  }

  val size = Device.size(block)

  val removable =
    propertyContent(block, "removable") map { removable =>
      removable.toInt != 0
    } getOrElse false

  val partitions = {
    val devName = dev.getFileName().toString
    (block * s"""${devName}[0-9]+""".r).get map { path =>
      DevicePartition(this, path.getName().substring(devName.length()).toInt)
    }
  }

  override def toString =
    s"Device(block=$block, vendor=$vendor, model=$model, ueventProps=$ueventProps)"

}


object Device
  extends Logging
{

  private val KeyValueRegexp = """^([^=]*)=(.*)$""".r

  def propertyContent(block: Path, path: String*): Option[String] = {
    val file = Paths.get(block.toString(), path: _*).toFile()

    Option(
      if (file.exists())
        SourceEx.autoCloseFile(file) { source =>
          source.getLines() map { line =>
            line.trim()
          } filterNot { line =>
            line == ""
          } mkString(" / ")
        }
      else null
    )
  }

  def size(block: Path): EitherEx[Throwable, Long] = {
    propertyContent(block, "size") map { size =>
      EitherEx(Right(size.toLong * 512))
    } getOrElse {
      try {
        val dev = Paths.get("/dev").resolve(block.getFileName())
        val CommandResult(result, stdout, stderr) = Command.execute(Seq("blockdev", "--getsz", dev.toString))
        if (result == 0) {
          EitherEx(Right(stdout.trim.toLong * 512))
        }
        else {
          val msg = s"Cannot get device size: $stderr"
          error(msg)
          EitherEx(Left(new Exception(msg)), -1L)
        }
      }
      catch {
        case e: Throwable =>
          EitherEx(Left(e), -1L)
      }
    }
  }

  def apply(block: Path): Device =
    new Device(block)

  def apply(block: File): Device =
    new Device(block.toPath())

}
