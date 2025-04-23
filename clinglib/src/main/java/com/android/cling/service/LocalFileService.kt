package com.android.cling.service

import android.annotation.SuppressLint
import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.arch.core.executor.ArchTaskExecutor
import com.android.cling.util.Utils.HTTP_SERVLET_KEY
import com.android.cling.util.Utils.POST_LISTEN_DEFAULT
import com.android.cling.util.Utils.getContentType
import org.eclipse.jetty.server.Server
import org.eclipse.jetty.servlet.ServletContextHandler
import org.eclipse.jetty.servlet.ServletHolder
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.util.*
import javax.servlet.http.HttpServlet
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse


/**
 * 本地服务器，用来访问本地的文件
 * http://localhost:8080/localeFile/xxx.txt
 */
class LocalFileService : Service() {

    private var server = Server(POST_LISTEN_DEFAULT)
    private var timeReCreate = 0

    override fun onCreate() {
        super.onCreate()
        createServer()
    }

    @SuppressLint("RestrictedApi")
    private fun createServer() {
        ArchTaskExecutor.getIOThreadExecutor().execute {
            try {
                server = Server(POST_LISTEN_DEFAULT)
                startServer()
            } catch (e: Exception) {
                timeReCreate++
                if (timeReCreate > 10) {
                    e.printStackTrace()
                    return@execute
                }
                POST_LISTEN_DEFAULT += 1
                createServer()
                return@execute
            }
        }
    }

    private fun startServer() {
        val contextHandler = ServletContextHandler(ServletContextHandler.SESSIONS)
        contextHandler.contextPath = "/"
        server.handler = contextHandler
        contextHandler.addServlet(ServletHolder(FileServlet()), "/$HTTP_SERVLET_KEY/*")
        server.start()
        server.join()
    }


    override fun onBind(p0: Intent?): IBinder? {
        return null
    }


    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return super.onStartCommand(intent, flags, startId)
    }


    internal class FileServlet : HttpServlet() {
        @Throws(IOException::class)
        override fun doGet(request: HttpServletRequest, response: HttpServletResponse) {
            try {
                val filePath = request.pathInfo.substring(1)
                val file = File(filePath)
                if (file.exists() && file.isFile) {
                    response.contentType = getContentType(filePath)

                    //fix 如果是下载文件，需要设置ContentLength,因为我用的jetty版本降低，response.setContentLengthLong()不支持
                    response.setContentLength(file.length().toInt())
                    response.setHeader(
                        "Content-Disposition",
                        "attachment; filename=\"" + file.name + "\""
                    )

                    FileInputStream(file).use { fileInputStream ->
                        response.outputStream.use { outputStream ->
                            val buffer = ByteArray(64000)
                            var bytesRead: Int
                            while (fileInputStream.read(buffer).also { bytesRead = it } != -1) {
                                outputStream.write(buffer, 0, bytesRead)
                            }
                        }
                    }
                } else {
                    response.status = HttpServletResponse.SC_NOT_FOUND
                }
            } catch (e: Exception) {
                response.sendError(HttpServletResponse.SC_FORBIDDEN, e.message)
            }
        }
    }

    override fun onDestroy() {
        kotlin.runCatching {
            server.stop()
            server.destroy()
        }
        super.onDestroy()
    }
}