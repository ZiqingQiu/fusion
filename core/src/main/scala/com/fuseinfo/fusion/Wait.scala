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
package com.fuseinfo.fusion

class Wait(taskName:String, params: java.util.Map[String, AnyRef])
  extends (java.util.Map[String, String] => String) with Serializable {

  def this(taskName:String) = this(taskName, new java.util.HashMap[String, AnyRef])

  override def apply(vars:java.util.Map[String, String]): String = {
    Thread.sleep(Long.MaxValue)
    "Wake up"
  }

  def getProcessorSchema:String = """{"title": "Wait","type": "object","properties": {
    "__class":{"type":"string","options":{"hidden":true},"default":"Wait"}
    },"required":["__class"]}"""
}
