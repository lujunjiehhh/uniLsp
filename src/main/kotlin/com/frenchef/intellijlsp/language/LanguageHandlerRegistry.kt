package com.frenchef.intellijlsp.language

import com.frenchef.intellijlsp.language.go.GoLanguageHandler
import com.frenchef.intellijlsp.language.js.JavaScriptLanguageHandler
import com.frenchef.intellijlsp.language.rust.RustLanguageHandler
import com.frenchef.intellijlsp.language.xml.XmlLanguageHandler

import com.intellij.openapi.diagnostic.logger
import com.intellij.psi.PsiFile

/**
 * 语言处理器注册表 - 管理语言处理器的注册和查找
 * 
 * 按照优先级顺序查找能处理指定文件的处理器。
 * JVM 处理器优先，通用处理器作为回退方案。
 */
object LanguageHandlerRegistry {
    private val log = logger<LanguageHandlerRegistry>()
    private val handlers = mutableListOf<LanguageHandler>()
    private val genericHandler = GenericLanguageHandler()

    init {
        // 注册默认处理器（按优先级顺序）
        register(JvmLanguageHandler())
        register(JavaScriptLanguageHandler())
        register(GoLanguageHandler())
        register(RustLanguageHandler())
        register(XmlLanguageHandler())
        // GenericLanguageHandler 作为回退，不添加到列表
    }

    /**
     * 注册语言处理器
     * 新注册的处理器优先级高于已有处理器
     */
    fun register(handler: LanguageHandler) {
        handlers.add(0, handler)
        log.info("Registered LanguageHandler: ${handler.javaClass.simpleName}")
    }

    /**
     * 获取能处理指定文件的语言处理器
     * 如果没有专用处理器，返回通用处理器
     */
    fun getHandler(file: PsiFile): LanguageHandler {
        val handler = handlers.find { it.supports(file) }
        if (handler != null) {
            log.debug("Using ${handler.javaClass.simpleName} for ${file.language.id}")
            return handler
        }
        log.debug("Using GenericLanguageHandler (fallback) for ${file.language.id}")
        return genericHandler
    }

    /**
     * 检查是否有专用处理器支持该文件
     */
    fun hasSpecializedHandler(file: PsiFile): Boolean {
        return handlers.any { it.supports(file) }
    }
}
