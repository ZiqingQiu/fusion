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
package com.fuseinfo.fusion.spark.util

import org.apache.spark.sql.{Dataset, Row}
import org.apache.spark.storage.StorageLevel

object SparkUtils {
  def registerDataFrame(df: Dataset[Row], tableName:String, params:java.util.Map[String, AnyRef]): Unit = {
    val df2 = params.get("repartition") match {
      case num:String => df.repartition(num.toInt)
      case _ => df
    }
    params.get("cache") match {
      case "true" => df2.cache
      case str:String => try {df2.persist(StorageLevel.fromString(str))} catch {case e:Exception => df2.persist()}
      case _ =>
    }
    df2.createOrReplaceTempView(tableName.toUpperCase)
  }
}