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
package com.fuseinfo.fusion.spark.reader

import com.fuseinfo.fusion.FusionFunction
import com.fuseinfo.fusion.spark.util.SparkUtils
import com.fuseinfo.fusion.util.VarUtils
import org.apache.spark.sql.SparkSession
import org.slf4j.LoggerFactory
import scala.collection.JavaConversions._

class AvroReader(taskName: String, params:java.util.Map[String, AnyRef]) extends FusionFunction {

  def this(taskName:String) = this(taskName, new java.util.HashMap[String, AnyRef])

  @transient private val logger = LoggerFactory.getLogger(this.getClass)

  override def init(params: java.util.Map[String, AnyRef]): Unit = {
    this.params.clear()
    this.params.putAll(params)
  }

  override def apply(vars:java.util.Map[String, String]): String = {
    val enrichedParams = params.filter(_._2.isInstanceOf[String])
      .mapValues(v => VarUtils.enrichString(v.toString, vars))
    val path = SparkUtils.stdPath(enrichedParams("path"))
    logger.info("{} Reading AVRO from {}", taskName, path:Any)
    val spark = SparkSession.getActiveSession.getOrElse(SparkSession.getDefaultSession.get)
    val df = spark.read.format("avro").load(path)
    SparkUtils.registerDataFrame(df, taskName, enrichedParams)
    s"Read Avro files from $path lazily"
  }

  override def getProcessorSchema:String = """{"title": "AvroReader","type": "object","properties": {
    "__class":{"type":"string","options":{"hidden":true},"default":"spark.reader.AvroReader"},
    "path":{"type":"string","description":"Path of the avro files"},
    "repartition":{"type":"string","format":"number","description":"Number of partitions"},
    "cache":{"type":"string","description":"cache the DataFrame?"}
    },"required":["__class","path"]}"""
}

