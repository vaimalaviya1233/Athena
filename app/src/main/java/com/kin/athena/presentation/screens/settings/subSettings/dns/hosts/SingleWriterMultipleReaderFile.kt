/* Copyright (C) 2024 Charles Lombardo <clombardo169@gmail.com>
 *
 * Derived from DNS66:
 * Copyright (C) 2016 - 2019 Julian Andres Klode <jak@jak-linux.org>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */


package com.kin.athena.presentation.screens.settings.subSettings.dns.hosts

import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream


class SingleWriterMultipleReaderFile(file: File) {
    val activeFile = file.absoluteFile
    val workFile = File(activeFile.absolutePath + ".dnsnet-new")

    @Throws(FileNotFoundException::class)
    fun openRead(): InputStream = FileInputStream(activeFile)


    @Throws(IOException::class)
    fun startWrite(): FileOutputStream {
        if (workFile.exists() && !workFile.delete()) {
            throw IOException("Cannot delete working file")
        }
        return FileOutputStream(workFile)
    }

    @Throws(IOException::class)
    fun finishWrite(stream: FileOutputStream) {
        try {
            stream.close()
        } catch (e: IOException) {
            failWrite(stream)
            throw e
        }
        if (!workFile.renameTo(activeFile)) {
            failWrite(stream)
            throw IOException("Cannot commit transaction")
        }
    }

    @Throws(IOException::class)
    fun failWrite(stream: FileOutputStream) {
        FileHelper.closeOrWarn(stream, "Cannot close working file")
        if (!workFile.delete()) {
            throw IOException("Cannot delete working file")
        }
    }
}