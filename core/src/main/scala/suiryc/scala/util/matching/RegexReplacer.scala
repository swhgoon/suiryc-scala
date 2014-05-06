package suiryc.scala.util.matching

import java.nio.file.Path
import scala.io.Codec
import scala.util.matching.Regex
import suiryc.scala.io.RichFile._


case class RegexReplacer(
  regex: Regex,
  replacer: Regex.Match => String
)

object RegexReplacer {

  def apply(regex: String, replacer: Regex.Match => String): RegexReplacer =
    RegexReplacer(regex.r, replacer)

  def apply(regex: String, replacer: String): RegexReplacer =
    RegexReplacer(regex.r, (m: Regex.Match) => replacer)

  def replace(content: String, rrs: RegexReplacer*) = {
    val newContent = rrs.foldLeft(content) { (content, rr) =>
      rr.regex.replaceAllIn(content, rr.replacer)
    }

    /* Note: doing any replacement then checking the result is not the most
     * efficient. We would need to get all matches and then perform the
     * replacements if needed; however the scala API does not seem to expose
     * the necessary classes/fields to do that cleanly.
     */
    RegexReplacement(newContent, newContent != content)
  }

  def inplace(path: Path, rrs: RegexReplacer*)(implicit codec: Codec) = {
    val file = path.toFile
    val replacement = replace(file.read(), rrs:_*)

    if (replacement.replaced) {
      file.write(replacement.content)
      true
    } else false
  }

}

case class RegexReplacement(content: String, replaced: Boolean) {

  override val toString = content

}