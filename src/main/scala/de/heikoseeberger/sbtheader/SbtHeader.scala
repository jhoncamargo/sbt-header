/*
 * Copyright 2015 Heiko Seeberger
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package de.heikoseeberger.sbtheader

import java.io.File
import java.nio.file.Files
import sbt._
import scala.collection.JavaConversions._
import scala.util.matching.Regex

object SbtHeader extends AutoPlugin {

  object autoImport {

    object HeaderPattern {
      val javaScala: Regex = """(?s)(/\*(?!\*).*?\*/(?:\n|(?:\r\n))+)(.*)""".r
      val python: Regex = """((?:#.*(?:\n|(?:\r\n)))+(?:\n|(?:\r\n))+)((?:.|\n|(?:\r\n))*)""".r
    }

    val headers: SettingKey[Map[String, (Regex, String)]] =
      settingKey("""Header pattern and text by extension; empty by default""")

    val createHeaders: TaskKey[Iterable[File]] =
      taskKey("Create/update headers")
  }

  private val shebangAndBody = """(#!.*(?:\n|(?:\r\n))+)((?:.|\n|(?:\r\n))*)""".r

  override def projectSettings: Seq[Setting[_]] =
    List(
      Keys.sources in autoImport.createHeaders := (Keys.sources in Compile).value ++ (Keys.sources in Test).value,
      autoImport.headers := Map.empty,
      autoImport.createHeaders := createHeaders(
        (Keys.sources in autoImport.createHeaders).value.toList,
        autoImport.headers.value,
        Keys.streams.value.log
      )
    )

  override def trigger: PluginTrigger = allRequirements

  private def createHeaders(sources: Seq[File], headers: Map[String, (Regex, String)], log: Logger): Iterable[File] = {
    val touchedFiles = sources
      .groupBy(_.extension)
      .collect { case (Some(ext), files) => headers.get(ext).map(_ -> files) }
      .flatten
      .flatMap { case ((headerPattern, headerText), files) => files.flatMap(createHeader(headerPattern, headerText)) }
    log.info(s"Headers created for ${touchedFiles.size} files${if (touchedFiles.isEmpty) "" else f":%n  " + touchedFiles.mkString(f"%n  ")}")
    touchedFiles
  }

  private def createHeader(headerPattern: Regex, headerText: String)(file: File): Option[File] = {
    def write(text: String) = Files.write(file.toPath, text.split(newLine).toList).toFile
    val (firstLine, text) = Files.readAllLines(file.toPath).mkString(newLine) match {
      case shebangAndBody(s, b) => (s, b)
      case other                => ("", other)
    }
    val modifiedText = text match {
      case headerPattern(`headerText`, _) => None
      case headerPattern(_, body)         => Some(firstLine + headerText + body)
      case body if body.isEmpty           => None
      case body                           => Some(firstLine + headerText + body.replaceAll("""^\s+""", "")) // Trim left
    }
    modifiedText.map(write)
  }
}