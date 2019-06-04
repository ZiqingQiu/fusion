/*
 * Copyright (c) 2018 Fuseinfo Inc.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package com.fuseinfo.fusion.spark.web

import java.io.{File, FileInputStream}
import java.net.URLEncoder
import java.util.regex.Pattern

import com.fuseinfo.fusion.spark.FusionHandler
import javax.servlet.http.{HttpServletRequest, HttpServletResponse}
import org.apache.commons.lang3.StringEscapeUtils
import org.apache.spark.sql.SparkSession
import org.mortbay.jetty.Request

import scala.collection.mutable

class DataHandler extends FusionHandler {
  val sqlBuffer = mutable.Map.empty[String, String]

  override def getContext: String = "/data"

  override def getRoles: Array[String] = Array("data")

  private val actionRegex = Pattern.compile("/+([^/]+)(.*)")

  override def handle(target: String, request: HttpServletRequest, response: HttpServletResponse, dispatch: Int): Unit = {

    val matcher = actionRegex.matcher(target)
    if (matcher.matches()) {
      val result = matcher.group(1) match {
        case "buffer" =>
          val userName = String.valueOf(request.getRemoteUser)
          sqlBuffer.getOrElseUpdate(userName, {
            val file = new File(userName + ".sql")
            val stream = if (file.canRead) new FileInputStream(file)
            else getClass.getClassLoader.getResourceAsStream(userName + ".sql")
            if (stream != null) scala.io.Source.fromInputStream(stream).mkString
            else ""
          })
        case "load" =>
          val paramMap = request.getParameterMap
          paramMap.get("sql") match {
            case Array(sql: String) =>
              val df = SparkSession.builder.getOrCreate.sql(sql)
              val result = df.head(1000)
              val length = result.length
              val sb = new StringBuilder("{\"draw\":1,\"recordsTotal\":")
              sb.append(length).append(",\"recordsFiltered\":").append(length).append(",\"data\":[ ")
              result.foreach { row =>
                sb.append('[')
                row.toSeq.foreach(f =>
                  sb.append('"').append(StringEscapeUtils.escapeJson(String.valueOf(f))).append("\","))
                sb.setCharAt(sb.length - 1, ']')
                sb.append(',')
              }
              sb.setCharAt(sb.length - 1, ']')
              sb.append('}')
              sb.toString
            case _ => ""
          }
        case "run" =>
          try {
            val paramMap = request.getParameterMap
            paramMap.get("sql") match {
              case Array(sql: String) =>
                val df = SparkSession.builder.getOrCreate.sql(sql)
                val encodedSql = URLEncoder.encode(sql, "UTF-8")
                """<!DOCTYPE html><html><head lang='en'><meta charset='utf-8'><meta content='width=device-width,initial-scale=1'
                name='viewport'><link rel="stylesheet" type="text/css" href="/css/dataTables.bootstrap.css">
                <link rel="stylesheet" href="/css/bootstrap.css"><script type="text/javascript" src="/js/jquery-3.3.1.js"></script>
                <script type="text/javascript" src="/js/jquery.dataTables.js"></script><script type="text/javascript" src="/js/bootstrap.js"></script>
                <script type="text/javascript" src="/js/dataTables.bootstrap.js"></script>""" +
                  "<script type='text/javascript' language='javascript' class='init'>\n" +
                  "$(document).ready(function() {$('#data').DataTable({\"processing\":true,\"ajax\":\"load?sql=" +
                  encodedSql + "\"});});</script></head><body>" +
                  "<table id='data' class='table table-striped table-bordered' cellspacing='0' width='100%'><thead><tr><th>" +
                  df.columns.mkString("</th><th>") + "</th></tr></thead></table></body></html>"
            }
          } catch {
            case e:Throwable =>
              e.printStackTrace()
              response.setContentType("text/html")
              """<html><body><script type='text/javascript'>
            setTimeout('self.close()',100);
            </script></body></html>"""
          }

        case _ => ""
      }
      val output = response.getWriter
      output.write(result)
      request match {
        case r: Request => r.setHandled(true)
        case _ =>
      }
    }
  }
}
